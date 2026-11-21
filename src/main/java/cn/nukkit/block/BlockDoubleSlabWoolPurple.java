package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolPurple extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolPurple() {
        this(0);
    }

    public BlockDoubleSlabWoolPurple(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PURPLE_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return PURPLE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PURPLE;
    }
}
