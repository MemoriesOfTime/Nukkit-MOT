package cn.nukkit.level;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.SubChunkRequestPacket;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntBiFunction;

/**
 * 每玩家子区块请求的延期队列与流量记账：包数/offset 总数双上限的排队、按昂贵条目扣减的
 * 预算排空、当前 tick 的响应字节账本与昂贵条目账本（立即应答与排空共用同一上限——高频
 * 单 offset 请求包无法绕过）。由 SubChunkRequestProcessor 驱动。主线程专用。
 * <p>
 * Per-player deferred sub-chunk request queue and flow accounting: queueing under
 * packet/offset caps, expensive-entry-budgeted draining, and the current tick's
 * response-byte and expensive-entry ledgers (immediate answers and drains share one
 * ceiling — bursts of single-offset request packets cannot bypass it). Driven by
 * SubChunkRequestProcessor. Main-thread only.
 */
public class PlayerSubChunkRequestState {

    // 队列上限：包数与 offset 总数，超限丢弃新请求（客户端会重新请求）
    // Queue caps: packets and total offsets; new requests are dropped over the caps (the client re-requests)
    private static final int MAX_DEFERRED_REQUESTS = 256;
    private static final int MAX_DEFERRED_OFFSETS = 65536;
    // 每 tick 每玩家响应序列化字节上限，超出部分留待下 tick / per-player per-tick response
    // byte cap; the excess waits for the next tick
    private static final int MAX_RESPONSE_TICK_BYTES = 4 * 1024 * 1024;
    // miss 补发积压上限（哈希数），超限丢弃并释放（客户端经重新请求重试）
    // / Miss-response backlog cap (hashes); over the cap entries drop and release
    // (the client retries via re-requests)
    private static final int MAX_DEFERRED_MISS_HASHES = 65536;

    private final ArrayDeque<SubChunkRequestPacket> deferredRequests = new ArrayDeque<>();
    // 队列保留的 offset 总数（空列表请求按 1 计）/ offsets retained in the queue (an empty list counts as 1)
    private int deferredOffsets;
    // miss 补发积压（BlobStatus 回报超出本 tick 字节预算的部分），按批排队、队头排空
    // / Miss-response backlog (the part of a BlobStatus report over this tick's byte
    // budget), queued per batch and drained from the head
    private final ArrayDeque<long[]> deferredMissBatches = new ArrayDeque<>();
    private int deferredMissHashes;
    // 当前 tick 累计的响应序列化字节（立即应答与排空共用）/ response bytes accumulated
    // in the current tick (immediate answers and drains share it)
    private int responseBytesTick = -1;
    private int responseBytesThisTick;
    // 当前 tick 累计的昂贵条目数（立即应答与排空共用）：高频单 offset 请求包不能绕过
    // 每 tick 条目预算 / expensive entries accumulated in the current tick (immediate
    // answers and drains share it): bursts of single-offset request packets must not
    // bypass the per-tick entry budget
    private int spentEntriesTick = -1;
    private int spentEntriesThisTick;

    /**
     * 请求的 offset 数（空列表表示单个中心位置）。
     * <p>
     * Offset count of a request (an empty list means the single center position).
     */
    public static int requestOffsetCount(SubChunkRequestPacket pk) {
        return pk.positionOffsets.isEmpty() ? 1 : pk.positionOffsets.size();
    }

    /**
     * 去重重复 offset：重复条目只放大序列化与排队成本，无语义价值。
     * <p>
     * Dedups repeated offsets: duplicates only amplify serialization and queueing
     * cost, with no semantic value.
     */
    public static void dedupeRequestOffsets(SubChunkRequestPacket pk) {
        List<BlockVector3> offsets = pk.positionOffsets;
        if (offsets.size() <= 1) {
            return;
        }
        LongOpenHashSet seen = new LongOpenHashSet(offsets.size());
        List<BlockVector3> unique = new ArrayList<>(offsets.size());
        for (BlockVector3 offset : offsets) {
            // 各分量为有符号字节，& 0xFF 后无损打包 / components are signed bytes;
            // & 0xFF packs them losslessly
            long key = ((long) (offset.x & 0xFF) << 16) | ((offset.y & 0xFF) << 8) | (offset.z & 0xFF);
            if (seen.add(key)) {
                unique.add(offset);
            }
        }
        if (unique.size() != offsets.size()) {
            pk.positionOffsets = unique;
        }
    }

    /**
     * 入队一个新请求；包数或 offset 总数超上限时拒绝（客户端会重新请求）。
     * <p>
     * Enqueues a new request; rejected over the packet or offset cap (the client re-requests).
     */
    public boolean tryDefer(SubChunkRequestPacket pk) {
        int count = requestOffsetCount(pk);
        if (this.deferredRequests.size() >= MAX_DEFERRED_REQUESTS
                || this.deferredOffsets + count > MAX_DEFERRED_OFFSETS) {
            return false;
        }
        this.deferredRequests.add(pk);
        this.deferredOffsets += count;
        return true;
    }

    /**
     * 重排队尾，无上限检查：剩余必小于刚出队的请求。
     * <p>
     * Requeues at the tail without cap checks: the residual is always smaller than
     * the request just dequeued.
     */
    public void requeue(SubChunkRequestPacket residual) {
        this.deferredRequests.add(residual);
        this.deferredOffsets += requestOffsetCount(residual);
    }

    /**
     * 按条目预算排空延期请求：answerer 消费 (pk, budget) 并返回消耗的昂贵条目数，
     * 未答完的剩余 offset 由 answerer 自行重排队尾。预算（可为 0，表示本 tick 已耗尽，
     * 留待下 tick）或字节上限耗尽即停。
     * <p>
     * Drains deferred requests under an entry budget: the answerer consumes (pk, budget)
     * and returns the expensive entries spent; unanswered offsets are requeued by the
     * answerer itself. Stops once the budget (may be 0 — exhausted this tick, wait for
     * the next) or the byte ceiling is exhausted.
     */
    public int drain(ToIntBiFunction<SubChunkRequestPacket, Integer> answerer,
                     int budget, BooleanSupplier keepGoing) {
        int remaining = Math.max(0, budget);
        int spent = 0;
        while (!this.deferredRequests.isEmpty() && remaining > 0 && keepGoing.getAsBoolean()) {
            SubChunkRequestPacket pk = this.deferredRequests.poll();
            this.deferredOffsets -= requestOffsetCount(pk);
            int consumed = Math.max(0, answerer.applyAsInt(pk, remaining));
            spent += consumed;
            remaining -= Math.min(consumed, remaining);
        }
        return spent;
    }

    public boolean hasDeferred() {
        return !this.deferredRequests.isEmpty();
    }

    public int deferredPacketCount() {
        return this.deferredRequests.size();
    }

    public int deferredOffsetCount() {
        return this.deferredOffsets;
    }

    public SubChunkRequestPacket peekDeferred() {
        return this.deferredRequests.peek();
    }

    /**
     * 当前 tick 的响应字节记账：跨 tick 自动清零，立即应答与排空共用同一上限。
     * <p>
     * Per-tick response byte accounting: auto-zeroed across ticks; the immediate-answer
     * path and the drain share the same ceiling.
     */
    public void accountResponseBytes(int tick, int bytes) {
        if (this.responseBytesTick != tick) {
            this.responseBytesTick = tick;
            this.responseBytesThisTick = 0;
        }
        this.responseBytesThisTick += bytes;
    }

    public boolean responseBytesExceeded(int tick) {
        return this.responseBytesTick == tick
                && this.responseBytesThisTick >= MAX_RESPONSE_TICK_BYTES;
    }

    /**
     * 当前 tick 剩余响应字节预算（子区块应答与 miss 补发共用同一账本，跨 tick 自动重置）。
     * <p>
     * Remaining response byte budget this tick (sub-chunk answers and miss responses
     * share one ledger; auto-reset across ticks).
     */
    public int remainingResponseBytes(int tick) {
        if (this.responseBytesTick != tick) {
            return MAX_RESPONSE_TICK_BYTES;
        }
        return Math.max(0, MAX_RESPONSE_TICK_BYTES - this.responseBytesThisTick);
    }

    /**
     * 本 tick 剩余条目预算（tick 预算 − 累计已消耗；跨 tick 自动重置）。立即应答与
     * 排空都以剩余量为准，二者合计不得超每 tick 预算。
     * <p>
     * Remaining entry budget this tick (tick budget − accumulated spent; auto-reset
     * across ticks). Immediate answers and drains both run on the remainder so their
     * combined spend stays within the per-tick budget.
     */
    public int remainingEntryBudget(int tick, int tickBudget) {
        if (this.spentEntriesTick != tick) {
            return tickBudget;
        }
        return Math.max(0, tickBudget - this.spentEntriesThisTick);
    }

    /**
     * 记账本 tick 消耗的昂贵条目数（立即应答与排空共用同一账本）。
     * <p>
     * Accounts expensive entries spent this tick (immediate answers and drains share
     * the same ledger).
     */
    public void accountSpentEntries(int tick, int spent) {
        if (spent <= 0) {
            return;
        }
        if (this.spentEntriesTick != tick) {
            this.spentEntriesTick = tick;
            this.spentEntriesThisTick = 0;
        }
        this.spentEntriesThisTick += spent;
    }

    /**
     * 清空延期队列、miss 积压与字节账本（切维度/换世界/下线时）。
     * <p>
     * Clears the deferred queue, miss backlog and byte ledgers (dimension switch / world change / quit).
     */
    public void clear() {
        this.deferredRequests.clear();
        this.deferredOffsets = 0;
        this.deferredMissBatches.clear();
        this.deferredMissHashes = 0;
        this.responseBytesTick = -1;
        this.responseBytesThisTick = 0;
        this.spentEntriesTick = -1;
        this.spentEntriesThisTick = 0;
    }

    /**
     * miss 补发积压入队（BlobStatus 回报超出本 tick 字节预算的部分）；哈希总数超上限时
     * 拒绝，调用方应直接 resolve 释放 pin（客户端经重新请求重试）。
     * <p>
     * Enqueues a miss-response backlog batch (the part of a BlobStatus report over
     * this tick's byte budget); rejected over the total-hash cap — the caller should
     * resolve right away to release the pin (the client retries via re-requests).
     */
    public boolean tryDeferMissBatch(long[] hashes) {
        if (this.deferredMissHashes + hashes.length > MAX_DEFERRED_MISS_HASHES) {
            return false;
        }
        this.deferredMissBatches.add(hashes);
        this.deferredMissHashes += hashes.length;
        return true;
    }

    /**
     * 队头取一批积压的 miss 哈希（无则 null）。
     * <p>
     * Polls the head miss backlog batch (null when empty).
     */
    public long[] pollMissBatch() {
        long[] batch = this.deferredMissBatches.poll();
        if (batch != null) {
            this.deferredMissHashes -= batch.length;
        }
        return batch;
    }

    /**
     * 未补发完的剩余部分推回队头（本 tick 预算耗尽时）。
     * <p>
     * Pushes an unsent residual back to the head (byte budget exhausted this tick).
     */
    public void pushMissBatchFront(long[] batch) {
        this.deferredMissBatches.addFirst(batch);
        this.deferredMissHashes += batch.length;
    }

    public int deferredMissHashCount() {
        return this.deferredMissHashes;
    }
}
