package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockSlabStone;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

public class PopulatorDesertWell extends Populator {
	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		final int biome = chunk.getBiomeId(7, 7);
		if ((biome == EnumBiome.DESERT.id || biome == EnumBiome.DESERT_HILLS.id || biome == EnumBiome.DESERT_M.id) && random.nextBoundedInt(500) == 0) {
			final int x = (chunkX << 4) + random.nextBoundedInt(16);
			final int z = (chunkZ << 4) + random.nextBoundedInt(16);
			final int y = getHighestWorkableBlock(level, x, z, chunk);

			if (y > 128) {
				return;
			}

			if (level.getBlockIdAt(x, y, z) != BlockID.SAND) {
				return;
			}

			for (int dx = -2; dx <= 2; ++dx) {
				for (int dz = -2; dz <= 2; ++dz) {
					if (level.getBlockIdAt(x + dx, y - 1, z + dz) == BlockID.AIR && level.getBlockIdAt(x + dx, y - 2, z + dz) == BlockID.AIR) {
						return;
					}
				}
			}

			for (int dy = -1; dy <= 0; ++dy) {
				for (int dx = -2; dx <= 2; ++dx) {
					for (int dz = -2; dz <= 2; ++dz) {
						level.setBlockAt(x + dx, y + dy, z + dz, BlockID.SANDSTONE);
					}
				}
			}
			level.setBlockAt(x, y, z, BlockID.WATER);
			level.setBlockAt(x - 1, y, z, BlockID.WATER);
			level.setBlockAt(x + 1, y, z, BlockID.WATER);
			level.setBlockAt(x, y, z - 1, BlockID.WATER);
			level.setBlockAt(x, y, z + 1, BlockID.WATER);
			for (int dx = -2; dx <= 2; ++dx) {
				for (int dz = -2; dz <= 2; ++dz) {
					if (dx == -2 || dx == 2 || dz == -2 || dz == 2) {
						level.setBlockAt(x + dx, y + 1, z + dz, BlockID.SANDSTONE);
					}
				}
			}
			level.setBlockAt(x + 2, y + 1, z, BlockID.STONE_SLAB, BlockSlabStone.SANDSTONE);
			level.setBlockAt(x - 2, y + 1, z, BlockID.STONE_SLAB, BlockSlabStone.SANDSTONE);
			level.setBlockAt(x, y + 1, z + 2, BlockID.STONE_SLAB, BlockSlabStone.SANDSTONE);
			level.setBlockAt(x, y + 1, z - 2, BlockID.STONE_SLAB, BlockSlabStone.SANDSTONE);
			for (int dx = -1; dx <= 1; ++dx) {
				for (int dz = -1; dz <= 1; ++dz) {
					if (dx == 0 && dz == 0) {
						level.setBlockAt(x + dx, y + 4, z + dz, BlockID.SANDSTONE);
					} else {
						level.setBlockAt(x + dx, y + 4, z + dz, BlockID.STONE_SLAB, BlockSlabStone.SANDSTONE);
					}
				}
			}
			for (int dy = 1; dy <= 3; ++dy) {
				level.setBlockAt(x - 1, y + dy, z - 1, BlockID.SANDSTONE);
				level.setBlockAt(x - 1, y + dy, z + 1, BlockID.SANDSTONE);
				level.setBlockAt(x + 1, y + dy, z - 1, BlockID.SANDSTONE);
				level.setBlockAt(x + 1, y + dy, z + 1, BlockID.SANDSTONE);
			}
		}
	}
}
