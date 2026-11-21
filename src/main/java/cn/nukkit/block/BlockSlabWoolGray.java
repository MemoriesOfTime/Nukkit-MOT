package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolGray extends BlockSlabWool {

    public BlockSlabWoolGray() {
        this(0);
    }

    public BlockSlabWoolGray(int meta) {
        super(meta, GRAY_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return GRAY_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GRAY;
    }
}
