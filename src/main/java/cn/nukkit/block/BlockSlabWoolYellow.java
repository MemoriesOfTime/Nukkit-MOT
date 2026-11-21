package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolYellow extends BlockSlabWool {

    public BlockSlabWoolYellow() {
        this(0);
    }

    public BlockSlabWoolYellow(int meta) {
        super(meta, YELLOW_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return YELLOW_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.YELLOW;
    }
}
