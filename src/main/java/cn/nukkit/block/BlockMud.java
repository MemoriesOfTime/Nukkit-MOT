package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockMud extends BlockSolid {
    public BlockMud() {
    }

    @Override
    public String getName() {
        return "Mud";
    }

    @Override
    public int getId() {
        return MUD;
    }

    // Vanilla mud collision is 14/16 high, like soul sand. A full cube here left the
    // server's floor 1/8 above the client's and corrected players standing on mud.
    @Override
    public double getMaxY() {
        return this.y + 1 - 0.125;
    }

    @Override
    public double getHardness() {
        return 0.5;
    }

    @Override
    public double getResistance() {
        return 0.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_SHOVEL;
    }

    @Override
    public boolean canHarvestWithHand() {
        return true;
    }
}