package cn.nukkit.level;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.SubChunkPacket;
import cn.nukkit.network.protocol.SubChunkRequestPacket;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntBiFunction;

/**
 * 每玩家子区块请求的延期队列与流量记账：包数/offset 总数双上限的排队、按 offset 扣减的
 * in-flight 提交、当前 tick 的响应字节账本与条目账本（发送侧；立即提交与排空共用同一上限
 * ——高频单 offset 请求包无法绕过）。序列化在异步线程执行（见
 * {@link cn.nukkit.network.process.processor.common.SubChunkRequestProcessor}），完成应答经
 * {@link #offerCompletion} 回主线程发送。除完成队列外主线程专用。
 * <p>
 * Per-player deferred sub-chunk request queue and flow accounting: queueing under
 * packet/offset caps, in-flight-credited submission, and the current tick's
 * response-byte and entry ledgers (send-side; immediate submissions and drains share
 * one ceiling — bursts of single-offset request packets cannot bypass them).
 * Serialization runs on the async thread (see
 * {@link cn.nukkit.network.process.processor.common.SubChunkRequestProcessor}); completed
 * answers return to the main thread via {@link #offerCompletion} for sending. Main-thread
 * only except the completion queue.
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
    // 已提交异步序列化未发送的 offset 上限：承载原每 tick 条目预算的防 burst 语义
    //（串行化离主线程后，改为在途量门控；高频单 offset 请求包同样不能绕过）
    // / Cap on offsets submitted for async serialization but not yet sent: carries the
    // anti-burst role of the old per-tick entry budget once serialization left the main
    // thread (bursts of single-offset packets cannot bypass it either)
    public static final int MAX_IN_FLIGHT_OFFSETS = 256;

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
    // 当前 tick 累计的昂贵条目数（发送侧记账，立即提交与排空共用）：高频单 offset 请求包
    // 不能绕过每 tick 条目预算 / expensive entries accumulated in the current tick
    // (send-side; immediate submissions and drains share it): bursts of single-offset
    // request packets must not bypass the per-tick entry budget
    private int spentEntriesTick = -1;
    private int spentEntriesThisTick;

    // ---- 异步序列化回传（唯一跨线程结构）---- / async serialization return (the only cross-thread structure) ----
    // worker 线程 offer、主线程 poll；双端队列支持字节/条目预算耗尽时推回队头
    // / offered by the worker, polled on main; a deque so a budget-blocked head can be
    // pushed back to the front
    private final ConcurrentLinkedDeque<CompletedSubChunkAnswer> completions = new ConcurrentLinkedDeque<>();
    // 已提交未发送的 offset 数（主线程专用）/ offsets submitted but not yet sent (main-thread only)
    private int inFlightOffsets;
    // clear() 递增；完成应答携带提交时快照，epoch 不符即丢弃（下线/切维度作废在途回包）
    // / bumped by clear(); a completion carries the submit-time snapshot and is dropped
    // on mismatch (in-flight answers die on quit / dimension switch)
    private int epoch;

    /**
     * 一次异步序列化的完成应答：worker 构建的条目列表与缓存模式下的 blob 组装材料
     * （blobId/data 的最终组装在主线程发送时完成——tryTrackClientCacheBlob 是玩家状态记账）。
     * <p>
     * A completed async serialization: the worker-built entry list plus, in cache mode,
     * the blob assembly parts (blobId/data are finalized on the main thread at send —
     * tryTrackClientCacheBlob is player-state accounting).
     */
    public static final class CompletedSubChunkAnswer {
        public final int epoch;
        public final SubChunkRequestPacket request;
        public final boolean cacheMode;
        public final List<SubChunkPacket.SubChunkData> entries;
        /** 每条目的 section 字节哈希（0 = 该条目不参与 blob 跟踪），仅 cacheMode / per-entry section hash (0 = not trackable), cacheMode only */
        public final long[] blobHashes;
        /** 每条目的 section 字节（跟踪成功时 pin 的载荷），仅 cacheMode / per-entry section bytes (the payload pinned on a successful track), cacheMode only */
        public final byte[][] blobSections;
        /** 每条目的方块实体字节（跟踪成功时替换 data），仅 cacheMode / per-entry block-entity bytes (replaces data on a successful track), cacheMode only */
        public final byte[][] blobEntities;
        /**
         * 提交时占用的 in-flight offset 数，发送时归还；epoch 失配丢弃时不归还——clear() 已把
         * 计数清零，再减会扣到新 epoch 提交的额度
         * / in-flight offsets held at submit, released on send; NOT released on an
         * epoch-mismatch drop — clear() already zeroed the counter, releasing again would
         * take credit owned by new-epoch submissions
         */
        public final int offsets;

        public CompletedSubChunkAnswer(int epoch, SubChunkRequestPacket request, boolean cacheMode,
                                       List<SubChunkPacket.SubChunkData> entries,
                                       long[] blobHashes, byte[][] blobSections, byte[][] blobEntities,
                                       int offsets) {
            this.epoch = epoch;
            this.request = request;
            this.cacheMode = cacheMode;
            this.entries = entries;
            this.blobHashes = blobHashes;
            this.blobSections = blobSections;
            this.blobEntities = blobEntities;
            this.offsets = offsets;
        }
    }

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
     * 按预算排空延期请求：answerer 消费 (pk, budget) 并返回消耗量（现行调用为提交异步
     * 序列化的 offset 数，从 in-flight 额度扣减），未答完的剩余 offset 由 answerer 自行
     * 重排队尾。预算（可为 0，表示本 tick 已耗尽，留待下 tick）或字节上限耗尽即停。
     * <p>
     * Drains deferred requests under a budget: the answerer consumes (pk, budget) and
     * returns what it spent (the current caller submits offsets for async serialization,
     * charged against in-flight credit); unanswered offsets are requeued by the answerer
     * itself. Stops once the budget (may be 0 — exhausted this tick, wait for the next)
     * or the byte ceiling is exhausted.
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
     * 清空延期队列、miss 积压、完成队列与全部账本（切维度/换世界/下线时）；epoch 递增使
     * 在途的异步回包作废。
     * <p>
     * Clears the deferred queue, miss backlog, completion queue and all ledgers
     * (dimension switch / world change / quit); the epoch bump invalidates in-flight
     * async answers.
     */
    public void clear() {
        this.deferredRequests.clear();
        this.deferredOffsets = 0;
        this.deferredMissBatches.clear();
        this.deferredMissHashes = 0;
        this.completions.clear();
        this.inFlightOffsets = 0;
        this.epoch++;
        this.responseBytesTick = -1;
        this.responseBytesThisTick = 0;
        this.spentEntriesTick = -1;
        this.spentEntriesThisTick = 0;
    }

    // ---- in-flight 与完成应答（见类注释的线程边界）---- / in-flight and completions (thread edges in the class javadoc) ----

    public int acquireInFlightOffsets(int desired) {
        if (desired <= 0) {
            return 0;
        }
        int granted = Math.min(desired, MAX_IN_FLIGHT_OFFSETS - this.inFlightOffsets);
        this.inFlightOffsets += granted;
        return granted;
    }

    /**
     * 归还已发送应答占用的 in-flight 额度（epoch 失配的应答不归还，见
     * {@link CompletedSubChunkAnswer#offsets}）。
     * <p>
     * Releases in-flight credit held by sent answers (epoch-mismatched answers do not
     * release; see {@link CompletedSubChunkAnswer#offsets}).
     */
    public void releaseInFlightOffsets(int count) {
        this.inFlightOffsets = Math.max(0, this.inFlightOffsets - count);
    }

    public int remainingInFlightOffsets() {
        return Math.max(0, MAX_IN_FLIGHT_OFFSETS - this.inFlightOffsets);
    }

    public int inFlightOffsetCount() {
        return this.inFlightOffsets;
    }

    public int epoch() {
        return this.epoch;
    }

    /**
     * worker 线程回传一个完成应答（唯一跨线程写入口）。
     * <p>
     * Returns a completed answer from the worker (the only cross-thread write entry point).
     */
    public void offerCompletion(CompletedSubChunkAnswer answer) {
        this.completions.add(answer);
    }

    public CompletedSubChunkAnswer pollCompletion() {
        return this.completions.poll();
    }

    public void pushCompletionFront(CompletedSubChunkAnswer answer) {
        this.completions.addFirst(answer);
    }

    public boolean hasCompletions() {
        return !this.completions.isEmpty();
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
