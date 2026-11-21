package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcretePurple extends BlockStairsConcrete {

    public BlockStairsConcretePurple() {
        this(0);
    }

    public BlockStairsConcretePurple(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PURPLE_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PURPLE;
    }
}
