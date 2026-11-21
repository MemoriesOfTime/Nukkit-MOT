package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteMagenta extends BlockSlabConcrete {

    public BlockSlabConcreteMagenta() {
        this(0);
    }

    public BlockSlabConcreteMagenta(int meta) {
        super(meta, MAGENTA_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return MAGENTA_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.MAGENTA;
    }
}
