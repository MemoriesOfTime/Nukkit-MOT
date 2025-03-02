package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabCherry  extends BlockSlab {
    public BlockSlabCherry() {
        this(0);
    }

    public BlockSlabCherry(int meta) {
        super(meta, CHERRY_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return CHERRY_SLAB;
    }

    @Override
    public String getName() {
        return "Cherry Slab";
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
        return BlockColor.CHERRY_BLOCK_COLOR;
    }
}
