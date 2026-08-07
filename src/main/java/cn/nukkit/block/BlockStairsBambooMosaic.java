package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsBambooMosaic extends BlockStairsWood {

    public BlockStairsBambooMosaic() {
        this(0);
    }

    public BlockStairsBambooMosaic(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_MOSAIC_STAIRS;
    }

    @Override
    public String getName() {
        return "Bamboo Mosaic Stairs";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BAMBOO_BLOCK_COLOR;
    }
}