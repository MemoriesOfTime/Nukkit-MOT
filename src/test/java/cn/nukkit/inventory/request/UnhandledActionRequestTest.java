package cn.nukkit.inventory.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.atLeastOnce;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.BeaconPaymentAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TakeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * An action the handler cannot process must fail its whole request.
 *
 * <p>Skipping it silently leaves the client and the server out of sync: later actions of the same
 * request keep running on top of a step that never happened, so items are either created from
 * nothing or lost without a trace.
 */
class UnhandledActionRequestTest {

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
    void registeredTypeWithoutProcessorFailsAndRollsBackTheWholeRequest() {
        ItemStackRequestActionProcessor<?> removed = unregister(ItemStackRequestActionType.BEACON_PAYMENT);
        try {
            assertRefused(new BeaconPaymentAction(1, 0));
        } finally {
            if (removed != null) {
                ItemStackRequestHandler.register(removed);
            }
        }
    }

    @Test
    void actionOutsideTheKnownTableFailsTheWholeRequestToo() {
        assertRefused(new ItemStackRequestAction() {
            @Override
            public ItemStackRequestActionType getType() {
                return null;
            }
        });
    }

    /**
     * Control: the very same take, alone, does move half a stack.
     *
     * <p>Without it a rollback assertion would pass even if the take never ran at all.
     */
    @Test
    void theSameTakeAloneReallyMovesTheItems() {
        Fixture fixture = fixture();
        ItemStackRequest request = new ItemStackRequest(
                7, new ItemStackRequestAction[]{take()}, new String[0]);

        ItemStackRequestHandler.handleRequests(fixture.player(), List.of(request));

        ItemStackResponsePacket response = capturePacket(fixture.player(), ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult(),
                "a plain take must succeed");
        assertEquals(32, fixture.inventory().getItem(0).getCount(), "half the stack must leave the slot");
        assertEquals(32, fixture.ui().getCursorInventory().getItem(0).getCount(),
                "half the stack must reach the cursor");
    }

    /** Take half a stack, then hit the unhandled action: nothing may survive the request. */
    private void assertRefused(ItemStackRequestAction unhandled) {
        Fixture fixture = fixture();
        ItemStackRequest request = new ItemStackRequest(
                7, new ItemStackRequestAction[]{take(), unhandled}, new String[0]);

        ItemStackRequestHandler.handleRequests(fixture.player(), List.of(request));

        ItemStackResponsePacket response = capturePacket(fixture.player(), ItemStackResponsePacket.class);
        assertEquals(ItemStackResponseStatus.ERROR, response.entries.get(0).getResult(),
                "an unhandled action must fail its request instead of being skipped");
        assertEquals(64, fixture.inventory().getItem(0).getCount(),
                "the taken half must be rolled back");
        assertTrue(fixture.ui().getCursorInventory().getItem(0).isNull(),
                "the cursor must not keep what the failed request moved");
        Mockito.verify(fixture.player(), atLeastOnce()).sendAllInventories();
    }

    private record Fixture(Player player, PlayerInventory inventory, PlayerUIInventory ui) {}

    private static TakeAction take() {
        return new TakeAction(
                32,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.CURSOR, 0, 0, null));
    }

    private static Fixture fixture() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        inventory.setItemForce(0, Item.get(Item.COBBLESTONE, 0, 64));
        return new Fixture(player, inventory, ui);
    }

    @SuppressWarnings("unchecked")
    private static ItemStackRequestActionProcessor<?> unregister(ItemStackRequestActionType type) {
        try {
            Field field = ItemStackRequestHandler.class.getDeclaredField("PROCESSORS");
            field.setAccessible(true);
            EnumMap<ItemStackRequestActionType, ItemStackRequestActionProcessor<?>> processors =
                    (EnumMap<ItemStackRequestActionType, ItemStackRequestActionProcessor<?>>) field.get(null);
            return processors.remove(type);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("processor table is not reachable", e);
        }
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        return player;
    }

    private static <T extends DataPacket> T capturePacket(Player player, Class<T> type) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player, atLeastOnce()).dataPacket(captor.capture());
        for (DataPacket packet : captor.getAllValues()) {
            if (type.isInstance(packet)) {
                return type.cast(packet);
            }
        }
        fail("Expected packet " + type.getSimpleName());
        return null;
    }
}
