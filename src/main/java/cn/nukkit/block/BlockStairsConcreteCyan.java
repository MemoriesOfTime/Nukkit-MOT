package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteCyan extends BlockStairsConcrete {

    public BlockStairsConcreteCyan() {
        this(0);
    }

    public BlockStairsConcreteCyan(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CYAN_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.CYAN;
    }
}
