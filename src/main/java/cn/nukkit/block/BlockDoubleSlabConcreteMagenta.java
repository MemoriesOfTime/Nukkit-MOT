package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteMagenta extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteMagenta() {
        this(0);
    }

    public BlockDoubleSlabConcreteMagenta(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MAGENTA_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return MAGENTA_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.MAGENTA;
    }
}
