package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteLightGray extends BlockStairsConcrete {

    public BlockStairsConcreteLightGray() {
        this(0);
    }

    public BlockStairsConcreteLightGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIGHT_GRAY_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_GRAY;
    }
}
