package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteGreen extends BlockStairsConcrete {

    public BlockStairsConcreteGreen() {
        this(0);
    }

    public BlockStairsConcreteGreen(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GREEN_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.GREEN;
    }
}
