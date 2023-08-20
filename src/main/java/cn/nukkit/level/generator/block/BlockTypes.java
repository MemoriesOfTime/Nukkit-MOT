package cn.nukkit.level.generator.block;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;

public final class BlockTypes {
	public static final boolean[] isPlant = new boolean[512];
	public static final boolean[] isPlantOrFluid = isPlant.clone();

	static {
		isPlant[BlockID.AIR] = true; //gap
		isPlant[Block.LOG] = true;
		isPlant[Block.LEAVES] = true;
		isPlant[Block.TALL_GRASS] = true;
		isPlant[Block.DEAD_BUSH] = true;
		isPlant[Block.DANDELION] = true;
		isPlant[Block.RED_FLOWER] = true;
		isPlant[Block.BROWN_MUSHROOM] = true;
		isPlant[Block.RED_MUSHROOM] = true;
		isPlant[Block.SNOW_LAYER] = true; //falls on trees
		isPlant[Block.CACTUS] = true;
		isPlant[Block.SUGARCANE_BLOCK] = true;
		isPlant[Block.PUMPKIN] = true;
		isPlant[Block.BROWN_MUSHROOM_BLOCK] = true;
		isPlant[Block.RED_MUSHROOM_BLOCK] = true;
		isPlant[Block.MELON_BLOCK] = true;
		isPlant[Block.VINE] = true;
		isPlant[Block.WATER_LILY] = true;
		isPlant[Block.COCOA] = true;
		isPlant[Block.LEAVES2] = true;
		isPlant[Block.LOG2] = true;
		isPlant[Block.DOUBLE_PLANT] = true;
	}

	static {
		isPlantOrFluid[Block.WATER] = true;
		isPlantOrFluid[Block.STILL_WATER] = true;
		isPlantOrFluid[Block.LAVA] = true;
		isPlantOrFluid[Block.STILL_LAVA] = true;
		isPlantOrFluid[Block.ICE] = true; //solid water
		isPlantOrFluid[Block.PACKED_ICE] = true; //solid water
		isPlantOrFluid[Block.BLUE_ICE] = true; //solid water
	}

	private BlockTypes() {

	}

	public static boolean isLiquid(final int id) {
		return id == BlockID.WATER || id == BlockID.STILL_WATER || id == BlockID.LAVA || id == BlockID.STILL_LAVA;
	}
}
