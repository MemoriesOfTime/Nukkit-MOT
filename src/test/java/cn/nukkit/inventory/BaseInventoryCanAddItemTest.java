package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseInventoryCanAddItemTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    private static final class TestInventory extends BaseInventory {

        TestInventory() {
            super(Mockito.mock(InventoryHolder.class), InventoryType.CHEST);
        }
    }

    @Test
    void anEmptySlotHoldsOneUnstackableItem() {
        TestInventory inventory = new TestInventory();
        int size = inventory.getSize();

        assertTrue(inventory.canAddItem(Item.get(ItemID.DIAMOND_HELMET, 0, size)));
        assertFalse(inventory.canAddItem(Item.get(ItemID.DIAMOND_HELMET, 0, size + 1)));
    }

    @Test
    void anEmptySlotStillHoldsAFullStackOfAStackableItem() {
        TestInventory inventory = new TestInventory();
        int capacity = inventory.getSize() * Math.min(Item.get(Item.STONE).getMaxStackSize(), inventory.getMaxStackSize());

        assertTrue(inventory.canAddItem(Item.get(Item.STONE, 0, capacity)));
        assertFalse(inventory.canAddItem(Item.get(Item.STONE, 0, capacity + 1)));
    }

    @Test
    void aPartiallyFilledStackStillCountsItsRemainder() {
        TestInventory inventory = new TestInventory();
        inventory.slots.put(0, Item.get(Item.STONE, 0, 60));
        int stack = Math.min(Item.get(Item.STONE).getMaxStackSize(), inventory.getMaxStackSize());
        int capacity = (inventory.getSize() - 1) * stack + (stack - 60);

        assertTrue(inventory.canAddItem(Item.get(Item.STONE, 0, capacity)));
        assertFalse(inventory.canAddItem(Item.get(Item.STONE, 0, capacity + 1)));
    }

    @Test
    void aFilledInventoryRefusesAnUnstackableItem() {
        TestInventory inventory = new TestInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.slots.put(slot, Item.get(Item.STONE, 0, 1));
        }

        assertFalse(inventory.canAddItem(Item.get(ItemID.DIAMOND_HELMET)));
    }
}
