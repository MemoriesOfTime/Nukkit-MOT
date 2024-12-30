package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;


public class PopulatorDeepslate extends Populator {

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < 8; y++) {
                    if (random.nextBoundedInt(y + 1) == 0) {
                        chunk.setBlockId(x, y, z, DEEPSLATE);
                    }
                }
            }
        }
    }
}
