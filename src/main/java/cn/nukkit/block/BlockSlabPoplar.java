package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabPoplar extends BlockSlab {

    public BlockSlabPoplar() {
        this(0);
    }

    public BlockSlabPoplar(int meta) {
        super(meta, POPLAR_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return POPLAR_SLAB;
    }

    @Override
    public String getName() {
        return "Poplar Slab";
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
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
