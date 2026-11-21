package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteGray extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteGray() {
        this(0);
    }

    public BlockDoubleSlabConcreteGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GRAY_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return GRAY_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GRAY;
    }
}
