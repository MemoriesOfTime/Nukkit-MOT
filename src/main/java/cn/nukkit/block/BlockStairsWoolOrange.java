package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolOrange extends BlockStairsWool {

    public BlockStairsWoolOrange() {
        this(0);
    }

    public BlockStairsWoolOrange(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return ORANGE_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.ORANGE;
    }
}
