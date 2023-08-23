package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;

public class PopulatorSoulsandFossils extends Populator {
    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        if (random.nextBoundedInt(5) == 0) {
            final int x = NukkitMath.randomRange(random, chunkX << 4, (chunkX << 4) + 15);
            final int z = NukkitMath.randomRange(random, chunkZ << 4, (chunkZ << 4) + 15);
            final int y = getHighestWorkableBlock(chunk, x & 0xF, z & 0xF);
            if (y != -1 && level.getBlockIdAt(x, y, z) != BlockID.NETHERRACK) {
                final int count = NukkitMath.randomRange(random, 10, 20);
                for (int i = 0; i < count; i++) {
                    level.setBlockAt(x + random.nextBoundedInt(6) - 3, y + random.nextBoundedInt(3), z + random.nextBoundedInt(6) - 3, BlockID.BONE_BLOCK);
                }
            }
        }
    }

    private int getHighestWorkableBlock(final FullChunk chunk, final int x, final int z) {
        int y;
        for (y = 120; y >= 0; y--) {
            final int b = chunk.getBlockId(x, y, z);
            if (b == BlockID.SOUL_SAND || b == BlockID.SOUL_SOIL) {
                break;
            }
        }
        return y == 0 ? -1 : y;
    }
}
