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

class InventoryRemoveItemConservationTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void rejectedRemovalMustRemainUnremoved() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 4));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> { ((EntityInventoryChangeEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item[] notRemoved = inventory.removeItem(Item.get(Item.EMERALD, 0, 4));
        assertEquals(4, inventory.getItem(0).getCount());
        assertEquals(4, count(notRemoved), "a rejected debit must not report success");
    }

    @Test void partiallyAcceptedRemovalMustReportOnlyActuallyDebitedAmount() {
        PlayerInventory inventory = new PlayerInventory(mock(Player.class));
        inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 2));
        inventory.setItemForce(1, Item.get(Item.EMERALD, 0, 3));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            if (event.getSlot() == 1) event.setCancelled();
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item[] notRemoved = inventory.removeItem(Item.get(Item.EMERALD, 0, 5));
        assertEquals(3, inventory.getItem(1).getCount());
        assertEquals(3, count(notRemoved), "two accepted debits plus three leftovers must equal five");
        reset(plugins);
        assertEquals(0, inventory.removeItem(notRemoved).length);
        assertTrue(inventory.getContents().isEmpty(), "retry only the unremoved remainder");
    }

    private static int count(Item[] items) {
        return java.util.Arrays.stream(items).mapToInt(Item::getCount).sum();
    }
}
