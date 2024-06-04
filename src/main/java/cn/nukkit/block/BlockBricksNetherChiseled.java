package cn.nukkit.block;

import cn.nukkit.item.Item;
/**
 * @author joserobjr
 */
public class BlockBricksNetherChiseled extends BlockBricksNether {
    public BlockBricksNetherChiseled() {
        // Does nothing
    }

    @Override
    public int getId() {
        return CHISELED_NETHER_BRICKS;
    }

    @Override
    public String getName() {
        return "Chiseled Nether Bricks";
    }
    
    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{
                    Item.get(Item.CHISELED_NETHER_BRICKS, 0, 1)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }
}
