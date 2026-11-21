package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteMagenta extends BlockStairsConcrete {

    public BlockStairsConcreteMagenta() {
        this(0);
    }

    public BlockStairsConcreteMagenta(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MAGENTA_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.MAGENTA;
    }
}
