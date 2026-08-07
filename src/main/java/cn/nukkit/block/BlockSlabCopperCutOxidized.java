package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockSlabCopperCutOxidized extends BlockSlabCopperCut {
    public BlockSlabCopperCutOxidized() {
        this(0);
    }

    public BlockSlabCopperCutOxidized(int meta) {
        super(meta, OXIDIZED_DOUBLE_CUT_COPPER_SLAB);
    }

    @Override
    public int getId() {
        return OXIDIZED_CUT_COPPER_SLAB;
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
    public int getWaxedBlockId() {
        return WAXED_OXIDIZED_CUT_COPPER_SLAB;
    }

    @Override
    public int getIncrementAgeBlockId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WEATHERED_CUT_COPPER_SLAB;
    }
}
