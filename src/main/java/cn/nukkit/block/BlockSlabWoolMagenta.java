package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolMagenta extends BlockSlabWool {

    public BlockSlabWoolMagenta() {
        this(0);
    }

    public BlockSlabWoolMagenta(int meta) {
        super(meta, MAGENTA_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return MAGENTA_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.MAGENTA;
    }
}
