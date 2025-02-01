package cn.nukkit.level.generator.object.tree;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;

/**
 * @author DaPorkchop_
 * Nukkit Project
 */
public class ObjectBigSpruceTree extends ObjectSpruceTree {

    private final float leafStartHeightMultiplier;
    private final int baseLeafRadius;
    boolean fromSapling;

    public ObjectBigSpruceTree(float leafStartHeightMultiplier, int baseLeafRadius) {
        this.leafStartHeightMultiplier = leafStartHeightMultiplier;
        this.baseLeafRadius = baseLeafRadius;
    }



    /*@Override
    protected void placeTrunk(ChunkManager level, int x, int y, int z, NukkitRandom random, int trunkHeight) {
        // The base dirt block
        level.setBlockAt(x, y - 1, z, Block.DIRT);
        int radius = 2;

        for (int yy = 0; yy < trunkHeight; ++yy) {
            for (int xx = 0; xx < radius; xx++) {
                for (int zz = 0; zz < radius; zz++) {
                    int blockId = level.getBlockIdAt(x, y + yy, z);
                    if (this.overridable(blockId)) {
                        level.setBlockAt(x + xx, y + yy, z + zz, this.getTrunkBlock(), this.getType());
                    }
                }
            }
        }
    }*/

    public void placeObject(ChunkManager level, int x, int y, int z, NukkitRandom random) {
        this.treeHeight = random.nextBoundedInt(15) + 15;

        int trunkHeight = this.getTreeHeight() - random.nextBoundedInt(3);
        int topSize = this.treeHeight - (int) (this.treeHeight * leafStartHeightMultiplier);

        // Устанавливаем ствол 2x2
        for (int xx = 0; xx < 2; xx++) {
            for (int zz = 0; zz < 2; zz++) {
                this.placeTrunk(level, x + xx, y, z + zz, random, trunkHeight);
            }
        }

        // Устанавливаем листву, ориентируясь на границы ствола
        this.placeLeaves(level, topSize, baseLeafRadius, x, y, z, random);
    }

    @Override
    public void placeLeaves(ChunkManager level, int topSize, int lRadius, int x, int y, int z, NukkitRandom random) {
        int radius = random.nextBoundedInt(2);
        int maxR = 1;
        int minR = 0;

        for (int yy = 0; yy <= topSize; ++yy) {
            int yyy = y + this.treeHeight - yy;

            for (int xx = x - radius - 1; xx <= x + radius + 2; ++xx) {
                for (int zz = z - radius - 1; zz <= z + radius + 2; ++zz) {
                    if (topSize - yy > 1 && radius > 0) {
                        if (Math.abs(xx - (x + 1)) > radius && Math.abs(zz - (z + 1)) > radius) {
                            continue;
                        }
                    }

                    if (!Block.isBlockSolidById(level.getBlockIdAt(xx, yyy, zz))) {
                        level.setBlockAt(xx, yyy, zz, this.getLeafBlock(), this.getType());
                    }
                }
            }

            if (radius >= maxR) {
                radius = minR;
                minR = 1;
                if (++maxR > lRadius) {
                    maxR = lRadius;
                }
            } else {
                ++radius;
            }
        }
    }
}
