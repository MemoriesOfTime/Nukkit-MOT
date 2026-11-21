package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteRed extends BlockSlabConcrete {

    public BlockSlabConcreteRed() {
        this(0);
    }

    public BlockSlabConcreteRed(int meta) {
        super(meta, RED_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return RED_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.RED;
    }
}
