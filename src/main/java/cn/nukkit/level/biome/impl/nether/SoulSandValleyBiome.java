package cn.nukkit.level.biome.impl.nether;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.generator.object.ore.OreType;
import cn.nukkit.level.generator.populator.impl.PopulatorOre;
import cn.nukkit.level.generator.populator.nether.PopulatorSoulsandFossils;

public class SoulSandValleyBiome extends NetherBiome {
	public SoulSandValleyBiome() {
		addPopulator(new PopulatorSoulsandFossils());
		addPopulator(new PopulatorOre(BlockID.SOUL_SAND, new OreType[]{
			new OreType(Block.get(BlockID.SOUL_SOIL), 3, 128, 0, 128, BlockID.SOUL_SAND)
		}));
	}

	@Override
	public String getName() {
		return "Soulsand Valley";
	}

	@Override
	public int getCoverBlock() {
		return BlockID.SOUL_SAND;
	}

	@Override
	public int getMiddleBlock() {
		return BlockID.SOUL_SAND;
	}
}
