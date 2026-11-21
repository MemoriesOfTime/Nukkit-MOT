package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolGray extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolGray() {
        this(0);
    }

    public BlockDoubleSlabWoolGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GRAY_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return GRAY_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GRAY;
    }
}
