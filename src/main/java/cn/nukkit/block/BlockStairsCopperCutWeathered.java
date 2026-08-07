package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCopperCutWeathered extends BlockStairsCopperCut {

    public BlockStairsCopperCutWeathered() {
        this(0);
    }

    public BlockStairsCopperCutWeathered(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WEATHERED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Weathered Cut Copper Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
    }

    @Override
    public int getCopperAge() {
        return 2;
    }

    @Override
    public int getWaxedBlockId() {
        return WAXED_WEATHERED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return OXIDIZED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return EXPOSED_CUT_COPPER_STAIRS;
    }
}