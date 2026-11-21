package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteCyan extends BlockSlabConcrete {

    public BlockSlabConcreteCyan() {
        this(0);
    }

    public BlockSlabConcreteCyan(int meta) {
        super(meta, CYAN_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return CYAN_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.CYAN;
    }
}
