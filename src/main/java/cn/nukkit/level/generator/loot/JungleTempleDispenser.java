package cn.nukkit.level.generator.loot;

import cn.nukkit.item.Item;
import com.google.common.collect.Maps;

public class JungleTempleDispenser extends RandomizableContainer {
    private static final JungleTempleDispenser INSTANCE = new JungleTempleDispenser();

    private JungleTempleDispenser() {
        super(Maps.newHashMap(), 9); //InventoryType.DISPENSER.getDefaultSize()

        final PoolBuilder pool1 = new PoolBuilder()
            .register(new ItemEntry(Item.ARROW, 0, 7, 2, 1));
        pools.put(pool1.build(), new RollEntry(2, 0));
    }

    public static JungleTempleDispenser get() {
        return INSTANCE;
    }
}
