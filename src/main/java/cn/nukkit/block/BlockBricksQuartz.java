package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

/**
 * @author joserobjr
 */
public class BlockBricksQuartz extends BlockSolid {

    @Override
    public int getId() {
        return QUARTZ_BRICKS;
    }

    @Override
    public String getName() {
        return "Quartz Bricks";
    }

    @Override
    public double getHardness() {
        return 0.8;
    }

    @Override
    public double getResistance() {
        return 4;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
    
    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{
                    Item.get(Item.QUARTZ_BRICKS, 0, 1)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }
}