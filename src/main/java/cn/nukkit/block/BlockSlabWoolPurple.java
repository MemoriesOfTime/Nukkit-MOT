package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolPurple extends BlockSlabWool {

    public BlockSlabWoolPurple() {
        this(0);
    }

    public BlockSlabWoolPurple(int meta) {
        super(meta, PURPLE_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return PURPLE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PURPLE;
    }
}
