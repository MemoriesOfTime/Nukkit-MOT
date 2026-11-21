package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcretePink extends BlockStairsConcrete {

    public BlockStairsConcretePink() {
        this(0);
    }

    public BlockStairsConcretePink(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PINK_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.PINK;
    }
}
