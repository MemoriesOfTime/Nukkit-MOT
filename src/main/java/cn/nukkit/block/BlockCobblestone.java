package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

/**
 * @author Angelic47
 * Nukkit Project
 */
public class BlockCobblestone extends BlockSolid {

    @Override
    public int getId() {
        return COBBLESTONE;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 30;
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
    public String getName() {
        return "Cobblestone";
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{
                    toItem()
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
}
