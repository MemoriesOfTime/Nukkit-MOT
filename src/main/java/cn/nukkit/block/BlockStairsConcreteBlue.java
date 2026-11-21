package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteBlue extends BlockStairsConcrete {

    public BlockStairsConcreteBlue() {
        this(0);
    }

    public BlockStairsConcreteBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLUE_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLUE;
    }
}
