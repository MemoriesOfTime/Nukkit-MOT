package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabPaleOak extends BlockSlab {

    public BlockSlabPaleOak() {
        this(0);
    }

    public BlockSlabPaleOak(int meta) {
        super(meta, PALE_OAK_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return PALE_OAK_SLAB;
    }

    @Override
    public String getName() {
        return "Pale Oak Slab";
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
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
