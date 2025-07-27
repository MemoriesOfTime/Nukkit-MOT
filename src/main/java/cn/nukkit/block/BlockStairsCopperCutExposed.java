package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCopperCutExposed extends BlockStairsCopperCut {

    public BlockStairsCopperCutExposed() {
        this(0);
    }

    public BlockStairsCopperCutExposed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return EXPOSED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Exposed Cut Copper Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_TERRACOTA_BLOCK_COLOR;
    }

    @Override
    public int getCopperAge() {
        return 1;
    }

    @Override
    public int getWaxedBlockId() {
        return WAXED_EXPOSED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return WEATHERED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return CUT_COPPER_STAIRS;
    }
}