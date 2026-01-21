package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockWallTuff extends BlockWallIndependentID {
    public BlockWallTuff() {
        this(0);
    }

    public BlockWallTuff(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Tuff Wall";
    }

    @Override
    public int getId() {
        return TUFF_WALL;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.TUFF_BLOCK_COLOR;
    }
}
