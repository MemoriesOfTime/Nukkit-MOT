package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteBrown extends BlockStairsConcrete {

    public BlockStairsConcreteBrown() {
        this(0);
    }

    public BlockStairsConcreteBrown(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BROWN_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.BROWN;
    }
}
