package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

/**
 * @author GoodLucky777
 */
public class BlockBricksDeepslate extends BlockSolid {
    public BlockBricksDeepslate() {
        // Does Nothing
    }

    @Override
    public int getId() {
        return DEEPSLATE_BRICKS;
    }

    @Override
    public String getName() {
        return "Deepslate Bricks";
    }

    @Override
    public double getHardness() {
        return 3.5;
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