package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolLightBlue extends BlockSlabWool {

    public BlockSlabWoolLightBlue() {
        this(0);
    }

    public BlockSlabWoolLightBlue(int meta) {
        super(meta, LIGHT_BLUE_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return LIGHT_BLUE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_BLUE;
    }
}
