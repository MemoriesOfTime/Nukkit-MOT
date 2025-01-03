package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

/**
 * @author DaPorkchop_
 *
 * Places bedrock on the bottom of the world
 */
public class PopulatorBedrock extends Populator {
    private final int bedrockLayer;

    public PopulatorBedrock(int bedrockLayer) {
        this.bedrockLayer = bedrockLayer;
    }

    public PopulatorBedrock() {
        this(0);
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        for (int x = 0; x < 16; x++)    {
            for (int z = 0; z < 16; z++)    {
                chunk.setBlockId(x, bedrockLayer, z, BEDROCK);
                for (int i = 1; i < 5; i++) {
                    if (random.nextBoundedInt(i) == 0)  {
                        chunk.setBlockId(x, bedrockLayer + i, z, BEDROCK);
                    }
                }
            }
        }
    }
}
