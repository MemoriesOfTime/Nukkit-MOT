package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockMudBricks extends BlockSolid {
    public BlockMudBricks() {}

    @Override
    public int getId() {
        return MUD_BRICKS;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public double getHardness() {
        return 1.5;
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

    @Override
    public String getName() {
        return "Mud Bricks";
    }
}
