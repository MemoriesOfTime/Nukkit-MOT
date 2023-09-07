package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.helper.EnsureAround;
import cn.nukkit.level.generator.populator.helper.EnsureBelow;
import cn.nukkit.level.generator.populator.helper.EnsureCover;
import cn.nukkit.level.generator.populator.type.PopulatorSurfaceBlock;
import cn.nukkit.math.NukkitRandom;

/**
 * @author DaPorkchop_
 */
public class PopulatorCactus extends PopulatorSurfaceBlock {

    @Override
    protected void populateCount(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        //不生成在边界，防止和其他区块上的方块相邻
        int x = random.nextBoundedInt(14) + 1;
        int z = random.nextBoundedInt(14) + 1;
        int y = getHighestWorkableBlock(level, x, z, chunk);
        int height = 1;
        int range = random.nextBoundedInt(18);
        if (range >= 16) {
            height = 3;
        }else if (range >= 11) {
            height = 2;
        }
        if (y > 0) {
            for (int i = 0; i < height; i++) {
                if (!canStay(x, y, z, chunk)) {
                    break;
                }
                placeBlock(x, y, z, getBlockId(x, z, random, chunk), chunk, random);
                y++;
            }
        }
    }

    @Override
    protected boolean canStay(int x, int y, int z, FullChunk chunk) {
        return EnsureCover.ensureCover(x, y, z, chunk) &&
                (EnsureBelow.ensureBelow(x, y, z, SAND, chunk) || EnsureBelow.ensureBelow(x, y, z, CACTUS, chunk)) &&
                EnsureAround.ensureAroundAir(x, y, z, chunk);
    }

    @Override
    protected int getBlockId(int x, int z, NukkitRandom random, FullChunk chunk) {
        return (CACTUS << Block.DATA_BITS) | 1;
    }
}
