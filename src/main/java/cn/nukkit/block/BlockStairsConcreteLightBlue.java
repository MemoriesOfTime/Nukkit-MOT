package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteLightBlue extends BlockStairsConcrete {

    public BlockStairsConcreteLightBlue() {
        this(0);
    }

    public BlockStairsConcreteLightBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIGHT_BLUE_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIGHT_BLUE;
    }
}
