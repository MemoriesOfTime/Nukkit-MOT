package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

public class PopulatorLava extends Populator {
    private ChunkManager level;
    private int randomAmount;
    private int baseAmount;
    private NukkitRandom random;

    public void setRandomAmount(final int amount) {
        randomAmount = amount;
    }

    public void setBaseAmount(final int amount) {
        baseAmount = amount;
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        this.random = random;
        if (random.nextRange(0, 100) < 5) {
            this.level = level;
            final int amount = random.nextRange(0, randomAmount + 1) + baseAmount;
            final int bx = chunkX << 4;
            final int bz = chunkZ << 4;
            for (int i = 0; i < amount; ++i) {
                final int x = random.nextRange(0, 15);
                final int z = random.nextRange(0, 15);
                final int y = getHighestWorkableBlock(chunk, x, z);
                if (y != -1 && chunk.getBlockId(x, y, z) == BlockID.AIR) {
                    chunk.setBlock(x, y, z, BlockID.LAVA);
                    chunk.setBlockLight(x, y, z, Block.light[BlockID.LAVA]);
                    lavaSpread(bx + x, y, bz + z);
                }
            }
        }
    }

    private int getFlowDecay(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        if (level.getBlockIdAt(x1, y1, z1) != level.getBlockIdAt(x2, y2, z2)) {
            return -1;
        }
        return level.getBlockDataAt(x2, y2, z2);
    }

    private void lavaSpread(final int x, final int y, final int z) {
        if (level.getChunk(x >> 4, z >> 4) == null) {
            return;
        }
        int decay = getFlowDecay(x, y, z, x, y, z);
        final int multiplier = 2;
        if (decay > 0) {
            int smallestFlowDecay = -100;
            smallestFlowDecay = getSmallestFlowDecay(x, y, z, x, y, z - 1, smallestFlowDecay);
            smallestFlowDecay = getSmallestFlowDecay(x, y, z, x, y, z + 1, smallestFlowDecay);
            smallestFlowDecay = getSmallestFlowDecay(x, y, z, x - 1, y, z, smallestFlowDecay);
            smallestFlowDecay = getSmallestFlowDecay(x, y, z, x + 1, y, z, smallestFlowDecay);
            int k = smallestFlowDecay + multiplier;
            if (k >= 8 || smallestFlowDecay < 0) {
                k = -1;
            }
            final int topFlowDecay = getFlowDecay(x, y, z, x, y + 1, z);
            if (topFlowDecay >= 0) {
                if (topFlowDecay >= 8) {
                    k = topFlowDecay;
                } else {
                    k = topFlowDecay | 0x08;
                }
            }
            if (decay < 8 && k < 8 && k > 1 && random.nextRange(0, 4) != 0) {
                k = decay;
            }
            if (k != decay) {
                decay = k;
                if (decay < 0) {
                    level.setBlockAt(x, y, z, 0);
                } else {
                    level.setBlockAt(x, y, z, BlockID.LAVA, decay);
                    lavaSpread(x, y, z);
                    return;
                }
            }
        }
        if (canFlowInto(x, y - 1, z)) {
            if (decay >= 8) {
                flowIntoBlock(x, y - 1, z, decay);
            } else {
                flowIntoBlock(x, y - 1, z, decay | 0x08);
            }
        } else if (decay >= 0 && (decay == 0 || !canFlowInto(x, y - 1, z))) {
            final boolean[] flags = getOptimalFlowDirections(x, y, z);
            int l = decay + multiplier;
            if (decay >= 8) {
                l = 1;
            }
            if (l >= 8) {
                return;
            }
            if (flags[0]) {
                flowIntoBlock(x - 1, y, z, l);
            }
            if (flags[1]) {
                flowIntoBlock(x + 1, y, z, l);
            }
            if (flags[2]) {
                flowIntoBlock(x, y, z - 1, l);
            }
            if (flags[3]) {
                flowIntoBlock(x, y, z + 1, l);
            }
        }
    }

    private void flowIntoBlock(final int x, final int y, final int z, final int newFlowDecay) {
        if (level.getBlockIdAt(x, y, z) == BlockID.AIR) {
            level.setBlockAt(x, y, z, BlockID.LAVA, newFlowDecay);
            lavaSpread(x, y, z);
        }
    }

    private boolean canFlowInto(final int x, final int y, final int z) {
        final int id = level.getBlockIdAt(x, y, z);
        return id == BlockID.AIR || id == BlockID.LAVA || id == BlockID.STILL_LAVA;
    }

    private int calculateFlowCost(final int xx, final int yy, final int zz, final int accumulatedCost, final int previousDirection) {
        int cost = 1000;
        for (int j = 0; j < 4; ++j) {
            if (j == 0 && previousDirection == 1 ||
                    j == 1 && previousDirection == 0 ||
                    j == 2 && previousDirection == 3 ||
                    j == 3 && previousDirection == 2
            ) {
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
                if (!canFlowInto(x, yy, z)) {
                    continue;
                }
                if (canFlowInto(x, yy, z) && level.getBlockDataAt(x, yy, z) == 0) {
                    continue;
                }
                if (canFlowInto(x, yy - 1, z)) {
                    return accumulatedCost;
                }
                if (accumulatedCost >= 4) {
                    continue;
                }
                final int realCost = calculateFlowCost(x, yy, z, accumulatedCost + 1, j);
                if (realCost < cost) {
                    cost = realCost;
                }
            }
        }
        return cost;
    }

    private boolean[] getOptimalFlowDirections(final int xx, final int yy, final int zz) {
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
            if (canFlowInto(x, yy - 1, z)) {
                flowCost[j] = 0;
            } else {
                flowCost[j] = calculateFlowCost(x, yy, z, 1, j);
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

    private int getSmallestFlowDecay(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final int decay) {
        int blockDecay = getFlowDecay(x1, y1, z1, x2, y2, z2);
        if (blockDecay < 0) {
            return decay;
        }
        if (blockDecay >= 8) {
            blockDecay = 0;
        }
        return decay >= 0 && blockDecay >= decay ? decay : blockDecay;
    }

    private int getHighestWorkableBlock(final FullChunk chunk, final int x, final int z) {
        int y;
        for (y = 127; y >= 0; y--) {
            final int b = chunk.getBlockId(x, y, z);
            if (b == BlockID.AIR) {
                break;
            }
        }
        return y == 0 ? -1 : y;
    }
}
