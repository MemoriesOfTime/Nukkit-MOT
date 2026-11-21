package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolBlue extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolBlue() {
        this(0);
    }

    public BlockDoubleSlabWoolBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLUE_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return BLUE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLUE;
    }
}
