package cn.nukkit.entity;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.SimpleAxisAlignedBB;

import java.util.*;

public class EntityCollision {
    private static final int BLOCK_CACHE_SIZE = 512;
    private static final int BLOCK_CACHE_MASK = BLOCK_CACHE_SIZE - 1;
    private static final int CHUNK_CACHE_SIZE = 64;

    private static final int BLOCK_CACHE_TTL = 40;
    private static final int CHUNK_CACHE_TTL = 100;

    private static final Block[] FAST_BLOCK_POOL = new Block[512];
    private static final FullChunk[] FAST_CHUNK_POOL = new FullChunk[128];

    private static int poolCleanupCounter = 0;

    private final BlockCacheEntry[] blockCache = new BlockCacheEntry[BLOCK_CACHE_SIZE];
    private final ChunkCacheEntry[] chunkCache = new ChunkCacheEntry[CHUNK_CACHE_SIZE];

    private final Entity entity;
    private long lastCleanupTick = 0;
    private int cleanupCounter = 0;

    private AxisAlignedBB lastBoundingBox = null;
    private long lastCheckTick = 0;
    private byte insideCache = 0;

    public EntityCollision(Entity entity) {
        this.entity = entity;
    }

    public List<Block> getCollisionBlocks(AxisAlignedBB bb, double motionX, double motionY, double motionZ) {
        double speedSq = motionX * motionX + motionY * motionY + motionZ * motionZ;
        if (speedSq < 0.001 && entity instanceof EntityLiving) {
            long currentTick = entity.getServer().getTick();
            if (currentTick % 10 != 0) {
                return Collections.emptyList();
            }
        }

        AxisAlignedBB boundingBox = bb.clone();
        double expand = Math.max(0.3, Math.sqrt(speedSq) * 1.2);

        AxisAlignedBB expandedBB = boundingBox.grow(expand, expand, expand);
        List<Block> blocks = getBlocksInBoundingBoxFast(expandedBB);

        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Block> collisionBlocks = new ArrayList<>(Math.min(blocks.size(), 8));

        double motionAbsX = Math.abs(motionX);
        double motionAbsY = Math.abs(motionY);
        double motionAbsZ = Math.abs(motionZ);
        AxisAlignedBB trajectoryBB = boundingBox.grow(motionAbsX + 0.3, motionAbsY + 0.3, motionAbsZ + 0.3);

        for (Block block : blocks) {
            int blockId = block.getId();

            if (blockId == Block.AIR) {
                continue;
            }

            if (blockId == Block.NETHER_PORTAL) {
                AxisAlignedBB portalBB = new SimpleAxisAlignedBB(
                        block.x, block.y, block.z,
                        block.x + 1, block.y + 1, block.z + 1
                );

                if (trajectoryBB.intersectsWith(portalBB)) {
                    collisionBlocks.add(block);
                }
            } else if (block.collidesWithBB(boundingBox, true)) {
                collisionBlocks.add(block);
            }
        }

        return collisionBlocks;
    }

    public List<Block> getBlocksInBoundingBoxFast(AxisAlignedBB bb) {
        long currentTickForCache = entity.getServer().getTick();
        if (bb.equals(lastBoundingBox) && currentTickForCache - lastCheckTick < 5) {
            return Collections.emptyList();
        }

        int minX = NukkitMath.floorDouble(bb.getMinX());
        int minY = Math.max(NukkitMath.floorDouble(bb.getMinY()), this.entity.getLevel().getMinBlockY());
        int minZ = NukkitMath.floorDouble(bb.getMinZ());
        int maxX = NukkitMath.ceilDouble(bb.getMaxX());
        int maxY = Math.min(NukkitMath.ceilDouble(bb.getMaxY()), this.entity.getLevel().getMaxBlockY());
        int maxZ = NukkitMath.ceilDouble(bb.getMaxZ());

        if (minY > maxY) {
            return Collections.emptyList();
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || sizeX * sizeY * sizeZ > 256) {
            return Collections.emptyList();
        }

        if (!this.entity.getLevel().isYInRange(minY) && !this.entity.getLevel().isYInRange(maxY)) {
            return Collections.emptyList();
        }

        int chunkMinX = minX >> 4;
        int chunkMaxX = maxX >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxZ = maxZ >> 4;

        int chunksWidth = chunkMaxX - chunkMinX + 1;
        int chunksDepth = chunkMaxZ - chunkMinZ + 1;

        FullChunk[] chunks = FAST_CHUNK_POOL;
        if (chunks.length < chunksWidth * chunksDepth) {
            chunks = new FullChunk[chunksWidth * chunksDepth];
        }

        loadChunksBatchFast(chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, chunksDepth, chunks);

        Block[] blockPool = FAST_BLOCK_POOL;
        int poolIndex = 0;

        poolCleanupCounter++;

        for (int x = minX; x <= maxX; x++) {
            int chunkX = x >> 4;
            int localX = x & 0x0f;
            int chunkIdxX = chunkX - chunkMinX;

            for (int z = minZ; z <= maxZ; z++) {
                int chunkZ = z >> 4;
                int chunkIdxZ = chunkZ - chunkMinZ;
                int chunkArrayIdx = chunkIdxX * chunksDepth + chunkIdxZ;

                FullChunk chunk = chunks[chunkArrayIdx];
                if (chunk == null) continue;

                int localZ = z & 0x0f;

                for (int y = minY; y <= maxY; y++) {
                    if (!this.entity.getLevel().isYInRange(y)) {
                        continue;
                    }

                    int cacheKey = ((x * 31 + y) * 31 + z) & BLOCK_CACHE_MASK;
                    BlockCacheEntry entry = blockCache[cacheKey];

                    Block block = null;
                    if (entry != null && entry.x == x && entry.y == y && entry.z == z &&
                            currentTickForCache - entry.timestamp < BLOCK_CACHE_TTL) {
                        block = entry.block;
                    } else {
                        int blockId = chunk.getBlockId(localX, y, localZ);
                        int blockMeta = chunk.getBlockData(localX, y, localZ);

                        block = Block.get(blockId, blockMeta, entity.getLevel(), x, y, z);

                        if (blockId != Block.AIR) {
                            blockCache[cacheKey] = new BlockCacheEntry(x, y, z, block, currentTickForCache);
                        }
                    }

                    if (poolIndex < blockPool.length) {
                        blockPool[poolIndex++] = block;
                    } else {
                        List<Block> result = new ArrayList<>(poolIndex + 1);
                        for (int i = 0; i < poolIndex; i++) {
                            result.add(blockPool[i]);
                        }
                        result.add(block);
                        return result;
                    }
                }
            }
        }

        if (poolIndex == 0) {
            return Collections.emptyList();
        }

        List<Block> result = new ArrayList<>(poolIndex);
        for (int i = 0; i < poolIndex; i++) {
            Block block = blockPool[i];
            if (block != null) {
                result.add(block);
            }
            blockPool[i] = null;
        }

        cleanupCounter++;
        if (cleanupCounter > 200) {
            cleanupOldCacheLazy(currentTickForCache);
            cleanupCounter = 0;
        }

        if (poolCleanupCounter > 1000) {
            Arrays.fill(FAST_BLOCK_POOL, null);
            Arrays.fill(FAST_CHUNK_POOL, null);
            poolCleanupCounter = 0;
        }

        return result;
    }

    private void loadChunksBatchFast(int minCX, int maxCX, int minCZ, int maxCZ, int depth, FullChunk[] chunks) {
        long currentTick = this.entity.getServer().getTick();

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                int idx = (cx - minCX) * depth + (cz - minCZ);

                int cacheKey = (cx * 31 + cz);
                int cacheIdx = cacheKey & (CHUNK_CACHE_SIZE - 1);

                ChunkCacheEntry entry = chunkCache[cacheIdx];
                if (entry != null && entry.key == cacheKey &&
                        currentTick - entry.timestamp < CHUNK_CACHE_TTL) {
                    chunks[idx] = entry.chunk;
                    continue;
                }

                FullChunk chunk = this.entity.getLevel().getChunkIfLoaded(cx, cz);
                if (chunk != null) {
                    chunkCache[cacheIdx] = new ChunkCacheEntry(cacheKey, chunk, currentTick);
                    chunks[idx] = chunk;
                }
            }
        }
    }

    public boolean isInsideSpecialBlock(AxisAlignedBB bb, int targetBlockId) {
        long currentTick = entity.getServer().getTick();

        if (bb.equals(lastBoundingBox) && currentTick - lastCheckTick < 3) {
            if (targetBlockId == Block.FIRE && (insideCache & 1) != 0) return true;
            if (targetBlockId == Block.LAVA && (insideCache & 2) != 0) return true;
            return targetBlockId == Block.WATER && (insideCache & 4) != 0;
        }

        lastBoundingBox = bb.clone();
        lastCheckTick = currentTick;
        insideCache = 0;

        int minX = NukkitMath.floorDouble(bb.getMinX());
        int minY = Math.max(NukkitMath.floorDouble(bb.getMinY()), entity.getLevel().getMinBlockY());
        int minZ = NukkitMath.floorDouble(bb.getMinZ());
        int maxX = NukkitMath.ceilDouble(bb.getMaxX());
        int maxY = Math.min(NukkitMath.ceilDouble(bb.getMaxY()), entity.getLevel().getMaxBlockY());
        int maxZ = NukkitMath.ceilDouble(bb.getMaxZ());

        boolean foundFire = false;
        boolean foundLava = false;
        boolean foundWater = false;

        for (int y = minY; y <= maxY; y++) {
            if (!entity.getLevel().isYInRange(y)) {
                continue;
            }

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int blockId = entity.getLevel().getBlockIdAt(x, y, z);

                    if (blockId == Block.FIRE) {
                        foundFire = true;
                        if (targetBlockId == Block.FIRE) {
                            insideCache |= 1;
                            return true;
                        }
                    } else if (blockId == Block.LAVA || blockId == Block.STILL_LAVA) {
                        foundLava = true;
                        if (targetBlockId == Block.LAVA) {
                            insideCache |= 2;
                            return true;
                        }
                    } else if (blockId == Block.WATER || blockId == Block.STILL_WATER) {
                        foundWater = true;
                        if (targetBlockId == Block.WATER) {
                            insideCache |= 4;
                            return true;
                        }
                    }

                    if (foundFire && foundLava && foundWater) {
                        if (foundFire) insideCache |= 1;
                        if (foundLava) insideCache |= 2;
                        if (foundWater) insideCache |= 4;
                        return targetBlockId == Block.FIRE ? foundFire :
                                targetBlockId == Block.LAVA ? foundLava : foundWater;
                    }
                }
            }
        }

        if (foundFire) insideCache |= 1;
        if (foundLava) insideCache |= 2;
        if (foundWater) insideCache |= 4;

        return targetBlockId == Block.FIRE ? foundFire :
                targetBlockId == Block.LAVA ? foundLava : foundWater;
    }

    public List<Block> getBlocksInBoundingBox(AxisAlignedBB bb) {
        return getBlocksInBoundingBoxFast(bb);
    }

    public void cleanupOldCache() {
        long currentTick = this.entity.getServer().getTick();
        if (currentTick - lastCleanupTick < 200) {
            return;
        }
        cleanupOldCacheLazy(currentTick);
    }

    private void cleanupOldCacheLazy(long currentTick) {
        for (int i = 0; i < BLOCK_CACHE_SIZE; i++) {
            BlockCacheEntry entry = blockCache[i];
            if (entry != null && currentTick - entry.timestamp > BLOCK_CACHE_TTL) {
                blockCache[i] = null;
            }
        }

        if (currentTick % 500 == 0) {
            for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
                ChunkCacheEntry entry = chunkCache[i];
                if (entry != null && currentTick - entry.timestamp > CHUNK_CACHE_TTL) {
                    chunkCache[i] = null;
                }
            }
        }

        lastCleanupTick = currentTick;
    }

    public void invalidatePositionCache() {
        lastBoundingBox = null;
        insideCache = 0;
    }

    private static class BlockCacheEntry {
        final int x, y, z;
        final Block block;
        final long timestamp;

        BlockCacheEntry(int x, int y, int z, Block block, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.timestamp = timestamp;
        }
    }

    private static class ChunkCacheEntry {
        final int key;
        final FullChunk chunk;
        final long timestamp;

        ChunkCacheEntry(int key, FullChunk chunk, long timestamp) {
            this.key = key;
            this.chunk = chunk;
            this.timestamp = timestamp;
        }
    }
}