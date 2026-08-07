package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsPaleOak extends BlockStairsWood {

    public BlockStairsPaleOak() {
        this(0);
    }

    public BlockStairsPaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_STAIRS;
    }

    @Override
    public String getName() {
        return "Pale Oak Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
