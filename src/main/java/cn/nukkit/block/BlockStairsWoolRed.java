package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolRed extends BlockStairsWool {

    public BlockStairsWoolRed() {
        this(0);
    }

    public BlockStairsWoolRed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return RED_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.RED;
    }
}
