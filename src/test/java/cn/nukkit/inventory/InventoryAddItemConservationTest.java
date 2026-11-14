package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Public inventory APIs must report amounts actually accepted after a cancelled event. */
class InventoryAddItemConservationTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void clearListener() { reset(Server.getInstance().getPluginManager()); }

    @Test
    void rejectedEmptySlotWriteMustReturnTheUngivenItem() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        cancelWrites();
        Item[] leftovers = inventory.addItem(Item.get(Item.DIAMOND, 0, 1));
        assertTrue(inventory.getContents().isEmpty(), "the slot event rejected the grant");
        assertEquals(1, leftovers.length, "a rejected grant must remain in leftovers");
        assertEquals(1, leftovers[0].getCount());
    }

    @Test
    void rejectedMergeMustReturnTheUngivenAmount() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        inventory.setItemForce(0, Item.get(Item.DIAMOND, 0, 10));
        cancelWrites();
        Item[] leftovers = inventory.addItem(Item.get(Item.DIAMOND, 0, 1));
        assertEquals(10, inventory.getItem(0).getCount(), "the slot event rejected the merge");
        assertEquals(1, leftovers.length, "a rejected merge must remain in leftovers");
        assertEquals(1, leftovers[0].getCount());
    }

    @Test
    void aRejectedMergeCanUseAnAllowedEmptySlotWithoutLosingItsAmount() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        inventory.setItemForce(0, Item.get(Item.DIAMOND, 0, 10));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == 0) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item request = Item.get(Item.DIAMOND, 0, 1);
        assertEquals(0, inventory.addItem(request).length);
        assertEquals(10, inventory.getItem(0).getCount());
        assertEquals(1, inventory.getItem(1).getCount());
        assertEquals(1, request.getCount(), "the caller's stack remains unchanged");
    }

    @Test
    void partialAcceptanceAndRetryOfOnlyTheRemainderConserveTheRequestedStack() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() != 0) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item request = Item.get(Item.DIAMOND, 0, 100);
        Item[] leftovers = inventory.addItem(request);
        assertEquals(64, count(inventory));
        assertEquals(1, leftovers.length);
        assertEquals(36, leftovers[0].getCount());
        assertEquals(100, count(inventory) + leftovers[0].getCount());
        assertEquals(100, request.getCount());
        reset(plugins);
        assertEquals(0, inventory.addItem(leftovers).length);
        assertEquals(100, count(inventory));
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(36, inventory.getItem(1).getCount());
    }

    @Test
    void aRejectedCandidateDoesNotIncreaseTheBaseOfTheNextMerge() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        inventory.setItemForce(0, Item.get(Item.DIAMOND, 0, 10));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getNewItem().getCount() != 12) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item[] leftovers = inventory.addItem(Item.get(Item.DIAMOND, 0, 1), Item.get(Item.DIAMOND, 0, 2));
        assertEquals(12, inventory.getItem(0).getCount());
        assertEquals(1, leftovers.length);
        assertEquals(1, leftovers[0].getCount());
        assertEquals(13, count(inventory) + leftovers[0].getCount());
    }

    private static int count(PlayerInventory inventory) {
        return inventory.getContents().values().stream().mapToInt(Item::getCount).sum();
    }

    private void cancelWrites() {
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
    }
}
