package cn.nukkit.level.generator.object.tree;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;

public abstract class ObjectNetherTree extends ObjectTree {
    protected final int treeHeight;

    public ObjectNetherTree() {
        this(new NukkitRandom().nextBoundedInt(9) + 4);
    }

    public ObjectNetherTree(final int treeHeight) {
        this.treeHeight = treeHeight;
    }

    @Override
    public int getTreeHeight() {
        return treeHeight;
    }

    @Override
    public void placeObject(final ChunkManager level, final int x, final int y, final int z, final NukkitRandom random) {
        if (checkY(level, y)) {
            return;
        }

        placeTrunk(level, x, y, z, random, getTreeHeight());

        final double blankArea = -3;
        final int mid = (int) (1 - blankArea / 2);
        for (int yy = y - 3 + treeHeight; yy <= y + treeHeight - 1; ++yy) {
            if (checkY(level, yy)) {
                continue;
            }

            for (int xx = x - mid; xx <= x + mid; xx++) {
                final int xOff = Math.abs(xx - x);
                for (int zz = z - mid; zz <= z + mid; zz += mid * 2) {
                    final int zOff = Math.abs(zz - z);
                    if (xOff == mid && zOff == mid && random.nextBoundedInt(2) == 0) {
                        continue;
                    }
                    if (!Block.solid[level.getBlockIdAt(xx, yy, zz)]) {
                        if (random.nextBoundedInt(20) == 0) {
                            level.setBlockAt(xx, yy, zz, Block.SHROOMLIGHT);
                        } else {
                            level.setBlockAt(xx, yy, zz, getLeafBlock());
                        }
                    }
                }
            }

            for (int zz = z - mid; zz <= z + mid; zz++) {
                final int zOff = Math.abs(zz - z);
                for (int xx = x - mid; xx <= x + mid; xx += mid * 2) {
                    final int xOff = Math.abs(xx - x);
                    if (xOff == mid && zOff == mid && random.nextBoundedInt(2) == 0) {
                        continue;
                    }
                    if (!Block.solid[level.getBlockIdAt(xx, yy, zz)]) {
                        if (random.nextBoundedInt(20) == 0) {
                            level.setBlockAt(xx, yy, zz, Block.SHROOMLIGHT);
                        } else {
                            level.setBlockAt(xx, yy, zz, getLeafBlock());
                        }
                    }
                }
            }
        }

        for (int yy = y - 4 + treeHeight; yy <= y + treeHeight - 3; ++yy) {
            if (checkY(level, yy)) { // 防止长出下界顶部基岩层
                continue;
            }

            for (int xx = x - mid; xx <= x + mid; xx++) {
                for (int zz = z - mid; zz <= z + mid; zz += mid * 2) {
                    if (!Block.solid[level.getBlockIdAt(xx, yy, zz)]) {
                        if (random.nextBoundedInt(3) == 0) {
                            for (int i = 0; i < random.nextBoundedInt(5); i++) {
                                if (!Block.solid[level.getBlockIdAt(xx, yy - i, zz)]) {
                                    level.setBlockAt(xx, yy - i, zz, getLeafBlock());
                                }
                            }
                        }
                    }
                }
            }

            for (int zz = z - mid; zz <= z + mid; zz++) {
                for (int xx = x - mid; xx <= x + mid; xx += mid * 2) {
                    if (!Block.solid[level.getBlockIdAt(xx, yy, zz)]) {
                        if (random.nextBoundedInt(3) == 0) {
                            for (int i = 0; i < random.nextBoundedInt(4); i++) {
                                if (!Block.solid[level.getBlockIdAt(xx, yy - i, zz)]) {
                                    level.setBlockAt(xx, yy - i, zz, getLeafBlock());
                                }
                            }
                        }
                    }
                }
            }
        }

        for (int xCanopy = x - mid + 1; xCanopy <= x + mid - 1; xCanopy++) {
            for (int zCanopy = z - mid + 1; zCanopy <= z + mid - 1; zCanopy++) {
                int y1 = y + treeHeight;
                if (checkY(level, y1)) {
                    continue;
                }
                if (!Block.solid[level.getBlockIdAt(xCanopy, y1, zCanopy)]) {
                    level.setBlockAt(xCanopy, y1, zCanopy, getLeafBlock());
                }
            }
        }
    }

    private boolean checkY(final ChunkManager chunkManager, final int y) {
        return y > chunkManager.getMaxBlockY() - 1;
    }

    @Override
    protected void placeTrunk(final ChunkManager level, final int x, final int y, final int z, final NukkitRandom random, final int trunkHeight) {
        level.setBlockAt(x, y, z, getTrunkBlock());
        for (int yy = 0; yy < trunkHeight; ++yy) {
            if (checkY(level, y + yy)) { // 防止长出下界顶部基岩层
                continue;
            }
            final int blockId = level.getBlockIdAt(x, y + yy, z);
            if (overridable(blockId)) {
                level.setBlockAt(x, y + yy, z, getTrunkBlock());
            }
        }
    }
}