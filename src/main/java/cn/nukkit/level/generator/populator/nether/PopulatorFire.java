package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitRandom;
import cn.wode490390.nukkit.vanillagenerator.populator.PopulatorBlock;

public class PopulatorFire extends PopulatorBlock {

    @Override
    public void decorate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk source) {
        int amount = 1 + random.nextBoundedInt(1 + random.nextBoundedInt(10));
        for (int j = 0; j < amount; j++) {
            int sourceX = (chunkX << 4) + random.nextBoundedInt(16);
            int sourceZ = (chunkZ << 4) + random.nextBoundedInt(16);
            int sourceY = 4 + random.nextBoundedInt(120);

            for (int i = 0; i < 64; i++) {
                int x = sourceX + random.nextBoundedInt(8) - random.nextBoundedInt(8);
                int z = sourceZ + random.nextBoundedInt(8) - random.nextBoundedInt(8);
                int y = sourceY + random.nextBoundedInt(4) - random.nextBoundedInt(4);

                if (y < 128 && level.getBlockIdAt(x, y, z) == AIR && level.getBlockIdAt(x, y - 1, z) == NETHERRACK) {
                    level.setBlockAt(x, y, z, FIRE);
                }
            }
        }
    }
}
