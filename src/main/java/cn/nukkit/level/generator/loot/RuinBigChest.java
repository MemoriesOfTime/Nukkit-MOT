package cn.nukkit.level.generator.loot;

import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

public class RuinBigChest extends RandomizableContainer {
    private static final RuinBigChest INSTANCE = new RuinBigChest();

    private RuinBigChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool1 = new PoolBuilder()
            .register(new ItemEntry(Item.COAL, 0, 4, 10))
            .register(new ItemEntry(Item.GOLD_NUGGET, 0, 3, 10))
            .register(new ItemEntry(Item.EMERALD, 1))
            .register(new ItemEntry(Item.WHEAT, 0, 3, 2, 10));
        pools.put(pool1.build(), new RollEntry(8, 2, pool1.getTotalWeight()));

        final PoolBuilder pool2 = new PoolBuilder()
            .register(new ItemEntry(Item.GOLDEN_APPLE, 1))
            .register(new ItemEntry(Item.ENCHANT_BOOK, 5))
            .register(new ItemEntry(Item.LEATHER_TUNIC, 1))
            .register(new ItemEntry(Item.GOLD_HELMET, 1))
            .register(new ItemEntry(Item.FISHING_ROD, 5));
        pools.put(pool2.build(), new RollEntry(1, pool2.getTotalWeight()));
    }

    public static RuinBigChest get() {
        return INSTANCE;
    }
}
