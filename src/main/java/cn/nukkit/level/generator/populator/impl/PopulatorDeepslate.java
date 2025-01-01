package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;


public class PopulatorDeepslate extends Populator {
    private final int bedrockLayer;

    public PopulatorDeepslate(int bedrockLayer) {
        this.bedrockLayer = bedrockLayer;
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for(int y = bedrockLayer; y < 0; y++) {
                    chunk.setBlockId(x, y, z, DEEPSLATE);
                }
            }
        }
    }
}
