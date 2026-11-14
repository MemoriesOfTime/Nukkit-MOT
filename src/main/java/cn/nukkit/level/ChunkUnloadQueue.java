package cn.nukkit.level;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.function.LongSupplier;

/**
 * 未使用区块的到期卸载队列：最旧优先，重新入队移到队尾，因此入队顺序就是到期顺序。
 * <p>每次入队分配新的 revision，结算只处理捕获的那个 revision——同毫秒重排不会被旧请求覆盖。
 * <p>Chunks waiting out their unload grace period, oldest first: a chunk queued again moves to the
 * tail, so insertion order is due order and a pass can stop at the first entry that is not due. Each
 * admission also takes a fresh revision, so a settlement only handles the request it captured.
 */
final class ChunkUnloadQueue {

    /** 区块没人使用后保持加载的宽限时长（毫秒）。/ Grace period in milliseconds. */
    static final long GRACE_MILLIS = 20_000L;

    /**
     * {@link #drain} 需要的 Level 侧判断，独立接口让队列逻辑可以脱离 Level 单测。
     * <p>Level-side decisions used by {@link #drain}, kept separate so the queue is testable on its own.
     */
    interface Target {

        /** 区块是否被 loader 持有；释放时会经 Level#unregisterChunkLoader 重新入队。/ Whether a loader holds the chunk. */
        boolean isChunkInUse(long hash);

        /** 区块写是否积压。/ Whether chunk writes are backlogged. */
        boolean isChunkSaveBacklogged();

        /** 卸载区块；被事件取消等拒绝时返回 false。/ Unloads the chunk, false when refused. */
        boolean unloadChunk(long hash);
    }

    private final Long2LongLinkedOpenHashMap queuedAt = new Long2LongLinkedOpenHashMap();
    private final Long2LongOpenHashMap revisions = new Long2LongOpenHashMap();
    private final LongSupplier clock;
    private long nextRevision;

    ChunkUnloadQueue() {
        this(ChunkUnloadQueue::monotonicMillis);
    }

    /** 可注入时钟的构造器，仅供测试控制宽限期。/ Injectable clock, for tests that control the grace period. */
    ChunkUnloadQueue(LongSupplier clock) {
        this.clock = clock;
    }

    /** 单调毫秒：墙钟跳变不得拖慢或提前卸载。/ Monotonic milliseconds, unaffected by wall-clock jumps. */
    static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    /** 当前队列时钟，供调用方换算 deadline。/ Current queue clock, for converting a deadline. */
    long now() {
        return this.clock.getAsLong();
    }

    /** 开始或重新开始宽限期，区块移到队尾。/ Starts or restarts the grace period; the chunk moves to the tail. */
    synchronized void request(long hash) {
        long revision = Math.incrementExact(this.nextRevision);
        this.nextRevision = revision;
        this.revisions.put(hash, revision);
        this.queuedAt.putAndMoveToLast(hash, this.clock.getAsLong());
    }

    /** 取消等待中的卸载请求。/ Cancels a waiting unload request. */
    synchronized void cancel(long hash) {
        this.queuedAt.remove(hash);
        this.revisions.remove(hash);
    }

    /** 区块是否在等待卸载。/ Whether the chunk is waiting to be unloaded. */
    synchronized boolean contains(long hash) {
        return this.queuedAt.containsKey(hash);
    }

    synchronized int size() {
        return this.queuedAt.size();
    }

    synchronized boolean isEmpty() {
        return this.queuedAt.isEmpty();
    }

    /**
     * 从队首卸载到期区块，遇到未到期或写积压即停；force 忽略宽限期、写积压与单次计数，改为遍历开始时的队列快照。
     * <p>Unloads the due chunks at the head, stopping at the first one that is not due or while the
     * writer is backlogged. A forced pass ignores the grace period, backpressure and the count cap,
     * and walks the entry snapshot taken when it started.
     *
     * @param target    对应的 Level 判断 / Level-side decisions
     * @param force     卸载开始前已排队的全部区块，不受 maxUnload 限制 / unload every chunk queued at the pass start, uncapped
     * @param maxUnload 非 force 时本次最多卸载数 / per-pass cap unless forced
     * @param deadline  {@link #now()} 时间轴的截止点，每次成功卸载后检查 / stop after an unload at this {@link #now()} value
     * @return false 表示因 deadline 停止 / false when the pass stopped on the deadline
     */
    boolean drain(Target target, boolean force, int maxUnload, long deadline) {
        if (force) {
            return this.drainForced(target, deadline);
        }

        long dueBefore = this.now() - GRACE_MILLIS;
        int unloaded = 0;
        while (unloaded < maxUnload) {
            long hash;
            long queuedAt;
            long revision;
            synchronized (this) {
                if (this.queuedAt.isEmpty()) {
                    return true;
                }
                hash = this.queuedAt.firstLongKey();
                queuedAt = this.queuedAt.get(hash);
                revision = this.revisions.get(hash);
            }
            if (queuedAt > dueBefore) {
                // 队首最旧，后面的区块都不会到期
                return true;
            }
            if (target.isChunkInUse(hash)) {
                // loader 又持有该区块；释放时会经 unregisterChunkLoader 重新入队
                this.settle(hash, revision, true);
                continue;
            }
            // 每次卸载前复查一次：上一次的区块和脏邻居可能已填满写队列；区块留在队首，不拆开一次卸载的邻居保存
            // Recheck between unloads: the previous chunk and its dirty neighbours may fill the writer.
            if (target.isChunkSaveBacklogged()) {
                return true;
            }
            boolean done = target.unloadChunk(hash);
            // 被拒的卸载（ChunkUnloadEvent 取消）重新计满宽限期，而不是每轮都触发事件
            // A refused unload waits a full grace period again instead of firing the event on every pass.
            this.settle(hash, revision, done);
            if (done) {
                unloaded++;
                if (this.now() >= deadline) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * force 按开始时的 (hash, revision) 快照逐个尝试：回调重排队列不会漏访或重复访问某个区块。
     * 轮内重新入队仍按快照尝试，其新请求由 settle 保留；轮内取消则跳过。
     * <p>A forced pass tries the (hash, revision) snapshot taken at its start, so callbacks reordering
     * the queue neither skip a chunk nor get one tried twice: renewals survive via settle, cancels are honoured.
     */
    private boolean drainForced(Target target, long deadline) {
        long[] hashes;
        long[] revisions;
        synchronized (this) {
            if (this.queuedAt.isEmpty()) {
                return true;
            }
            // 入队顺序即到期顺序，快照保持原顺序
            // Insertion order is due order, so the snapshot keeps it.
            hashes = this.queuedAt.keySet().toLongArray();
            revisions = new long[hashes.length];
            for (int i = 0; i < hashes.length; i++) {
                revisions[i] = this.revisions.get(hashes[i]);
            }
        }
        for (int i = 0; i < hashes.length; i++) {
            long hash = hashes[i];
            long revision = revisions[i];
            if (!this.isQueued(hash)) {
                // 轮内被取消：尊重取消 / cancelled during the pass, honour it
                continue;
            }
            if (target.isChunkInUse(hash)) {
                this.settle(hash, revision, true);
                continue;
            }
            boolean done = target.unloadChunk(hash);
            this.settle(hash, revision, done);
            if (done && this.now() >= deadline) {
                return false;
            }
        }
        return true;
    }

    /** 快照条目是否仍在队列（轮内取消会移除）。/ Whether a snapshot entry is still queued. */
    private synchronized boolean isQueued(long hash) {
        return this.queuedAt.containsKey(hash);
    }

    /**
     * 结算一次卸载：移除，或把被拒的区块移到队尾重新计时；请求已变更（取消或卸载期间重新入队）时保留新请求。
     * <p>Removes a handled chunk, or re-queues a refused one with a fresh grace period, unless the
     * request changed meanwhile (cancelled, or queued again while the chunk was being unloaded).
     */
    private void settle(long hash, long revision, boolean remove) {
        synchronized (this) {
            if (!this.queuedAt.containsKey(hash) || this.revisions.get(hash) != revision) {
                return;
            }
            if (remove) {
                this.cancel(hash);
            } else {
                this.request(hash);
            }
        }
    }
}
