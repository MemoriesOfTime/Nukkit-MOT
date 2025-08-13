package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockSlabCopperCutWeatheredWaxed extends BlockSlabCopperCut {
    public BlockSlabCopperCutWeatheredWaxed() {
        this(0);
    }

    public BlockSlabCopperCutWeatheredWaxed(int meta) {
        super(meta, WAXED_WEATHERED_DOUBLE_CUT_COPPER_SLAB);
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_CUT_COPPER_SLAB;
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
        return WAXED_OXIDIZED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WAXED_EXPOSED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDewaxedBlockId() {
        return WEATHERED_CUT_COPPER_SLAB;
    }
}
