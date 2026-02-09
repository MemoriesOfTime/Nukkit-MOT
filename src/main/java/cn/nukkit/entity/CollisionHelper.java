package cn.nukkit.entity;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;

import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Collision helper for entities and other targets.
 * @author labarjni
 */
public record CollisionHelper(Entity entity) {

    /**
     * Gets blocks that collide with current entity's AABB.
     *
     * @return Array of colliding blocks
     */
    public Block[] getCollisionBlocks() {
        if (entity.isClosed()) return Block.EMPTY_ARRAY;

        Level level = entity.getLevel();
        if (level == null) return Block.EMPTY_ARRAY;

        AxisAlignedBB boundingBox = entity.getBoundingBox();

        Block[] blocks = getBlocksInBoundingBox(boundingBox.grow(0.5, 0, 0.5));
        if (blocks.length == 0) return Block.EMPTY_ARRAY;

        double shrinkX = (boundingBox.getMaxX() - boundingBox.getMinX()) * 0.25;
        double shrinkZ = (boundingBox.getMaxZ() - boundingBox.getMinZ()) * 0.25;

        Block[] result = new Block[Math.min(blocks.length, 4)];
        int count = 0;

        for (Block block : blocks) {
            if (block.collidesWithBB(boundingBox.shrink(shrinkX, 0, shrinkZ), true)) {
                if (count == result.length) {
                    result = Arrays.copyOf(result, result.length * 2);
                }
                result[count++] = block;
            }
        }

        return count == 0 ? Block.EMPTY_ARRAY :
                count == result.length ? result :
                        Arrays.copyOf(result, count);
    }

    /**
     * Gets blocks in bounding box.
     *
     * @param boundingBox Bounding box to check
     * @return Array of blocks
     */
    public Block[] getBlocksInBoundingBox(AxisAlignedBB boundingBox) {
        Level level = entity.getLevel();
        if (level == null || entity.isClosed()) return Block.EMPTY_ARRAY;

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) return Block.EMPTY_ARRAY;

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) return Block.EMPTY_ARRAY;

        int estimatedCount = (maxX - minX + 1) * (maxZ - minZ + 1) * (clampedMaxY - clampedMinY + 1);
        Block[] result = new Block[Math.min(estimatedCount, 64)];
        int count = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = clampedMinY; y <= clampedMaxY; y++) {
                    int blockId = level.getBlockIdAt(x, y, z);
                    if (blockId == Block.AIR) continue;

                    if (count == result.length) {
                        result = Arrays.copyOf(result, Math.min(result.length * 2, estimatedCount));
                    }

                    int blockData = level.getBlockDataAt(x, y, z);
                    result[count++] = Block.get(blockId, blockData, level, x, y, z);
                }
            }
        }

        return count == 0 ? Block.EMPTY_ARRAY : Arrays.copyOf(result, count);
    }

    /**
     * Checks if bounding box intersects specific block type.
     *
     * @param boundingBox The bounding box to test.
     * @param targetBlockId The block ID to check (e.g., Block.FIRE).
     * @return {@code true} if any matching block intersects the box.
     */
    public boolean isInsideBlock(
            AxisAlignedBB boundingBox,
            int targetBlockId
    ) {
        Level level = entity.getLevel();
        if (level == null || entity.isClosed()) return false;

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) return false;

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) return false;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = clampedMinY; y <= clampedMaxY; y++) {
                    if (level.getBlockIdAt(x, y, z) != targetBlockId) continue;

                    int blockData = level.getBlockDataAt(x, y, z);
                    Block block = Block.get(targetBlockId, blockData, level, x, y, z);
                    if (block.collidesWithBB(boundingBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
     * API
        Contains auxiliary collision methods
        Uses lists instead of arrays
     */

    /**
     * Gets colliding entities within a bounding box.
     *
     * @param level The level to check
     * @param boundingBox The axis-aligned bounding box to check
     * @return List of colliding entities
     */
    public static List<Entity> getCollidingEntities(Level level, AxisAlignedBB boundingBox) {
        return getCollidingEntities(level, boundingBox, null);
    }

    /**
     * Gets colliding entities within a bounding box.
     *
     * @param level The level to check
     * @param boundingBox The axis-aligned bounding box to check
     * @param entity Entity to exclude from results (can be null)
     * @return List of colliding entities
     */
    public static List<Entity> getCollidingEntities(Level level, AxisAlignedBB boundingBox, @Nullable Entity entity) {
        List<Entity> nearby = new ArrayList<>();

        if (entity == null || entity.canCollide()) {
            int minX = NukkitMath.floorDouble((boundingBox.getMinX() - 2) / 16);
            int maxX = NukkitMath.ceilDouble((boundingBox.getMaxX() + 2) / 16);
            int minZ = NukkitMath.floorDouble((boundingBox.getMinZ() - 2) / 16);
            int maxZ = NukkitMath.ceilDouble((boundingBox.getMaxZ() + 2) / 16);

            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    for (Entity e : level.getChunkEntities(x, z, false).values()) {
                        if ((entity == null || (e != entity && entity.canCollideWith(e))) && e.getBoundingBox().intersectsWith(boundingBox)) {
                            nearby.add(e);
                        }
                    }
                }
            }
        }

        return nearby;
    }

    /**
     * Gets blocks that collide with bounding box in a level.
     *
     * @param level The level to check
     * @param boundingBox The axis-aligned bounding box
     * @return List of colliding blocks
     */
    public static @NotNull List<Block> getCollisionBlocks(
            Level level,
            AxisAlignedBB boundingBox
    ) {
        return getCollisionBlocks(
                level,
                boundingBox,
                null,
                false,
                false
        );
    }

    /**
     * Gets blocks that collide with bounding box in a level.
     *
     * @param level The level to check
     * @param boundingBox The axis-aligned bounding box
     * @param entity Optional entity for chunk reference
     * @param targetFirst If true, returns at first collision
     * @return List of colliding blocks
     */
    public static @NotNull List<Block> getCollisionBlocks(
            Level level,
            AxisAlignedBB boundingBox,
            Entity entity,
            boolean targetFirst
    ) {
        return getCollisionBlocks(
                level,
                boundingBox,
                entity,
                targetFirst,
                false,
                block -> block.getId() != Block.AIR
        );
    }

    /**
     * Gets blocks that collide with bounding box in a level.
     *
     * @param level The level to check
     * @param boundingBox The axis-aligned bounding box
     * @param entity Optional entity for chunk reference
     * @param targetFirst If true, returns at first collision
     * @param ignoreCollidesCheck If true, ignores block.canPassThrough() check
     * @return List of colliding blocks
     */
    public static @NotNull List<Block> getCollisionBlocks(
            Level level,
            AxisAlignedBB boundingBox,
            Entity entity,
            boolean targetFirst,
            boolean ignoreCollidesCheck
    ) {
        return getCollisionBlocks(
                level,
                boundingBox,
                entity,
                targetFirst,
                ignoreCollidesCheck,
                block -> block.getId() != Block.AIR
        );
    }

    /**
     * Gets blocks that collide with bounding box in a level.
     *
     * @param level The level to check
     * @param boundingBox The axis-aligned bounding box
     * @param entity Optional entity for chunk reference
     * @param targetFirst If true, returns at first collision
     * @param ignoreCollidesCheck If true, ignores block.canPassThrough() check
     * @param condition Additional condition for block filtering
     * @return List of colliding blocks
     */
    public static List<Block> getCollisionBlocks(
            Level level,
            AxisAlignedBB boundingBox,
            Entity entity,
            boolean targetFirst,
            boolean ignoreCollidesCheck,
            Predicate<Block> condition
    ) {
        if (level == null) return List.of(Block.EMPTY_ARRAY);

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) return List.of(Block.EMPTY_ARRAY);

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) return List.of(Block.EMPTY_ARRAY);

        if (targetFirst) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                        Block block = level.getBlock(x, y, z, false);
                        if (block != null && condition.test(block) &&
                                (ignoreCollidesCheck || block.collidesWithBB(boundingBox))) {
                            return Collections.singletonList(block);
                        }
                    }
                }
            }
        } else {
            List<Block> collides = new ArrayList<>();
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                        Block block = level.getBlock(entity != null ? entity.chunk : null,
                                x, y, z, 0, false);
                        if (block != null && condition.test(block) &&
                                (ignoreCollidesCheck || block.collidesWithBB(boundingBox))) {
                            collides.add(block);
                        }
                    }
                }
            }
            return collides;
        }

        return List.of(Block.EMPTY_ARRAY);
    }

    /**
     * Checks if there are any collision blocks in the bounding box.
     *
     * @param level Level to check
     * @param entity Optional entity for chunk reference
     * @param boundingBox The axis-aligned bounding box
     * @param checkCanPassThrough If true, checks block.canPassThrough()
     * @return true if there are collision blocks
     */
    public static boolean hasCollisionBlocks(
            Level level,
            @Nullable Entity entity,
            AxisAlignedBB boundingBox,
            boolean checkCanPassThrough
    ) {
        if (level == null) return false;

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) return false;

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) return false;

        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                    Block block = level.getBlock(entity != null ? entity.chunk : null, x, y, z, 0, false);
                    if (block != null &&
                            (!checkCanPassThrough || !block.canPassThrough()) &&
                            block.collidesWithBB(boundingBox)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if there are any collision blocks in the bounding box.
     *
     * @param level Level to check
     * @param entity Entity for chunk reference
     * @param boundingBox The axis-aligned bounding box
     * @return true if there are collision blocks
     */
    public static boolean hasCollisionBlocks(
            Level level,
            Entity entity,
            AxisAlignedBB boundingBox
    ) {
        return hasCollisionBlocks(
                level,
                entity,
                boundingBox,
                true
        );
    }

    /**
     * Gets collision cubes (bounding boxes) for blocks.
     *
     * @param level Level to check
     * @param entity Optional entity to exclude from entity collisions
     * @param boundingBox The axis-aligned bounding box
     * @param entities If true, includes entity collisions
     * @param solidEntities If true, includes only solid entities
     * @return List of collision cubes
     */
    public static List<AxisAlignedBB> getCollisionCubes(
            Level level,
            @Nullable Entity entity,
            AxisAlignedBB boundingBox,
            boolean entities,
            boolean solidEntities
    ) {
        if (level == null) return Block.EMPTY_LIST;

        List<AxisAlignedBB> collides = new ArrayList<>();

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return collides;
        }

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) {
            return collides;
        }

        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                    Block block = level.getBlock(x, y, z, false);
                    if (block.getId() != Block.AIR && !block.canPassThrough() && block.collidesWithBB(boundingBox)) {
                        AxisAlignedBB blockBB = block.getBoundingBox();
                        if (blockBB != null) {
                            collides.add(blockBB);
                        }
                    }
                }
            }
        }

        if (entities || solidEntities) {
            for (Entity e : getCollidingEntities(level, boundingBox.grow(0.25f, 0.25f, 0.25f), entity)) {
                if (solidEntities || !e.canPassThrough()) {
                    collides.add(e.getBoundingBox().clone());
                }
            }
        }

        return collides;
    }

    /**
     * Gets collision cubes (bounding boxes) for blocks only.
     *
     * @param level Level to check
     * @param entity Entity for level reference
     * @param boundingBox The axis-aligned bounding box
     * @return List of collision cubes
     */
    public static List<AxisAlignedBB> getCollisionCubes(
            Level level,
            Entity entity,
            AxisAlignedBB boundingBox
    ) {
        return getCollisionCubes(
                level,
                entity,
                boundingBox,
                false,
                false
        );
    }

    /**
     * Gets collision cubes (bounding boxes) for blocks only.
     *
     * @param level Level to check
     * @param entity Entity for level reference
     * @param boundingBox The axis-aligned bounding box
     * @param entities If true, includes entity collisions
     * @return List of collision cubes
     */
    public static List<AxisAlignedBB> getCollisionCubes(
            Level level,
            Entity entity,
            AxisAlignedBB boundingBox,
            boolean entities
    ) {
        return getCollisionCubes(
                level,
                entity,
                boundingBox,
                entities,
                false
        );
    }

    /**
     * Gets blocks around the entity's current position.
     *
     * @return Array of blocks around the entity
     */
    public Block[] getBlocksAround() {
        return getBlocksInBoundingBox(entity.boundingBox);
    }
}