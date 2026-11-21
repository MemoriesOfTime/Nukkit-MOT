package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolGreen extends BlockStairsWool {

    public BlockStairsWoolGreen() {
        this(0);
    }

    public BlockStairsWoolGreen(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GREEN_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GREEN;
    }
}
