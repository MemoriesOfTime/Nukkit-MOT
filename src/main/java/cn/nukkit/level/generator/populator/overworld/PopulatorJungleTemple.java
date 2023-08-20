package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.structure.JungleTemple;
import cn.nukkit.level.generator.structure.ScatteredStructurePiece;
import cn.nukkit.math.NukkitRandom;

public class PopulatorJungleTemple extends PopulatorScatteredStructure {
	@Override
	protected boolean canGenerate(final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
		final int biome = chunk.getBiomeId(7, 7);
		return (biome == EnumBiome.JUNGLE.id || biome == EnumBiome.JUNGLE_EDGE.id || biome == EnumBiome.JUNGLE_EDGE_M.id || biome == EnumBiome.JUNGLE_HILLS.id || biome == EnumBiome.JUNGLE_M.id) && super.canGenerate(chunkX, chunkZ, random, chunk);
	}

	@Override
	protected ScatteredStructurePiece getPiece(final int chunkX, final int chunkZ) {
		return new JungleTemple(getStart(chunkX, chunkZ));
	}
}
