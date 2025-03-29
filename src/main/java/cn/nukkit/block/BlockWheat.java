package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSeedsWheat;
import cn.nukkit.item.ItemWheat;
import cn.nukkit.utils.Utils;

/**
 * Created on 2015/12/2 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockWheat extends BlockCrops {

    public BlockWheat() {
        this(0);
    }

    public BlockWheat(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Wheat Block";
    }

    @Override
    public int getId() {
        return WHEAT_BLOCK;
    }

    @Override
    public Item toItem() {
        return new ItemSeedsWheat();
    }

    @Override
    public String getIdentifier() {
        return "minecraft:wheat";
    }

    @Override
    public Item[] getDrops(Item item) {
        if (this.getPropertyValue(GROWTH) >= 7) {
            return new Item[]{
                    new ItemWheat(),
                    new ItemSeedsWheat(0, Utils.random.nextInt(1, 3))
            };
        } else {
            return new Item[]{
                    new ItemSeedsWheat()
            };
        }
    }
}
