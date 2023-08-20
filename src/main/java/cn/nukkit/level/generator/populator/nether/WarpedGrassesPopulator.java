package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class WarpedGrassesPopulator extends Populator {
	private ChunkManager level;

	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		this.level = level;
		final int amount = random.nextBoundedInt(128) + 192;

		for (int i = 0; i < amount; ++i) {
			final int x = NukkitMath.randomRange(random, chunkX << 4, (chunkX << 4) + 15);
			final int z = NukkitMath.randomRange(random, chunkZ << 4, (chunkZ << 4) + 15);
			final IntArrayList ys = getHighestWorkableBlocks(x, z);
			for (final int y : ys) {
				if (y <= 1) continue;
				if (random.nextBoundedInt(4) == 0) continue;
				final int randomType = random.nextBoundedInt(6);
				final int blockID;

				if (randomType == 0) {
					blockID = BlockID.WARPED_FUNGUS;
				} else {
					blockID = BlockID.WARPED_ROOTS;
				}

				this.level.setBlockAt(x, y, z, blockID);
			}
		}
	}

	private IntArrayList getHighestWorkableBlocks(final int x, final int z) {
		int y;
		final IntArrayList blockYs = new IntArrayList();
		for (y = 128; y > 0; --y) {
			final int b = level.getBlockIdAt(x, y, z);
			if (b == Block.WARPED_NYLIUM && level.getBlockIdAt(x, y + 1, z) == 0) {
				blockYs.add(y + 1);
			}
		}
		return blockYs;
	}
}
