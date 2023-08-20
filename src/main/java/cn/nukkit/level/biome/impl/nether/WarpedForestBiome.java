package cn.nukkit.level.biome.impl.nether;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.generator.populator.nether.WarpedFungiTreePopulator;
import cn.nukkit.level.generator.populator.nether.WarpedGrassesPopulator;

public class WarpedForestBiome extends NetherBiome {
	public WarpedForestBiome() {
		addPopulator(new WarpedFungiTreePopulator());
		addPopulator(new WarpedGrassesPopulator());
	}

	@Override
	public String getName() {
		return "Warped Forest";
	}

	@Override
	public int getCoverBlock() {
		return BlockID.WARPED_NYLIUM;
	}

	@Override
	public int getMiddleBlock() {
		return BlockID.NETHERRACK;
	}
}
