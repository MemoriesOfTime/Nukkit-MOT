package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolPink extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolPink() {
        this(0);
    }

    public BlockDoubleSlabWoolPink(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PINK_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return PINK_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PINK;
    }
}
