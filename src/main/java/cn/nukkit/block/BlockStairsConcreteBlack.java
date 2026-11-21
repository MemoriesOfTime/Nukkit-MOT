package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteBlack extends BlockStairsConcrete {

    public BlockStairsConcreteBlack() {
        this(0);
    }

    public BlockStairsConcreteBlack(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BLACK_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BLACK;
    }
}
