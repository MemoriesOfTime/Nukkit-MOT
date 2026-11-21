package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteBlue extends BlockSlabConcrete {

    public BlockSlabConcreteBlue() {
        this(0);
    }

    public BlockSlabConcreteBlue(int meta) {
        super(meta, BLUE_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BLUE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLUE;
    }
}
