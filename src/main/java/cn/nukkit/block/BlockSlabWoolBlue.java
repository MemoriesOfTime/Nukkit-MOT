package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolBlue extends BlockSlabWool {

    public BlockSlabWoolBlue() {
        this(0);
    }

    public BlockSlabWoolBlue(int meta) {
        super(meta, BLUE_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BLUE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLUE;
    }
}
