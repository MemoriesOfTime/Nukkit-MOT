package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolBlack extends BlockSlabWool {

    public BlockSlabWoolBlack() {
        this(0);
    }

    public BlockSlabWoolBlack(int meta) {
        super(meta, BLACK_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BLACK_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLACK;
    }
}
