package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteLime extends BlockSlabConcrete {

    public BlockSlabConcreteLime() {
        this(0);
    }

    public BlockSlabConcreteLime(int meta) {
        super(meta, LIME_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return LIME_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIME;
    }
}
