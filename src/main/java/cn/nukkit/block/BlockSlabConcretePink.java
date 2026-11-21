package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcretePink extends BlockSlabConcrete {

    public BlockSlabConcretePink() {
        this(0);
    }

    public BlockSlabConcretePink(int meta) {
        super(meta, PINK_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return PINK_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PINK;
    }
}
