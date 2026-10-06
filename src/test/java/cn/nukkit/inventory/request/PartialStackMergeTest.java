package cn.nukkit.inventory.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceAction;
import cn.nukkit.plugin.PluginManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A merge that does not fit in full moves what fits, the way vanilla does.
 *
 * <p>Refusing the whole request left both slots untouched, so items with a small stack limit
 * (ender pearls, eggs, snowballs) looked to the player as if they could not be stacked at all.
 */
class PartialStackMergeTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void overflowingMergeFillsTheDestinationAndKeepsTheRemainder() {
        Player player = mockPlayer();
        PlayerInventory inventory = player.getInventory();

        ActionResponse response = place(player, inventory, 15, 2, 2);

        assertNotNull(response);
        assertTrue(response.success(), "an overflowing merge is vanilla behaviour, not an error");
        assertEquals(16, inventory.getItem(0).getCount(), "destination is filled to the stack limit");
        assertEquals(1, inventory.getItem(1).getCount(), "the remainder stays on the source side");
    }

    @Test
    void mergeThatFitsMovesEverything() {
        Player player = mockPlayer();
        PlayerInventory inventory = player.getInventory();

        ActionResponse response = place(player, inventory, 4, 3, 3);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(7, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isNull(), "source slot is emptied by a full transfer");
    }

    @Test
    void mergeIntoAFullStackIsStillRefused() {
        Player player = mockPlayer();
        PlayerInventory inventory = player.getInventory();

        ActionResponse response = place(player, inventory, 16, 4, 4);

        assertNotNull(response);
        assertFalse(response.success(), "nothing fits, so nothing moves");
        assertEquals(16, inventory.getItem(0).getCount());
        assertEquals(4, inventory.getItem(1).getCount());
    }

    private ActionResponse place(
            Player player, PlayerInventory inventory, int destCount, int srcCount, int requested) {
        Item dst = Item.get(Item.ENDER_PEARL, 0, destCount);
        dst.autoAssignStackNetworkId();
        inventory.setItem(0, dst, false);
        Item src = Item.get(Item.ENDER_PEARL, 0, srcCount);
        src.autoAssignStackNetworkId();
        inventory.setItem(1, src, false);

        PlaceAction place = new PlaceAction(
                requested,
                new ItemStackRequestSlotData(
                        ContainerSlotType.HOTBAR, 1, inventory.getItem(1).getStackNetId(), null),
                new ItemStackRequestSlotData(
                        ContainerSlotType.HOTBAR, 0, inventory.getItem(0).getStackNetId(), null));
        return new PlaceActionProcessor()
                .handle(
                        place,
                        player,
                        new ItemStackRequestContext(
                                new ItemStackRequest(1, new ItemStackRequestAction[0], new String[0])));
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        Mockito.when(player.isCreative()).thenReturn(false);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        return player;
    }
}
