package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteRed extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteRed() {
        this(0);
    }

    public BlockDoubleSlabConcreteRed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return RED_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return RED_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.RED;
    }
}
