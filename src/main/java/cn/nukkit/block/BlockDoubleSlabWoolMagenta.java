package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolMagenta extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolMagenta() {
        this(0);
    }

    public BlockDoubleSlabWoolMagenta(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MAGENTA_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return MAGENTA_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.MAGENTA;
    }
}
