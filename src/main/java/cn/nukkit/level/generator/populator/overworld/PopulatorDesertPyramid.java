package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.structure.ScatteredStructurePiece;
import cn.nukkit.math.NukkitRandom;

public class PopulatorDesertPyramid extends PopulatorScatteredStructure {
	@Override
	protected boolean canGenerate(final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		final int biome = chunk.getBiomeId(7, 7);
		return (biome == EnumBiome.DESERT.id || biome == EnumBiome.BEACH.id || biome == EnumBiome.DESERT_HILLS.id || biome == EnumBiome.DESERT_M.id) && super.canGenerate(chunkX, chunkZ, random, chunk);
	}

	@Override
	protected ScatteredStructurePiece getPiece(final int chunkX, final int chunkZ) {
		return new cn.nukkit.level.generator.structure.DesertPyramid(getStart(chunkX, chunkZ));
	}
}
