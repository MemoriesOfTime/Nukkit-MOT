package cn.nukkit.level;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * 每玩家客户端缓存事务状态：pending 哈希集合 + 每列一个事务 + 无事务独立记账（trackBlob），
 * 三者均有超时强关安全阀。仅主线程。
 * <p>
 * Per-player client cache transaction state: pending hashes, one transaction per chunk
 * and standalone trackBlob entries — all covered by the timeout force-close valve.
 * Main-thread only.
 */
public class PlayerBlobCacheState {

    private final ClientCacheBlobStore store;
    private final int pendingCap;

    private final LongOpenHashSet pending = new LongOpenHashSet();
    private final ArrayDeque<Transaction> transactions = new ArrayDeque<>();
    // 无事务独立记账的入队 tick（键 = 有独立 pin 且未被事务收养的哈希），供超时清扫
    // / Ticks when standalone (trackBlob) entries were tracked (keys = hashes with a
    // standalone pin not adopted by a transaction), for the timeout sweep
    private final Long2IntOpenHashMap trackTicks = new Long2IntOpenHashMap();

    public PlayerBlobCacheState(ClientCacheBlobStore store, int pendingCap) {
        this.store = store;
        this.pendingCap = Math.max(1, pendingCap);
    }

    /**
     * 开一个列事务。要求哈希对应的 blob 已写入仓库（由装配方负责 put），本方法只 pin。
     * 重复哈希按去重后计一份 pin（空子区块等重复内容若按元素 pin 而按集合 unpin 会永久泄漏）。
     * pending 超限或仓库 pinned 字节硬顶超限时返回 false，调用方应降级为全量发送。
     * <p>
     * Opens a chunk transaction. Blobs must already be in the store (the assembler puts them);
     * this only pins. Duplicate hashes account for one pin per distinct hash (pinning per
     * element while unpinning per set would permanently leak repeated content such as empty
     * sub-chunks). Returns false over the pending cap or the store's pinned-byte hard cap —
     * the caller should degrade to a full send.
     */
    public boolean tryOpenTransaction(long[] hashes, int openTick) {
        LongOpenHashSet distinct = new LongOpenHashSet(hashes);
        // 上限只计新增：已 pending 的哈希不重复占额，否则大视野首屏的空子区块重复内容
        // 会被误判超限而整体降级全量
        // / The cap counts only additions: hashes already pending must not
        // double-charge, or a large first screen of duplicate empty sub-chunks
        // misjudges the cap and degrades wholesale to full sends
        int additions = 0;
        LongIterator it = distinct.iterator();
        while (it.hasNext()) {
            if (!this.pending.contains(it.nextLong())) {
                additions++;
            }
        }
        if (this.pending.size() + additions > this.pendingCap) {
            return false;
        }
        if (!this.store.canPinBatch(distinct.toLongArray())) {
            // pinned 字节硬顶：不部分提交，整体降级 / pinned-byte hard cap: no partial
            // commit, the whole transaction degrades
            return false;
        }
        for (long hash : distinct) {
            if (this.pending.add(hash) || this.heldByOpenTransaction(hash)) {
                this.store.pin(hash);
            } else {
                // 已 pending 且无事务持有：复用 trackBlob 的独立 pin，随本事务解除；
                // 收养后不再是独立记账，退出超时清扫
                // / Already pending without a transaction: the standalone trackBlob pin is
                // reused and releases with this transaction; adopted entries leave the sweep
                this.trackTicks.remove(hash);
            }
        }
        this.transactions.add(new Transaction(distinct, openTick));
        return true;
    }

    private boolean heldByOpenTransaction(long hash) {
        for (Transaction tx : this.transactions) {
            if (tx.hashes.contains(hash)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 无事务的单 blob 记账（子区块条目用）：put+pin+pending 并记录 tick 供超时清扫；
     * 重复 track 刷新超时窗口。pending 超限或 pinned 字节硬顶超限时返回 false。
     * <p>
     * Tracks a single blob without a transaction (sub-chunk entries): put, pin, mark
     * pending and stamp the tick for the timeout sweep; re-tracking refreshes the
     * deadline. Returns false over the pending cap or the pinned-byte hard cap.
     */
    public boolean trackBlob(long hash, byte[] payload, int trackTick) {
        if (this.pending.contains(hash)) {
            if (this.trackTicks.containsKey(hash)) {
                this.trackTicks.put(hash, trackTick);
            }
            return true;
        }
        if (this.pending.size() + 1 > this.pendingCap) {
            return false;
        }
        this.store.put(hash, payload);
        if (!this.store.tryPin(hash)) {
            // pinned 字节硬顶：blob 留在仓库按未 pin LRU 管理不泄漏，本条降级 raw
            // / pinned-byte hard cap: the blob stays in the store as unpinned LRU
            // content (no leak); this entry degrades to raw
            return false;
        }
        this.pending.add(hash);
        this.trackTicks.put(hash, trackTick);
        return true;
    }

    /**
     * 客户端 hit/miss 后解除一个哈希：从所有包含它的事务移除（事务清空即关闭），解除 pin；
     * 无事务的独立记账（trackBlob）也在此解除 pin。
     * <p>
     * Resolves a hash after a client hit/miss: removed from every transaction holding it
     * (empty transactions close), unpinning each reference; standalone trackBlob pins
     * release here too.
     */
    public void resolve(long hash) {
        boolean inAnyTransaction = false;
        for (Transaction tx : this.transactions) {
            if (tx.hashes.remove(hash)) {
                this.store.unpin(hash);
                inAnyTransaction = true;
            }
        }
        this.transactions.removeIf(tx -> tx.hashes.isEmpty());
        if (this.pending.remove(hash) && !inAnyTransaction) {
            this.store.unpin(hash);
            this.trackTicks.remove(hash);
        }
    }

    /**
     * 关闭超时事务并清扫超时的无事务独立记账（客户端未回应 BlobStatus 的安全阀），
     * 返回强关数量供日志。仍被存活事务持有的哈希保留 pending——提前移除会让后续
     * trackBlob 重复记账、resolve 因 inAnyTransaction 跳过解钉，泄漏一条连 clear()
     * 都无法释放的永久 pin（仓库为全服共享）。
     * <p>
     * Force-closes transactions older than the timeout and sweeps expired standalone
     * trackBlob entries (safety valve for clients that never answer BlobStatus).
     * Returns how many were closed, for logging. Hashes still held by live
     * transactions stay pending — removing them early lets a later trackBlob
     * re-account while resolve skips the unpin (inAnyTransaction), leaking a
     * permanent pin that not even clear() releases (the store is server-wide).
     */
    public int closeExpired(int currentTick, int timeoutTicks) {
        int closed = 0;
        ArrayDeque<Transaction> expired = null;
        while (!this.transactions.isEmpty() && currentTick - this.transactions.peekFirst().openTick > timeoutTicks) {
            Transaction tx = this.transactions.pollFirst();
            if (expired == null) {
                expired = new ArrayDeque<>();
            }
            expired.add(tx);
            closed++;
        }
        if (expired != null) {
            LongOpenHashSet stillHeld = null;
            if (!this.transactions.isEmpty()) {
                stillHeld = new LongOpenHashSet();
                for (Transaction tx : this.transactions) {
                    for (long hash : tx.hashes) {
                        stillHeld.add(hash);
                    }
                }
            }
            for (Transaction tx : expired) {
                for (long hash : tx.hashes) {
                    this.store.unpin(hash);
                    if (stillHeld == null || !stillHeld.contains(hash)) {
                        this.pending.remove(hash);
                    }
                }
            }
        }
        closed += this.sweepExpiredStandalone(currentTick, timeoutTicks);
        return closed;
    }

    /**
     * 清扫超时的独立记账：解钉并移出 pending。trackTicks 的键恒为未被事务收养的
     * 独立 pin，故不会与事务释放重复解钉。
     * <p>
     * Sweeps expired standalone entries: unpin and drop from pending. trackTicks keys
     * are by construction standalone pins not adopted by a transaction, so this never
     * double-releases against the transaction path.
     */
    private int sweepExpiredStandalone(int currentTick, int timeoutTicks) {
        if (this.trackTicks.isEmpty()) {
            return 0;
        }
        int swept = 0;
        Iterator<Long2IntMap.Entry> it = this.trackTicks.long2IntEntrySet().iterator();
        while (it.hasNext()) {
            Long2IntMap.Entry entry = it.next();
            if (currentTick - entry.getIntValue() > timeoutTicks) {
                long hash = entry.getLongKey();
                it.remove();
                if (this.pending.remove(hash)) {
                    this.store.unpin(hash);
                }
                swept++;
            }
        }
        return swept;
    }

    /**
     * 清空全部状态（切维度/下线），解除所有 pin（事务 pin 与 trackBlob 独立 pin）。
     * <p>
     * Clears all state (dimension switch / disconnect), unpinning everything
     * (transaction pins and standalone trackBlob pins).
     */
    public void clear() {
        for (Transaction tx : this.transactions) {
            for (long hash : tx.hashes) {
                this.store.unpin(hash);
                this.pending.remove(hash);
            }
        }
        for (long hash : this.pending) {
            this.store.unpin(hash);
        }
        this.transactions.clear();
        this.pending.clear();
        this.trackTicks.clear();
    }

    public int getOpenTransactionCount() {
        return this.transactions.size();
    }

    public int getPendingSize() {
        return this.pending.size();
    }

    private static final class Transaction {
        private final LongOpenHashSet hashes;
        private final int openTick;

        private Transaction(LongOpenHashSet hashes, int openTick) {
            this.hashes = hashes;
            this.openTick = openTick;
        }
    }
}
