package cn.nukkit.level.generator.loot;

import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

public class ShipwreckSupplyChest extends RandomizableContainer {
    private static final ShipwreckSupplyChest INSTANCE = new ShipwreckSupplyChest();

    private ShipwreckSupplyChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool1 = new PoolBuilder()
            .register(new ItemEntry(Item.PAPER, 0, 12, 8))
            .register(new ItemEntry(Item.POTATO, 0, 6, 2, 7))
            .register(new ItemEntry(Item.POISONOUS_POTATO, 0, 6, 2, 7))
            .register(new ItemEntry(Item.CARROT, 0, 8, 4, 7))
            .register(new ItemEntry(Item.WHEAT, 0, 21, 8, 7))
            .register(new ItemEntry(Item.COAL, 0, 8, 2, 6))
            .register(new ItemEntry(Item.ROTTEN_FLESH, 0, 24, 5, 5))
            .register(new ItemEntry(BlockID.PUMPKIN, 0, 3, 2))
            .register(new ItemEntry(Item.GUNPOWDER, 0, 5, 3))
            .register(new ItemEntry(BlockID.TNT, 0, 2, 1))
            .register(new ItemEntry(Item.LEATHER_CAP, 3))
            .register(new ItemEntry(Item.LEATHER_TUNIC, 3))
            .register(new ItemEntry(Item.LEATHER_PANTS, 3))
            .register(new ItemEntry(Item.LEATHER_BOOTS, 3));
        pools.put(pool1.build(), new RollEntry(10, 3, pool1.getTotalWeight()));
    }

    public static ShipwreckSupplyChest get() {
        return INSTANCE;
    }
}
