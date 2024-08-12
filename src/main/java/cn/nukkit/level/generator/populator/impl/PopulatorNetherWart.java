package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

/**
 * Created by PetteriM1
 */
public class PopulatorNetherWart extends Populator {

    private int randomAmount;
    private int baseAmount;

    public void setRandomAmount(int randomAmount) {
        this.randomAmount = randomAmount;
    }

    public void setBaseAmount(int baseAmount) {
        this.baseAmount = baseAmount;
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        int amount = random.nextRange(0, this.randomAmount + 1) + this.baseAmount;
        for (int i = 0; i < amount; ++i) {
            int x = random.nextRange(0, 15);
            int z = random.nextRange(0, 15);
            int y = getHighestWorkableBlock(chunk, x, z);
            if (y != -1 && canNetherWartStay(chunk, x, y, z)) {
                chunk.setBlock(x, y, z, Block.NETHER_WART_BLOCK);
            }
        }
    }

    private static boolean canNetherWartStay(FullChunk chunk, int x, int y, int z) {
        return (chunk.getBlockId(x, y, z) == Block.AIR) && chunk.getBlockId(x, y - 1, z) == Block.SOUL_SAND;
    }

    private static int getHighestWorkableBlock(FullChunk chunk, int x, int z) {
        int y;

        for (y = 0; y < 127; ++y) {
            int b = chunk.getBlockId(x, y, z);
            if (b == Block.AIR) {
                break;
            }
        }
        return y == 0 ? -1 : y;
    }
}