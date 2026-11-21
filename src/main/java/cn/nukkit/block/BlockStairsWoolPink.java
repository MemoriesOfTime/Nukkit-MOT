package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolPink extends BlockStairsWool {

    public BlockStairsWoolPink() {
        this(0);
    }

    public BlockStairsWoolPink(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PINK_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PINK;
    }
}
