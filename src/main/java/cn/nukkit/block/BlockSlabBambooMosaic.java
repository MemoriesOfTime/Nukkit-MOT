package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabBambooMosaic extends BlockSlab {
    public BlockSlabBambooMosaic() {
        this(0);
    }

    public BlockSlabBambooMosaic(int meta) {
        super(meta, BAMBOO_MOSAIC_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BAMBOO_MOSAIC_SLAB;
    }

    @Override
    public String getName() {
        return "Bamboo Mosaic Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BAMBOO_BLOCK_COLOR;
    }
}

