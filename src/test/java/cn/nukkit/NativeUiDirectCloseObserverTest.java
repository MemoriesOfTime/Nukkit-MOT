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
import cn.nukkit.inventory.GrindstoneInventory;
import cn.nukkit.inventory.PlayerUIComponent;
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

/** Actual network/UI custody cleanup coupled to an observer removing its window. */
class NativeUiDirectCloseObserverTest {
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

    private static Item stack(int count) { return Item.get(Item.EMERALD, 0, count); }

    private static int count(Inventory inventory) {
        return inventory.getContents().values().stream()
                .filter(item -> item.getId() == Item.EMERALD).mapToInt(Item::getCount).sum();
    }

    private void directCleanupObserverRemovesWindow(boolean grinding) {
        PlayerUIComponent station = grinding
                ? new GrindstoneInventory(player.getUIInventory(), new Position(1, 0, 1, level))
                : new AnvilInventory(player.getUIInventory(), new Position(1, 0, 1, level));
        int id = player.addWindow(station, grinding ? Player.GRINDSTONE_WINDOW_ID : Player.ANVIL_WINDOW_ID);
        assertTrue(id >= 0);
        station.setItemForce(0, stack(4));
        AtomicBoolean observerRemovedWindow = new AtomicBoolean();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityInventoryChangeEvent event
                    && event.getNewItem().getId() == Item.EMERALD
                    && observerRemovedWindow.compareAndSet(false, true)) {
                player.removeWindow(station);
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        sent.clear();
        assertDoesNotThrow(() -> station.close(player));
        assertTrue(observerRemovedWindow.get(), "Actual input return invokes the native inventory observer");
        assertEquals(4, count(player.getInventory()) + count(player.getUIInventory()) + dropped.get());
        assertTrue(station.getItem(0).isNull());
        assertEquals(-1, player.getWindowId(station));
        assertSame(player.getUIInventory().getViewers(), station.getViewers());
        assertTrue(player.getUIInventory().getViewers().contains(player));
        List<ContainerClosePacket> closes = sent.stream().filter(ContainerClosePacket.class::isInstance)
                .map(ContainerClosePacket.class::cast).toList();
        assertFalse(closes.isEmpty());
        assertTrue(closes.stream().allMatch(packet -> packet.windowId == id),
                "Reentrant removal during custody return cannot replace the real window id by -1");
    }

    @Test void directAnvilCleanupObserverRetainsValidCloseWindowId() {
        directCleanupObserverRemovesWindow(false);
    }
    @Test void directGrindstoneCleanupObserverRetainsValidCloseWindowId() {
        directCleanupObserverRemovesWindow(true);
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
    }
}
