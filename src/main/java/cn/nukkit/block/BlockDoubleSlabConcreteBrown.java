package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteBrown extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteBrown() {
        this(0);
    }

    public BlockDoubleSlabConcreteBrown(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BROWN_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return BROWN_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BROWN;
    }
}
