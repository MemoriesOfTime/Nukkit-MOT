package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteLightBlue extends BlockSlabConcrete {

    public BlockSlabConcreteLightBlue() {
        this(0);
    }

    public BlockSlabConcreteLightBlue(int meta) {
        super(meta, LIGHT_BLUE_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return LIGHT_BLUE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_BLUE;
    }
}
