package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteBlack extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteBlack() {
        this(0);
    }

    public BlockDoubleSlabConcreteBlack(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLACK_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return BLACK_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLACK;
    }
}
