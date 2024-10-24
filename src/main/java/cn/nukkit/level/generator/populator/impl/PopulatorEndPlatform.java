package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

public class PopulatorEndPlatform extends Populator {

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        if(100 >> 4 != chunkX || 0 != chunkZ) {
            return;
        }

        this.place(level);
    }

    public void place(ChunkManager level) {
        int centerX = 100;
        int centerY = 0;

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int x = centerX + j;
                int y = centerY + i;
                level.setBlockAt(x, 48, y, OBSIDIAN);
                level.setBlockAt(x, 49, y, AIR);
                level.setBlockAt(x, 50, y, AIR);
            }
        }
    }

}
