package cn.nukkit.level.biome.impl.nether;

import cn.nukkit.block.BlockID;

public class WastelandsBiome extends NetherBiome {
	@Override
	public String getName() {
		return "Wastelands";
	}

	@Override
	public int getCoverBlock() {
		return BlockID.NETHERRACK;
	}

	@Override
	public int getMiddleBlock() {
		return BlockID.NETHERRACK;
	}
}
