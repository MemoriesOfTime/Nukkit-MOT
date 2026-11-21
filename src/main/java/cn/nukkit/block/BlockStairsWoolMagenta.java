package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolMagenta extends BlockStairsWool {

    public BlockStairsWoolMagenta() {
        this(0);
    }

    public BlockStairsWoolMagenta(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MAGENTA_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.MAGENTA;
    }
}
