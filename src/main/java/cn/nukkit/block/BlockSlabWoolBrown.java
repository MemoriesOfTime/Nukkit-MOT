package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolBrown extends BlockSlabWool {

    public BlockSlabWoolBrown() {
        this(0);
    }

    public BlockSlabWoolBrown(int meta) {
        super(meta, BROWN_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BROWN_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BROWN;
    }
}
