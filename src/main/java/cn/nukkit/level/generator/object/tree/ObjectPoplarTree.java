package cn.nukkit.level.generator.object.tree;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockLeaves;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 白杨树生成器：细高主干 + 菱形层叠树冠，叶色由调用方指定。
 * <p>
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 */
public class ObjectPoplarTree extends TreeGenerator {

    private static final int[] LEAF_RADIUS_WEIGHTS = {5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 7, 8};
    private static final int[] DIRECTION_X = {0, 1, 0, -1};
    private static final int[] DIRECTION_Z = {-1, 0, 1, 0};
    /** 原木 pillar_axis 编码，与 {@link cn.nukkit.block.BlockWood#faces} 一致。 */
    private static final int LOG_Y = 0;
    private static final int LOG_X = 4;
    private static final int LOG_Z = 8;

    private final int leafId;

    public ObjectPoplarTree(int leafId) {
        if (leafId != BlockID.RED_POPLAR_LEAVES
                && leafId != BlockID.ORANGE_POPLAR_LEAVES
                && leafId != BlockID.YELLOW_POPLAR_LEAVES) {
            throw new IllegalArgumentException("Not a poplar leaf block: " + leafId);
        }
        this.leafId = leafId;
    }

    @Override
    public boolean generate(ChunkManager level, NukkitRandom random, Vector3 origin) {
        int x = origin.getFloorX();
        int y = origin.getFloorY();
        int z = origin.getFloorZ();
        int height = 7 + random.nextBoundedInt(5);
        int foliageHeight = 5 + random.nextBoundedInt(2);
        int radius = LEAF_RADIUS_WEIGHTS[random.nextBoundedInt(LEAF_RADIUS_WEIGHTS.length)] - 1;
        int foliageY = y + height - 4;

        Map<Position, Placement> blocks = new LinkedHashMap<>();
        for (int dy = 0; dy < height; dy++) {
            putLog(blocks, x, y + dy, z, LOG_Y);
        }

        int[] directions = {0, 1, 2, 3};
        for (int i = directions.length - 1; i > 0; i--) {
            int other = random.nextBoundedInt(i + 1);
            int tmp = directions[i];
            directions[i] = directions[other];
            directions[other] = tmp;
        }
        int branchCount = 1 + random.nextBoundedInt(4);
        for (int i = 0; i < branchCount; i++) {
            int direction = directions[i];
            putLog(blocks, x + DIRECTION_X[direction], y + height - 5,
                    z + DIRECTION_Z[direction], DIRECTION_X[direction] != 0 ? LOG_X : LOG_Z);
        }

        boolean flipRhombus = random.nextBoolean();
        addLeavesRow(blocks, random, x, foliageY, z, radius - 2, foliageHeight - 1, foliageHeight, flipRhombus);
        addLeavesRow(blocks, random, x, foliageY, z, radius - 1, foliageHeight - 2, foliageHeight, flipRhombus);
        addLeavesRow(blocks, random, x, foliageY, z, radius - 1, foliageHeight - 3, foliageHeight, flipRhombus);
        for (int dy = foliageHeight - 4; dy >= 1; dy--) {
            addLeavesRow(blocks, random, x, foliageY, z, radius, dy, foliageHeight, flipRhombus);
        }
        addFoliageLogs(blocks, x, foliageY + foliageHeight - 4, z, radius, foliageHeight, flipRhombus);
        addLeavesRow(blocks, random, x, foliageY, z, radius - 1, 0, foliageHeight, flipRhombus);
        addLeavesRow(blocks, random, x, foliageY, z, Math.max(1, Math.min(radius - 2, 2)),
                -1, foliageHeight, flipRhombus);

        if (y - 1 < level.getMinBlockY() || y + height + 1 > level.getMaxBlockY()) {
            return false;
        }
        for (Position pos : blocks.keySet()) {
            if (pos.y() < level.getMinBlockY() || pos.y() > level.getMaxBlockY()
                    || !canReplace(level, pos, x, y, z)) {
                return false;
            }
        }

        setDirtAt(level, new Vector3(x, y - 1, z));
        for (Map.Entry<Position, Placement> entry : blocks.entrySet()) {
            Position pos = entry.getKey();
            Placement block = entry.getValue();
            level.setBlockAt(pos.x(), pos.y(), pos.z(), block.id(), block.meta());
        }
        return true;
    }

    private boolean canReplace(ChunkManager level, Position pos, int originX, int originY, int originZ) {
        int id = level.getBlockIdAt(pos.x(), pos.y(), pos.z());
        if (id == BlockID.AIR) {
            return true;
        }
        if (id == BlockID.POPLAR_SAPLING) {
            return pos.x() == originX && pos.y() == originY && pos.z() == originZ;
        }
        Block block = Block.get(id, level.getBlockDataAt(pos.x(), pos.y(), pos.z()));
        return block instanceof BlockLeaves leaves && !leaves.isPersistent();
    }

    private static void putLog(Map<Position, Placement> blocks, int x, int y, int z, int axis) {
        blocks.put(new Position(x, y, z), new Placement(BlockID.POPLAR_LOG, axis));
    }

    private void addLeavesRow(Map<Position, Placement> blocks, NukkitRandom random, int x, int foliageY, int z,
                              int radius, int dy, int foliageHeight, boolean flipRhombus) {
        boolean partial = isPartialRow(foliageHeight, dy);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int absX = Math.abs(dx);
                int absZ = Math.abs(dz);
                if (partial && (absX == radius || absZ == radius)) {
                    continue;
                }
                int cornerCut = cornerCut(dx, dz, radius, partial, flipRhombus);
                int sideHole = random.nextFloat() <= 0.15f ? 1 : 0;
                if (withinRhombus(radius, absX, absZ, cornerCut, sideHole)) {
                    blocks.putIfAbsent(new Position(x + dx, foliageY + dy, z + dz),
                            new Placement(leafId, 0));
                }
            }
        }
    }

    private void addFoliageLogs(Map<Position, Placement> blocks, int x, int y, int z,
                                int radius, int foliageHeight, boolean flipRhombus) {
        int dy = foliageHeight - 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int absX = Math.abs(dx);
                int absZ = Math.abs(dz);
                if (!withinRhombus(radius, absX, absZ,
                        cornerCut(dx, dz, radius, isPartialRow(foliageHeight, dy), flipRhombus), 2)) {
                    continue;
                }
                int axis = dz == 0 && radius - absX >= 4 ? LOG_X
                        : dx == 0 && radius - absZ >= 4 ? LOG_Z : -1;
                Position pos = new Position(x + dx, y, z + dz);
                Placement current = blocks.get(pos);
                if (axis != -1 && current != null && current.id() == leafId) {
                    putLog(blocks, pos.x(), pos.y(), pos.z(), axis);
                }
            }
        }
    }

    private static boolean isPartialRow(int foliageHeight, int dy) {
        return dy == foliageHeight - 1 || dy == foliageHeight - 2;
    }

    private static int cornerCut(int dx, int dz, int radius, boolean partial, boolean flip) {
        boolean smallCorner = flip ? dx > 0 && dz > 0 || dx < 0 && dz < 0
                : dx > 0 && dz < 0 || dx < 0 && dz > 0;
        return smallCorner ? radius - 1 : partial ? radius + 1 : radius;
    }

    private static boolean withinRhombus(int radius, int absX, int absZ, int cornerCut, int sideRemoval) {
        return absX + absZ <= radius * 2 - cornerCut - sideRemoval;
    }

    private record Position(int x, int y, int z) {
    }

    private record Placement(int id, int meta) {
    }
}
