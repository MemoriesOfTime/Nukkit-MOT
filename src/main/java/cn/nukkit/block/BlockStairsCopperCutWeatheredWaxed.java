package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCopperCutWeatheredWaxed extends BlockStairsCopperCutWaxed {
    public BlockStairsCopperCutWeatheredWaxed() {
        this(0);
    }

    public BlockStairsCopperCutWeatheredWaxed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Waxed Weathered Cut Copper Stairs";
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
    public int getIncrementAgeBlockId() {
        return WAXED_OXIDIZED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WAXED_EXPOSED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDewaxedBlockId() {
        return WEATHERED_CUT_COPPER_STAIRS;
    }
}