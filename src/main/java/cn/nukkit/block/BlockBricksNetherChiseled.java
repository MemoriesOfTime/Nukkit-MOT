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
                    Item.getBlockItem(getId(), 0, 1)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }
}
