package cn.nukkit.level.generator.block;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.BlockVector3;

public final class LiquidUpdater {
    private LiquidUpdater() {

    }

    public static void lavaSpread(final ChunkManager level, final BlockVector3 vec) {
        lavaSpread(level, vec.x, vec.y, vec.z, 0);
    }

    private static void lavaSpread(final ChunkManager level, final int x, final int y, final int z, int counter) {
        if (counter > 200) {
            return;
        }

        if (level.getChunk(x >> 4, z >> 4) == null) {
            return;
        }

        int decay = getFlowDecay(level, x, y, z, x, y, z);
        final int multiplier = 2;

        if (decay > 0) {
            int smallestFlowDecay = -100;
            smallestFlowDecay = getSmallestFlowDecay(level, x, y, z, x, y, z - 1, smallestFlowDecay);
            smallestFlowDecay = getSmallestFlowDecay(level, x, y, z, x, y, z + 1, smallestFlowDecay);
            smallestFlowDecay = getSmallestFlowDecay(level, x, y, z, x - 1, y, z, smallestFlowDecay);
            smallestFlowDecay = getSmallestFlowDecay(level, x, y, z, x + 1, y, z, smallestFlowDecay);

            int k = smallestFlowDecay + multiplier;
            if (k >= 8 || smallestFlowDecay < 0) {
                k = -1;
            }

            final int topFlowDecay = getFlowDecay(level, x, y, z, x, y + 1, z);
            if (topFlowDecay >= 0) {
                if (topFlowDecay >= 8) {
                    k = topFlowDecay;
                } else {
                    k = topFlowDecay | 0x08;
                }
            }

            if (decay < 8 && k < 8 && k > 1) {
                k = decay;
            }

            if (k != decay) {
                decay = k;
                if (decay < 0) {
                    level.setBlockAt(x, y, z, 0);
                } else {
                    level.setBlockAt(x, y, z, BlockID.LAVA, decay);
                    lavaSpread(level, x, y, z, counter + 1);
                    return;
                }
            }
        }

        if (canFlowInto(level, x, y - 1, z)) {
            if (decay >= 8) {
                flowIntoBlock(level, x, y - 1, z, decay, counter + 1);
            } else {
                flowIntoBlock(level, x, y - 1, z, decay | 0x08, counter + 1);
            }
        } else if (decay >= 0 && (decay == 0 || !canFlowInto(level, x, y - 1, z))) {
            final boolean[] flags = getOptimalFlowDirections(level, x, y, z);
            int l = decay + multiplier;

            if (decay >= 8) {
                l = 1;
            }

            if (l >= 8) {
                return;
            }

            if (flags[0]) {
                flowIntoBlock(level, x - 1, y, z, l, ++counter);
            }
            if (flags[1]) {
                flowIntoBlock(level, x + 1, y, z, l, ++counter);
            }
            if (flags[2]) {
                flowIntoBlock(level, x, y, z - 1, l, ++counter);
            }
            if (flags[3]) {
                flowIntoBlock(level, x, y, z + 1, l, counter + 1);
            }
        }
    }

    private static int getFlowDecay(final ChunkManager level, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        if (level.getBlockIdAt(x1, y1, z1) == level.getBlockIdAt(x2, y2, z2)) {
            return level.getBlockDataAt(x2, y2, z2);
        }
        return -1;
    }

    private static void flowIntoBlock(final ChunkManager level, final int x, final int y, final int z, final int newFlowDecay, final int counter) {
        if (level.getBlockIdAt(x, y, z) == BlockID.AIR) {
            level.setBlockAt(x, y, z, BlockID.LAVA, newFlowDecay);
            lavaSpread(level, x, y, z, counter + 1);
        }
    }

    private static boolean canFlowInto(final ChunkManager level, final int x, final int y, final int z) {
        final int id = level.getBlockIdAt(x, y, z);
        return id == BlockID.AIR || id == BlockID.LAVA || id == BlockID.STILL_LAVA;
    }

    private static int calculateFlowCost(final ChunkManager level, final int xx, final int yy, final int zz, final int accumulatedCost, final int previousDirection) {
        int cost = 1000;

        for (int j = 0; j < 4; ++j) {
            if (j == 0 && previousDirection == 1 || j == 1 && previousDirection == 0 || j == 2 && previousDirection == 3 || j == 3 && previousDirection == 2) {
                int x = xx;
                int z = zz;

                if (j == 0) {
                    --x;
                } else if (j == 1) {
                    ++x;
                } else if (j == 2) {
                    --z;
                } else {
                    ++z;
                }

                if (!canFlowInto(level, x, yy, z)) {
                    continue;
                }
                if (canFlowInto(level, x, yy, z) && level.getBlockDataAt(x, yy, z) == 0) {
                    continue;
                }
                if (canFlowInto(level, x, yy - 1, z)) {
                    return accumulatedCost;
                }

                if (accumulatedCost >= 4) {
                    continue;
                }

                final int realCost = calculateFlowCost(level, x, yy, z, accumulatedCost + 1, j);
                if (realCost < cost) {
                    cost = realCost;
                }
            }
        }

        return cost;
    }

    private static boolean[] getOptimalFlowDirections(final ChunkManager level, final int xx, final int yy, final int zz) {
        final int[] flowCost = {0, 0, 0, 0};
        final boolean[] isOptimalFlowDirection = {false, false, false, false};

        for (int j = 0; j < 4; ++j) {
            flowCost[j] = 1000;
            int x = xx;
            int z = zz;

            if (j == 0) {
                --x;
            } else if (j == 1) {
                ++x;
            } else if (j == 2) {
                --z;
            } else {
                ++z;
            }

            if (canFlowInto(level, x, yy - 1, z)) {
                flowCost[j] = 0;
            } else {
                flowCost[j] = calculateFlowCost(level, x, yy, z, 1, j);
            }
        }

        int minCost = flowCost[0];
        for (int i = 1; i < 4; ++i) {
            if (flowCost[i] < minCost) {
                minCost = flowCost[i];
            }
        }

        for (int i = 0; i < 4; ++i) {
            isOptimalFlowDirection[i] = flowCost[i] == minCost;
        }

        return isOptimalFlowDirection;
    }

    private static int getSmallestFlowDecay(final ChunkManager level, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final int decay) {
        int blockDecay = getFlowDecay(level, x1, y1, z1, x2, y2, z2);
        if (blockDecay < 0) {
            return decay;
        }
        if (blockDecay >= 8) {
            blockDecay = 0;
        }
        return decay >= 0 && blockDecay >= decay ? decay : blockDecay;
    }
}
