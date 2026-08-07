package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCopperCutOxidizedWaxed extends BlockStairsCopperCutWaxed {

    public BlockStairsCopperCutOxidizedWaxed() {
        this(0);
    }

    public BlockStairsCopperCutOxidizedWaxed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Cut Copper Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_NYLIUM_BLOCK_COLOR;
    }

    @Override
    public int getCopperAge() {
        return 3;
    }

    @Override
    public int getIncrementAgeBlockId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WAXED_WEATHERED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDewaxedBlockId() {
        return OXIDIZED_CUT_COPPER_STAIRS;
    }
}
