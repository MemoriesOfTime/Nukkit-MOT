package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolCyan extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolCyan() {
        this(0);
    }

    public BlockDoubleSlabWoolCyan(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CYAN_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return CYAN_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.CYAN;
    }
}
