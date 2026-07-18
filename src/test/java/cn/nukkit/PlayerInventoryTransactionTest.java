package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.transaction.action.DropItemAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInventoryTransactionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void serverAuthoritativeLegacyDropAllowsOnlyWorldDropPlusInventoryDecrease() {
        Player player = Mockito.mock(Player.class);
        PlayerInventory inventory = new PlayerInventory(player);

        Item oldItem = Item.get(Item.STONE, 0, 64);
        Item newItem = Item.get(Item.STONE, 0, 63);
        Item droppedItem = Item.get(Item.STONE, 0, 1);

        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.transactionType = InventoryTransactionPacket.TYPE_NORMAL;
        packet.actions = new NetworkInventoryAction[]{
                worldDrop(droppedItem),
                inventoryChange(oldItem, newItem)
        };

        List<InventoryAction> actions = List.of(
                new DropItemAction(Item.get(Item.AIR), droppedItem),
                new SlotChangeAction(inventory, 0, oldItem, newItem)
        );
        assertTrue(Player.isServerAuthoritativeLegacyDropTransaction(packet, actions));

        packet.actions[1].newItem = Item.get(Item.STONE, 0, 62);
        assertFalse(Player.isServerAuthoritativeLegacyDropTransaction(packet, actions));
    }

    private static NetworkInventoryAction worldDrop(Item droppedItem) {
        NetworkInventoryAction action = new NetworkInventoryAction();
        action.sourceType = NetworkInventoryAction.SOURCE_WORLD;
        action.inventorySlot = InventoryTransactionPacket.ACTION_MAGIC_SLOT_DROP_ITEM;
        action.oldItem = Item.get(Item.AIR);
        action.newItem = droppedItem;
        return action;
    }

    private static NetworkInventoryAction inventoryChange(Item oldItem, Item newItem) {
        NetworkInventoryAction action = new NetworkInventoryAction();
        action.sourceType = NetworkInventoryAction.SOURCE_CONTAINER;
        action.windowId = ContainerIds.INVENTORY;
        action.inventorySlot = 0;
        action.oldItem = oldItem;
        action.newItem = newItem;
        return action;
    }
}
