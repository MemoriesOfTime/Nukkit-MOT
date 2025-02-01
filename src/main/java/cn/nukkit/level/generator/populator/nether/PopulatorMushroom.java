package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitRandom;
import cn.wode490390.nukkit.vanillagenerator.populator.PopulatorBlock;

public class PopulatorMushroom extends PopulatorBlock {

    private final int type;

    public PopulatorMushroom(int type) {
        if (type != BROWN_MUSHROOM && type != RED_MUSHROOM) {
            throw new IllegalArgumentException("PopulatorMushroom type must be BROWN_MUSHROOM or RED_MUSHROOM");
        }
        this.type = type;
    }

    @Override
    public void decorate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk source) {
        int sourceX = (chunkX << 4) + random.nextBoundedInt(16);
        int sourceZ = (chunkZ << 4) + random.nextBoundedInt(16);
        int sourceY = random.nextBoundedInt(128);

        for (int i = 0; i < 64; i++) {
            int x = sourceX + random.nextBoundedInt(8) - random.nextBoundedInt(8);
            int z = sourceZ + random.nextBoundedInt(8) - random.nextBoundedInt(8);
            int y = sourceY + random.nextBoundedInt(4) - random.nextBoundedInt(4);

            int below = level.getBlockIdAt(x, y - 1, z);
            if (y < 128 && level.getBlockIdAt(x, y, z) == AIR && (below == NETHERRACK || below == QUARTZ_ORE || below == SOUL_SAND || below == GRAVEL)) {
                level.setBlockAt(x, y, z, this.type);
            }
        }
    }
}
