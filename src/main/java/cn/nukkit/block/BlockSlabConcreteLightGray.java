package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteLightGray extends BlockSlabConcrete {

    public BlockSlabConcreteLightGray() {
        this(0);
    }

    public BlockSlabConcreteLightGray(int meta) {
        super(meta, LIGHT_GRAY_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return LIGHT_GRAY_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_GRAY;
    }
}
