package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolLightGray extends BlockStairsWool {

    public BlockStairsWoolLightGray() {
        this(0);
    }

    public BlockStairsWoolLightGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIGHT_GRAY_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_GRAY;
    }
}
