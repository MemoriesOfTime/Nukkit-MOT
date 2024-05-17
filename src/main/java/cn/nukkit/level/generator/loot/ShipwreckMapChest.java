package cn.nukkit.level.generator.loot;

import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

public class ShipwreckMapChest extends RandomizableContainer {
    private static final ShipwreckMapChest INSTANCE = new ShipwreckMapChest();

    private ShipwreckMapChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool1 = new PoolBuilder()
                .register(new ItemEntry(Item.EMPTY_MAP, 1)); // 395 exploration_map
        pools.put(pool1.build(), new RollEntry(1, pool1.getTotalWeight()));

        final PoolBuilder pool2 = new PoolBuilder()
                .register(new ItemEntry(Item.COMPASS, 1))
                .register(new ItemEntry(Item.EMPTY_MAP, 1)) // 395
                .register(new ItemEntry(Item.CLOCK, 1))
                .register(new ItemEntry(Item.PAPER, 0, 10, 20))
                .register(new ItemEntry(Item.FEATHER, 0, 5, 10))
                .register(new ItemEntry(Item.BOOK, 0, 5, 5));
        pools.put(pool2.build(), new RollEntry(3, pool2.getTotalWeight()));
    }

    public static ShipwreckMapChest get() {
        return INSTANCE;
    }
}
