package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteBlue extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteBlue() {
        this(0);
    }

    public BlockDoubleSlabConcreteBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLUE_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return BLUE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLUE;
    }
}
