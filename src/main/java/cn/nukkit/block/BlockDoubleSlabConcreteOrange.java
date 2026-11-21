package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteOrange extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteOrange() {
        this(0);
    }

    public BlockDoubleSlabConcreteOrange(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return ORANGE_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return ORANGE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.ORANGE;
    }
}
