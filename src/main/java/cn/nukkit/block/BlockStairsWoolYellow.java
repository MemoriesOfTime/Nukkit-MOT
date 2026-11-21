package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolYellow extends BlockStairsWool {

    public BlockStairsWoolYellow() {
        this(0);
    }

    public BlockStairsWoolYellow(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return YELLOW_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.YELLOW;
    }
}
