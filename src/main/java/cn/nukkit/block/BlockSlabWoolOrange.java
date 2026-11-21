package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolOrange extends BlockSlabWool {

    public BlockSlabWoolOrange() {
        this(0);
    }

    public BlockSlabWoolOrange(int meta) {
        super(meta, ORANGE_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return ORANGE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.ORANGE;
    }
}
