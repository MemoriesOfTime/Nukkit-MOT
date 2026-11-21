package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcretePink extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcretePink() {
        this(0);
    }

    public BlockDoubleSlabConcretePink(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PINK_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return PINK_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PINK;
    }
}
