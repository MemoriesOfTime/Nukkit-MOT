package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteWhite extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteWhite() {
        this(0);
    }

    public BlockDoubleSlabConcreteWhite(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WHITE_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return WHITE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.WHITE;
    }
}
