package cn.nukkit.level.generator.loot;

import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

//\\ ./data/behavior_packs/vanilla/loot_tables/chests/stronghold_library.json
public class StrongholdLibraryChest extends RandomizableContainer {
    private static final StrongholdLibraryChest INSTANCE = new StrongholdLibraryChest();

    private StrongholdLibraryChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool1 = new PoolBuilder()
            .register(new ItemEntry(Item.BOOK, 0, 3, 100))
            .register(new ItemEntry(Item.PAPER, 0, 7, 2, 100))
            .register(new ItemEntry(Item.EMPTY_MAP, 5))
            .register(new ItemEntry(Item.COMPASS, 5))
            .register(new ItemEntry(Item.ENCHANTED_BOOK, 60));
        pools.put(pool1.build(), new RollEntry(10, 2, pool1.getTotalWeight()));
    }

    public static StrongholdLibraryChest get() {
        return INSTANCE;
    }
}
