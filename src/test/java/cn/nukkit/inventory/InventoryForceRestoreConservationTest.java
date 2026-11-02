package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.inventory.request.ItemStackRequestHandler;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryForceRestoreConservationTest {
    @BeforeAll
    static void init() { MockServer.init(); }

    static class QuietInventory extends BaseInventory {
        int changed;
        QuietInventory() { super(null, InventoryType.CHEST, new HashMap<>(), 3); }
        @Override public boolean setItem(int slot, Item item, boolean send) {
            throw new AssertionError("restore must bypass normal grants and events");
        }
        @Override public Item[] addItem(Item... items) {
            throw new AssertionError("restore must not distribute overflow");
        }
        @Override public void onSlotChange(int slot, Item before, boolean send) {
            assertFalse(send, "restore must not send packets");
            changed++;
        }
    }

    @Test
    void fullInventoryRetainsLegacyOverstackNbtAndNetworkIdExactly() {
        QuietInventory inv = new QuietInventory();
        inv.setItemForce(0, Item.get(Item.DIRT, 0, 64));
        inv.setItemForce(1, Item.get(Item.STONE, 0, 64));
        inv.setItemForce(2, Item.get(Item.SAND, 0, 64));
        Item snapshot = Item.get(Item.SPLASH_POTION, 5, 2);
        snapshot.setNamedTag(new CompoundTag().putString("origin", "saved_before_request"));
        snapshot.autoAssignStackNetworkId();
        inv.changed = 0;

        inv.setItemForce(0, snapshot);

        assertItem(snapshot, inv.getItem(0));
        assertEquals(64, inv.getItem(1).getCount());
        assertEquals(64, inv.getItem(2).getCount());
        assertEquals(1, inv.changed);
        snapshot.setCount(1);
        assertEquals(2, inv.getItem(0).getCount(), "stored snapshot must be an independent clone");
    }

    @Test
    void realRollbackRestoresEverySlotWithoutCrossSlotLossOrDuplication() throws Exception {
        QuietInventory inv = new QuietInventory();
        Map<Integer, Item> snapshot = new LinkedHashMap<>();
        Item overstack = Item.get(Item.SPLASH_POTION, 5, 2);
        overstack.autoAssignStackNetworkId();
        snapshot.put(0, overstack);
        snapshot.put(1, Item.get(Item.DIAMOND, 0, 11));
        // Slot 1 is temporarily empty after the failed request. The old force path
        // routed a potion here, then overwrote it when restoring the diamonds.
        inv.setItemForce(2, Item.get(Item.GOLD_INGOT, 0, 9));
        Method restore = ItemStackRequestHandler.class.getDeclaredMethod("restoreInventory", Inventory.class, Map.class);
        restore.setAccessible(true);
        restore.invoke(null, inv, snapshot);
        assertItem(overstack, inv.getItem(0));
        assertEquals(11, inv.getItem(1).getCount());
        assertEquals(Item.DIAMOND, inv.getItem(1).getId());
        assertTrue(inv.getItem(2).isNull());
        assertEquals(13, inv.getContents().values().stream().mapToInt(Item::getCount).sum());
        restore.invoke(null, inv, snapshot);
        assertEquals(13, inv.getContents().values().stream().mapToInt(Item::getCount).sum(), "repeated restore is idempotent");
    }

    @Test
    void clearAndInvalidSlotsRetainTheirExistingSemantics() {
        QuietInventory inv = new QuietInventory();
        inv.setItemForce(0, Item.get(Item.DIAMOND));
        inv.setItemForce(-1, Item.get(Item.STONE));
        inv.setItemForce(3, Item.get(Item.STONE));
        assertEquals(1, inv.getContents().size());
        inv.setItemForce(0, null);
        assertTrue(inv.getContents().isEmpty());
    }

    private static void assertItem(Item expected, Item actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getDamage(), actual.getDamage());
        assertEquals(expected.getCount(), actual.getCount());
        assertEquals(expected.getStackNetId(), actual.getStackNetId());
        assertArrayEquals(expected.getCompoundTag(), actual.getCompoundTag());
    }
}
