package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteLime extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteLime() {
        this(0);
    }

    public BlockDoubleSlabConcreteLime(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIME_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return LIME_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIME;
    }
}
