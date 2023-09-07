package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockCryingObsidian extends BlockSolid {
    @Override
    public String getName() {
        return "Crying Obsidian";
    }

    @Override
    public int getId() {
        return CRYING_OBSIDIAN;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        return 50;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public double getResistance() {
        return 1200;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBePulled() {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
}
