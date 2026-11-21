package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteGray extends BlockSlabConcrete {

    public BlockSlabConcreteGray() {
        this(0);
    }

    public BlockSlabConcreteGray(int meta) {
        super(meta, GRAY_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return GRAY_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GRAY;
    }
}
