package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockDoubleSlabConcreteCyan extends BlockDoubleSlabConcrete {

    public BlockDoubleSlabConcreteCyan() {
        this(0);
    }

    public BlockDoubleSlabConcreteCyan(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CYAN_CONCRETE_DOUBLE_SLAB;
    }

    @Override
    protected int getSingleSlabId() {
        return CYAN_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.CYAN;
    }
}
