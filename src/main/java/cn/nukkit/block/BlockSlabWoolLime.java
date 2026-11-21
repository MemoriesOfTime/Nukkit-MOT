package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolLime extends BlockSlabWool {

    public BlockSlabWoolLime() {
        this(0);
    }

    public BlockSlabWoolLime(int meta) {
        super(meta, LIME_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return LIME_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIME;
    }
}
