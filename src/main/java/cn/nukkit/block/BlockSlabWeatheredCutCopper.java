package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockSlabWeatheredCutCopper extends BlockSlabCutCopper {
    public BlockSlabWeatheredCutCopper() {
        this(0);
    }

    public BlockSlabWeatheredCutCopper(int meta) {
        super(meta, WEATHERED_DOUBLE_CUT_COPPER_SLAB);
    }

    @Override
    public int getId() {
        return WEATHERED_CUT_COPPER_SLAB;
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
        return WAXED_WEATHERED_CUT_COPPER_SLAB;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return OXIDIZED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return EXPOSED_CUT_COPPER_SLAB;
    }
}
