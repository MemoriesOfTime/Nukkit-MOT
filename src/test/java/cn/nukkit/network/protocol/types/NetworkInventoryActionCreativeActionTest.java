package cn.nukkit.network.protocol.types;

import cn.nukkit.Player;
import cn.nukkit.inventory.transaction.action.CreativeInventoryAction;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkInventoryActionCreativeActionTest {

    @Test
    void createActionRetainsTypeFromNetworkAction() {
        CreativeInventoryAction action = createCreativeAction(
                InventoryTransactionPacket.ACTION_MAGIC_SLOT_CREATIVE_CREATE_ITEM);

        assertEquals(CreativeInventoryAction.TYPE_CREATE_ITEM, action.getActionType());
    }

    @Test
    void deleteActionRetainsTypeFromNetworkAction() {
        CreativeInventoryAction action = createCreativeAction(
                InventoryTransactionPacket.ACTION_MAGIC_SLOT_CREATIVE_DELETE_ITEM);

        assertEquals(CreativeInventoryAction.TYPE_DELETE_ITEM, action.getActionType());
    }

    private static CreativeInventoryAction createCreativeAction(int inventorySlot) {
        Player player = mock(Player.class);
        when(player.isCreative()).thenReturn(true);

        NetworkInventoryAction networkAction = new NetworkInventoryAction();
        networkAction.sourceType = NetworkInventoryAction.SOURCE_CREATIVE;
        networkAction.inventorySlot = inventorySlot;

        return (CreativeInventoryAction) networkAction.createInventoryAction(player);
    }
}
