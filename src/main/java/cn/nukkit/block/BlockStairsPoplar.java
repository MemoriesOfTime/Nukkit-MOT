package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsPoplar extends BlockStairsWood {

    public BlockStairsPoplar() {
        this(0);
    }

    public BlockStairsPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_STAIRS;
    }

    @Override
    public String getName() {
        return "Poplar Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
