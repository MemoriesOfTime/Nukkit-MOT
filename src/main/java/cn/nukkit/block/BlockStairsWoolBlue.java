package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolBlue extends BlockStairsWool {

    public BlockStairsWoolBlue() {
        this(0);
    }

    public BlockStairsWoolBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLUE_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLUE;
    }
}
