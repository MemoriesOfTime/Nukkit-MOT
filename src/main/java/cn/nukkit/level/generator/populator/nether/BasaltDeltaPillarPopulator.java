package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class BasaltDeltaPillarPopulator extends Populator {
    private ChunkManager level;

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        this.level = level;
        final int amount = random.nextBoundedInt(128) + 128;

        for (int i = 0; i < amount; ++i) {
            final int x = NukkitMath.randomRange(random, chunkX << 4, (chunkX << 4) + 15);
            final int z = NukkitMath.randomRange(random, chunkZ << 4, (chunkZ << 4) + 15);
            final IntArrayList ys = getHighestWorkableBlocks(x, z);
            for (final int y : ys) {
                if (y <= 1) continue;
                if (random.nextBoundedInt(5) == 0) continue;
                for (int randomHeight = 0; randomHeight < random.nextBoundedInt(5) + 1; randomHeight++) {
                    final int placeLocation = y + randomHeight;
                    if (placeLocation > level.getMaxBlockY()) {
                        continue;
                    }
                    this.level.setBlockAt(x, placeLocation, z, BlockID.BASALT);
                }
            }
        }
    }

    private IntArrayList getHighestWorkableBlocks(final int x, final int z) {
        int y;
        final IntArrayList blockYs = new IntArrayList();
        for (y = 127; y > 0; --y) {
            final int b = level.getBlockIdAt(x, y, z);
            if ((b == Block.BASALT || b == Block.BLACKSTONE) && level.getBlockIdAt(x, y + 1, z) == Block.AIR) {
                blockYs.add(y + 1);
            }
        }
        return blockYs;
    }
}
