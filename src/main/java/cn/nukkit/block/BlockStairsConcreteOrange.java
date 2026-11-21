package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteOrange extends BlockStairsConcrete {

    public BlockStairsConcreteOrange() {
        this(0);
    }

    public BlockStairsConcreteOrange(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return ORANGE_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.ORANGE;
    }
}
