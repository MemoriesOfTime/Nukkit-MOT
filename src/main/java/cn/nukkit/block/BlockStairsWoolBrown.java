package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolBrown extends BlockStairsWool {

    public BlockStairsWoolBrown() {
        this(0);
    }

    public BlockStairsWoolBrown(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BROWN_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BROWN;
    }
}
