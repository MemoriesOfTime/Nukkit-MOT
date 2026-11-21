package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolGreen extends BlockSlabWool {

    public BlockSlabWoolGreen() {
        this(0);
    }

    public BlockSlabWoolGreen(int meta) {
        super(meta, GREEN_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return GREEN_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GREEN;
    }
}
