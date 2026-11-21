package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabWoolLime extends BlockDoubleSlabWool {

    public BlockDoubleSlabWoolLime() {
        this(0);
    }

    public BlockDoubleSlabWoolLime(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIME_WOOL_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return LIME_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIME;
    }
}
