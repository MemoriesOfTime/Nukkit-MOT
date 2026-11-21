package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolRed extends BlockSlabWool {

    public BlockSlabWoolRed() {
        this(0);
    }

    public BlockSlabWoolRed(int meta) {
        super(meta, RED_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return RED_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.RED;
    }
}
