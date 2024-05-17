package cn.nukkit.level.generator.loot;

import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

public class DesertPyramidChest extends RandomizableContainer {
    private static final DesertPyramidChest INSTANCE = new DesertPyramidChest();

    private DesertPyramidChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool1 = new PoolBuilder()
                .register(new ItemEntry(Item.DIAMOND, 0, 3, 1, 5))
                .register(new ItemEntry(Item.IRON_INGOT, 0, 5, 1, 15))
                .register(new ItemEntry(Item.GOLD_INGOT, 0, 7, 2, 15))
                .register(new ItemEntry(Item.EMERALD, 0, 3, 1, 15))
                .register(new ItemEntry(Item.BONE, 0, 6, 4, 25))
                .register(new ItemEntry(Item.SPIDER_EYE, 0, 3, 1, 25))
                .register(new ItemEntry(Item.ROTTEN_FLESH, 0, 7, 3, 25))
                .register(new ItemEntry(Item.SADDLE, 20))
                .register(new ItemEntry(Item.IRON_HORSE_ARMOR, 15))
                .register(new ItemEntry(Item.GOLD_HORSE_ARMOR, 10))
                .register(new ItemEntry(Item.DIAMOND_HORSE_ARMOR, 5))
                .register(new ItemEntry(Item.ENCHANTED_BOOK, 20))
                .register(new ItemEntry(Item.GOLDEN_APPLE, 20))
                .register(new ItemEntry(Item.GOLDEN_APPLE_ENCHANTED, 2))
                .register(new ItemEntry(BlockID.AIR, 15));
        pools.put(pool1.build(), new RollEntry(4, 2, pool1.getTotalWeight()));

        final PoolBuilder pool2 = new PoolBuilder()
                .register(new ItemEntry(Item.BONE, 0, 8, 1, 10))
                .register(new ItemEntry(Item.GUNPOWDER, 0, 8, 1, 10))
                .register(new ItemEntry(Item.ROTTEN_FLESH, 0, 8, 1, 10))
                .register(new ItemEntry(Item.STRING, 0, 8, 1, 10))
                .register(new ItemEntry(BlockID.SAND, 0, 8, 1, 10));
        pools.put(pool2.build(), new RollEntry(4, pool2.getTotalWeight()));
    }

    public static DesertPyramidChest get() {
        return INSTANCE;
    }
}
