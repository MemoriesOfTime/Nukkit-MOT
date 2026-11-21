package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolWhite extends BlockStairsWool {

    public BlockStairsWoolWhite() {
        this(0);
    }

    public BlockStairsWoolWhite(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WHITE_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.WHITE;
    }
}
