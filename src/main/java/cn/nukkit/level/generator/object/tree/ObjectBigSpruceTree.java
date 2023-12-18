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
        this(leafStartHeightMultiplier, baseLeafRadius, false);
    }

    public ObjectBigSpruceTree(float leafStartHeightMultiplier, int baseLeafRadius, boolean fromSapling) {
        this.leafStartHeightMultiplier = leafStartHeightMultiplier;
        this.baseLeafRadius = baseLeafRadius;
        this.fromSapling = fromSapling;
    }

    @Override
    public void placeObject(ChunkManager level, int x, int y, int z, NukkitRandom random) {
        this.treeHeight = fromSapling ? Utils.rand(10, 15) : random.nextBoundedInt(15) + 20;

        int trunkHeight = this.getTreeHeight() - random.nextBoundedInt(3);
        int topSize = this.treeHeight - (int) (this.treeHeight * leafStartHeightMultiplier);
        int lRadius = baseLeafRadius + random.nextBoundedInt(2);

        ArrayList<Vector3> list = new ArrayList<>();
        list.add(new Vector3(x, y, z));

        for (int xx = -1; xx <= 1; xx++) {
            for (int zz = -1; zz <= 1; zz++) {
                if (xx == 0 && zz == 0) {
                    continue;
                }
                int xxx = x + xx;
                int zzz = z + zz;
                if (level.getBlockIdAt(xxx, y, zzz) == Block.SAPLING) {
                    list.add(new Vector3(xxx, y, zzz));
                }
            }
        }

        for (Vector3 pos : list) {
            this.placeTrunk(level, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), random, trunkHeight);
            this.placeLeaves(level, topSize, lRadius, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), random);
        }
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

    public void placeLeaves(ChunkManager level, int topSize, int lRadius, int x, int y, int z, NukkitRandom random)   {
        int radius = 1 + random.nextBoundedInt(1);
        int maxR = 1;
        int minR = 0;

        for (int yy = 0; yy <= topSize; ++yy) {
            int yyy = y + this.treeHeight - yy;

            for (int xx = x - radius; xx <= x + radius; ++xx) {
                int xOff = Math.abs(xx - x);
                for (int zz = z - radius; zz <= z + radius; ++zz) {
                    int zOff = Math.abs(zz - z);
                    if (xOff == radius && zOff == radius && radius > 0) {
                        continue;
                    }

                    if (!Block.solid[level.getBlockIdAt(xx, yyy, zz)]) {
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
