package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockUpdateEntry;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.extern.log4j.Log4j2;

import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class BlockUpdateScheduler {

    private final Level level;
    private long lastTick;
    private final Long2ObjectNonBlockingMap<Set<BlockUpdateEntry>> queuedUpdates;
    private final Set<BlockUpdateEntry> globalIndex = ConcurrentHashMap.newKeySet();

    // PNX localizes the scheduler to a chunk. Keep MOT's world timeline, but index each
    // chunk's queued buckets so a save only visits its 18x18 snapshot area.
    // Guarded by this; no block callback runs while holding the index lock.
    private final Map<Long, Map<Long, Set<BlockUpdateEntry>>> chunkUpdates = new HashMap<>();

    private Set<BlockUpdateEntry> pendingUpdates;
    private DeferredBucket deferredBucket;
    private long lastBudgetWarningTick = Long.MIN_VALUE;

    private static final class DeferredBucket {
        final Set<BlockUpdateEntry> active;
        final Set<BlockUpdateEntry> remaining;
        final Iterator<BlockUpdateEntry> iterator;
        final long tick;

        DeferredBucket(Set<BlockUpdateEntry> updates, long tick) {
            this.active = updates;
            this.remaining = new HashSet<>(updates);
            this.iterator = updates.iterator();
            this.tick = tick;
        }
    }

    public BlockUpdateScheduler(Level level, long currentTick) {
        queuedUpdates = new Long2ObjectNonBlockingMap<>();
        lastTick = currentTick;
        this.level = level;
    }

    /**
     * @return how many scheduled updates were performed, so the level can report its load
     */
    public int tick(long currentTick) {
        Server server = level.getServer();
        int budget = server != null && server.scheduledBlockUpdateBudget
                ? Math.max(1, server.scheduledBlockUpdatesPerTick) : Integer.MAX_VALUE;
        int performed = resumeDeferred(budget);
        if (deferredBucket != null || performed >= budget) {
            reportBudget(currentTick, budget);
            return performed;
        }
        // Should only perform once, unless ticks were skipped. Do not advance past a
        // deferred bucket: later due buckets must remain reachable on the next tick.
        if (currentTick - lastTick < Short.MAX_VALUE) {// Arbitrary
            for (long tick = lastTick + 1; tick <= currentTick; tick++) {
                performed += perform(tick, budget - performed);
                if (performed >= budget) {
                    reportBudget(currentTick, budget);
                    return performed;
                }
            }
        } else {
            LongArrayList times = new LongArrayList(queuedUpdates.keySet());
            Collections.sort(times);
            for (long tick : times) {
                if (tick <= currentTick) {
                    performed += perform(tick, budget - performed);
                    if (performed >= budget) {
                        reportBudget(currentTick, budget);
                        return performed;
                    }
                } else {
                    break;
                }
            }
        }
        lastTick = currentTick;
        return performed;
    }

    private void reportBudget(long currentTick, int budget) {
        if (deferredBucket != null && (lastBudgetWarningTick == Long.MIN_VALUE
                || currentTick - lastBudgetWarningTick >= 1200)) {
            lastBudgetWarningTick = currentTick;
            log.warn("Scheduled block update anomaly guard deferred {} updates in {} at tick {} (limit {})",
                    deferredBucket.remaining.size(), level.getName(), currentTick, budget);
        }
    }

    /** Scheduled updates still waiting for their tick or an anomaly guard continuation. */
    public synchronized int getPendingCount() {
        return globalIndex.size() + (deferredBucket != null && pendingUpdates == null
                ? deferredBucket.remaining.size() : 0);
    }

    private int perform(long tick, int budget) {
        Set<BlockUpdateEntry> updates;
        synchronized (this) {
            lastTick = tick;
            updates = pendingUpdates = queuedUpdates.remove(tick);
            if (updates == null) return 0;
            globalIndex.removeAll(updates);
            if (updates.size() > budget) {
                // Capture MOT's existing iterator once, rather than sorting/re-enqueuing
                // its remainder. The chunk index already owns every unexecuted entry.
                deferredBucket = new DeferredBucket(updates, tick);
                LongOpenHashSet changedChunks = new LongOpenHashSet();
                for (BlockUpdateEntry entry : updates) {
                    markDeferredSnapshotChunks(entry, changedChunks);
                }
                for (long hash : changedChunks) {
                    markDeferredChunkChanged(hash);
                }
            } else {
                for (BlockUpdateEntry entry : updates) {
                    removeFromChunkIndex(tick, entry);
                }
            }
        }
        if (deferredBucket != null) {
            return resumeDeferred(budget);
        }
        int performed = 0;
        try {
            for (BlockUpdateEntry entry : updates) {
                performed++;
                execute(entry);
            }
        } finally {
            pendingUpdates = null;
        }
        return performed;
    }

    private int resumeDeferred(int budget) {
        DeferredBucket bucket = deferredBucket;
        if (bucket == null) return 0;
        int performed = 0;
        pendingUpdates = bucket.active;
        try {
            while (performed < budget && bucket.iterator.hasNext()) {
                BlockUpdateEntry entry = bucket.iterator.next();
                synchronized (this) {
                    if (!bucket.remaining.remove(entry)) continue; // cancelled between ticks
                    removeFromChunkIndex(bucket.tick, entry);
                    markDeferredSnapshotChunks(entry, null);
                }
                performed++;
                execute(entry);
            }
            if (!bucket.iterator.hasNext()) {
                synchronized (this) {
                    deferredBucket = null;
                }
            }
        } finally {
            pendingUpdates = null;
        }
        return performed;
    }

    private void markDeferredSnapshotChunks(BlockUpdateEntry entry, LongOpenHashSet initialMarks) {
        int blockX = entry.pos.getFloorX();
        int blockZ = entry.pos.getFloorZ();
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        // MOT saves [chunk*16-2, chunk*16+16), so its east/south snapshots also
        // own an entry in the final two columns. Include those loaded owners when
        // removing a persisted tick, otherwise their old copy replays after reload.
        int maxChunkX = chunkX + ((blockX & 15) >= 14 ? 1 : 0);
        int maxChunkZ = chunkZ + ((blockZ & 15) >= 14 ? 1 : 0);
        for (int x = chunkX; x <= maxChunkX; x++) {
            for (int z = chunkZ; z <= maxChunkZ; z++) {
                long hash = Level.chunkHash(x, z);
                if (initialMarks != null) initialMarks.add(hash);
                else markDeferredChunkChanged(hash);
            }
        }
    }

    private void markDeferredChunkChanged(long hash) {
        // Scheduled-only work need not change a block. Dirty the loaded snapshot owner so both
        // its remaining tick list and removal of an executed/cancelled tick are saved.
        BaseFullChunk chunk = level.getChunkIfLoaded(Level.getHashX(hash), Level.getHashZ(hash));
        if (chunk != null) chunk.setChanged();
    }

    private void execute(BlockUpdateEntry entry) {
        if (level.isAreaLoaded(new SimpleAxisAlignedBB(entry.pos, entry.pos))) {
            Block block = level.getBlock(entry.pos, entry.block.layer);
            if (Block.equals(block, entry.block, false)) {
                block.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            }
        } else {
            level.scheduleUpdate(entry.block, entry.pos, 0);
        }
    }

    public synchronized Set<BlockUpdateEntry> getPendingBlockUpdates(AxisAlignedBB boundingBox) {
        Set<BlockUpdateEntry> set = new HashSet<>();
        if (boundingBox.getMaxX() <= boundingBox.getMinX()
                || boundingBox.getMaxZ() <= boundingBox.getMinZ()) {
            return set;
        }
        int minChunkX = NukkitMath.floorDouble(boundingBox.getMinX()) >> 4;
        int maxChunkX = NukkitMath.floorDouble(Math.nextDown(boundingBox.getMaxX())) >> 4;
        int minChunkZ = NukkitMath.floorDouble(boundingBox.getMinZ()) >> 4;
        int maxChunkZ = NukkitMath.floorDouble(Math.nextDown(boundingBox.getMaxZ())) >> 4;
        long chunkCount = ((long) maxChunkX - minChunkX + 1) * ((long) maxChunkZ - minChunkZ + 1);
        if (chunkCount > chunkUpdates.size()) {
            // Preserve the general AABB API without walking every coordinate of a huge area.
            for (Map.Entry<Long, Map<Long, Set<BlockUpdateEntry>>> chunk : chunkUpdates.entrySet()) {
                int x = Level.getHashX(chunk.getKey());
                int z = Level.getHashZ(chunk.getKey());
                if (x >= minChunkX && x <= maxChunkX && z >= minChunkZ && z <= maxChunkZ) {
                    collectPending(chunk.getValue(), boundingBox, set);
                }
            }
        } else {
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    collectPending(chunkUpdates.get(Level.chunkHash(x, z)), boundingBox, set);
                }
            }
        }
        return set;
    }

    private void collectPending(Map<Long, Set<BlockUpdateEntry>> buckets, AxisAlignedBB boundingBox,
                                Set<BlockUpdateEntry> result) {
        if (buckets == null) return;
        for (Set<BlockUpdateEntry> updates : buckets.values()) {
            for (BlockUpdateEntry update : updates) {
                Vector3 pos = update.pos;
                if (pos.getX() >= boundingBox.getMinX() && pos.getX() < boundingBox.getMaxX()
                        && pos.getZ() >= boundingBox.getMinZ() && pos.getZ() < boundingBox.getMaxZ()) {
                    result.add(update);
                }
            }
        }
    }

    private void removeFromChunkIndex(long tick, BlockUpdateEntry entry) {
        long chunkHash = Level.chunkHash(entry.pos.getFloorX() >> 4, entry.pos.getFloorZ() >> 4);
        Map<Long, Set<BlockUpdateEntry>> buckets = chunkUpdates.get(chunkHash);
        if (buckets == null) return;
        Set<BlockUpdateEntry> updates = buckets.get(tick);
        if (updates != null && updates.remove(entry) && updates.isEmpty()) {
            buckets.remove(tick);
        }
        if (buckets.isEmpty()) chunkUpdates.remove(chunkHash);
    }

    public boolean isBlockTickPending(Vector3 pos, Block block) {
        Set<BlockUpdateEntry> tmpUpdates = pendingUpdates;
        if (tmpUpdates == null || tmpUpdates.isEmpty()) return false;
        return tmpUpdates.contains(new BlockUpdateEntry(pos, block));
    }

    private long getMinTime(BlockUpdateEntry entry) {
        return Math.max(entry.delay, lastTick + 1);
    }

    public synchronized void add(BlockUpdateEntry entry) {
        long time = getMinTime(entry);
        Set<BlockUpdateEntry> updateSet = queuedUpdates.get(time);
        if (updateSet == null) {
            Set<BlockUpdateEntry> tmp = queuedUpdates.putIfAbsent(time, updateSet = ConcurrentHashMap.newKeySet());
            if (tmp != null) updateSet = tmp;
        }
        if (updateSet.add(entry)) {
            long chunkHash = Level.chunkHash(entry.pos.getFloorX() >> 4, entry.pos.getFloorZ() >> 4);
            chunkUpdates.computeIfAbsent(chunkHash, ignored -> new HashMap<>())
                    .computeIfAbsent(time, ignored -> new HashSet<>()).add(entry);
        }
        globalIndex.add(entry);
    }

    public synchronized boolean contains(BlockUpdateEntry entry) {
        // MOT removes the whole active bucket from its future dedup index. Keep that
        // callback contract, but prevent duplicate scheduling of a deferred remainder
        // between ticks. A separately requeued future entry remains independently visible.
        return globalIndex.contains(entry) || (pendingUpdates == null && deferredBucket != null
                && deferredBucket.remaining.contains(entry));
    }

    public synchronized boolean remove(BlockUpdateEntry entry) {
        globalIndex.remove(entry);
        for (Map.Entry<Long, Set<BlockUpdateEntry>> tickUpdateSet : queuedUpdates.entrySet()) {
            if (tickUpdateSet.getValue().remove(entry)) {
                removeFromChunkIndex(tickUpdateSet.getKey(), entry);
                return true;
            }
        }
        if (pendingUpdates == null && deferredBucket != null && deferredBucket.remaining.remove(entry)) {
            removeFromChunkIndex(deferredBucket.tick, entry);
            markDeferredSnapshotChunks(entry, null);
            return true;
        }
        return false;
    }

    @Deprecated
    @SuppressWarnings("SuspiciousMethodCalls")
    public synchronized boolean remove(Vector3 pos) {
        globalIndex.removeIf(e -> e.pos.equals(pos));
        for (Map.Entry<Long, Set<BlockUpdateEntry>> tickUpdateSet : queuedUpdates.entrySet()) {
            if (tickUpdateSet.getValue().remove(pos)) {
                long chunkHash = Level.chunkHash(pos.getFloorX() >> 4, pos.getFloorZ() >> 4);
                Map<Long, Set<BlockUpdateEntry>> buckets = chunkUpdates.get(chunkHash);
                if (buckets != null) {
                    Set<BlockUpdateEntry> updates = buckets.get(tickUpdateSet.getKey());
                    if (updates != null && updates.remove(pos) && updates.isEmpty()) {
                        buckets.remove(tickUpdateSet.getKey());
                    }
                    if (buckets.isEmpty()) chunkUpdates.remove(chunkHash);
                }
                return true;
            }
        }
        return false;
    }
}
