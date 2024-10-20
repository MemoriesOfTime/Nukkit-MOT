package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockPackedMud extends BlockSolid {
    public BlockPackedMud() {
    }

    @Override
    public int getId() {
        return PACKED_MUD;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public double getHardness() {
        return 1;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return true;
    }

    @Override
    public String getName() {
        return "Packed Mud";
    }
}
