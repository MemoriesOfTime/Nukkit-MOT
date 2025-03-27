package cn.nukkit.level.generator.object.tree;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

import java.util.ArrayList;

public class ObjectBigSpruceTree extends ObjectSpruceTree {

    private final float leafStartHeightMultiplier;
    private final int baseLeafRadius;
    private final boolean forceBig;

    public ObjectBigSpruceTree(float leafStartHeightMultiplier, int baseLeafRadius) {
        this(leafStartHeightMultiplier, baseLeafRadius, false);
    }

    public ObjectBigSpruceTree(float leafStartHeightMultiplier, int baseLeafRadius, boolean forceBig) {
        this.leafStartHeightMultiplier = leafStartHeightMultiplier;
        this.baseLeafRadius = baseLeafRadius;
        this.forceBig = forceBig;
    }

    @Override
    public void placeObject(ChunkManager level, int x, int y, int z, NukkitRandom random) {
        this.treeHeight = random.nextBoundedInt(15) + 15;
        int trunkHeight = this.getTreeHeight();
        int topSize = this.treeHeight - (int) (this.treeHeight * leafStartHeightMultiplier);

        ArrayList<Vector3> trunkPositions = new ArrayList<>();

        if (this.forceBig) {

            trunkPositions.add(new Vector3(x, y, z));
            trunkPositions.add(new Vector3(x + 1, y, z));
            trunkPositions.add(new Vector3(x, y, z + 1));
            trunkPositions.add(new Vector3(x + 1, y, z + 1));
        } else {

            trunkPositions.add(new Vector3(x, y, z));
            for (int xx = -1; xx <= 1; xx++) {
                for (int zz = -1; zz <= 1; zz++) {
                    if (xx == 0 && zz == 0) continue;
                    int xxx = x + xx;
                    int zzz = z + zz;
                    if (level.getBlockIdAt(xxx, y, zzz) == Block.SAPLING) {
                        trunkPositions.add(new Vector3(xxx, y, zzz));
                    }
                }
            }
        }


        for (Vector3 pos : trunkPositions) {
            this.placeSingleTrunk(level, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), random, trunkHeight);
        }


        if (this.forceBig) {
            this.placeBigLeaves(level, topSize, baseLeafRadius, x, y, z, trunkPositions, random);
        } else {
            for (Vector3 pos : trunkPositions) {
                this.placeLeaves(level, topSize, baseLeafRadius, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), random);
            }
        }
    }

    protected void placeSingleTrunk(ChunkManager level, int x, int y, int z, NukkitRandom random, int trunkHeight) {
        level.setBlockAt(x, y - 1, z, Block.DIRT);

        for (int yy = 0; yy < trunkHeight; ++yy) {
            int blockId = level.getBlockIdAt(x, y + yy, z);
            if (this.overridable(blockId)) {
                level.setBlockAt(x, y + yy, z, this.getTrunkBlock(), this.getType());
            }
        }
    }

    protected void placeBigLeaves(ChunkManager level, int topSize, int lRadius, int x, int y, int z,
                                  ArrayList<Vector3> trunks, NukkitRandom random) {

        int centerX = x;
        int centerZ = z;
        if (trunks.size() > 1) {
            centerX = (int) trunks.stream().mapToInt(Vector3::getFloorX).average().orElse(x);
            centerZ = (int) trunks.stream().mapToInt(Vector3::getFloorZ).average().orElse(z);
        }


        int maxRadius = baseLeafRadius + 1;
        int radius = 2;
        int maxR = 2;
        int minR = 1;

        for (int yy = 0; yy <= topSize + 2; ++yy) {
            int yyy = y + this.treeHeight - yy;

            for (int xx = centerX - radius; xx <= centerX + radius; ++xx) {
                for (int zz = centerZ - radius; zz <= centerZ + radius; ++zz) {
                    int xOff = Math.abs(xx - centerX);
                    int zOff = Math.abs(zz - centerZ);


                    if (xOff == radius && zOff == radius && radius > 1) {
                        continue;
                    }


                    boolean isTrunk = false;
                    for (Vector3 trunk : trunks) {
                        if (xx == trunk.getFloorX() && zz == trunk.getFloorZ()) {
                            isTrunk = true;
                            break;
                        }
                    }

                    if (!isTrunk && !Block.solid[level.getBlockIdAt(xx, yyy, zz)]) {
                        level.setBlockAt(xx, yyy, zz, this.getLeafBlock(), this.getType());
                    }
                }
            }

            if (radius >= maxR) {
                radius = minR;
                minR = 1;
                if (++maxR > maxRadius) {
                    maxR = maxRadius;
                }
            } else {
                ++radius;
            }
        }
    }

    @Override
    public void placeLeaves(ChunkManager level, int topSize, int lRadius, int x, int y, int z, NukkitRandom random) {

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