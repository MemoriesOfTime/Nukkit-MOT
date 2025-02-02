package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabMangrove extends BlockSlab {

    public BlockSlabMangrove() {
        this(0);
    }

    public BlockSlabMangrove(int meta) {
        super(meta, MANGROVE_DOUBLE_SLAB);
    }

    protected BlockSlabMangrove(int meta, int doubleSlab) {
        super(meta, doubleSlab);
    }

    @Override
    public int getId() {
        return MANGROVE_SLAB;
    }

    @Override
    public String getName() {
        return "Mangrove Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MANGROVE_BLOCK_COLOR;
    }

}
