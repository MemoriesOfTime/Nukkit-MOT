package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteYellow extends BlockStairsConcrete {

    public BlockStairsConcreteYellow() {
        this(0);
    }

    public BlockStairsConcreteYellow(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return YELLOW_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.YELLOW;
    }
}
