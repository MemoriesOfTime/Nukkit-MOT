package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsWoolLime extends BlockStairsWool {

    public BlockStairsWoolLime() {
        this(0);
    }

    public BlockStairsWoolLime(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return LIME_WOOL_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.LIME;
    }
}
