package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitRandom;
import cn.wode490390.nukkit.vanillagenerator.populator.PopulatorBlock;

public class PopulatorLava extends PopulatorBlock {

    private ChunkManager level;
    private NukkitRandom random;

    private final boolean flowing;
    
    public PopulatorLava() {
        this(false);
    }

    public PopulatorLava(boolean flowing) {
        this.flowing = flowing;
    }

    @Override
    public void decorate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk source) {
        this.level = level;
        this.random = random;

        int bx = chunkX << 4;
        int bz = chunkZ << 4;

        int sourceX = bx + random.nextBoundedInt(16);
        int sourceZ = bz + random.nextBoundedInt(16);
        int sourceY = this.flowing ? 4 + random.nextBoundedInt(120) : 10 + random.nextBoundedInt(108);

        int block = level.getBlockIdAt(sourceX, sourceY, sourceZ);
        if (block != NETHERRACK && block != 0 || level.getBlockIdAt(sourceX, sourceY + 1, sourceZ) != NETHERRACK) {
            return;
        }
        int netherrackBlockCount = 0;
        int airBlockCount = 0;

        int neighbor = level.getBlockIdAt(sourceX + 1, sourceY, sourceZ);
        if (neighbor == NETHERRACK) {
            netherrackBlockCount++;
        } else if (neighbor == AIR) {
            airBlockCount++;
        }
        neighbor = level.getBlockIdAt(sourceX, sourceY, sourceZ + 1);
        if (neighbor == NETHERRACK) {
            netherrackBlockCount++;
        } else if (neighbor == AIR) {
            airBlockCount++;
        }
        neighbor = level.getBlockIdAt(sourceX - 1, sourceY, sourceZ);
        if (neighbor == NETHERRACK) {
            netherrackBlockCount++;
        } else if (neighbor == AIR) {
            airBlockCount++;
        }
        neighbor = level.getBlockIdAt(sourceX, sourceY - 1, sourceZ);
        if (neighbor == NETHERRACK) {
            netherrackBlockCount++;
        } else if (neighbor == AIR) {
            airBlockCount++;
        }
        neighbor = level.getBlockIdAt(sourceX, sourceY, sourceZ - 1);
        if (neighbor == NETHERRACK) {
            netherrackBlockCount++;
        } else if (neighbor == AIR) {
            airBlockCount++;
        }

        if (netherrackBlockCount == 5 || this.flowing && airBlockCount == 1 && netherrackBlockCount == 4) {
            level.setBlockAt(sourceX, sourceY, sourceZ, LAVA);
            this.lavaSpread(sourceX, sourceY, sourceZ);
        }
    }

    private int getFlowDecay(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (this.level.getBlockIdAt(x1, y1, z1) != this.level.getBlockIdAt(x2, y2, z2)) {
            return -1;
        } else {
            return this.level.getBlockDataAt(x2, y2, z2);
        }
    }

    private void lavaSpread(int x, int y, int z) {
        if (this.level.getChunk(x >> 4, z >> 4) == null) {
            return;
        }
        int decay = this.getFlowDecay(x, y, z, x, y, z);
        int multiplier = 2;
        if (decay > 0) {
            int smallestFlowDecay = -100;
            smallestFlowDecay = this.getSmallestFlowDecay(x, y, z, x, y, z - 1, smallestFlowDecay);
            smallestFlowDecay = this.getSmallestFlowDecay(x, y, z, x, y, z + 1, smallestFlowDecay);
            smallestFlowDecay = this.getSmallestFlowDecay(x, y, z, x - 1, y, z, smallestFlowDecay);
            smallestFlowDecay = this.getSmallestFlowDecay(x, y, z, x + 1, y, z, smallestFlowDecay);
            int k = smallestFlowDecay + multiplier;
            if (k >= 8 || smallestFlowDecay < 0) {
                k = -1;
            }
            int topFlowDecay = this.getFlowDecay(x, y, z, x, y + 1, z);
            if (topFlowDecay >= 0) {
                if (topFlowDecay >= 8) {
                    k = topFlowDecay;
                } else {
                    k = topFlowDecay | 0x8;
                }
            }
            if (decay < 8 && k < 8 && k > 1 && this.random.nextRange(0, 4) != 0) {
                k = decay;
            }
            if (k != decay) {
                decay = k;
                if (decay < 0) {
                    this.level.setBlockAt(x, y, z, 0);
                } else {
                    this.level.setBlockAt(x, y, z, Block.LAVA, decay);
                    this.lavaSpread(x, y, z);
                    return;
                }
            }
        }
        if (this.canFlowInto(x, y - 1, z)) {
            if (decay >= 8) {
                this.flowIntoBlock(x, y - 1, z, decay);
            } else {
                this.flowIntoBlock(x, y - 1, z, decay | 0x8);
            }
        } else if (decay >= 0 && (decay == 0 || !this.canFlowInto(x, y - 1, z))) {
            boolean[] flags = this.getOptimalFlowDirections(x, y, z);
            int l = decay + multiplier;
            if (decay >= 8) {
                l = 1;
            }
            if (l >= 8) {
                return;
            }
            if (flags[0]) {
                this.flowIntoBlock(x - 1, y, z, l);
            }
            if (flags[1]) {
                this.flowIntoBlock(x + 1, y, z, l);
            }
            if (flags[2]) {
                this.flowIntoBlock(x, y, z - 1, l);
            }
            if (flags[3]) {
                this.flowIntoBlock(x, y, z + 1, l);
            }
        }
    }

    private void flowIntoBlock(int x, int y, int z, int newFlowDecay) {
        if (this.level.getBlockIdAt(x, y, z) == Block.AIR) {
            this.level.setBlockAt(x, y, z, Block.LAVA, newFlowDecay);
            this.lavaSpread(x, y, z);
        }
    }

    private boolean canFlowInto(int x, int y, int z) {
        int id = this.level.getBlockIdAt(x, y, z);
        return id == Block.AIR || id == Block.LAVA || id == Block.STILL_LAVA;
    }

    private int calculateFlowCost(int xx, int yy, int zz, int accumulatedCost, int previousDirection) {
        int cost = 1000;
        for (int j = 0; j < 4; ++j) {
            if (j == 0 && previousDirection == 1 || j == 1 && previousDirection == 0 || j == 2 && previousDirection == 3 || j == 3 && previousDirection == 2) {
                int x = xx;
                int y = yy;
                int z = zz;
                switch (j) {
                    case 0:
                        --x;
                        break;
                    case 1:
                        ++x;
                        break;
                    case 2:
                        --z;
                        break;
                    case 3:
                        ++z;
                        break;
                }
                if (!this.canFlowInto(x, y, z) || this.canFlowInto(x, y, z) && this.level.getBlockDataAt(x, y, z) == 0) {
                    continue;
                } else if (this.canFlowInto(x, y - 1, z)) {
                    return accumulatedCost;
                }
                if (accumulatedCost >= 4) {
                    continue;
                }
                int realCost = this.calculateFlowCost(x, y, z, accumulatedCost + 1, j);
                if (realCost < cost) {
                    cost = realCost;
                }
            }
        }
        return cost;
    }

    private boolean[] getOptimalFlowDirections(int xx, int yy, int zz) {
        int[] flowCost = {0, 0, 0, 0};
        boolean[] isOptimalFlowDirection = {false, false, false, false};
        for (int j = 0; j < 4; ++j) {
            flowCost[j] = 1000;
            int x = xx;
            int y = yy;
            int z = zz;
            switch (j) {
                case 0:
                    --x;
                    break;
                case 1:
                    ++x;
                    break;
                case 2:
                    --z;
                    break;
                case 3:
                    ++z;
                    break;
            }
            if (!this.canFlowInto(x, y, z) || this.canFlowInto(x, y, z) && this.level.getBlockDataAt(x, y, z) == 0) {

            } else if (this.canFlowInto(x, y - 1, z)) {
                flowCost[j] = 0;
            } else {
                flowCost[j] = this.calculateFlowCost(x, y, z, 1, j);
            }
        }
        int minCost = flowCost[0];
        for (int i = 1; i < 4; ++i) {
            if (flowCost[i] < minCost) {
                minCost = flowCost[i];
            }
        }
        for (int i = 0; i < 4; ++i) {
            isOptimalFlowDirection[i] = (flowCost[i] == minCost);
        }
        return isOptimalFlowDirection;
    }

    private int getSmallestFlowDecay(int x1, int y1, int z1, int x2, int y2, int z2, int decay) {
        int blockDecay = this.getFlowDecay(x1, y1, z1, x2, y2, z2);
        if (blockDecay < 0) {
            return decay;
        } else if (blockDecay >= 8) {
            blockDecay = 0;
        }
        return (decay >= 0 && blockDecay >= decay) ? decay : blockDecay;
    }
}
