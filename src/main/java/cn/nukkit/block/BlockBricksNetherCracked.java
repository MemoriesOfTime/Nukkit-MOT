package cn.nukkit.block;

import cn.nukkit.item.Item;
/**
 * @author joserobjr
 */
public class BlockBricksNetherCracked extends BlockBricksNether {
    public BlockBricksNetherCracked() {
        // Does nothing
    }

    @Override
    public int getId() {
        return CRACKED_NETHER_BRICKS;
    }

    @Override
    public String getName() {
        return "Cracked Nether Bricks";
    }
    
    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{
                    Item.get(Item.CRACKED_NETHER_BRICKS, 0, 1)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }
}
