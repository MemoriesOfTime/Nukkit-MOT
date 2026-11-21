package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteRed extends BlockStairsConcrete {

    public BlockStairsConcreteRed() {
        this(0);
    }

    public BlockStairsConcreteRed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return RED_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.RED;
    }
}
