package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolLightGray extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolLightGray() {
        this(0);
    }

    public BlockDoubleSlabWoolLightGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIGHT_GRAY_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return LIGHT_GRAY_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_GRAY;
    }
}
