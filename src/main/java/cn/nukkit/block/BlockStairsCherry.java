package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCherry extends BlockStairsWood {

    public BlockStairsCherry() {
        this(0);
    }

    public BlockStairsCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_STAIRS;
    }

    @Override
    public String getName() {
        return "Cherry Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CHERRY_BLOCK_COLOR;
    }
}
