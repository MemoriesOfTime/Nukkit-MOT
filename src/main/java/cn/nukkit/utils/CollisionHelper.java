package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockBarrier;
import cn.nukkit.block.BlockBell;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockFenceGate;
import cn.nukkit.block.BlockUnknown;
import cn.nukkit.block.BlockWall;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Collision helper for entities and other targets.
 * @author labarjni
 */
public record CollisionHelper(Entity entity) {

    /** Cap on block positions visited per collision query; guards against runaway loops from malformed AABBs (cf. EaseCation). */
    private static final int MAX_BOUNDING_BOX_ITERATIONS = 1_000_000;

    /** Per-axis cap on motion-based AABB expansion; vanilla speeds stay well below it (elytra ≈ 3/tick), preventing a single bogus motion from exploding the sweep. {@link #MAX_BOUNDING_BOX_ITERATIONS} still backstops oversized base boxes. */
    private static final double MAX_MOTION_EXPANSION = 64.0;

    /** Minimum interval (ms) between duplicate runaway-AABB log entries for the same entity. */
    private static final long LOG_THROTTLE_MS = 30_000L;

    /** Per-entity last-log timestamp for throttling runaway-AABB warnings. Weak keys auto-clear when the entity is GC'd. */
    private static final Map<Entity, Long> RUNAWAY_LOG_TIMES = Collections.synchronizedMap(new WeakHashMap<>());

    /** Test-only hook to reset the throttle map between tests. Not part of the public API. */
    static void resetThrottleStateForTests() {
        RUNAWAY_LOG_TIMES.clear();
    }

    /** Default filter of the {@code getCollisionBlocks} overloads: everything except air. Kept as a
     * constant so the loop can recognise it and take the allocation-free air fast path below. */
    private static final Predicate<Block> NOT_AIR = block -> block.getId() != Block.AIR;

    /**
     * Resolves the chunk owning a block column, reusing {@code hint} when it already covers it.
     * Returns {@code null} for an unloaded chunk, which every caller here treats as air - exactly what
     * {@link Level#getBlock} produces for a missing chunk.
     */
    private static FullChunk chunkAt(Level level, FullChunk hint, int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        if (hint != null && hint.getX() == cx && hint.getZ() == cz) {
            return hint;
        }
        return level.getChunkIfLoaded(cx, cz);
    }

    /**
     * Allocation-free air probe: reads the raw block id out of the section instead of materialising a
     * Block. Empty cells dominate every entity bounding box, and each one used to allocate a BlockAir
     * (plus its Position/Vector3 clone) that the very next line threw away. Air has no bounding box and
     * no collision box, so skipping it is behaviour-preserving for all callers below.
     */
    private static boolean isAirAt(FullChunk chunk, int x, int y, int z) {
        return chunk == null || chunk.getBlockId(x & 0xF, y, z & 0xF, 0) == Block.AIR;
    }

    /** Rejects non-finite AABBs: NaN/Infinity make floor/ceil overflow and throw NegativeArraySizeException. */
    private static boolean isFinite(AxisAlignedBB boundingBox) {
        return boundingBox != null
                && Double.isFinite(boundingBox.getMinX())
                && Double.isFinite(boundingBox.getMinY())
                && Double.isFinite(boundingBox.getMinZ())
                && Double.isFinite(boundingBox.getMaxX())
                && Double.isFinite(boundingBox.getMaxY())
                && Double.isFinite(boundingBox.getMaxZ());
    }

    /** True if the AABB's block range exceeds {@link #MAX_BOUNDING_BOX_ITERATIONS}; uses long arithmetic, and non-positive sizes count as exceeding. */
    private static boolean exceedsMaxIterations(int minX, int minY, int minZ,
                                                int maxX, int maxY, int maxZ) {
        long sx = (long) maxX - minX + 1;
        long sy = (long) maxY - minY + 1;
        long sz = (long) maxZ - minZ + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0) {
            return true;
        }
        return sx * sy * sz > MAX_BOUNDING_BOX_ITERATIONS;
    }

    /**
     * Logs a runaway-AABB warning once per {@link #LOG_THROTTLE_MS} window per entity, to aid tracing without log flooding.
     * Throttling uses a single atomic {@link Map#compute} to prevent duplicate concurrent warnings.
     */
    private static void logRunawayAABB(@Nullable Entity entity, AxisAlignedBB bb, String where) {
        if (entity == null) return;
        Server server = Server.getInstance();
        if (server == null) return;
        MainLogger logger = server.getLogger();
        if (logger == null) return;

        long now = System.currentTimeMillis();
        boolean[] shouldLog = {false};
        RUNAWAY_LOG_TIMES.compute(entity, (k, last) -> {
            if (last != null && now - last < LOG_THROTTLE_MS) {
                return last; // within window
            }
            shouldLog[0] = true;
            return now;
        });
        if (!shouldLog[0]) return;

        logger.warning("Runaway collision AABB in " + where
                + " (truncated to " + MAX_BOUNDING_BOX_ITERATIONS + " blocks)"
                + " entity=" + entity.getClass().getSimpleName()
                + " id=" + entity.getId()
                + " pos=(" + entity.x + ", " + entity.y + ", " + entity.z + ")"
                + " motion=(" + entity.motionX + ", " + entity.motionY + ", " + entity.motionZ + ")"
                + " bb=[(" + bb.getMinX() + ", " + bb.getMinY() + ", " + bb.getMinZ() + ")"
                + " -> (" + bb.getMaxX() + ", " + bb.getMaxY() + ", " + bb.getMaxZ() + ")]");
    }

    /** Logs a runaway-AABB condition for the static (level-only) collision APIs, where no entity is available. */
    private static void logRunawayAABBStatic(AxisAlignedBB bb, String where) {
        Server server = Server.getInstance();
        if (server == null) return;
        MainLogger logger = server.getLogger();
        if (logger == null) return;
        logger.warning("Runaway collision AABB in " + where
                + " (truncated to " + MAX_BOUNDING_BOX_ITERATIONS + " blocks)"
                + " bb=[(" + bb.getMinX() + ", " + bb.getMinY() + ", " + bb.getMinZ() + ")"
                + " -> (" + bb.getMaxX() + ", " + bb.getMaxY() + ", " + bb.getMaxZ() + ")]");
    }

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

        double motionAbsX = Math.abs(entity.motionX);
        double motionAbsY = Math.abs(entity.motionY);
        double motionAbsZ = Math.abs(entity.motionZ);
        // Cap per-axis expansion; see MAX_MOTION_EXPANSION.
        double expandX = Math.min(MAX_MOTION_EXPANSION, Math.max(0.5, motionAbsX + 0.3));
        double expandY = Math.min(MAX_MOTION_EXPANSION, Math.max(0.5, motionAbsY + 0.3));
        double expandZ = Math.min(MAX_MOTION_EXPANSION, Math.max(0.5, motionAbsZ + 0.3));

        Block[] blocks = this.getCollisionCandidates(boundingBox.grow(expandX, expandY, expandZ), boundingBox);
        return filterCollisionBlocks(blocks, boundingBox, expandX, expandY, expandZ);
    }

    /**
     * The per-block half of {@link #getCollisionBlocks()}: which scanned blocks the entity touches.
     * Split out unchanged so the scan can be checked against {@link #getBlocksInBoundingBox} by tests.
     */
    static Block[] filterCollisionBlocks(Block[] blocks, AxisAlignedBB boundingBox,
                                         double expandX, double expandY, double expandZ) {
        if (blocks.length == 0) return Block.EMPTY_ARRAY;

        Block[] result = new Block[Math.min(blocks.length, 4)];
        int count = 0;

        for (Block block : blocks) {
            if (block.canPassThrough()) {
                if (block.hasDynamicCollision()) {
                    // Dynamic traversable-block collision; reuse the per-axis cap to bound the trajectory BB.
                    AxisAlignedBB trajectoryBB = boundingBox.grow(expandX, expandY, expandZ);
                    if (block.collidesWithBB(trajectoryBB, true)) {
                        if (count == result.length) {
                            result = Arrays.copyOf(result, result.length << 1);
                        }
                        result[count++] = block;
                    }
                } else {
                    // Simple traversable blocks with shrunk hitboxes to avoid diagonal collisions
                    double shrinkX = (boundingBox.getMaxX() - boundingBox.getMinX()) * 0.25;
                    double shrinkZ = (boundingBox.getMaxZ() - boundingBox.getMinZ()) * 0.25;
                    AxisAlignedBB shrinkBB = boundingBox.shrink(shrinkX, 0, shrinkZ);
                    if (block.collidesWithBB(shrinkBB, true)) {
                        if (count == result.length) {
                            result = Arrays.copyOf(result, result.length << 1);
                        }
                        result[count++] = block;
                    }
                }
            } else if (block.collidesWithBB(boundingBox, true)) {
                if (count == result.length) {
                    result = Arrays.copyOf(result, result.length << 1);
                }
                result[count++] = block;
            }
        }

        return count == 0 ? Block.EMPTY_ARRAY : Arrays.copyOf(result, count);
    }

    /* ---------------------------------------------------------------------------------------------
     * Collision candidates.
     *
     * getCollisionBlocks scans the bounding box grown by at least half a block on every side, but the
     * margin only exists for fire and nether portals (hasDynamicCollision), which are tested against
     * that grown "trajectory" box. Every other block is tested against the entity's own box (solid) or
     * a box inside it (pass-through), and its shape lies inside its own cell - except fences, fence
     * gates and walls, which reach half a block above it, and bells, padded by 1e-6 on every side.
     * So outside the cells the entity's box overlaps only fire, portals, the one layer of fences,
     * gates and walls below the feet, bells, custom blocks and unregistered ids can ever pass the
     * filter. Everything else there - above all the floor every mob stands on - used to be
     * materialised, tested and dropped every tick, one section lock per cell.
     *
     * The candidate scan keeps the very same cells, in the very same x, z, y order, and materialises
     * blocks exactly as getBlocksInBoundingBox does; it only leaves out blocks that cannot pass the
     * unchanged filter. Standard LevelDB sections are read one cuboid per section under a single lock.
     * ------------------------------------------------------------------------------------------- */

    /** Shape flags per legacy block id; see {@link #shapeFlags()}. */
    static final byte SHAPE_UNKNOWN = 1;
    static final byte SHAPE_DYNAMIC = 2;
    static final byte SHAPE_OVERHANG = 4;

    /** Collision shapes of fences, gates and walls end this far above their cell. */
    private static final double COLLISION_OVERHANG = 0.5;

    private static volatile byte[] shapeFlags;

    private static final byte CELL_AIR = 0;
    private static final byte CELL_PAIR = 1;
    private static final byte CELL_FALLBACK = 2;

    /** Scratch buffers kept per thread; larger scans allocate instead of pinning memory. */
    private static final int SCRATCH_RETAIN_LIMIT = 1 << 14;
    private static final ThreadLocal<long[]> SCRATCH_PAIRS = ThreadLocal.withInitial(() -> new long[256]);
    private static final ThreadLocal<byte[]> SCRATCH_CELLS = ThreadLocal.withInitial(() -> new byte[256]);

    /**
     * Per legacy id: SHAPE_DYNAMIC when the block collides through the trajectory box, SHAPE_OVERHANG
     * when its collision shape rises above its cell, SHAPE_UNKNOWN when nothing is known about the id
     * (not registered or no prototype). Ids outside the table (custom blocks) are treated as unknown.
     * Returns null before {@link Block#init()}, which keeps the original scan.
     */
    static byte[] shapeFlags() {
        byte[] flags = shapeFlags;
        if (flags == null) {
            @SuppressWarnings("rawtypes")
            Class[] list = Block.list;
            if (list == null) {
                return null;
            }
            flags = new byte[list.length];
            for (int id = 0; id < list.length; id++) {
                if (list[id] == null) {
                    flags[id] = SHAPE_UNKNOWN;
                    continue;
                }
                byte flag;
                try {
                    // The same prototypes the scan materialises from, so the table cannot disagree with them.
                    Block prototype = Block.get(id);
                    if (prototype == null || prototype instanceof BlockUnknown || prototype instanceof BlockBell) {
                        // A bell pads its collision box by 1e-6 on every side so a dropped item rings it.
                        flag = SHAPE_UNKNOWN;
                    } else {
                        flag = 0;
                        if (prototype.hasDynamicCollision()) {
                            flag |= SHAPE_DYNAMIC;
                        }
                        if (prototype instanceof BlockFence || prototype instanceof BlockFenceGate
                                || prototype instanceof BlockWall) {
                            flag |= SHAPE_OVERHANG;
                        }
                    }
                } catch (Throwable ignored) {
                    flag = SHAPE_UNKNOWN;
                }
                flags[id] = flag;
            }
            shapeFlags = flags;
        }
        return flags;
    }

    /** Whether a block with this raw id, outside the cells the entity overlaps, can still pass the filter. */
    private static boolean mayCollideFromOutside(byte[] flags, int id, boolean overhangLayer) {
        if (id < 0 || id >= flags.length) {
            return true;
        }
        int flag = flags[id];
        return (flag & (SHAPE_UNKNOWN | SHAPE_DYNAMIC)) != 0 || (overhangLayer && (flag & SHAPE_OVERHANG) != 0);
    }

    /**
     * Blocks of {@code scanBox} that {@link #filterCollisionBlocks} can accept for an entity whose own
     * box is {@code entityBox}: the result of {@code getBlocksInBoundingBox(scanBox)} without the blocks
     * the filter always rejects, in the same order and materialised the same way.
     */
    Block[] getCollisionCandidates(AxisAlignedBB scanBox, AxisAlignedBB entityBox) {
        Level level = entity.getLevel();
        byte[] flags = shapeFlags();
        if (level == null || entity.isClosed() || flags == null || !isFinite(scanBox) || !isFinite(entityBox)
                || level.getClass() != Level.class || level.getProvider() == null
                || level.getProvider().getClass() != LevelDBProvider.class) {
            return this.getBlocksInBoundingBox(scanBox);
        }

        int minX = NukkitMath.floorDouble(scanBox.getMinX());
        int minY = NukkitMath.floorDouble(scanBox.getMinY());
        int minZ = NukkitMath.floorDouble(scanBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(scanBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(scanBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(scanBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) return Block.EMPTY_ARRAY;

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) return Block.EMPTY_ARRAY;

        long estimatedCount = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (clampedMaxY - clampedMinY + 1);
        if (estimatedCount <= 0 || estimatedCount > MAX_BOUNDING_BOX_ITERATIONS) {
            logRunawayAABB(entity, scanBox, "getBlocksInBoundingBox");
            return Block.EMPTY_ARRAY;
        }

        // Cells whose own shape can reach the entity's box: x with x < maxX && x + 1 > minX, the same
        // for z and y, plus the layers from which a shape rising COLLISION_OVERHANG above its cell can.
        // Math.ceil on purpose: NukkitMath.ceilDouble answers floor for positive fractions.
        int innerMinX = NukkitMath.floorDouble(entityBox.getMinX());
        int innerMaxX = (int) Math.ceil(entityBox.getMaxX()) - 1;
        int innerMinZ = NukkitMath.floorDouble(entityBox.getMinZ());
        int innerMaxZ = (int) Math.ceil(entityBox.getMaxZ()) - 1;
        int innerMinY = NukkitMath.floorDouble(entityBox.getMinY());
        int innerMaxY = (int) Math.ceil(entityBox.getMaxY()) - 1;
        int overhangMinY = NukkitMath.floorDouble(entityBox.getMinY() - COLLISION_OVERHANG);

        int sizeY = clampedMaxY - clampedMinY + 1;
        int sizeZ = maxZ - minZ + 1;
        int strideX = sizeZ * sizeY;
        int cells = (int) estimatedCount;
        long[] pairs = scratchPairs(cells);
        byte[] kinds = scratchCells(cells);
        Arrays.fill(kinds, 0, cells, CELL_AIR);

        // Pass 1: every chunk once, every standard section as one locked cuboid read.
        FullChunk hint = entity.chunk;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            int x0 = Math.max(minX, chunkX << 4);
            int x1 = Math.min(maxX, (chunkX << 4) + 15);
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                int z0 = Math.max(minZ, chunkZ << 4);
                int z1 = Math.min(maxZ, (chunkZ << 4) + 15);
                FullChunk chunk = chunkAt(level, hint, x0, z0);
                if (chunk == null) {
                    continue;
                }
                hint = chunk;
                boolean standardChunk = chunk.getClass() == LevelDBChunk.class
                        && chunk.getProvider() == level.getProvider()
                        && chunk.getX() == chunkX && chunk.getZ() == chunkZ;
                for (int sectionY = clampedMinY >> 4; sectionY <= clampedMaxY >> 4; sectionY++) {
                    int y0 = Math.max(clampedMinY, sectionY << 4);
                    int y1 = Math.min(clampedMaxY, (sectionY << 4) + 15);
                    int base = (x0 - minX) * strideX + (z0 - minZ) * sizeY + (y0 - clampedMinY);
                    byte kind;
                    if (standardChunk) {
                        ChunkSection section = ((LevelDBChunk) chunk).getSection(sectionY);
                        if (section.getClass() == EmptyChunkSection.class) {
                            continue;
                        }
                        if (section.getClass() == LevelDBChunkSection.class) {
                            ((LevelDBChunkSection) section).getBlockStatePairs(0, x0 & 0xF, y0 & 0xF, z0 & 0xF,
                                    x1 & 0xF, y1 & 0xF, z1 & 0xF, pairs, base, strideX, sizeY);
                            kind = CELL_PAIR;
                        } else {
                            kind = CELL_FALLBACK;
                        }
                    } else {
                        kind = CELL_FALLBACK;
                    }
                    for (int x = x0; x <= x1; x++) {
                        for (int z = z0; z <= z1; z++) {
                            int index = base + (x - x0) * strideX + (z - z0) * sizeY;
                            Arrays.fill(kinds, index, index + (y1 - y0 + 1), kind);
                        }
                    }
                }
            }
        }

        // Pass 2: the original x, z, y order; materialise what the filter can use.
        Block[] result = new Block[(int) Math.min(estimatedCount, 16)];
        int count = 0;
        hint = entity.chunk;
        for (int x = minX; x <= maxX; x++) {
            boolean insideX = x >= innerMinX && x <= innerMaxX;
            for (int z = minZ; z <= maxZ; z++) {
                boolean inside = insideX && z >= innerMinZ && z <= innerMaxZ;
                int index = (x - minX) * strideX + (z - minZ) * sizeY;
                FullChunk chunk = null;
                for (int y = clampedMinY; y <= clampedMaxY; y++, index++) {
                    byte kind = kinds[index];
                    if (kind == CELL_AIR) {
                        continue;
                    }
                    Block block;
                    boolean detached = false;
                    if (kind == CELL_PAIR) {
                        long pair = pairs[index];
                        int id = (int) (pair >>> 32);
                        if (id == Block.AIR) {
                            continue;
                        }
                        if (!(inside && y >= innerMinY && y <= innerMaxY)
                                && !mayCollideFromOutside(flags, id, inside && y >= overhangMinY && y < innerMinY)) {
                            continue;
                        }
                        block = Block.get(id, (int) pair, level, x, y, z, 0);
                        // The earlier air probe or a custom factory's returned id cannot prove ownership.
                        detached = id >= 0 && id < CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID;
                    } else {
                        // Non-standard chunk or section: exactly the per-cell path of getBlocksInBoundingBox.
                        if (chunk == null) {
                            chunk = chunkAt(level, hint, x, z);
                            if (chunk != null) {
                                hint = chunk;
                            }
                        }
                        if (isAirAt(chunk, x, y, z)) continue;
                        if (level.isYInRange(y) && chunk != null
                                && chunk.getX() == (x >> 4) && chunk.getZ() == (z >> 4)) {
                            int[] state = chunk.getBlockState(x & 0xF, y, z & 0xF, 0);
                            block = Block.get(state[0], state[1], level, x, y, z, 0);
                            detached = state[0] >= 0 && state[0] < CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID;
                        } else {
                            block = level.getBlock(chunk, x, y, z, 0, false);
                        }
                    }
                    if (block == null || block.isAir()) continue;

                    if (count == result.length) {
                        result = Arrays.copyOf(result, (int) Math.min((long) result.length * 2, estimatedCount));
                    }
                    result[count++] = detached ? block : block.clone();
                }
            }
        }

        return count == 0 ? Block.EMPTY_ARRAY : Arrays.copyOf(result, count);
    }

    private static long[] scratchPairs(int size) {
        if (size > SCRATCH_RETAIN_LIMIT) {
            return new long[size];
        }
        long[] buffer = SCRATCH_PAIRS.get();
        if (buffer.length < size) {
            buffer = new long[Math.max(size, buffer.length << 1)];
            SCRATCH_PAIRS.set(buffer);
        }
        return buffer;
    }

    private static byte[] scratchCells(int size) {
        if (size > SCRATCH_RETAIN_LIMIT) {
            return new byte[size];
        }
        byte[] buffer = SCRATCH_CELLS.get();
        if (buffer.length < size) {
            buffer = new byte[Math.max(size, buffer.length << 1)];
            SCRATCH_CELLS.set(buffer);
        }
        return buffer;
    }

    /**
     * Gets blocks around the entity's current position.
     *
     * @return Array of blocks around the entity
     */
    public Block[] getBlocksAround() {
        return getBlocksInBoundingBox(entity.getBoundingBox());
    }

    /**
     * Gets blocks in bounding box.
     *
     * @param boundingBox Bounding box to check
     * @return Array of blocks
     */
    public Block[] getBlocksInBoundingBox(AxisAlignedBB boundingBox) {
        Level level = entity.getLevel();
        if (level == null || entity.isClosed() || !isFinite(boundingBox)) return Block.EMPTY_ARRAY;

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

        // long arithmetic avoids int overflow (NegativeArraySizeException); cap bounds runaway queries.
        long estimatedCount = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (clampedMaxY - clampedMinY + 1);
        if (estimatedCount <= 0 || estimatedCount > MAX_BOUNDING_BOX_ITERATIONS) {
            logRunawayAABB(entity, boundingBox, "getBlocksInBoundingBox");
            return Block.EMPTY_ARRAY;
        }

        Block[] result = new Block[(int) Math.min(estimatedCount, 64)];
        int count = 0;
        boolean standardLevel = level.getClass() == Level.class;
        boolean standardProvider = standardLevel && level.getProvider() != null
                && level.getProvider().getClass() == LevelDBProvider.class;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                FullChunk chunk = chunkAt(level, entity.chunk, x, z);
                boolean standardChunk = standardProvider && chunk != null && chunk.getClass() == LevelDBChunk.class
                        && chunk.getProvider() == level.getProvider() && chunk.getX() == (x >> 4) && chunk.getZ() == (z >> 4);
                for (int y = clampedMinY; y <= clampedMaxY; y++) {
                    boolean haveState = false;
                    long statePair = 0L;
                    if (standardChunk) {
                        ChunkSection section = ((LevelDBChunk) chunk).getSection(y >> 4);
                        if (section.getClass() == EmptyChunkSection.class) continue;
                        if (section.getClass() == LevelDBChunkSection.class) {
                            statePair = ((LevelDBChunkSection) section).getBlockStatePair(x & 0xF, y & 0xF, z & 0xF, 0);
                            if ((int) (statePair >>> 32) == Block.AIR) continue;
                            haveState = true;
                        }
                    }
                    if (!haveState && isAirAt(chunk, x, y, z)) continue;

                    Block block;
                    boolean detached = false;
                    if (standardLevel && level.isYInRange(y) && chunk != null
                            && chunk.getX() == (x >> 4) && chunk.getZ() == (z >> 4)) {
                        // Match Level.getBlock's unpacked path; extended metadata must not be truncated.
                        int id;
                        int meta;
                        if (haveState) {
                            id = (int) (statePair >>> 32);
                            meta = (int) statePair;
                        } else {
                            int[] state = chunk.getBlockState(x & 0xF, y, z & 0xF, 0);
                            id = state[0];
                            meta = state[1];
                        }
                        block = Block.get(id, meta, level, x, y, z, 0);
                        // The earlier air probe or a custom factory's returned id cannot prove ownership.
                        detached = id >= 0 && id < CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID;
                    } else {
                        block = level.getBlock(chunk, x, y, z, 0, false);
                    }
                    if (block == null || block.isAir()) continue;

                    if (count == result.length) {
                        result = Arrays.copyOf(result, (int) Math.min((long) result.length * 2, estimatedCount));
                    }

                    result[count++] = detached ? block : block.clone();
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
        if (level == null || entity.isClosed() || !isFinite(boundingBox)) return false;

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
        if (exceedsMaxIterations(minX, clampedMinY, minZ, maxX, clampedMaxY, maxZ)) {
            logRunawayAABB(entity, boundingBox, "isInsideBlock");
            return false;
        }

        boolean skipAir = targetBlockId != Block.AIR;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                FullChunk chunk = chunkAt(level, entity.chunk, x, z);
                for (int y = clampedMinY; y <= clampedMaxY; y++) {
                    if (skipAir && isAirAt(chunk, x, y, z)) continue;

                    Block block = level.getBlock(chunk, x, y, z, 0, false);
                    if (block == null || block.getId() != targetBlockId) continue;

                    if (block.collidesWithBB(boundingBox, true)) {
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

        if ((entity == null || entity.canCollide()) && isFinite(boundingBox)) {
            int minX = NukkitMath.floorDouble((boundingBox.getMinX() - 2) / 16);
            int maxX = NukkitMath.ceilDouble((boundingBox.getMaxX() + 2) / 16);
            int minZ = NukkitMath.floorDouble((boundingBox.getMinZ() - 2) / 16);
            int maxZ = NukkitMath.ceilDouble((boundingBox.getMaxZ() + 2) / 16);

            // Guard against oversized chunk ranges (e.g. from corrupted positions): a 1M-block sweep is already unreasonable.
            long chunkRange = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (chunkRange <= 0 || chunkRange > MAX_BOUNDING_BOX_ITERATIONS) {
                if (entity != null) {
                    logRunawayAABB(entity, boundingBox, "getCollidingEntities");
                } else {
                    logRunawayAABBStatic(boundingBox, "getCollidingEntities");
                }
                return nearby;
            }

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
                NOT_AIR
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
                NOT_AIR
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
        if (level == null || !isFinite(boundingBox)) return Collections.emptyList();

        int minX = NukkitMath.floorDouble(boundingBox.getMinX());
        int minY = NukkitMath.floorDouble(boundingBox.getMinY());
        int minZ = NukkitMath.floorDouble(boundingBox.getMinZ());
        int maxX = NukkitMath.ceilDouble(boundingBox.getMaxX());
        int maxY = NukkitMath.ceilDouble(boundingBox.getMaxY());
        int maxZ = NukkitMath.ceilDouble(boundingBox.getMaxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) return Collections.emptyList();

        int clampedMinY = Math.max(minY, level.getMinBlockY());
        int clampedMaxY = Math.min(maxY, level.getMaxBlockY());
        if (clampedMinY > clampedMaxY) return Collections.emptyList();
        if (exceedsMaxIterations(minX, clampedMinY, minZ, maxX, clampedMaxY, maxZ)) {
            if (entity != null) {
                logRunawayAABB(entity, boundingBox, "getCollisionBlocks(static)");
            } else {
                logRunawayAABBStatic(boundingBox, "getCollisionBlocks(static)");
            }
            return Collections.emptyList();
        }

        // Only the built-in filter is known to reject air; a caller-supplied predicate may well be
        // looking for it, so the fast path stays off for anything else.
        boolean skipAir = condition == NOT_AIR;
        FullChunk hint = entity != null && entity.getLevel() == level ? entity.chunk : null;

        if (targetFirst) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    FullChunk chunk = chunkAt(level, hint, x, z);
                    for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                        if (skipAir && isAirAt(chunk, x, y, z)) continue;

                        Block block = level.getBlock(chunk, x, y, z, 0, false);
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
                    FullChunk chunk = chunkAt(level, hint, x, z);
                    for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                        if (skipAir && isAirAt(chunk, x, y, z)) continue;

                        Block block = level.getBlock(chunk, x, y, z, 0, false);
                        if (block != null && condition.test(block) &&
                                (ignoreCollidesCheck || block.collidesWithBB(boundingBox))) {
                            collides.add(block);
                        }
                    }
                }
            }
            return collides;
        }

        return Collections.emptyList();
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
        if (level == null || !isFinite(boundingBox)) return false;

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
        if (exceedsMaxIterations(minX, clampedMinY, minZ, maxX, clampedMaxY, maxZ)) {
            if (entity != null) {
                logRunawayAABB(entity, boundingBox, "hasCollisionBlocks");
            } else {
                logRunawayAABBStatic(boundingBox, "hasCollisionBlocks");
            }
            return false;
        }

        FullChunk hint = entity != null && entity.getLevel() == level ? entity.chunk : null;
        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                FullChunk chunk = chunkAt(level, hint, x, z);
                for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                    // Air passes through and owns no bounding box, so it never reached `return true`.
                    if (isAirAt(chunk, x, y, z)) continue;

                    Block block = level.getBlock(chunk, x, y, z, 0, false);
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
            Entity entity,
            AxisAlignedBB boundingBox,
            boolean entities,
            boolean solidEntities
    ) {
        if (level == null || !isFinite(boundingBox)) return Block.EMPTY_LIST;

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
        if (exceedsMaxIterations(minX, clampedMinY, minZ, maxX, clampedMaxY, maxZ)) {
            if (entity != null) {
                logRunawayAABB(entity, boundingBox, "getCollisionCubes");
            } else {
                logRunawayAABBStatic(boundingBox, "getCollisionCubes");
            }
            return collides;
        }

        FullChunk hint = entity != null && entity.getLevel() == level ? entity.chunk : null;
        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                FullChunk chunk = chunkAt(level, hint, x, z);
                for (int y = clampedMinY; y <= clampedMaxY; ++y) {
                    // Air is neither a barrier nor solid: it contributed no cube here.
                    if (isAirAt(chunk, x, y, z)) continue;

                    Block block = level.getBlock(chunk, x, y, z, 0, false);
                    if (block instanceof BlockBarrier && entity.canPassThroughBarrier()) {
                        continue;
                    }
                    if (!block.canPassThrough()) {
                        block.addCollisionBoxesToList(boundingBox, collides);
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
}
