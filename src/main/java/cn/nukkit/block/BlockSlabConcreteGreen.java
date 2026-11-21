package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteGreen extends BlockSlabConcrete {

    public BlockSlabConcreteGreen() {
        this(0);
    }

    public BlockSlabConcreteGreen(int meta) {
        super(meta, GREEN_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return GREEN_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GREEN;
    }
}
