package cn.nukkit.block;

import cn.nukkit.utils.DyeColor;

public class BlockStairsConcreteWhite extends BlockStairsConcrete {

    public BlockStairsConcreteWhite() {
        this(0);
    }

    public BlockStairsConcreteWhite(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WHITE_CONCRETE_STAIRS;
    }

    @Override
    public DyeColor getDyeColor() {
        return DyeColor.WHITE;
    }
}
