package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.helper.EnsureBelow;
import cn.nukkit.level.generator.populator.helper.EnsureCover;
import cn.nukkit.level.generator.populator.helper.EnsureGrassBelow;
import cn.nukkit.level.generator.populator.type.PopulatorSurfaceBlock;
import cn.nukkit.math.NukkitRandom;

/**
 * @author Niall Lindsay (Niall7459)
 * <p>
 * Nukkit Project
 * </p>
 */
public class PopulatorSugarcane extends PopulatorSurfaceBlock {

    private static boolean findWater(int x, int y, int z, FullChunk chunk) {
        for (int i = x - 1; i < (x + 1); i++) {
            for (int j = z - 1; j < (z + 1); j++) {
                int b = chunk.getBlockId(i, y, j);
                if (b == Block.WATER || b == Block.STILL_WATER) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean canStay(int x, int y, int z, FullChunk chunk) {
        try {
            return EnsureCover.ensureCover(x, y, z, chunk)
                    && (EnsureGrassBelow.ensureGrassBelow(x, y, z, chunk) || EnsureBelow.ensureBelow(x, y, z, SAND, chunk))
                    && findWater(x, y - 1, z, chunk);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected int getBlockId(int x, int z, NukkitRandom random, FullChunk chunk) {
        return (SUGARCANE_BLOCK << Block.DATA_BITS) | 1;
    }
}
