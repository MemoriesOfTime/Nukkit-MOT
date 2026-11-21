package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteLime extends BlockStairsConcrete {

    public BlockStairsConcreteLime() {
        this(0);
    }

    public BlockStairsConcreteLime(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIME_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIME;
    }
}
