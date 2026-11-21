package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteGray extends BlockStairsConcrete {

    public BlockStairsConcreteGray() {
        this(0);
    }

    public BlockStairsConcreteGray(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GRAY_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GRAY;
    }
}
