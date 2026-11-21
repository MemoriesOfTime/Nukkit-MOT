package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolLightGray extends BlockSlabWool {

    public BlockSlabWoolLightGray() {
        this(0);
    }

    public BlockSlabWoolLightGray(int meta) {
        super(meta, LIGHT_GRAY_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return LIGHT_GRAY_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_GRAY;
    }
}
