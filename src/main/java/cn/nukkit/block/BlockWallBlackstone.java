package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

public class BlockWallBlackstone extends BlockWall {

    public BlockWallBlackstone() {
        this(0);
    }

    public BlockWallBlackstone(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Blackstone Wall";
    }

    @Override
    public int getId() {
        return BLACKSTONE_WALL;
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
}
