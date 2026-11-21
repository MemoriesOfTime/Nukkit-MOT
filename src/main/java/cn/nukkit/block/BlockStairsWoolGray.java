package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolGray extends BlockStairsWool {

    public BlockStairsWoolGray() {
        this(0);
    }

    public BlockStairsWoolGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GRAY_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GRAY;
    }
}
