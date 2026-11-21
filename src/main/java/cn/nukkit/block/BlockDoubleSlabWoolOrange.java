package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolOrange extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolOrange() {
        this(0);
    }

    public BlockDoubleSlabWoolOrange(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return ORANGE_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return ORANGE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.ORANGE;
    }
}
