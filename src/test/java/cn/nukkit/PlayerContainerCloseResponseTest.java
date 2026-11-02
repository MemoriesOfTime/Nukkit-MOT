package cn.nukkit;

import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.FakeBlockMenu;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.CraftingGrid;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import cn.nukkit.network.protocol.ProtocolInfo;
import java.util.stream.Stream;

import java.net.InetSocketAddress;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerContainerCloseResponseTest {
    private TestPlayer player;

    @BeforeEach
    void setup() {
        MockServer.reset();
        Server server = MockServer.get();
        Level playerLevel = mock(Level.class);
        when(playerLevel.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(server.getDefaultLevel()).thenReturn(playerLevel);
        SourceInterface source = mock(SourceInterface.class);
        NetworkPlayerSession session = mock(NetworkPlayerSession.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(session);
        player = new TestPlayer(source);
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void serverCloseAcknowledgementReceivesTheResponseTheClientWaitsFor(int protocol) {
        player.protocol(protocol);
        Inventory window = window(null);
        player.register(window, 41);
        player.removeWindow(window);
        assertEquals(1, player.closes.size());
        assertTrue(player.closes.get(0).wasServerInitiated);
        assertNull(player.getWindowById(41));
        player.closes.clear();

        player.receiveClose(41);

        assertEquals(1, player.closes.size(), "without the response a 1.21.70 client cannot open its inventory");
        assertEquals(41, player.closes.get(0).windowId);
        assertFalse(player.closes.get(0).wasServerInitiated);
    }

    @Test
    void serverCloseAcknowledgementDoesNotAnswerWhileAFreshWindowIsOpen() {
        Inventory previous = window(null);
        player.register(previous, 41);
        player.removeWindow(previous);
        Inventory fresh = window(null);
        player.register(fresh, 42);
        player.closes.clear();

        player.receiveClose(41);

        assertSame(fresh, player.getWindowById(42));
        assertTrue(player.closes.isEmpty(), "a late response must not shut the fresh window");
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void genuineKnownWindowCloseStillReceivesItsResponse(int protocol) {
        player.protocol(protocol);
        Inventory window = window(null);
        player.register(window, 42);

        player.receiveClose(42);

        assertNull(player.getWindowById(42));
        assertEquals(1, player.closes.size());
        ContainerClosePacket response = player.closes.get(0);
        assertEquals(42, response.windowId);
        assertFalse(response.wasServerInitiated);
        assertEquals(ContainerType.CONTAINER, response.type);
        response.protocol = protocol;
        response.encode();
        assertArrayEquals(protocol >= 685 ? new byte[] {47, 42, 0, 0} : new byte[] {47, 42, 0},
                response.getBuffer(), "the known-close wire shape stays valid for this protocol");
        verify(MockServer.get().getPluginManager()).callEvent(any(InventoryCloseEvent.class));
    }

    @Test
    void lateUnknownIdDoesNotCloseFreshWindowOrBlockItsLegitimateClose() {
        Inventory fresh = window(null);
        player.register(fresh, 43);
        player.receiveClose(41);
        assertSame(fresh, player.getWindowById(43));
        assertTrue(player.closes.isEmpty());
        player.receiveClose(43);
        assertNull(player.getWindowById(43));
        assertEquals(1, player.closes.size());
    }

    @Test
    void legitimateCloseOfReopenedSameIdIsNotSwallowed() {
        Inventory previous = window(null);
        player.register(previous, 41);
        player.removeWindow(previous);
        player.closes.clear();
        Inventory fresh = window(null);
        player.register(fresh, 41);
        player.receiveClose(41);
        assertNull(player.getWindowById(41));
        assertEquals(1, player.closes.size());
        assertFalse(player.closes.get(0).wasServerInitiated);
    }

    @Test
    void duplicateNoneCloseWithNoUiHasNoResponse() {
        player.receiveClose(-1);
        player.receiveClose(-1);
        assertTrue(player.closes.isEmpty());
    }

    @Test
    void unknownNoneCloseDoesNotTouchPendingFakeWindow() {
        Inventory fake = window(new FakeBlockMenu(null, new Position(1, 2, 3, player.level)));
        player.register(fake, 5);
        player.receiveClose(-1);
        player.receiveClose(-1);
        assertSame(fake, player.getWindowById(5));
        assertTrue(player.closes.isEmpty());
    }

    @Test
    void genuineMinusOneWorldCloseRespondsOnceAndDuplicateDoesNot() {
        Level level = mock(Level.class);
        BlockEntityChest chest = mock(BlockEntityChest.class);
        when(chest.getLevel()).thenReturn(level);
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        when(level.getBlockEntityIfLoaded(any(Vector3.class))).thenReturn(chest);
        Inventory world = window(chest);
        player.register(world, 9);

        player.receiveClose(-1);
        assertNull(player.getWindowById(9));
        assertEquals(1, player.closes.size());
        assertEquals(-1, player.closes.get(0).windowId);
        assertFalse(player.closes.get(0).wasServerInitiated);
        player.closes.clear();
        player.receiveClose(-1);
        assertTrue(player.closes.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void mainInventoryIdZeroCloseIsStillAcknowledgedAndPermanentWindowRetained(int protocol) {
        player.protocol(protocol);
        PlayerInventory main = new PlayerInventory(player);
        player.register(main, 0);
        player.permanentWindows.add(0);
        player.inventoryOpen(true);
        player.receiveClose(0);
        assertFalse(player.inventoryOpen());
        assertSame(main, player.getWindowById(0));
        assertEquals(1, player.closes.size());
        assertEquals(0, player.closes.get(0).windowId);
        assertFalse(player.closes.get(0).wasServerInitiated);
    }

    @Test
    void mainInventoryMinusOneCloseKeepsPermanentMainAndAcknowledges() {
        PlayerInventory main = new PlayerInventory(player);
        player.register(main, 0);
        player.permanentWindows.add(0);
        player.inventoryOpen(true);
        player.receiveClose(-1);
        assertFalse(player.inventoryOpen());
        assertSame(main, player.getWindowById(0));
        assertEquals(1, player.closes.size());
        assertEquals(-1, player.closes.get(0).windowId);
        assertFalse(player.closes.get(0).wasServerInitiated);
    }

    @Test
    void staleCloseDoesNotClearPendingForms() {
        FormWindowSimple pending = new FormWindowSimple("pending", "pending");
        player.formWindows.put(7, pending);
        player.receiveClose(41);
        player.receiveClose(-1);
        assertSame(pending, player.formWindows.get(7));
        assertEquals(1, player.closes.size(), "only the positive id is answered, the NONE close is not");
        assertEquals(41, player.closes.get(0).windowId);
    }

    static Stream<Integer> protocols() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream().filter(protocol -> protocol >= 589 && protocol <= 2193);
    }

    private static Inventory window(cn.nukkit.inventory.InventoryHolder holder) {
        return new ContainerInventory(holder, InventoryType.CHEST) { };
    }

    private static final class TestPlayer extends Player {
        final List<ContainerClosePacket> closes = new ArrayList<>();

        TestPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            protocol(2193);
            this.spawned = this.loggedIn = this.loginVerified = true;
            this.craftingType = CRAFTING_SMALL;
            this.craftingGrid = mock(CraftingGrid.class);
        }

        void protocol(int protocol) {
            this.protocol = protocol;
            this.gameVersion = GameVersion.byProtocol(protocol, false);
        }

        void inventoryOpen(boolean open) {
            try {
                Field field = Player.class.getDeclaredField("inventoryOpen");
                field.setAccessible(true);
                field.setBoolean(this, open);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        boolean inventoryOpen() {
            try {
                Field field = Player.class.getDeclaredField("inventoryOpen");
                field.setAccessible(true);
                return field.getBoolean(this);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        void register(Inventory window, int id) {
            this.windows.forcePut(window, id);
            this.topWindow = window;
            window.getViewers().add(this);
        }

        void receiveClose(int id) {
            ContainerClosePacket request = new ContainerClosePacket();
            request.windowId = id;
            request.type = ContainerType.NONE;
            handleDataPacket(request);
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            if (packet instanceof ContainerClosePacket close) closes.add(close);
            return true;
        }

        @Override
        public void resetCraftingGridType() { }
    }
}
