package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolLightBlue extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolLightBlue() {
        this(0);
    }

    public BlockDoubleSlabWoolLightBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIGHT_BLUE_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return LIGHT_BLUE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_BLUE;
    }
}
