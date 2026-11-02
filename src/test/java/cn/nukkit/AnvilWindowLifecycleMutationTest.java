package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.inventory.AnvilInventory;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.FakeBlockMenu;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Public #976 window lifecycle coupled to the actual #1120 transaction/UI ownership. */
class AnvilWindowLifecycleMutationTest {
    private TestPlayer player;
    private Level level;
    private PluginManager plugins;
    private List<DataPacket> sent;
    private AtomicInteger dropped;

    @BeforeEach void setup() {
        MockServer.reset();
        Server server = MockServer.get();
        plugins = server.getPluginManager();
        when(server.isPrimaryThread()).thenReturn(true);
        level = mock(Level.class);
        VibrationManager vibrations = mock(VibrationManager.class);
        when(level.getVibrationManager()).thenReturn(vibrations);
        when(server.getDefaultLevel()).thenReturn(level);
        SourceInterface source = mock(SourceInterface.class);
        NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(session);
        sent = new ArrayList<>();
        doAnswer(call -> { sent.add(call.getArgument(0)); return null; })
                .when(session).sendPacket(any(DataPacket.class));
        dropped = new AtomicInteger();
        doAnswer(call -> {
            Item item = call.getArgument(1);
            if (item.getId() == Item.EMERALD) dropped.addAndGet(item.getCount());
            return null;
        }).when(level).dropItem(any(Vector3.class), any(Item.class));
        player = new TestPlayer(source);
    }

    private enum Outcome { ACCEPTED, REFUSED, ERROR }
    private enum ClosePath { CLIENT_PACKET, REPLACEMENT_REQUEST }
    private enum OpenOutcome { ACCEPTED, REFUSED, ERROR }

    private static Item stack(int count) { return Item.get(Item.EMERALD, 0, count); }

    private static int count(Inventory inventory) {
        return inventory.getContents().values().stream()
                .filter(item -> item.getId() == Item.EMERALD).mapToInt(Item::getCount).sum();
    }

    private void transfer(int protocol, ClosePath closePath, Outcome outcome, OpenOutcome openOutcome) {
        player.protocol(protocol);
        AnvilInventory anvil = new AnvilInventory(player.getUIInventory(), new Position(1, 0, 1, level));
        int oldId = player.addWindow(anvil, Player.ANVIL_WINDOW_ID);
        assertEquals(Player.ANVIL_WINDOW_ID, oldId);
        anvil.setItemForce(AnvilInventory.TARGET, stack(4));
        ContainerInventory fresh = new ContainerInventory(
                new FakeBlockMenu(null, new Position(2, 0, 2, level)), InventoryType.CHEST) { };
        List<Integer> openedIds = new ArrayList<>();
        AtomicBoolean requested = new AtomicBoolean();
        AtomicBoolean inDestinationCallback = new AtomicBoolean();
        AtomicBoolean sourceOutcomeApplied = new AtomicBoolean();
        AtomicInteger idDuringCallback = new AtomicInteger(-1);
        AtomicInteger inputDuringCallback = new AtomicInteger(-1);
        AtomicInteger closePacketsDuringCallback = new AtomicInteger(-1);
        AssertionError sourceError = new AssertionError("source callback error after destination credit");
        AssertionError openError = new AssertionError("new window open callback error");
        sent.clear();
        doAnswer(call -> {
            Event raw = call.getArgument(0);
            if (raw instanceof EntityInventoryChangeEvent event) {
                if (event.getSlot() == 4 && event.getNewItem().getId() == Item.EMERALD
                        && requested.compareAndSet(false, true)) {
                    inDestinationCallback.set(true);
                    try {
                        if (closePath == ClosePath.CLIENT_PACKET) player.receiveClose(oldId, ContainerType.ANVIL);
                        else player.requestWindowOpen(fresh, null, () -> true, openedIds::add);
                        idDuringCallback.set(player.getWindowId(anvil));
                        inputDuringCallback.set(anvil.getInputSlot().getCount());
                        closePacketsDuringCallback.set((int) sent.stream()
                                .filter(ContainerClosePacket.class::isInstance).count());
                    } finally {
                        inDestinationCallback.set(false);
                    }
                }
                if (!inDestinationCallback.get() && event.getSlot() == AnvilInventory.ANVIL_INPUT_UI_SLOT
                        && event.getNewItem().isNull() && outcome != Outcome.ACCEPTED
                        && sourceOutcomeApplied.compareAndSet(false, true)) {
                    if (outcome == Outcome.ERROR) throw sourceError;
                    event.setCancelled();
                }
            }
            if (raw instanceof InventoryOpenEvent event && event.getInventory() == fresh) {
                if (openOutcome == OpenOutcome.ERROR) throw openError;
                if (openOutcome == OpenOutcome.REFUSED) event.setCancelled();
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        InventoryTransaction transaction = new InventoryTransaction(player, List.of(
                new SlotChangeAction(player.getInventory(), 4, Item.AIR_ITEM.clone(), stack(4)),
                new SlotChangeAction(anvil, AnvilInventory.TARGET, stack(4), Item.AIR_ITEM.clone())));
        assertTrue(transaction.canExecute());
        if (outcome == Outcome.ERROR) {
            assertSame(sourceError, assertThrows(AssertionError.class, transaction::execute));
        } else {
            assertEquals(outcome == Outcome.ACCEPTED, assertDoesNotThrow(transaction::execute));
        }
        assertTrue(requested.get(), "actual native slot callback must run");
        if (outcome != Outcome.ACCEPTED) assertTrue(sourceOutcomeApplied.get());
        assertEquals(oldId, idDuringCallback.get(), "owned close must preserve the old window mapping");
        assertEquals(4, inputDuringCallback.get(), "close must not consume the transaction's provisional source");
        assertEquals(0, closePacketsDuringCallback.get(), "ACK/close must wait for the owner to finish");
        assertEquals(-1, player.getWindowId(anvil));
        assertTrue(anvil.getInputSlot().isNull());
        assertEquals(4, count(player.getInventory()) + count(player.getUIInventory()) + count(fresh) + dropped.get(),
                "accepted, refused and error paths must keep exactly the original four items");
        List<ContainerClosePacket> closes = sent.stream().filter(ContainerClosePacket.class::isInstance)
                .map(ContainerClosePacket.class::cast).toList();
        assertFalse(closes.isEmpty());
        assertTrue(closes.stream().allMatch(packet -> packet.windowId == oldId),
                "mapping removal must not replace the real close window id with -1");
        if (closePath == ClosePath.REPLACEMENT_REQUEST) {
            assertTrue(player.isAwaitingContainerClose(oldId));
            assertTrue(openedIds.isEmpty(), "new window cannot open before old close ACK");
            assertFalse(fresh.getViewers().contains(player));
            sent.clear();
            assertDoesNotThrow(() -> player.receiveClose(oldId, ContainerType.ANVIL));
            if (openOutcome == OpenOutcome.ACCEPTED) {
                assertEquals(1, openedIds.size());
                assertTrue(openedIds.get(0) > 0);
                assertSame(fresh, player.getWindowById(openedIds.get(0)));
                assertTrue(fresh.getViewers().contains(player));
                assertTrue(sent.stream().anyMatch(ContainerOpenPacket.class::isInstance));
            } else {
                assertEquals(List.of(-1), openedIds);
                assertEquals(-1, player.getWindowId(fresh));
                assertFalse(fresh.getViewers().contains(player));
            }
            player.receiveClose(oldId, ContainerType.ANVIL);
            assertEquals(1, openedIds.size(), "late ACK must not repeat an open completion");
        } else {
            assertFalse(player.isAwaitingContainerClose(oldId));
            assertFalse(closes.get(0).wasServerInitiated);
        }
        assertEquals(4, count(player.getInventory()) + count(player.getUIInventory()) + count(fresh) + dropped.get());
        try (InventoryMutationGuard.Lease lease = InventoryMutationGuard.tryClaim(
                List.of(player.getInventory(), player.getUIInventory(), anvil))) {
            assertNotNull(lease, "close/error recovery must release every aliased ownership claim");
        }
    }

    @ParameterizedTest @ValueSource(ints = {589, 685, 2193})
    void closePacketDuringAcceptedTransferWaitsForNativeOwner(int protocol) {
        transfer(protocol, ClosePath.CLIENT_PACKET, Outcome.ACCEPTED, OpenOutcome.ACCEPTED);
    }
    @Test void closePacketDuringRefusedTransferReturnsOnlyOriginalItems() {
        transfer(2193, ClosePath.CLIENT_PACKET, Outcome.REFUSED, OpenOutcome.ACCEPTED);
    }
    @Test void closePacketDuringErrorRollbackReturnsOnlyOriginalItems() {
        transfer(2193, ClosePath.CLIENT_PACKET, Outcome.ERROR, OpenOutcome.ACCEPTED);
    }
    @ParameterizedTest @ValueSource(ints = {589, 685, 2193})
    void replacementDuringAcceptedTransferWaitsForOwnerThenCloseAck(int protocol) {
        transfer(protocol, ClosePath.REPLACEMENT_REQUEST, Outcome.ACCEPTED, OpenOutcome.ACCEPTED);
    }
    @Test void replacementDuringRefusedTransferReturnsOnlyOriginalItems() {
        transfer(2193, ClosePath.REPLACEMENT_REQUEST, Outcome.REFUSED, OpenOutcome.ACCEPTED);
    }
    @Test void replacementDuringErrorRollbackReturnsOnlyOriginalItems() {
        transfer(2193, ClosePath.REPLACEMENT_REQUEST, Outcome.ERROR, OpenOutcome.ACCEPTED);
    }
    @Test void refusedNewWindowCompletesOnceWithoutLosingItems() {
        transfer(2193, ClosePath.REPLACEMENT_REQUEST, Outcome.ACCEPTED, OpenOutcome.REFUSED);
    }
    @Test void throwingNewWindowCompletesOnceWithoutLosingItems() {
        transfer(2193, ClosePath.REPLACEMENT_REQUEST, Outcome.ACCEPTED, OpenOutcome.ERROR);
    }

    private void directCloseDuringTransfer(Outcome outcome) {
        AnvilInventory anvil = new AnvilInventory(player.getUIInventory(), new Position(1, 0, 1, level));
        int oldId = player.addWindow(anvil, Player.ANVIL_WINDOW_ID);
        assertEquals(Player.ANVIL_WINDOW_ID, oldId);
        anvil.setItemForce(AnvilInventory.TARGET, stack(4));
        AtomicBoolean requested = new AtomicBoolean();
        AtomicBoolean insideDirectClose = new AtomicBoolean();
        AtomicBoolean sourceOutcomeApplied = new AtomicBoolean();
        AtomicInteger idDuringCallback = new AtomicInteger(-1);
        AtomicInteger packetsDuringCallback = new AtomicInteger(-1);
        AssertionError original = new AssertionError("source error following direct public close");
        sent.clear();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityInventoryChangeEvent event) {
                if (event.getSlot() == 4 && event.getNewItem().getId() == Item.EMERALD
                        && requested.compareAndSet(false, true)) {
                    insideDirectClose.set(true);
                    try {
                        anvil.close(player);
                        idDuringCallback.set(player.getWindowId(anvil));
                        packetsDuringCallback.set((int) sent.stream()
                                .filter(ContainerClosePacket.class::isInstance).count());
                    } finally { insideDirectClose.set(false); }
                }
                if (!insideDirectClose.get() && event.getSlot() == AnvilInventory.ANVIL_INPUT_UI_SLOT
                        && event.getNewItem().isNull() && outcome != Outcome.ACCEPTED
                        && sourceOutcomeApplied.compareAndSet(false, true)) {
                    if (outcome == Outcome.ERROR) throw original;
                    event.setCancelled();
                }
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        InventoryTransaction tx = new InventoryTransaction(player, List.of(
                new SlotChangeAction(player.getInventory(), 4, Item.AIR_ITEM.clone(), stack(4)),
                new SlotChangeAction(anvil, AnvilInventory.TARGET, stack(4), Item.AIR_ITEM.clone())));
        if (outcome == Outcome.ERROR) assertSame(original, assertThrows(AssertionError.class, tx::execute));
        else assertEquals(outcome == Outcome.ACCEPTED, assertDoesNotThrow(tx::execute));
        assertTrue(requested.get());
        assertEquals(oldId, idDuringCallback.get(), "Direct close retains the claimed window until native rollback/commit");
        assertEquals(0, packetsDuringCallback.get(), "Direct close cannot publish an early close while its UI/main are owned");
        assertEquals(4, count(player.getInventory()) + count(player.getUIInventory()) + dropped.get());
        assertTrue(anvil.getInputSlot().isNull());
        assertSame(player.getUIInventory().getViewers(), anvil.getViewers(),
                "Native UI components share the permanent backing viewers after their window retires");
        assertEquals(-1, player.getWindowId(anvil));
        assertTrue(player.getUIInventory().getViewers().contains(player));
        List<ContainerClosePacket> closes = sent.stream().filter(ContainerClosePacket.class::isInstance)
                .map(ContainerClosePacket.class::cast).toList();
        assertFalse(closes.isEmpty());
        assertTrue(closes.stream().allMatch(packet -> packet.windowId == oldId));
        try (InventoryMutationGuard.Lease lease = InventoryMutationGuard.tryClaim(
                List.of(player.getInventory(), player.getUIInventory(), anvil))) {
            assertNotNull(lease);
        }
    }

    @Test void directCloseDuringAcceptedTransferWaitsForNativeOwner() {
        directCloseDuringTransfer(Outcome.ACCEPTED);
    }
    @Test void directCloseDuringRefusedTransferReturnsOnlyOriginalItems() {
        directCloseDuringTransfer(Outcome.REFUSED);
    }
    @Test void directCloseDuringErrorRollbackReturnsOnlyOriginalItems() {
        directCloseDuringTransfer(Outcome.ERROR);
    }
    @Test void ordinaryDirectClosePreservesItemsAndRetiresViewerAndWindow() {
        AnvilInventory anvil = new AnvilInventory(player.getUIInventory(), new Position(1, 0, 1, level));
        int id = player.addWindow(anvil, Player.ANVIL_WINDOW_ID);
        anvil.setItemForce(AnvilInventory.TARGET, stack(4));
        sent.clear();
        assertDoesNotThrow(() -> anvil.close(player));
        assertEquals(4, count(player.getInventory()) + count(player.getUIInventory()) + dropped.get());
        assertTrue(anvil.getInputSlot().isNull());
        assertSame(player.getUIInventory().getViewers(), anvil.getViewers(),
                "Native UI components share the permanent backing viewers after their window retires");
        assertEquals(-1, player.getWindowId(anvil));
        assertTrue(player.getUIInventory().getViewers().contains(player));
        List<ContainerClosePacket> closes = sent.stream().filter(ContainerClosePacket.class::isInstance)
                .map(ContainerClosePacket.class::cast).toList();
        assertFalse(closes.isEmpty());
        assertTrue(closes.stream().allMatch(packet -> packet.windowId == id));
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
        void protocol(int protocol) {
            this.protocol = protocol;
            this.gameVersion = GameVersion.byProtocol(protocol, false);
        }
        void receiveClose(int id, ContainerType type) {
            ContainerClosePacket packet = new ContainerClosePacket();
            packet.windowId = id;
            packet.type = type;
            handleDataPacket(packet);
        }
    }
}
