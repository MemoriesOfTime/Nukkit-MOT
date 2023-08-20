package cn.nukkit.level.biome.impl.nether;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.generator.populator.nether.CrimsonFungiTreePopulator;
import cn.nukkit.level.generator.populator.nether.CrimsonGrassesPopulator;

public class CrimsonForestBiome extends NetherBiome {
	public CrimsonForestBiome() {
		addPopulator(new CrimsonFungiTreePopulator());
		addPopulator(new CrimsonGrassesPopulator());
	}

	@Override
	public String getName() {
		return "Crimson Forest";
	}

	@Override
	public int getCoverBlock() {
		return BlockID.CRIMSON_NYLIUM;
	}

	@Override
	public int getMiddleBlock() {
		return BlockID.NETHERRACK;
	}
}
