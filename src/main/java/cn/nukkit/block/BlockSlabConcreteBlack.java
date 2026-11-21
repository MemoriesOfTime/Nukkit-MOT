package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteBlack extends BlockSlabConcrete {

    public BlockSlabConcreteBlack() {
        this(0);
    }

    public BlockSlabConcreteBlack(int meta) {
        super(meta, BLACK_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BLACK_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLACK;
    }
}
