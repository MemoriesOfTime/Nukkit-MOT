package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.helper.EnsureBelow;
import cn.nukkit.level.generator.populator.helper.EnsureCover;
import cn.nukkit.level.generator.populator.type.PopulatorOceanFloorSurfaceBlock;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.utils.Utils;

public class PopulatorKelp extends PopulatorOceanFloorSurfaceBlock {

    @Override
    protected boolean canStay(int x, int y, int z, FullChunk chunk) {
        return EnsureCover.ensureWaterCover(x, y, z, chunk) && EnsureBelow.ensureBelow(x, y, z, GRAVEL, chunk);
    }

    @Override
    protected int getBlockId(int x, int z, NukkitRandom random, FullChunk chunk) {
        return Block.BLOCK_KELP << Block.DATA_BITS;
    }

    @Override
    protected void placeBlock(int x, int y, int z, int id, FullChunk chunk, NukkitRandom random) {
        int height = Utils.rand(1, 25);
        int lastTop = y;

        for (int part = 0; part < height; part++) {
            int yy = y + part;
            if (yy < 256 && chunk.getBlockId(x, yy, z) == STILL_WATER && chunk.getBlockId(x, yy + 1, z) == STILL_WATER) {
                chunk.setBlock(x, yy, z, Block.BLOCK_KELP, part);
                chunk.setFullBlockId(x, yy, z, 1, Block.STILL_WATER << Block.DATA_BITS);
                lastTop = yy;
            } else {
                lastTop = yy - 1;
                break;
            }
        }

        // Stop top part growing
        chunk.setBlockData(x, lastTop, z, 24);
    }
}
