package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockTuff extends BlockSolid {
    public BlockTuff() {
    }

    @Override
    public String getName() {
        return "Tuff";
    }

    @Override
    public int getId() {
        return TUFF;
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
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
}
