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

public class EntityCollision implements ChunkLoader {

    private final Entity entity;
    private final int loaderId;
    private boolean registered = false;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;

    private final Cache<Long, FullChunk> chunkCache = Caffeine.newBuilder()
            .maximumSize(8)
            .build();

    /**
     * Cache for block data to reduce getBlock calls.
     * Key format: (chunkKey << 16) | (localX << 12) | (y << 8) | (localZ << 4)
     * Value: (blockId << 8) | blockData
     */
    private final Cache<Long, Integer> blockDataCache = Caffeine.newBuilder()
            .maximumSize(512)
            .build();

    public EntityCollision(Entity entity) {
        this.entity = entity;
        this.loaderId = Level.generateChunkLoaderId(this);
        this.registerInChunk(entity.getChunkX(), entity.getChunkZ());
    }

    /**
     * Clears data when the entity is removed.
     */
    public void cleanup() {
        chunkCache.invalidateAll();
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

        if (Math.abs(bcX - ecX) <= 1 && Math.abs(bcZ - ecZ) <= 1) {
            long chunkKey = ((long) bcX << 32) | (bcZ & 0xFFFFFFFFL);
            chunkCache.invalidate(chunkKey);

            int localX = blockX & 0x0f;
            int localZ = blockZ & 0x0f;
            int y = pos.getFloorY();

            long baseKey = (chunkKey << 16) | (localX << 12) | (localZ << 4);

            int minY = Math.max(y - 2, level.getMinBlockY());
            int maxY = Math.min(y + 2, level.getMaxBlockY());

            for (int cacheY = minY; cacheY <= maxY; cacheY++) {
                long blockKey = baseKey | (cacheY & 0xFF);
                blockDataCache.invalidate(blockKey);
            }
        }
    }

    /**
     * Invalidate cache when chunk is unloaded to prevent stale references and memory leaks.
     */
    @Override
    public void onChunkUnloaded(FullChunk chunk) {
        if (chunk != null && !entity.isClosed()) {
            long chunkKey = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
            chunkCache.invalidate(chunkKey);
        }
    }

    /**
     * Returns blocks colliding with the entity's AABB, extended by its motion vector.
     *
     * @param boundingBox The entity's current axis-aligned bounding box.
     * @param motionX X component of motion.
     * @param motionY Y component of motion.
     * @param motionZ Z component of motion.
     * @return Colliding blocks, or an empty list if none.
     */
    public List<Block> getCollisionBlocks(AxisAlignedBB boundingBox, double motionX, double motionY, double motionZ) {
        if (entity.isClosed()) {
            return Collections.emptyList();
        }

        Level level = entity.getLevel();
        if (level == null) {
            return Collections.emptyList();
        }

        List<Block> blocks = this.getBlocksInBoundingBox(boundingBox);

        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Block> collisionBlocks = new ArrayList<>(8);
        for (Block block : blocks) {
            int id = block.getId();
            if (id == Block.NETHER_PORTAL) {
                double motionAbsX = Math.abs(motionX);
                double motionAbsY = Math.abs(motionY);
                double motionAbsZ = Math.abs(motionZ);
                if (block.collidesWithBB(boundingBox.grow(motionAbsX + 1, motionAbsY + 1, motionAbsZ + 1))) {
                    collisionBlocks.add(block);
                }
            } else if (block.collidesWithBB(boundingBox, true)) {
                collisionBlocks.add(block);
            }
        }

        return collisionBlocks.isEmpty() ? Collections.emptyList() : collisionBlocks;
    }

    /**
     * Returns non-air blocks in the given AABB using chunk and block data caching.
     *
     * @param boundingBox The bounding box to scan.
     * @return Intersecting non-air blocks, or an empty list if none.
     */
    public List<Block> getBlocksInBoundingBox(AxisAlignedBB boundingBox) {
        Level level = entity.getLevel();
        if (level == null || entity.isClosed()) {
            return Collections.emptyList();
        }

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = Math.max(NukkitMath.floorDouble(boundingBox.getMinY()), level.getMinBlockY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.floorDouble(boundingBox.getMaxX());
        int maxY = Math.min(NukkitMath.floorDouble(boundingBox.getMaxY()), level.getMaxBlockY());
        int maxZ = NukkitMath.floorDouble(boundingBox.getMaxZ());

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
                FullChunk chunk = chunkCache.getIfPresent(chunkKey);
                if (chunk == null) {
                    chunk = level.getChunkIfLoaded(chunkX, chunkZ);
                    if (chunk != null) {
                        chunkCache.put(chunkKey, chunk);
                    } else {
                        continue;
                    }
                }
                for (int y = minY; y <= maxY; y++) {
                    if (!level.isYInRange(y)) continue;

                    long blockKey = (chunkKey << 16) | (localX << 12) | ((long) y << 8) | (localZ << 4);
                    Integer cachedData = blockDataCache.getIfPresent(blockKey);

                    int blockId;
                    int blockData;

                    if (cachedData != null) {
                        blockId = cachedData >> 8;
                        blockData = cachedData & 0xFF;
                    } else {
                        blockId = chunk.getBlockId(localX, y, localZ);
                        if (blockId == Block.AIR) continue;
                        blockData = chunk.getBlockData(localX, y, localZ);
                        blockDataCache.put(blockKey, (blockId << 8) | blockData);
                    }

                    if (blockId == Block.AIR) continue;

                    Block block = Block.get(blockId, blockData, level, x, y, z);
                    result.add(block);
                }
            }
        }
        return result;
    }

    /**
     * Checks if the bounding box intersects a block of the given type.
     * Uses chunk and block data caching for performance.
     *
     * @param boundingBox The bounding box to test.
     * @param targetBlockId The block ID to check (e.g., Block.FIRE).
     * @return {@code true} if any matching block intersects the box.
     */
    public boolean isInsideSpecialBlock(AxisAlignedBB boundingBox, int targetBlockId) {
        Level level = entity.getLevel();
        if (level == null || entity.isClosed()) {
            return false;
        }

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = Math.max(NukkitMath.floorDouble(boundingBox.getMinY()), level.getMinBlockY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.floorDouble(boundingBox.getMaxX());
        int maxY = Math.min(NukkitMath.floorDouble(boundingBox.getMaxY()), level.getMaxBlockY());
        int maxZ = NukkitMath.floorDouble(boundingBox.getMaxZ());

        if (minY > maxY || !level.isYInRange(minY) || !level.isYInRange(maxY)) {
            return false;
        }

        this.updateChunk();

        for (int x = minX; x <= maxX; x++) {
            int chunkX = x >> 4;
            int localX = x & 0x0f;
            for (int z = minZ; z <= maxZ; z++) {
                int chunkZ = z >> 4;
                int localZ = z & 0x0f;
                long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                FullChunk chunk = chunkCache.getIfPresent(chunkKey);
                if (chunk == null) {
                    chunk = level.getChunkIfLoaded(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    chunkCache.put(chunkKey, chunk);
                }
                for (int y = minY; y <= maxY; y++) {
                    if (!level.isYInRange(y)) continue;

                    long blockKey = (chunkKey << 16) | (localX << 12) | (y << 8) | (localZ << 4);
                    Integer cachedData = blockDataCache.getIfPresent(blockKey);

                    int blockId;
                    int blockData;

                    if (cachedData != null) {
                        blockId = cachedData >> 8;
                        if (blockId != targetBlockId) continue;
                        blockData = cachedData & 0xFF;
                    } else {
                        blockId = chunk.getBlockId(localX, y, localZ);
                        if (blockId != targetBlockId) continue;
                        blockData = chunk.getBlockData(localX, y, localZ);

                        blockDataCache.put(blockKey, (blockId << 8) | blockData);
                    }

                    Block block = Block.get(blockId, blockData, level, x, y, z);
                    if (block.collidesWithBB(boundingBox)) {
                        return true;
                    }
                }
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