package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteGreen extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteGreen() {
        this(0);
    }

    public BlockDoubleSlabConcreteGreen(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GREEN_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return GREEN_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GREEN;
    }
}
