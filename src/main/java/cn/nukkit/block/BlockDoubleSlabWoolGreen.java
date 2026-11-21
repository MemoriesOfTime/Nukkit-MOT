package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolGreen extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolGreen() {
        this(0);
    }

    public BlockDoubleSlabWoolGreen(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GREEN_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return GREEN_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GREEN;
    }
}
