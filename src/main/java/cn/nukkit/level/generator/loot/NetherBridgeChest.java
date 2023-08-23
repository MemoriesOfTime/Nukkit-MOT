package cn.nukkit.level.generator.loot;

import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

public class NetherBridgeChest extends RandomizableContainer {
    private static final NetherBridgeChest INSTANCE = new NetherBridgeChest();

    private NetherBridgeChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool = new PoolBuilder()
            .register(new ItemEntry(Item.DIAMOND, 0, 3, 5))
            .register(new ItemEntry(Item.IRON_INGOT, 0, 5, 5))
            .register(new ItemEntry(Item.GOLD_INGOT, 0, 3, 15))
            .register(new ItemEntry(Item.GOLDEN_SWORD, 5))
            .register(new ItemEntry(Item.GOLD_CHESTPLATE, 5))
            .register(new ItemEntry(Item.FLINT_AND_STEEL, 5))
            .register(new ItemEntry(Item.NETHER_WART, 0, 7, 3, 5))
            .register(new ItemEntry(Item.SADDLE, 10))
            .register(new ItemEntry(Item.GOLD_HORSE_ARMOR, 8))
            .register(new ItemEntry(Item.IRON_HORSE_ARMOR, 5))
            .register(new ItemEntry(Item.DIAMOND_HORSE_ARMOR, 3))
            .register(new ItemEntry(BlockID.OBSIDIAN, 0, 4, 2, 2));
        pools.put(pool.build(), new RollEntry(4, 2, pool.getTotalWeight()));
    }

    public static NetherBridgeChest get() {
        return INSTANCE;
    }
}
