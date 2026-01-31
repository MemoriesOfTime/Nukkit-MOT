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
    private final Cache<Integer, FullChunk> chunkCache = Caffeine.newBuilder()
            .maximumSize(4)
            .build();

    public EntityCollision(Entity entity) {
        this.entity = entity;
    }

    /**
     * Clears internal chunk cache to free memory when the entity is removed.
     */
    public void clearCaches() {
        chunkCache.invalidateAll();
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
            int chunkKey = ecX * 31 + ecZ;
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
                if (boundingBox.grow(motionAbsX + 0.3, motionAbsY + 0.3, motionAbsZ + 0.3).intersectsWith(boundingBox)) {
                    collisionBlocks.add(block);
                }
            } else if (block.collidesWithBB(boundingBox, true)) {
                collisionBlocks.add(block);
            }
        }

        return collisionBlocks.isEmpty() ? Collections.emptyList() : collisionBlocks;
    }

    /**
     * Returns non-air blocks in the given AABB using chunk caching.
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

        List<Block> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            int chunkX = x >> 4;
            int localX = x & 0x0f;
            for (int z = minZ; z <= maxZ; z++) {
                int chunkZ = z >> 4;
                int localZ = z & 0x0f;
                int chunkKey = chunkX * 31 + chunkZ;
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
                    int blockId = chunk.getBlockId(localX, y, localZ);
                    if (blockId == Block.AIR) continue;
                    int blockData = chunk.getBlockData(localX, y, localZ);
                    Block block = Block.get(blockId, blockData, level, x, y, z);
                    result.add(block);
                }
            }
        }
        return result;
    }

    /**
     * Checks if the bounding box intersects a block of the given type.
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

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!level.isYInRange(y)) continue;
                    Block block = level.getBlock(x, y, z);
                    int id = block.getId();
                    if (id == targetBlockId && block.getBoundingBox().intersectsWith(boundingBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override public int getLoaderId() { return 0; }
    @Override public boolean isLoaderActive() { return false; }
    @Override public Position getPosition() { return entity.getPosition(); }
    @Override public double getX() { return entity.getChunkX(); }
    @Override public double getZ() { return entity.getChunkZ(); }
    @Override public Level getLevel() { return entity.getLevel(); }
    @Override public void onChunkChanged(FullChunk chunk) {}
    @Override public void onChunkLoaded(FullChunk chunk) {}
    @Override public void onChunkUnloaded(FullChunk chunk) {}
    @Override public void onChunkPopulated(FullChunk chunk) {}
}