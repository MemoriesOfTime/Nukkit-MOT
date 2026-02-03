package cn.nukkit.entity;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class EntityCollision implements ChunkLoader {

    private final Entity entity;
    private final int loaderId;
    private boolean registered = false;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;

    /**
     * Cache for block data with versioning.
     * Key: (chunkKey << 16) | (localX << 12) | (y << 8) | (localZ << 4)
     * Value: (blockId << 20) | (blockData << 12) | (version & 0xFFF)
     */
    private final Cache<Long, Integer> blockDataCache = Caffeine.newBuilder()
            .maximumSize(128)
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .expireAfterAccess(3, TimeUnit.SECONDS)
            .softValues()
            .build();

    private int cacheVersion = 0;

    public EntityCollision(Entity entity) {
        this.entity = entity;
        this.loaderId = Level.generateChunkLoaderId(this);
    }

    /**
     * Cleans up when entity is removed.
     */
    public void cleanup() {
        blockDataCache.invalidateAll();
        this.unregisterFromChunk();
    }

    /**
     * Immediately updates block in the chunk.
     * Marks cached collision data as potentially stale.
     */
    @Override
    public void onBlockChanged(Vector3 pos) {
        if (pos == null || entity == null || entity.isClosed()) {
            return;
        }
        Level level = entity.getLevel();
        if (level == null) return;

        int blockX = pos.getFloorX();
        int blockZ = pos.getFloorZ();
        int ecX = entity.getChunkX();
        int ecZ = entity.getChunkZ();
        int bcX = blockX >> 4;
        int bcZ = blockZ >> 4;

        if (Math.abs(bcX - ecX) > 2 || Math.abs(bcZ - ecZ) > 2) {
            return;
        }

        long chunkKey = ((long) bcX << 32) | (bcZ & 0xFFFFFFFFL);
        cacheVersion = (cacheVersion + 1) & 0xFFF;

        int localX = blockX & 0x0f;
        int localZ = blockZ & 0x0f;
        int y = pos.getFloorY();
        long blockKey = (chunkKey << 16) | (localX << 12) | ((long) y << 8) | (localZ << 4);
        blockDataCache.invalidate(blockKey);
    }

    /**
     * Invalidate cache when chunk is unloaded to prevent stale references.
     */
    @Override
    public void onChunkUnloaded(FullChunk chunk) {
        if (chunk != null && !entity.isClosed()) {
            long chunkKey = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
            blockDataCache.asMap().keySet().removeIf(key -> (key >> 16) == chunkKey);
        }
    }

    /**
     * Gets cached block data or loads from chunk.
     * Returns null for invalid chunk.
     *
     * @param chunk Chunk containing block
     * @param blockKey Cache key for block
     * @param localX Local X in chunk
     * @param y Y coordinate
     * @param localZ Local Z in chunk
     * @return Array [blockId, blockData] or null
     */
    private int[] getBlockData(FullChunk chunk, long blockKey, int localX, int y, int localZ) {
        if (chunk == null) return null;

        Integer cached = blockDataCache.getIfPresent(blockKey);

        if (cached != null) {
            int cachedVersion = cached & 0xFFF;
            int cachedBlockId = (cached >> 20) & 0xFFF;

            if (cachedVersion == (cacheVersion & 0xFFF) && cachedBlockId != Block.AIR) {
                return new int[]{cachedBlockId, (cached >> 12) & 0xFF};
            }
        }

        int blockId = chunk.getBlockId(localX, y, localZ);
        int blockData = chunk.getBlockData(localX, y, localZ);

        if (blockId != Block.AIR) {
            int packed = (blockId << 20) | (blockData << 12) | (cacheVersion & 0xFFF);
            blockDataCache.put(blockKey, packed);
        }

        return new int[]{blockId, blockData};
    }

    /**
     * Gets blocks that collide with entity's AABB.
     *
     * @param boundingBox The entity's AABB
     * @return List of colliding blocks
     */
    public List<Block> getCollisionBlocks(AxisAlignedBB boundingBox) {
        if (entity.isClosed()) {
            return Collections.emptyList();
        }

        Level level = entity.getLevel();
        if (level == null) {
            return Collections.emptyList();
        }

        // expand by 0.5 on X and Z, 'cause sometimes it lags to detect
        List<Block> blocks = this.getBlocksInBoundingBox(boundingBox.grow(0.5, 0, 0.5));

        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Block> collisionBlocks = new ArrayList<>(8);
        for (Block block : blocks) {
            // center the BB so it stops catching diagonal corner glitches
            double shrinkAmountX = (boundingBox.getMaxX() - boundingBox.getMinX()) * 0.25;
            double shrinkAmountZ = (boundingBox.getMaxZ() - boundingBox.getMinZ()) * 0.25;

            if (block.collidesWithBB(boundingBox.shrink(shrinkAmountX, 0, shrinkAmountZ), true)) {
                collisionBlocks.add(block);
            }
        }

        return collisionBlocks.isEmpty() ? Collections.emptyList() : collisionBlocks;
    }

    /**
     * Gets non-air blocks in bounding box with caching.
     *
     * @param boundingBox Bounding box to check
     * @return List of non-air blocks
     */
    public List<Block> getBlocksInBoundingBox(AxisAlignedBB boundingBox) {
        Level level = entity.getLevel();
        if (level == null || entity.isClosed()) {
            return Collections.emptyList();
        }

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return Collections.emptyList();
        }

        if (!level.isYInRange(minY) || !level.isYInRange(maxY)) {
            return Collections.emptyList();
        }

        this.updateChunk();

        List<Block> result = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            int chunkX = x >> 4;
            int localX = x & 0x0f;
            for (int z = minZ; z <= maxZ; z++) {
                int chunkZ = z >> 4;
                int localZ = z & 0x0f;
                long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

                FullChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
                if (chunk == null) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (!level.isYInRange(y)) continue;

                    long blockKey = (chunkKey << 16) | (localX << 12) | ((long) y << 8) | (localZ << 4);

                    int[] blockData = this.getBlockData(chunk, blockKey, localX, y, localZ);
                    if (blockData == null || blockData[0] == Block.AIR) continue;

                    Block block = Block.get(blockData[0], blockData[1], level, x, y, z);
                    result.add(block);
                }
            }
        }
        return result;
    }

    /**
     * Checks if bounding box intersects specific block type.
     *
     * @param boundingBox The bounding box to test.
     * @param targetBlockId The block ID to check (e.g., Block.FIRE).
     * @return {@code true} if any matching block intersects the box.
     */
    public boolean isInsideBlock(AxisAlignedBB boundingBox, int targetBlockId) {
        List<Block> blocks = this.getBlocksInBoundingBox(boundingBox);

        if (blocks.isEmpty()) {
            return false;
        }

        for (Block block : blocks) {
            if (block.getId() == targetBlockId && block.collidesWithBB(boundingBox)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getLoaderId() {
        return this.loaderId;
    }

    @Override
    public boolean isLoaderActive() {
        return !entity.isClosed() && entity.getLevel() != null && this.registered;
    }

    @Override
    public Position getPosition() {
        return entity.getPosition();
    }

    @Override
    public double getX() {
        return entity.getChunkX();
    }

    @Override
    public double getZ() {
        return entity.getChunkZ();
    }

    @Override
    public Level getLevel() {
        return entity.getLevel();
    }

    @Override
    public void onChunkChanged(FullChunk chunk) {}

    @Override
    public void onChunkLoaded(FullChunk chunk) {}

    @Override
    public void onChunkPopulated(FullChunk chunk) {}

    private void updateChunk() {
        if (entity.isClosed() || entity.getLevel() == null) {
            this.unregisterFromChunk();
            return;
        }

        int currentChunkX = entity.getChunkX();
        int currentChunkZ = entity.getChunkZ();

        if (!this.registered || currentChunkX != this.chunkX || currentChunkZ != this.chunkZ) {
            this.unregisterFromChunk();
            this.registerInChunk(currentChunkX, currentChunkZ);
        }
    }

    private void registerInChunk(int chunkX, int chunkZ) {
        Level level = entity.getLevel();
        if (level == null || this.registered) {
            return;
        }

        level.registerChunkLoader(this, chunkX, chunkZ);
        this.registered = true;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    private void unregisterFromChunk() {
        if (!this.registered || entity.getLevel() == null) {
            return;
        }

        Level level = entity.getLevel();
        level.unregisterChunkLoader(this, this.chunkX, this.chunkZ);

        this.registered = false;
        this.chunkX = Integer.MIN_VALUE;
        this.chunkZ = Integer.MIN_VALUE;
    }
}