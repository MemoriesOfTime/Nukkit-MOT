package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolYellow extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolYellow() {
        this(0);
    }

    public BlockDoubleSlabWoolYellow(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return YELLOW_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return YELLOW_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.YELLOW;
    }
}
