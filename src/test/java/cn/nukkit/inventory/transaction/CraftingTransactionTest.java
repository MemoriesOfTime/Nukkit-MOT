package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.Event;
import cn.nukkit.inventory.CraftingGrid;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.CraftingTakeResultAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CraftingTransactionTest {

    private Server server;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        MockServer.reset();

        server = MockServer.get();
        PluginManager pluginManager = mock(PluginManager.class);
        doNothing().when(pluginManager).callEvent(any(Event.class));
        when(server.getPluginManager()).thenReturn(pluginManager);

        player = mock(Player.class);
        inventory = new PlayerInventory(player);
        when(player.getServer()).thenReturn(server);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getCraftingGrid()).thenReturn(mock(CraftingGrid.class));

        Field protocolField = Player.class.getDeclaredField("protocol");
        protocolField.setAccessible(true);
        protocolField.setInt(player, 0);
    }

    @Test
    void normalizeCraftingResultPrefersPartialStackOverClientEmptySlot() {
        Item existing = Item.get(ItemID.STICK, 0, 63);
        Item result = Item.get(ItemID.STICK, 0, 2);
        inventory.setItem(5, existing, false);

        CraftingTransaction transaction = new CraftingTransaction(player, List.of(
                new CraftingTakeResultAction(result, Item.get(Item.AIR)),
                new SlotChangeAction(inventory, 10, Item.get(Item.AIR), result)
        ));

        transaction.normalizeCraftingResultSlots();

        assertEquals(3, transaction.getActionList().size());

        SlotChangeAction merge = (SlotChangeAction) transaction.getActionList().get(1);
        SlotChangeAction empty = (SlotChangeAction) transaction.getActionList().get(2);

        assertEquals(5, merge.getSlot());
        assertEquals(63, merge.getSourceItem().getCount());
        assertEquals(64, merge.getTargetItem().getCount());

        assertEquals(0, empty.getSlot());
        assertEquals(1, empty.getTargetItem().getCount());
    }

    @Test
    void normalizeCraftingResultRemovesClientEmptySlotWhenPartialStackHasEnoughRoom() {
        Item existing = Item.get(Block.COBBLESTONE, 0, 63);
        Item result = Item.get(Block.COBBLESTONE, 0, 1);
        inventory.setItem(3, existing, false);

        CraftingTransaction transaction = new CraftingTransaction(player, List.of(
                new CraftingTakeResultAction(result, Item.get(Item.AIR)),
                new SlotChangeAction(inventory, 8, Item.get(Item.AIR), result)
        ));

        transaction.normalizeCraftingResultSlots();

        assertEquals(2, transaction.getActionList().size());

        InventoryAction action = transaction.getActionList().get(1);
        SlotChangeAction merge = (SlotChangeAction) action;

        assertEquals(3, merge.getSlot());
        assertEquals(63, merge.getSourceItem().getCount());
        assertEquals(64, merge.getTargetItem().getCount());
    }

    @Test
    void normalizeCraftingResultUsesServerFirstEmptySlotForRemainder() {
        Item existing = Item.get(ItemID.STICK, 0, 64);
        Item result = Item.get(ItemID.STICK, 0, 2);
        inventory.setItem(0, existing, false);
        inventory.setItem(1, existing, false);

        CraftingTransaction transaction = new CraftingTransaction(player, List.of(
                new CraftingTakeResultAction(result, Item.get(Item.AIR)),
                new SlotChangeAction(inventory, 10, Item.get(Item.AIR), result)
        ));

        transaction.normalizeCraftingResultSlots();

        assertEquals(2, transaction.getActionList().size());

        SlotChangeAction empty = (SlotChangeAction) transaction.getActionList().get(1);
        assertEquals(2, empty.getSlot());
        assertEquals(2, empty.getTargetItem().getCount());
    }
}
