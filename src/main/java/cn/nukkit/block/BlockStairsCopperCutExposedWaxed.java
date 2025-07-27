package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCopperCutExposedWaxed extends BlockStairsCopperCutWaxed {

    public BlockStairsCopperCutExposedWaxed() {
        this(0);
    }

    public BlockStairsCopperCutExposedWaxed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Waxed Exposed Cut Copper Stairs";
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
    public int getIncrementAgeBlockId() {
        return WAXED_WEATHERED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WAXED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getDewaxedBlockId() {
        return EXPOSED_CUT_COPPER_STAIRS;
    }
}
