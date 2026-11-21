package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockSlabWoolCyan extends BlockSlabWool {

    public BlockSlabWoolCyan() {
        this(0);
    }

    public BlockSlabWoolCyan(int meta) {
        super(meta, CYAN_WOOL_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return CYAN_WOOL_SLAB;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.CYAN;
    }
}
