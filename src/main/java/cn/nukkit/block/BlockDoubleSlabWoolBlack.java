package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolBlack extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolBlack() {
        this(0);
    }

    public BlockDoubleSlabWoolBlack(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLACK_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return BLACK_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLACK;
    }
}
