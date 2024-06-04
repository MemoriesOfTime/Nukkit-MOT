package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

public class BlockSlabWarped extends BlockSlab {

    public BlockSlabWarped() {
        this(0);
    }

    public BlockSlabWarped(int meta) {
        super(meta, WARPED_DOUBLE_SLAB);
    }

    protected BlockSlabWarped(int meta, int doubleSlab) {
        super(meta, doubleSlab);
    }

    @Override
    public int getId() {
        return WARPED_SLAB;
    }

    @Override
    public String getName() {
        return "Warped Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

}
