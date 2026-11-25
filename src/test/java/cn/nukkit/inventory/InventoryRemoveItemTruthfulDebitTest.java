package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryRemoveItemTruthfulDebitTest {
    @BeforeAll static void initialize() { MockServer.init(); }
    @AfterEach void resetEvents() { reset(Server.getInstance().getPluginManager()); }

    @Test void successfulSetterMustReportOnlyTheCountActuallyRemoved() {
        Player player = mock(Player.class);
        PlayerInventory inventory = new PlayerInventory(player);
        inventory.setItemForce(0, Item.get(Item.EMERALD, 0, 8));
        var plugins = Server.getInstance().getPluginManager();
        doAnswer(call -> {
            EntityInventoryChangeEvent event = call.getArgument(0);
            event.setNewItem(Item.get(Item.EMERALD, 0, 7));
            return null;
        }).when(plugins).callEvent(any(EntityInventoryChangeEvent.class));
        Item[] rejected = inventory.removeItem(Item.get(Item.EMERALD, 0, 4));
        int remainingRequest = Arrays.stream(rejected).mapToInt(Item::getCount).sum();
        int actualDebit = 8 - inventory.getItem(0).getCount();
        assertEquals(4, actualDebit + remainingRequest, "reported debit must equal actual slot removal");
        assertEquals(8, inventory.getItem(0).getCount());
        assertEquals(4, remainingRequest);
    }
}
