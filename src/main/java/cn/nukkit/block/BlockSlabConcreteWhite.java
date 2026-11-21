package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabConcreteWhite extends BlockSlabConcrete {

    public BlockSlabConcreteWhite() {
        this(0);
    }

    public BlockSlabConcreteWhite(int meta) {
        super(meta, WHITE_CONCRETE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return WHITE_CONCRETE_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.WHITE;
    }
}
