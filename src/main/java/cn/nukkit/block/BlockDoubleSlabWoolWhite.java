package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolWhite extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolWhite() {
        this(0);
    }

    public BlockDoubleSlabWoolWhite(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WHITE_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return WHITE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.WHITE;
    }
}
