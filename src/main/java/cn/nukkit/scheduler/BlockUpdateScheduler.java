package cn.nukkit.scheduler;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockUpdateEntry;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public BlockUpdateScheduler(Level level, long currentTick) {
        queuedUpdates = new Long2ObjectNonBlockingMap<>();
        lastTick = currentTick;
        this.level = level;
    }

    /**
     * @return how many scheduled updates were performed, so the level can report its load
     */
    public int tick(long currentTick) {
        int performed = 0;
        // Should only perform once, unless ticks were skipped
        if (currentTick - lastTick < Short.MAX_VALUE) {// Arbitrary
            for (long tick = lastTick + 1; tick <= currentTick; tick++) {
                performed += perform(tick);
            }
        } else {
            LongArrayList times = new LongArrayList(queuedUpdates.keySet());
            Collections.sort(times);
            for (long tick : times) {
                if (tick <= currentTick) {
                    performed += perform(tick);
                } else {
                    break;
                }
            }
        }
        lastTick = currentTick;
        return performed;
    }

    /** Scheduled updates still waiting for their tick. */
    public int getPendingCount() {
        return globalIndex.size();
    }

    private int perform(long tick) {
        int performed = 0;
        try {
            lastTick = tick;
            Set<BlockUpdateEntry> updates;
            synchronized (this) {
                updates = pendingUpdates = queuedUpdates.remove(tick);
                if (updates != null) {
                    globalIndex.removeAll(updates);
                    for (BlockUpdateEntry entry : updates) {
                        removeFromChunkIndex(tick, entry);
                    }
                }
            }
            if (updates != null) {
                for (BlockUpdateEntry entry : updates) {
                    performed++;
                    if (level.isAreaLoaded(new SimpleAxisAlignedBB(entry.pos, entry.pos))) {
                        Block block = level.getBlock(entry.pos, entry.block.layer);

                        if (Block.equals(block, entry.block, false)) {
                            block.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
                        }
                    } else {
                        level.scheduleUpdate(entry.block, entry.pos, 0);
                    }
                }
            }
        } finally {
            pendingUpdates = null;
        }
        return performed;
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

    public boolean contains(BlockUpdateEntry entry) {
        return globalIndex.contains(entry);
    }

    public synchronized boolean remove(BlockUpdateEntry entry) {
        globalIndex.remove(entry);
        for (Map.Entry<Long, Set<BlockUpdateEntry>> tickUpdateSet : queuedUpdates.entrySet()) {
            if (tickUpdateSet.getValue().remove(entry)) {
                removeFromChunkIndex(tickUpdateSet.getKey(), entry);
                return true;
            }
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
