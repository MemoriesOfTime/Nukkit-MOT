package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabBamboo extends BlockSlab {
    public BlockSlabBamboo() {
        this(0);
    }

    public BlockSlabBamboo(int meta) {
        super(meta, BAMBOO_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BAMBOO_SLAB;
    }

    @Override
    public String getName() {
        return "Bamboo Slab";
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
