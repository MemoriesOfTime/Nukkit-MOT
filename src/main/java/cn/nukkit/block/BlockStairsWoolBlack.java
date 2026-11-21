package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolBlack extends BlockStairsWool {

    public BlockStairsWoolBlack() {
        this(0);
    }

    public BlockStairsWoolBlack(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLACK_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLACK;
    }
}
