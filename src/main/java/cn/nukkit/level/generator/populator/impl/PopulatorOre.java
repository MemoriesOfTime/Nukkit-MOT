package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.object.ore.OreType;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;

public class PopulatorOre extends Populator {
	private final int replaceId;
	private OreType[] oreTypes = OreType.EMPTY_ARRAY;

	public PopulatorOre() {
		this(Block.STONE);
	}

	public PopulatorOre(final int id) {
		replaceId = id;
	}

	public PopulatorOre(final int replaceId, final OreType[] oreTypes) {
		this.replaceId = replaceId;
		this.oreTypes = oreTypes;
	}

	@Override
	public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		final int sx = chunkX << 4;
		final int ex = sx + 15;
		final int sz = chunkZ << 4;
		final int ez = sz + 15;
		for (final OreType type : oreTypes) {
			for (int i = 0; i < type.clusterCount; i++) {
				final int x = NukkitMath.randomRange(random, sx, ex);
				final int z = NukkitMath.randomRange(random, sz, ez);
				final int y = NukkitMath.randomRange(random, type.minHeight, type.maxHeight);
				if (level.getBlockIdAt(x, y, z) == replaceId) {
					type.spawn(level, random, replaceId, x, y, z);
				}
			}
		}
	}

	public void setOreTypes(final OreType[] oreTypes) {
		this.oreTypes = oreTypes;
	}
}
