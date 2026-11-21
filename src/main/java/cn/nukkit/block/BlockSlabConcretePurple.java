package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcretePurple extends BlockSlabConcrete {

    public BlockSlabConcretePurple() {
        this(0);
    }

    public BlockSlabConcretePurple(int meta) {
        super(meta, PURPLE_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return PURPLE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PURPLE;
    }
}
