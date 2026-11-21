package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcretePurple extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcretePurple() {
        this(0);
    }

    public BlockDoubleSlabConcretePurple(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PURPLE_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return PURPLE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PURPLE;
    }
}
