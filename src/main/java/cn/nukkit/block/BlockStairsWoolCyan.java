package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolCyan extends BlockStairsWool {

    public BlockStairsWoolCyan() {
        this(0);
    }

    public BlockStairsWoolCyan(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CYAN_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.CYAN;
    }
}
