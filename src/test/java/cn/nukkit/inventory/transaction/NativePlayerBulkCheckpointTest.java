package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerBulkCheckpointTest {
    @BeforeAll static void initialize() { NativeWorkstationOverflowTest.initialize(); }
    @AfterEach void reset() { MockServer.reset(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void closeFromTheFirstBulkWriteCapturesTheWholeAcceptedBatch(boolean removing) throws Exception {
        var f = NativePlayerProfileLifecycleTest.fixture();
        f.station.setItemForce(0, Item.AIR_ITEM);
        PlayerInventory inventory = f.player.getInventory();
        inventory.clearAll();
        Item first = NativePlayerProfileLifecycleTest.marked(1);
        Item second = NativePlayerProfileLifecycleTest.marked(2);
        if (removing) { inventory.setItemForce(0, first); inventory.setItemForce(1, second); }
        var saved = new ArrayList<CompoundTag>(); NativePlayerProfileLifecycleTest.observeSaves(f, saved);
        var requested = new AtomicInteger(); var quits = new AtomicInteger();
        f.events(event -> {
            if (event instanceof PlayerQuitEvent) quits.incrementAndGet();
            if (event instanceof EntityInventoryChangeEvent change && change.getSlot() == 0
                    && (removing ? change.getNewItem().isNull() : change.getNewItem().equalsExact(first))
                    && requested.getAndIncrement() == 0) f.player.close();
        });
        assertEquals(0, (removing ? inventory.removeItem(first, second) : inventory.addItem(first, second)).length);
        assertTrue(f.player.closed); assertNull(f.player.getInventory()); assertEquals(1, quits.get());
        assertFalse(saved.isEmpty());
        CompoundTag last = saved.get(saved.size() - 1);
        for (Item item : new Item[]{first, second}) {
            int count = last.getList("Inventory", CompoundTag.class).getAll().stream().map(NBTIO::getItemHelper)
                    .filter(value -> value.equals(item, true, true)).mapToInt(Item::getCount).sum();
            assertEquals(removing ? 0 : item.getCount(), count, "profile reflects the completed whole bulk operation");
        }
    }
}
