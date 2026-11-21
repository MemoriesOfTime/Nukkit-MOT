package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteYellow extends BlockSlabConcrete {

    public BlockSlabConcreteYellow() {
        this(0);
    }

    public BlockSlabConcreteYellow(int meta) {
        super(meta, YELLOW_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return YELLOW_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.YELLOW;
    }
}
