package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolPink extends BlockSlabWool {

    public BlockSlabWoolPink() {
        this(0);
    }

    public BlockSlabWoolPink(int meta) {
        super(meta, PINK_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return PINK_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PINK;
    }
}
