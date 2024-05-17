package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class BasaltDeltaMagmaPopulator extends Populator {
    private ChunkManager level;

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        this.level = level;
        final int amount = random.nextBoundedInt(4) + 20;

        for (int i = 0; i < amount; ++i) {
            final int x = NukkitMath.randomRange(random, chunkX << 4, (chunkX << 4) + 15);
            final int z = NukkitMath.randomRange(random, chunkZ << 4, (chunkZ << 4) + 15);
            final IntArrayList ys = getHighestWorkableBlocks(x, z);
            for (final int y : ys) {
                if (y <= 1) continue;
                this.level.setBlockAt(x, y, z, BlockID.MAGMA);
            }
        }
    }

    private IntArrayList getHighestWorkableBlocks(final int x, final int z) {
        int y;
        final IntArrayList blockYs = new IntArrayList();
        for (y = 128; y > 0; --y) {
            final int b = level.getBlockIdAt(x, y, z);
            final int b1 = level.getBlockIdAt(x + 1, y, z);
            final int b2 = level.getBlockIdAt(x - 1, y, z);
            final int b3 = level.getBlockIdAt(x, y, z + 1);
            final int b4 = level.getBlockIdAt(x, y, z - 1);
            if ((b == Block.BASALT || b == Block.BLACKSTONE) && level.getBlockIdAt(x, y + 1, z) == 0 &&
                    (b1 == BlockID.STILL_LAVA ||
                            b2 == BlockID.STILL_LAVA ||
                            b3 == BlockID.STILL_LAVA ||
                            b4 == BlockID.STILL_LAVA ||
                            b1 == BlockID.LAVA ||
                            b2 == BlockID.LAVA ||
                            b3 == BlockID.LAVA ||
                            b4 == BlockID.LAVA
                    )
            ) {
                blockYs.add(y);
            }
        }
        return blockYs;
    }
}
