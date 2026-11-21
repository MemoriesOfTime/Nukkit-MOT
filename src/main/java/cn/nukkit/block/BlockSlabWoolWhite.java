package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolWhite extends BlockSlabWool {

    public BlockSlabWoolWhite() {
        this(0);
    }

    public BlockSlabWoolWhite(int meta) {
        super(meta, WHITE_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return WHITE_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.WHITE;
    }
}
