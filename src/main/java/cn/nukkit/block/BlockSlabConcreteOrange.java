package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteOrange extends BlockSlabConcrete {

    public BlockSlabConcreteOrange() {
        this(0);
    }

    public BlockSlabConcreteOrange(int meta) {
        super(meta, ORANGE_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return ORANGE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.ORANGE;
    }
}
