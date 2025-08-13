package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockSlabCutCopperWaxed extends BlockSlabCutCopper {
    public BlockSlabCutCopperWaxed() {
        this(0);
    }

    public BlockSlabCutCopperWaxed(int meta) {
        super(meta, WAXED_DOUBLE_CUT_COPPER_SLAB);
    }

    @Override
    public int getId() {
        return WAXED_CUT_COPPER_SLAB;
    }

    @Override
    public int getCopperAge() {
        return 2;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return WAXED_EXPOSED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDecrementAgeBlockId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDewaxedBlockId() {
        return CUT_COPPER_SLAB;
    }
}
