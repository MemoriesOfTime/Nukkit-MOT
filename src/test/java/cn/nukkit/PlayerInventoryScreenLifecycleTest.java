package cn.nukkit;

import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.event.Event;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Runs the actual Player packet dispatcher, native inventory callbacks and network send path. */
class PlayerInventoryScreenLifecycleTest {
    private TestPlayer player;
    private List<DataPacket> sent;
    private Level level;
    private cn.nukkit.plugin.PluginManager plugins;

    @BeforeEach void setup() {
        MockServer.reset();
        Server server = MockServer.get();
        plugins = server.getPluginManager();
        when(server.isPrimaryThread()).thenReturn(true);
        level = mock(Level.class);
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(server.getDefaultLevel()).thenReturn(level);
        SourceInterface source = mock(SourceInterface.class);
        NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(session);
        sent = new ArrayList<>();
        doAnswer(call -> { sent.add(call.getArgument(0)); return null; }).when(session).sendPacket(any(DataPacket.class));
        player = new TestPlayer(source);
    }

    @ParameterizedTest @MethodSource("protocols")
    void replacementClosesOldViewerAndWaitsForAckBeforeOpeningNext(int protocol) {
        player.protocol(protocol);
        ChestInventory old = chest();
        ShulkerBoxInventory fresh = shulker();
        int oldId = player.addWindow(old);
        assertTrue(oldId > 0);
        sent.clear();
        assertEquals(-1, player.addWindow(fresh), "replacement must not pretend to have opened before the close handshake");
        assertFalse(old.getViewers().contains(player));
        assertNull(player.getWindowById(oldId));
        assertFalse(fresh.getViewers().contains(player));
        assertEquals(1, sent.stream().filter(ContainerClosePacket.class::isInstance).count());
        assertFalse(sent.stream().anyMatch(ContainerOpenPacket.class::isInstance));
        sent.clear();
        player.receiveClose(oldId, ContainerType.CONTAINER);
        assertEquals(1, sent.stream().filter(ContainerClosePacket.class::isInstance).count());
        assertFalse(((ContainerClosePacket) sent.get(0)).wasServerInitiated);
        assertTrue(player.addWindow(fresh) > 0);
        assertTrue(fresh.getViewers().contains(player));
    }

    @ParameterizedTest @MethodSource("protocols")
    void mainInventoryMinusOneCloseAlwaysFinishesHandshake(int protocol) {
        player.protocol(protocol);
        player.openMain();
        sent.clear();
        player.receiveClose(-1, ContainerType.INVENTORY);
        assertEquals(1, sent.stream().filter(ContainerClosePacket.class::isInstance).count(), "main-inventory NONE/SMALL close must ACK before returning");
        assertSame(player.getInventory(), player.getWindowById(0));
        assertTrue(player.getInventory().getViewers().contains(player));
        player.openMain();
        assertTrue(sent.stream().anyMatch(ContainerOpenPacket.class::isInstance));
    }

    @Test void canceledOpenDoesNotLeakMapViewerOrTopWindow() {
        ChestInventory chest = chest();
        doAnswer(call -> { if (call.getArgument(0) instanceof InventoryOpenEvent event) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertEquals(-1, player.addWindow(chest));
        assertEquals(-1, player.getWindowId(chest));
        assertFalse(chest.getViewers().contains(player));
        assertTrue(player.getTopWindow().isEmpty(), "crafting and permanent UI mappings are not screen windows");
    }

    @Test void recursiveCloseCallbackCannotReopenBeforeCleanupOrClearFreshMain() {
        ChestInventory chest = chest();
        int id = player.addWindow(chest);
        ShulkerBoxInventory fresh = shulker();
        List<Integer> attempts = new ArrayList<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof InventoryCloseEvent event && event.getInventory() == chest) {
                attempts.add(player.addWindow(fresh));
                player.removeWindow(chest);
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        sent.clear();
        player.receiveClose(id, ContainerType.CONTAINER);
        assertEquals(List.of(-1), attempts);
        assertFalse(chest.getViewers().contains(player));
        assertFalse(fresh.getViewers().contains(player));
        assertEquals(Integer.MIN_VALUE, player.getClosingWindowId());
        assertTrue(player.addWindow(fresh) > 0);
    }

    @ParameterizedTest @MethodSource("protocols")
    void deferredRequestReportsOnlyActualOpenAfterAck(int protocol) {
        player.protocol(protocol);
        ChestInventory old = chest();
        int oldId = player.addWindow(old);
        ShulkerBoxInventory fresh = shulker();
        List<Integer> callbacks = new ArrayList<>();
        sent.clear();
        player.requestWindowOpen(fresh, null, () -> true, callbacks::add);
        assertTrue(player.isAwaitingContainerClose(oldId));
        assertTrue(callbacks.isEmpty());
        assertEquals(1, sent.stream().filter(ContainerClosePacket.class::isInstance).count());
        sent.clear();
        player.receiveClose(oldId, ContainerType.NONE);
        assertEquals(1, callbacks.size());
        assertTrue(callbacks.get(0) > 0);
        assertTrue(fresh.getViewers().contains(player));
        assertInstanceOf(ContainerClosePacket.class, sent.get(0));
        assertFalse(((ContainerClosePacket) sent.get(0)).wasServerInitiated);
        assertEquals(ContainerType.CONTAINER, ((ContainerClosePacket) sent.get(0)).type);
        assertInstanceOf(ContainerOpenPacket.class, sent.get(1));
        assertFalse(player.isAwaitingContainerClose(oldId));
        sent.clear();
        player.receiveClose(oldId, ContainerType.CONTAINER);
        player.receiveClose(89, ContainerType.NONE);
        assertTrue(sent.isEmpty(), "late/duplicate closes must not send an unsolicited ack onto the new screen");
        assertTrue(fresh.getViewers().contains(player));
    }

    @Test void removeUnmappedQueuedInventoryCancelsItAndAckCannotResurrectIt() {
        ChestInventory old = chest();
        int id = player.addWindow(old);
        ShulkerBoxInventory fresh = shulker();
        List<Integer> callbacks = new ArrayList<>();
        player.requestWindowOpen(fresh, null, () -> true, callbacks::add);
        player.removeWindow(fresh);
        assertEquals(List.of(-1), callbacks);
        player.receiveClose(id, ContainerType.CONTAINER);
        assertEquals(List.of(-1), callbacks);
        assertFalse(fresh.getViewers().contains(player));
    }

    @Test void replacementCancelsOlderPendingRequestExactlyOnce() {
        ChestInventory old = chest();
        int id = player.addWindow(old);
        ShulkerBoxInventory first = shulker();
        ChestInventory latest = chest();
        List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
        var firstRequest = player.requestWindowOpen(first, null, () -> true, a::add);
        player.requestWindowOpen(latest, null, () -> true, b::add);
        firstRequest.cancel();
        assertEquals(List.of(-1), a);
        player.receiveClose(id, ContainerType.CONTAINER);
        assertFalse(first.getViewers().contains(player));
        assertEquals(1, b.size());
        assertTrue(b.get(0) > 0);
    }

    @Test void pendingForcedIdCannotBeReusedOrEvictPermanentUiMapping() {
        ChestInventory old = chest();
        assertEquals(2, player.addWindow(old, 2));
        player.removeWindow(old);
        assertEquals(-1, player.addWindow(shulker(), 2));
        assertEquals(-1, player.addWindow(shulker(), ContainerIds.UI));
        assertSame(player.getUIInventory(), player.getWindowById(ContainerIds.UI));
        player.receiveClose(2, ContainerType.CONTAINER);
        ShulkerBoxInventory fresh = shulker();
        assertEquals(2, player.addWindow(fresh, 2));
        player.receiveClose(2, ContainerType.CONTAINER);
        assertFalse(fresh.getViewers().contains(player));
    }

    @Test void lateOldCloseCannotResetFreshMainInventoryCraftingOrCursor() {
        ChestInventory old = chest();
        int id = player.addWindow(old);
        player.removeWindow(old);
        player.receiveClose(id, ContainerType.CONTAINER);
        player.openMain();
        player.getCraftingGrid().setItem(0, Item.get(Item.DIAMOND, 0, 3));
        player.getCursorInventory().setItem(0, Item.get(Item.GOLD_INGOT, 0, 2));
        sent.clear();
        player.receiveClose(id, ContainerType.CONTAINER);
        assertTrue(sent.isEmpty());
        assertEquals(3, player.getCraftingGrid().getItem(0).getCount());
        assertEquals(2, player.getCursorInventory().getItem(0).getCount());
        player.receiveClose(0, ContainerType.INVENTORY);
        assertTrue(player.getCraftingGrid().getItem(0).isNull());
        assertTrue(player.getCursorInventory().getItem(0).isNull());
        assertEquals(5, player.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum());
    }

    @Test void mainInventoryRequestReplacesChestThroughSameBarrier() {
        ChestInventory old = chest();
        int id = player.addWindow(old);
        sent.clear();
        player.openMain();
        assertTrue(player.isAwaitingContainerClose(id));
        assertFalse(old.getViewers().contains(player));
        assertFalse(sent.stream().anyMatch(ContainerOpenPacket.class::isInstance));
        player.receiveClose(id, ContainerType.CONTAINER);
        assertTrue(sent.stream().filter(ContainerOpenPacket.class::isInstance)
                .map(ContainerOpenPacket.class::cast).anyMatch(open -> open.windowId == 0));
        player.receiveClose(-1, ContainerType.INVENTORY);
        assertSame(player.getInventory(), player.getWindowById(0));
    }

    @Test void rawWorkbenchUsesBarrierAndOwnCancellationHandle() {
        ChestInventory old = chest();
        int id = player.addWindow(old);
        int[] opened = {0}, refused = {0};
        var request = player.requestInventoryScreenOpen(() -> true, () -> {
            opened[0]++;
            ContainerOpenPacket open = new ContainerOpenPacket(); open.windowId = -1; open.type = 1;
            player.dataPacket(open);
        }, () -> refused[0]++);
        request.cancel();
        player.receiveClose(id, ContainerType.CONTAINER);
        assertEquals(0, opened[0]); assertEquals(1, refused[0]);
        player.requestInventoryScreenOpen(() -> true, () -> {
            ContainerOpenPacket open = new ContainerOpenPacket(); open.windowId = -1; open.type = 1;
            assertTrue(player.dataPacket(open));
        }, () -> fail("valid raw open refused"));
        assertFalse(player.closeInventoryScreen(0, ContainerType.INVENTORY));
        assertTrue(player.closeInventoryScreen(-1, ContainerType.WORKBENCH));
        assertTrue(player.isAwaitingContainerClose(-1));
        player.receiveClose(-1, ContainerType.WORKBENCH);
        assertFalse(player.isAwaitingContainerClose(-1));
    }

    @Test void disconnectOrTeleportCleanupCancelsPendingOpen() {
        int id = player.addWindow(chest());
        ShulkerBoxInventory fresh = shulker(); List<Integer> callback = new ArrayList<>();
        player.requestWindowOpen(fresh, null, () -> true, callback::add);
        player.removeAllWindows();
        player.receiveClose(id, ContainerType.CONTAINER);
        assertEquals(List.of(-1), callback);
        assertFalse(fresh.getViewers().contains(player));
    }

    @Test void validityChangedDuringOpenEventPreventsOpenPacketAndSuccess() {
        boolean[] valid = {true}; ChestInventory chest = chest(); List<Integer> callback = new ArrayList<>();
        doAnswer(call -> { if (call.getArgument(0) instanceof InventoryOpenEvent) valid[0] = false; return null; })
                .when(plugins).callEvent(any(Event.class));
        sent.clear();
        player.requestWindowOpen(chest, null, () -> valid[0], callback::add);
        assertEquals(List.of(-1), callback);
        assertFalse(chest.getViewers().contains(player));
        assertFalse(sent.stream().anyMatch(ContainerOpenPacket.class::isInstance));
        assertEquals(-1, player.getWindowId(chest));
    }

    @Test void cancelledOutboundCloseRetainsRetryableScreenAndNeverArmsBarrier() {
        int id = player.addWindow(chest());
        MockServer.get().callDataPkSendEv = true;
        doAnswer(call -> { if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                && event.getPacket() instanceof ContainerClosePacket) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertEquals(-1, player.addWindow(shulker()));
        assertFalse(player.isAwaitingContainerClose(id));
        doNothing().when(plugins).callEvent(any(Event.class));
        List<Integer> callbacks = new ArrayList<>();
        ShulkerBoxInventory fresh = shulker();
        player.requestWindowOpen(fresh, null, () -> true, callbacks::add);
        assertTrue(player.isAwaitingContainerClose(id));
        assertTrue(callbacks.isEmpty());
        player.receiveClose(id, ContainerType.CONTAINER);
        assertTrue(callbacks.get(0) > 0);
    }

    @Test void closeEventExceptionRestoresSentinelAndCleansOldViewerBeforeNextOpen() {
        ChestInventory old = chest(); int id = player.addWindow(old);
        doAnswer(call -> { if (call.getArgument(0) instanceof InventoryCloseEvent) throw new IllegalStateException("close callback"); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertDoesNotThrow(() -> player.receiveClose(id, ContainerType.CONTAINER));
        assertEquals(Integer.MIN_VALUE, player.getClosingWindowId());
        assertFalse(old.getViewers().contains(player));
        assertEquals(-1, player.getWindowId(old));
        doNothing().when(plugins).callEvent(any(Event.class));
        assertTrue(player.addWindow(shulker()) > 0);
    }

    @Test void pendingServerCloseAcceptsNoneRejectionAliasWithoutLeavingBarrier() {
        int id = player.addWindow(chest());
        ShulkerBoxInventory fresh = shulker(); List<Integer> callback = new ArrayList<>();
        player.requestWindowOpen(fresh, null, () -> true, callback::add);
        sent.clear();
        assertTrue(player.isAwaitingContainerClose(-1));
        player.receiveClose(-1, ContainerType.NONE);
        assertEquals(id, ((ContainerClosePacket) sent.get(0)).windowId);
        assertEquals(ContainerType.CONTAINER, ((ContainerClosePacket) sent.get(0)).type);
        assertTrue(callback.get(0) > 0);
        assertFalse(player.isAwaitingContainerClose(-1));
    }

    @Test void rawWorkbenchCloseRetainsSmallCraftingMappingWhileAckIsPending() {
        player.requestInventoryScreenOpen(() -> true, () -> {
            player.craftingType = Player.CRAFTING_BIG;
            player.setCraftingGrid(player.getUIInventory().getBigCraftingGrid());
            ContainerOpenPacket open = new ContainerOpenPacket(); open.windowId = -1; open.type = 1;
            assertTrue(player.dataPacket(open));
        }, () -> fail("valid raw open refused"));
        player.getCraftingGrid().setItem(0, Item.get(Item.DIAMOND, 0, 2));
        assertTrue(player.closeInventoryScreen(-1, ContainerType.WORKBENCH));
        assertSame(player.getCraftingGrid(), player.getWindowById(ContainerIds.NONE));
        assertInstanceOf(CraftingGrid.class, player.getCraftingGrid());
        assertFalse(player.getCraftingGrid() instanceof BigCraftingGrid);
        assertTrue(player.isAwaitingContainerClose(-1));
        assertEquals(2, player.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum());
        player.receiveClose(-1, ContainerType.WORKBENCH);
        assertSame(player.getCraftingGrid(), player.getWindowById(ContainerIds.NONE));
    }



    @Test void throwingCancellationCallbackDoesNotLoseNextRequest() {
        int id = player.addWindow(chest());
        player.requestWindowOpen(shulker(), null, () -> true, result -> { if (result == -1) throw new IllegalStateException("refused callback"); });
        List<Integer> callback = new ArrayList<>(); ChestInventory fresh = chest();
        assertDoesNotThrow(() -> player.requestWindowOpen(fresh, null, () -> true, callback::add));
        player.receiveClose(id, ContainerType.CONTAINER);
        assertTrue(callback.get(0) > 0);
        assertTrue(fresh.getViewers().contains(player));
    }

    @Test void rawOpenReentryCancelsOldRequestBeforeItCanSendOverNewScreen() {
        MockServer.get().callDataPkSendEv = true;
        boolean[] reentered = {false}; int[] refused = {0};
        doAnswer(call -> {
            if (!reentered[0] && call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                    && event.getPacket() instanceof ContainerOpenPacket) {
                reentered[0] = true;
                player.requestInventoryScreenOpen(() -> true, () -> {
                    ContainerOpenPacket second = new ContainerOpenPacket(); second.windowId = -1; second.type = 1;
                    assertTrue(player.dataPacket(second));
                }, () -> fail("new raw request refused"));
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        player.requestInventoryScreenOpen(() -> true, () -> {
            ContainerOpenPacket first = new ContainerOpenPacket(); first.windowId = -1; first.type = 1;
            assertFalse(player.dataPacket(first));
        }, () -> refused[0]++);
        assertTrue(reentered[0]); assertEquals(1, refused[0]);
        assertEquals(1, sent.stream().filter(ContainerOpenPacket.class::isInstance).count());
    }

    @Test void deniedDoubleChestOpenLeavesNoViewersOnEitherHalf() {
        ChestInventory left = chest(), right = chest();
        when(left.getHolder().getRealInventory()).thenReturn(left);
        when(right.getHolder().getRealInventory()).thenReturn(right);
        DoubleChestInventory both = new DoubleChestInventory(left.getHolder(), right.getHolder());
        MockServer.get().callDataPkSendEv = true;
        doAnswer(call -> { if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                && event.getPacket() instanceof ContainerOpenPacket) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertEquals(-1, player.addWindow(both));
        assertFalse(both.getViewers().contains(player));
        assertFalse(left.getViewers().contains(player));
        assertFalse(right.getViewers().contains(player));
    }

    @Test void deniedEnderChestOpenCannotPublishLidOrRetainWorldViewer() {
        cn.nukkit.block.BlockEnderChest chest = mock(cn.nukkit.block.BlockEnderChest.class);
        java.util.Set<Player> viewers = new java.util.HashSet<>();
        when(chest.getViewers()).thenReturn(viewers);
        when(chest.getLevel()).thenReturn(level);
        player.setViewingEnderChest(chest);
        MockServer.get().callDataPkSendEv = true;
        doAnswer(call -> { if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                && event.getPacket() instanceof ContainerOpenPacket) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        sent.clear();
        assertEquals(-1, player.addWindow(player.getEnderChestInventory()));
        assertNull(player.getViewingEnderChest());
        assertTrue(viewers.isEmpty());
        assertFalse(player.getEnderChestInventory().getViewers().contains(player));
        verify(level, never()).addChunkPacket(anyInt(), anyInt(), any(BlockEventPacket.class));
    }

    @Test void nativeEnderChestActivationReallyOpensAfterPreviousCloseAck() {
        cn.nukkit.block.BlockEnderChest block = new cn.nukkit.block.BlockEnderChest();
        block.level = level;
        cn.nukkit.blockentity.BlockEntityEnderChest holder = mock(cn.nukkit.blockentity.BlockEntityEnderChest.class);
        holder.namedTag = new cn.nukkit.nbt.tag.CompoundTag();
        java.util.Set<Player> viewers = new java.util.HashSet<>();
        when(holder.getViewers()).thenReturn(viewers);
        when(holder.getLevel()).thenReturn(level);
        when(level.getBlockEntity(any(Vector3.class))).thenReturn(holder);
        when(level.getBlockEntityIfLoaded(any(Vector3.class))).thenReturn(holder);
        when(level.getBlock(any(Vector3.class))).thenAnswer(call -> ((Vector3) call.getArgument(0)).y > 0
                ? new cn.nukkit.block.BlockAir() : block);
        when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(new cn.nukkit.block.BlockAir());
        cn.nukkit.level.format.generic.BaseFullChunk chunk = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
        when(chunk.getEntities()).thenReturn(java.util.Collections.emptyMap());
        when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        int oldId = player.addWindow(chest());
        // chest() sets the standing holder stub; restore ender chest after the old screen opened.
        when(level.getBlockEntityIfLoaded(any(Vector3.class))).thenReturn(holder);
        assertTrue(block.onActivate(Item.AIR_ITEM, player));
        assertTrue(player.isAwaitingContainerClose(oldId));
        assertNull(player.getViewingEnderChest());
        player.receiveClose(oldId, ContainerType.CONTAINER);
        assertSame(block, player.getViewingEnderChest());
        assertTrue(player.getEnderChestInventory().getViewers().contains(player));
        assertTrue(viewers.contains(player));
        int id = player.getWindowId(player.getEnderChestInventory());
        assertTrue(id > 0);
        player.receiveClose(id, ContainerType.CONTAINER);
        assertTrue(viewers.isEmpty());
        assertNull(player.getViewingEnderChest());
    }

    @Test void invalidPendingRequestDoesNotCloseAnExistingHealthyScreen() {
        ChestInventory existing = chest(); int id = player.addWindow(existing);
        sent.clear(); List<Integer> results = new ArrayList<>();
        player.requestWindowOpen(shulker(), null, () -> false, results::add);
        assertEquals(List.of(-1), results);
        assertSame(existing, player.getWindowById(id));
        assertTrue(existing.getViewers().contains(player));
        assertTrue(sent.isEmpty());
    }

    @Test void oldestPreviewBoundaryKeepsCloseBeforeOpenOrder() {
        replacementClosesOldViewerAndWaitsForAckBeforeOpeningNext(588);
    }

    @Test void nativeTradePacketUsesLogicalIdAndExactWireAliasThroughCloseBarrier() {
        TradeInventory trade = trade();
        assertEquals(Player.TRADE_WINDOW_ID, player.addWindow(trade, Player.TRADE_WINDOW_ID));
        assertTrue(sent.stream().anyMatch(UpdateTradePacket.class::isInstance));
        assertSame(trade, player.getWindowById((byte) Player.TRADE_WINDOW_ID));
        sent.clear();
        player.receiveClose((byte) Player.TRADE_WINDOW_ID, ContainerType.TRADE);
        assertEquals(1, sent.stream().filter(ContainerClosePacket.class::isInstance).count());
        assertEquals(-1, player.getWindowId(trade));
        assertNull(player.getWindowById((byte) Player.TRADE_WINDOW_ID));
        trade = trade();
        assertEquals(Player.TRADE_WINDOW_ID, player.addWindow(trade, Player.TRADE_WINDOW_ID));
        player.removeWindow(trade);
        assertTrue(player.isAwaitingContainerClose((byte) Player.TRADE_WINDOW_ID));
        ChestInventory fresh = chest(); List<Integer> callback = new ArrayList<>();
        player.requestWindowOpen(fresh, null, () -> true, callback::add);
        sent.clear();
        player.receiveClose((byte) Player.TRADE_WINDOW_ID, ContainerType.TRADE);
        assertEquals(Player.TRADE_WINDOW_ID, ((ContainerClosePacket) sent.get(0)).windowId);
        assertEquals(ContainerType.TRADE, ((ContainerClosePacket) sent.get(0)).type);
        assertTrue(callback.get(0) > 0);
        assertTrue(fresh.getViewers().contains(player));
        assertNull(player.getWindowById(-12));
    }

    @Test void deniedTradeOpenReleasesRecipeIdsAndDoesNotOpenUnmappedClientUi() {
        int recipesBefore = cn.nukkit.utils.TradeRecipeBuildUtils.RECIPE_MAP.size();
        TradeInventory trade = trade();
        trade.getHolder().getRecipes().add(new cn.nukkit.nbt.tag.CompoundTag());
        MockServer.get().callDataPkSendEv = true;
        doAnswer(call -> { if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                && event.getPacket() instanceof UpdateTradePacket) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertEquals(-1, player.addWindow(trade, Player.TRADE_WINDOW_ID));
        assertFalse(trade.getViewers().contains(player));
        assertEquals(recipesBefore, cn.nukkit.utils.TradeRecipeBuildUtils.RECIPE_MAP.size());
        assertFalse(sent.stream().anyMatch(UpdateTradePacket.class::isInstance));
    }

    @Test void nativeCommandEditorsUseOneResponseAndServerCloseBarrier() {
        when(MockServer.get().isOp(any())).thenReturn(true);
        player.gamemode = Player.CREATIVE;
        for (boolean minecart : new boolean[] {false, true}) {
            Inventory command = minecart
                    ? new CommandBlockMinecartInventory(mock(cn.nukkit.entity.item.EntityMinecartCommandBlock.class))
                    : new CommandBlockInventory(mock(cn.nukkit.blockentity.BlockEntityCommandBlock.class));
            int id = player.addWindow(command);
            assertTrue(id > 0);
            sent.clear();
            player.receiveClose(id, ContainerType.COMMAND_BLOCK);
            assertEquals(1, sent.stream().filter(ContainerClosePacket.class::isInstance).count());
            assertFalse(((ContainerClosePacket) sent.get(0)).wasServerInitiated);
            id = player.addWindow(command);
            player.removeWindow(command);
            assertTrue(player.isAwaitingContainerClose(id));
            ChestInventory fresh = chest(); List<Integer> callback = new ArrayList<>();
            player.requestWindowOpen(fresh, null, () -> true, callback::add);
            player.receiveClose(id, ContainerType.COMMAND_BLOCK);
            assertTrue(callback.get(0) > 0);
            player.receiveClose(callback.get(0), ContainerType.CONTAINER);
        }
    }

    @Test void cancelledCloseAckKeepsBarrierAndQueuedRequestUntilAcceptedRetry() {
        int id = player.addWindow(chest());
        ChestInventory fresh = chest(); List<Integer> callbacks = new ArrayList<>();
        player.requestWindowOpen(fresh, null, () -> true, callbacks::add);
        MockServer.get().callDataPkSendEv = true;
        doAnswer(call -> { if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                && event.getPacket() instanceof ContainerClosePacket close && !close.wasServerInitiated) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        player.receiveClose(id, ContainerType.CONTAINER);
        assertTrue(player.isAwaitingContainerClose(id));
        assertTrue(callbacks.isEmpty());
        doNothing().when(plugins).callEvent(any(Event.class));
        player.receiveClose(id, ContainerType.CONTAINER);
        assertFalse(player.isAwaitingContainerClose(id));
        assertTrue(callbacks.get(0) > 0);
    }

    @Test void removeAllClosesRawAndPersonalScreensAndRetainsLogicalSlots() {
        player.openMain();
        player.removeAllWindows();
        assertTrue(player.isAwaitingContainerClose(0));
        assertSame(player.getInventory(), player.getWindowById(0));
        assertSame(player.getUIInventory(), player.getWindowById(ContainerIds.UI));
        assertSame(player.getCraftingGrid(), player.getWindowById(ContainerIds.NONE));
        player.receiveClose(0, ContainerType.INVENTORY);
        player.requestInventoryScreenOpen(() -> true, () -> {
            player.setCraftingGrid(player.getUIInventory().getBigCraftingGrid());
            player.craftingType = Player.CRAFTING_BIG;
            ContainerOpenPacket open = new ContainerOpenPacket(); open.windowId = -1; open.type = 1;
            player.dataPacket(open);
        }, () -> fail("raw refused"));
        player.removeAllWindows();
        assertTrue(player.isAwaitingContainerClose(-1));
        assertSame(player.getCraftingGrid(), player.getWindowById(ContainerIds.NONE));
    }

    private TradeInventory trade() {
        cn.nukkit.entity.passive.EntityVillager villager = mock(cn.nukkit.entity.passive.EntityVillager.class);
        villager.tierExpRequirement = new int[] {0};
        when(villager.getRecipes()).thenReturn(new cn.nukkit.nbt.tag.ListTag<cn.nukkit.nbt.tag.Tag>("Recipes"));
        when(villager.getNameTag()).thenReturn("villager");
        return new TradeInventory(villager);
    }

    @Test void componentCloseAndDeniedOpenRetainPermanentUiViewerOwnership() {
        assertTrue(player.getUIInventory().getViewers().contains(player));
        AnvilInventory anvil = new AnvilInventory(player.getUIInventory(), new cn.nukkit.level.Position(0, 0, 0, level));
        int id = player.addWindow(anvil, 2);
        player.receiveClose(id, ContainerType.ANVIL);
        assertTrue(player.getUIInventory().getViewers().contains(player));
        MockServer.get().callDataPkSendEv = true;
        doAnswer(call -> { if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event
                && event.getPacket() instanceof ContainerOpenPacket) event.setCancelled(); return null; })
                .when(plugins).callEvent(any(Event.class));
        assertEquals(-1, player.addWindow(new AnvilInventory(player.getUIInventory(), new cn.nukkit.level.Position(0, 0, 0, level)), 2));
        assertTrue(player.getUIInventory().getViewers().contains(player));
    }

    @Test void realDefaultWindowRegistrationKeepsMappingsWithoutOpeningAnyScreen() {
        sent.clear();
        player.registerDefaultsBeforeSpawn();
        assertSame(player.getInventory(), player.getWindowById(ContainerIds.INVENTORY));
        assertSame(player.getUIInventory(), player.getWindowById(ContainerIds.UI));
        assertSame(player.getOffhandInventory(), player.getWindowById(ContainerIds.OFFHAND));
        assertSame(player.getCraftingGrid(), player.getWindowById(ContainerIds.NONE));
        assertTrue(player.getInventory().getViewers().contains(player));
        assertTrue(player.getUIInventory().getViewers().contains(player));
        assertTrue(player.getTopWindow().isEmpty());
        assertTrue(sent.isEmpty());
        assertTrue(player.addWindow(chest()) > 0);
    }

    static Stream<Integer> protocols() { return ProtocolInfo.SUPPORTED_PROTOCOLS.stream().filter(p -> p >= 589 && p <= 2193); }

    private ChestInventory chest() {
        BlockEntityChest chest = mock(BlockEntityChest.class);
        when(chest.getLevel()).thenReturn(level);
        when(level.getBlockEntityIfLoaded(any(Vector3.class))).thenReturn(chest);
        return new ChestInventory(chest);
    }

    private ShulkerBoxInventory shulker() {
        BlockEntityShulkerBox box = mock(BlockEntityShulkerBox.class);
        when(box.getLevel()).thenReturn(level);
        when(level.getBlockEntityIfLoaded(any(Vector3.class))).thenReturn(box);
        return new ShulkerBoxInventory(box);
    }

    private static final class TestPlayer extends Player {
        TestPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            protocol(2193);
            this.spawned = this.loggedIn = this.loginVerified = true;
            this.inventory = new PlayerInventory(this);
            this.enderChestInventory = new PlayerEnderChestInventory(this);
            this.playerUIInventory = new PlayerUIInventory(this);
            this.playerUIInventory.getViewers().add(this);
            this.craftingGrid = this.playerUIInventory.getCraftingGrid();
            this.windows.put(this.inventory, ContainerIds.INVENTORY);
            this.permanentWindows.add(ContainerIds.INVENTORY);
            this.inventory.getViewers().add(this);
            this.windows.put(this.playerUIInventory, ContainerIds.UI);
            this.permanentWindows.add(ContainerIds.UI);
            this.windows.put(this.craftingGrid, ContainerIds.NONE);
        }
        void registerDefaultsBeforeSpawn() {
            this.windows.clear(); this.permanentWindows.clear(); this.topWindow = null;
            this.offhandInventory = new PlayerOffhandInventory(this);
            this.spawned = false;
            this.addDefaultWindows();
            this.spawned = true;
        }
        void protocol(int protocol) { this.protocol = protocol; this.gameVersion = GameVersion.byProtocol(protocol, false); }
        void openMain() { InteractPacket packet = new InteractPacket(); packet.action = InteractPacket.ACTION_OPEN_INVENTORY; handleDataPacket(packet); }
        void receiveClose(int id, ContainerType type) { ContainerClosePacket packet = new ContainerClosePacket(); packet.windowId = id; packet.type = type; handleDataPacket(packet); }
    }
}
