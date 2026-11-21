package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteBrown extends BlockSlabConcrete {

    public BlockSlabConcreteBrown() {
        this(0);
    }

    public BlockSlabConcreteBrown(int meta) {
        super(meta, BROWN_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BROWN_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BROWN;
    }
}
