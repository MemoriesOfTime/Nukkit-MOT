package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolLightBlue extends BlockStairsWool {

    public BlockStairsWoolLightBlue() {
        this(0);
    }

    public BlockStairsWoolLightBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIGHT_BLUE_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_BLUE;
    }
}
