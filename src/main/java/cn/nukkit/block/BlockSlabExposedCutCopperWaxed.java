package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockSlabExposedCutCopperWaxed extends BlockSlabCutCopper {
    public BlockSlabExposedCutCopperWaxed() {
        this(0);
    }

    public BlockSlabExposedCutCopperWaxed(int meta) {
        super(meta, WAXED_EXPOSED_DOUBLE_CUT_COPPER_SLAB);
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_CUT_COPPER_SLAB;
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
        return WAXED_WEATHERED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WAXED_CUT_COPPER_SLAB;
    }

    @Override
    public int getDewaxedBlockId() {
        return EXPOSED_CUT_COPPER_SLAB;
    }
}
