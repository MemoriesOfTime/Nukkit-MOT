package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteYellow extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteYellow() {
        this(0);
    }

    public BlockDoubleSlabConcreteYellow(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return YELLOW_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return YELLOW_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.YELLOW;
    }
}
