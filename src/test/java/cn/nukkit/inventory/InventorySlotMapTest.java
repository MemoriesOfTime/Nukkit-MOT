package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import org.objenesis.ObjenesisStd;

import static org.junit.jupiter.api.Assertions.*;

class InventorySlotMapTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void publicMapAndNativeAccessorsShareTheSameSlots() throws NoSuchFieldException {
        assertEquals(Map.class, BaseInventory.class.getField("slots").getType());
        BaseInventory inventory = new TestInventory();
        Item item = Item.get(Item.STONE, 0, 7);
        inventory.slots.put(0, item);
        assertSame(item, inventory.getUnclonedItem(0));
        assertSame(item, inventory.getItemFast(0));
        assertNotSame(item, inventory.getItem(0));
        assertEquals(7, inventory.getItem(0).getCount());
        assertNotEquals(0, item.getStackNetId());

        Item replacement = Item.get(Item.DIRT, 0, 9);
        inventory.slots.replaceAll((slot, previous) -> replacement);
        assertSame(replacement, inventory.getUnclonedItem(0));
        inventory.slots.entrySet().iterator().next().setValue(item);
        assertSame(item, inventory.getItemFast(0));
        assertTrue(inventory.slots.keySet().remove(0));
        assertTrue(inventory.getItem(0).isNull());

        inventory.setItemForce(2, Item.get(Item.STONE, 0, 42));
        assertEquals(42, inventory.slots.get(2).getCount());
        inventory.setItemForce(2, Item.get(Item.AIR));
        assertFalse(inventory.slots.containsKey(2));

        int hiddenSlot = 65_536;
        inventory.slots.put(hiddenSlot, item);
        assertSame(item, inventory.getUnclonedItem(hiddenSlot));
        Map<Integer, Item> copy = inventory.getContents();
        copy.clear();
        assertSame(item, inventory.getItemFast(hiddenSlot));
        inventory.slots.values().clear();
        assertTrue(inventory.getItem(hiddenSlot).isNull());
    }

    @Test
    void emptyItemsHaveIndependentMutableState() {
        BaseInventory inventory = new TestInventory();
        assertIndependentEmptyItems(inventory.getItem(0), inventory.getItem(0));
        assertIndependentEmptyItems(inventory.getUnclonedItem(0), inventory.getUnclonedItem(0));
    }

    @Test
    void reflectivelyReplacedSlotsMapRemainsOperationalWithoutConstructorInit() throws ReflectiveOperationException {
        TestInventory inventory = new ObjenesisStd().newInstance(TestInventory.class);
        var slotsField = BaseInventory.class.getField("slots");
        slotsField.setAccessible(true);
        slotsField.set(inventory, new HashMap<Integer, Item>());
        inventory.setSize(40);

        Item helmet = Item.get(Item.DIAMOND_HELMET, 0, 1);
        helmet.setStackNetId(0);
        inventory.slots.put(36, helmet);

        assertEquals(Item.DIAMOND_HELMET, inventory.getItem(36).getId());
        assertTrue(helmet.getStackNetId() > 0);
        assertSame(helmet, inventory.getItemFast(36));
        assertTrue(inventory.getItemFast(37).isNull());

        inventory.setItemForce(37, Item.get(Item.DIAMOND_CHESTPLATE, 0, 1));
        assertEquals(Item.DIAMOND_CHESTPLATE, inventory.slots.get(37).getId());
        inventory.setItemForce(37, Item.get(Item.AIR));
        assertFalse(inventory.slots.containsKey(37));
    }

    private static void assertIndependentEmptyItems(Item first, Item second) {
        assertInstanceOf(ItemBlock.class, first);
        assertInstanceOf(ItemBlock.class, second);
        assertNotSame(first, second);
        assertNotSame(first.getBlockUnsafe(), second.getBlockUnsafe());
        assertEquals(0, first.getCount());
        assertFalse(first.hasMeta());
        first.setCount(5);
        first.setDamage(7);
        first.getBlockUnsafe().setComponents(8, 9, 10);
        assertEquals(0, second.getCount());
        assertFalse(second.hasMeta());
        assertEquals(0, second.getBlockUnsafe().getFloorX());
    }

    private static final class TestInventory extends BaseInventory {
        TestInventory() {
            super(null, InventoryType.CHEST);
        }
    }
}
