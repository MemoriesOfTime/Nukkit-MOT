package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolBrown extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolBrown() {
        this(0);
    }

    public BlockDoubleSlabWoolBrown(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BROWN_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return BROWN_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BROWN;
    }
}
