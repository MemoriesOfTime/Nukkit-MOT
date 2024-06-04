package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

public class BlockSlabCrimson extends BlockSlab {

    public BlockSlabCrimson() {
        this(0);
    }

    public BlockSlabCrimson(int meta) {
        super(meta, CRIMSON_DOUBLE_SLAB);
    }

    protected BlockSlabCrimson(int meta, int doubleSlab) {
        super(meta, doubleSlab);
    }

    @Override
    public int getId() {
        return CRIMSON_SLAB;
    }

    @Override
    public String getName() {
        return "Crimson Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

}
