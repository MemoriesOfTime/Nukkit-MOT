package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockSlabCopperCutOxidizedWaxed extends BlockSlabCopperCut {
    public BlockSlabCopperCutOxidizedWaxed() {
        this(0);
    }

    public BlockSlabCopperCutOxidizedWaxed(int meta) {
        super(meta, WAXED_OXIDIZED_DOUBLE_CUT_COPPER_SLAB);
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_CUT_COPPER_SLAB;
    }

    @Override
    public int onUpdate(int type) {
        return 0;
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
        return WAXED_WEATHERED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDewaxedBlockId() {
        return OXIDIZED_CUT_COPPER_SLAB;
    }
}
