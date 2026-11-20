package cn.nukkit.inventory.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.InventoryType;
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
        assertTrue(response.success(), "an overflowing merge can move the amount that fits");
        assertEquals(16, inventory.getItem(0).getCount());
        assertEquals(1, inventory.getItem(1).getCount());
    }

    @Test
    void overflowingTransferIntoEmptyLimitedInventoryMovesOnlyWhatFits() {
        Player player = mockPlayer();
        PlayerInventory sourceInventory = player.getInventory();
        BaseInventory destinationInventory = new BaseInventory(player, InventoryType.CHEST) {
            @Override
            public int getMaxStackSize() {
                return 16;
            }
        };
        destinationInventory.setSize(1);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(destinationInventory));

        Item source = Item.get(1, 0, 20);
        source.autoAssignStackNetworkId();
        sourceInventory.setItem(1, source, false);
        ActionResponse response = place(
                player,
                20,
                slot(ContainerSlotType.HOTBAR, 1, sourceInventory.getItem(1)),
                slot(ContainerSlotType.LEVEL_ENTITY, 0, destinationInventory.getItem(0)));

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(16, destinationInventory.getItem(0).getCount());
        assertEquals(4, sourceInventory.getItem(1).getCount());
    }

    @Test
    void mergeThatFitsMovesEverything() {
        Player player = mockPlayer();
        PlayerInventory inventory = player.getInventory();

        ActionResponse response = place(player, inventory, 4, 3, 3);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(7, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isNull());
    }

    @Test
    void mergeIntoAFullStackIsStillRefused() {
        Player player = mockPlayer();
        PlayerInventory inventory = player.getInventory();

        ActionResponse response = place(player, inventory, 16, 4, 4);

        assertNotNull(response);
        assertFalse(response.success());
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

        return place(
                player,
                requested,
                slot(ContainerSlotType.HOTBAR, 1, inventory.getItem(1)),
                slot(ContainerSlotType.HOTBAR, 0, inventory.getItem(0)));
    }

    private ActionResponse place(
            Player player,
            int requested,
            ItemStackRequestSlotData source,
            ItemStackRequestSlotData destination) {
        PlaceAction place = new PlaceAction(requested, source, destination);
        return new PlaceActionProcessor()
                .handle(
                        place,
                        player,
                        new ItemStackRequestContext(
                                new ItemStackRequest(1, new ItemStackRequestAction[0], new String[0])));
    }

    private static ItemStackRequestSlotData slot(ContainerSlotType type, int index, Item item) {
        return new ItemStackRequestSlotData(type, index, item.getStackNetId(), null);
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
