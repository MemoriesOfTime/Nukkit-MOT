package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolPurple extends BlockStairsWool {

    public BlockStairsWoolPurple() {
        this(0);
    }

    public BlockStairsWoolPurple(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PURPLE_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PURPLE;
    }
}
