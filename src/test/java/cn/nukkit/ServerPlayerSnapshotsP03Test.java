package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.server.ServerTickEndEvent;
import cn.nukkit.event.server.ServerTickStartEvent;
import cn.nukkit.level.Level;
import cn.nukkit.network.Network;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerPlayerSnapshotsP03Test {
    @Test
    void updateSnapshotFinishesRemovedMembersAndLaterPhasesSeeNewMembership() throws Exception {
        Fixture f = new Fixture(true);
        Player a = f.add(1, true, true), b = f.add(2, true, true), added = mock(Player.class);
        Player first = f.connected.values().iterator().next(); Player removed = first == a ? b : a;
        doAnswer(invocation -> {
            f.connected.values().remove(removed); f.online.values().remove(removed);
            f.connected.put(address(3), added); f.online.put(uuid(3), added); return false;
        }).when(first).onUpdate(1);
        f.tick();
        verify(a).onUpdate(1); verify(b).onUpdate(1); verify(added, never()).onUpdate(anyInt());
        verify(removed, never()).resetPacketCounters(); verify(added).resetPacketCounters();
        verify(removed, never()).checkNetwork(); verify(first).checkNetwork(); verify(added).checkNetwork();
        verify(f.server).getOnlinePlayers(); f.verifyEvents();
    }

    @Test
    void networkSnapshotFinishesRemovedMembersAndDefersAdditions() throws Exception {
        Fixture f = new Fixture(false);
        Player a = f.add(1, true, true), b = f.add(2, true, true), added = mock(Player.class);
        Player first = f.connected.values().iterator().next(); Player removed = first == a ? b : a;
        doAnswer(invocation -> {
            f.connected.values().remove(removed); f.online.values().remove(removed);
            f.connected.put(address(3), added); f.online.put(uuid(3), added); return null;
        }).when(first).checkNetwork();
        f.tick();
        verify(a).resetPacketCounters(); verify(b).resetPacketCounters(); verify(added, never()).resetPacketCounters();
        verify(a).checkNetwork(); verify(b).checkNetwork(); verify(added, never()).checkNetwork();
        doNothing().when(first).checkNetwork();
        f.tick();
        verify(first, times(2)).checkNetwork(); verify(removed, times(1)).checkNetwork(); verify(added).checkNetwork();
        verify(added).resetPacketCounters(); verify(f.server, times(2)).getOnlinePlayers();
        verify(f.plugins, times(4)).callEvent(any(Event.class));
    }

    @Test
    void connectedAndOnlineCollectionsKeepTheirSeparateCallbackRoles() throws Exception {
        Fixture f = new Fixture(true);
        Player connectedOnly = f.add(1, true, false), onlineOnly = f.add(2, false, true);
        f.tick();
        verify(connectedOnly).onUpdate(1); verify(connectedOnly, never()).resetPacketCounters(); verify(connectedOnly).checkNetwork();
        verify(onlineOnly, never()).onUpdate(anyInt()); verify(onlineOnly).resetPacketCounters(); verify(onlineOnly, never()).checkNetwork();
        verify(f.server).getOnlinePlayers(); f.verifyEvents();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nullConnectedMemberKeepsFailureAndFinallyDispatch(boolean alwaysTick) throws Exception {
        Fixture f = new Fixture(alwaysTick); f.connected.put(address(1), null);
        assertThrows(NullPointerException.class, f::tick);
        f.verifyEvents();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void invalidRawMemberKeepsPerElementCastTiming(boolean alwaysTick) throws Exception {
        Fixture f = new Fixture(alwaysTick); f.add(1, true, true); f.add(2, true, true);
        Player first = f.connected.values().iterator().next();
        InetSocketAddress later = null;
        for (Map.Entry<InetSocketAddress, Player> e : f.connected.entrySet()) if (e.getValue() != first) later = e.getKey();
        assertNotNull(later); ((Map) f.connected).put(later, "invalid member");
        assertThrows(ClassCastException.class, f::tick);
        if (alwaysTick) verify(first).onUpdate(1); else verify(first).checkNetwork();
        f.verifyEvents();
    }

    private static InetSocketAddress address(int id) { return InetSocketAddress.createUnresolved("p03-test-" + id, 10000 + id); }
    private static UUID uuid(int id) { return new UUID(0, id); }
    private static final class Fixture {
        final Server server = mock(Server.class, CALLS_REAL_METHODS);
        final PluginManager plugins = mock(PluginManager.class);
        final Map<InetSocketAddress, Player> connected = new HashMap<>();
        final Map<UUID, Player> online = new HashMap<>();
        Fixture(boolean alwaysTick) throws Exception {
            set("network", mock(Network.class)); set("scheduler", mock(ServerScheduler.class)); set("pluginManager", plugins);
            set("players", connected); set("playerList", online); set("levelArray", new Level[0]);
            set("pendingPlayerCloses", new ConcurrentHashMap<>());
            set("tickAverage", new float[20]); set("useAverage", new float[20]); set("alwaysTickPlayers", alwaysTick);
            set("autoSave", false); set("autoSaveTicks", Integer.MAX_VALUE);
        }
        Player add(int id, boolean connect, boolean list) {
            Player p = mock(Player.class); if (connect) connected.put(address(id), p); if (list) online.put(uuid(id), p); return p;
        }
        void tick() { server.tick(server.getNextTick(), () -> 0L); }
        void verifyEvents() {
            verify(plugins).callEvent(isA(ServerTickStartEvent.class)); verify(plugins).callEvent(isA(ServerTickEndEvent.class));
        }
        void set(String name, Object value) throws Exception { Field f=Server.class.getDeclaredField(name); f.setAccessible(true); f.set(server,value); }
    }
}
