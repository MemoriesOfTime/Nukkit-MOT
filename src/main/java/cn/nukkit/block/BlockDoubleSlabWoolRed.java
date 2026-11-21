package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolRed extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolRed() {
        this(0);
    }

    public BlockDoubleSlabWoolRed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return RED_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return RED_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.RED;
    }
}
