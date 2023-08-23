package cn.nukkit.level.generator.loot;

import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

//\\ ./data/behavior_packs/vanilla/loot_tables/chests/village_two_room_house.json (1.9.0.15)
public class VillageTwoRoomHouseChest extends RandomizableContainer {
    private static final VillageTwoRoomHouseChest INSTANCE = new VillageTwoRoomHouseChest();

    private VillageTwoRoomHouseChest() {
        super(Maps.newHashMap(), InventoryType.CHEST.getDefaultSize());

        final PoolBuilder pool1 = new PoolBuilder()
            .register(new ItemEntry(Item.POTATO, 0, 8, 5, 10))
            .register(new ItemEntry(Item.CARROT, 0, 8, 4, 10))
            .register(new ItemEntry(Item.WHEAT, 0, 12, 8, 15))
            .register(new ItemEntry(Item.WHEAT_SEEDS, 0, 4, 2, 5))
            .register(new ItemEntry(Item.BEETROOT, 0, 8, 5, 5))
            .register(new ItemEntry(Item.WOODEN_HOE, 1));
        pools.put(pool1.build(), new RollEntry(8, 6, pool1.getTotalWeight()));
    }

    public static VillageTwoRoomHouseChest get() {
        return INSTANCE;
    }
}
