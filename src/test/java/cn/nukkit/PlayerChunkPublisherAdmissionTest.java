package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.NetworkChunkPublisherUpdatePacket;
import cn.nukkit.network.protocol.RemoveEntityPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Publisher admission has its own submission and reentrant ordering lifetime. */
class PlayerChunkPublisherAdmissionTest {
    @Test
    void cancelledInitialPublisherIsRetriedEvenAfterAllTerrainFinished() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        f.onSend(event -> {
            if (event.getPacket() instanceof NetworkChunkPublisherUpdatePacket
                    && attempts.getAndIncrement() == 0) event.setCancelled();
        });
        assertTrue(f.player.orderChunks());
        assertTrue(f.publishers().isEmpty());
        assertFalse(f.player.loadQueue.isEmpty());
        for (long index : f.player.loadQueue) f.player.usedChunks.put(index, Boolean.TRUE);
        f.player.loadQueue.clear();

        assertTrue(f.player.orderChunks());

        assertEquals(1, f.publishers().size(),
                "failed publisher admission still needs retry when no terrain or unload remains");
    }

    @Test
    void throwingInitialPublisherRetainsRetryAfterTerrainDrains() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("publisher listener failed");
        f.onSend(event -> {
            if (event.getPacket() instanceof NetworkChunkPublisherUpdatePacket
                    && attempts.getAndIncrement() == 0) throw expected;
        });
        assertSame(expected, assertThrows(IllegalStateException.class, f.player::orderChunks));
        assertFalse(f.player.loadQueue.isEmpty());
        for (long index : f.player.loadQueue) f.player.usedChunks.put(index, Boolean.TRUE);
        f.player.loadQueue.clear();

        assertTrue(f.player.orderChunks());

        assertEquals(1, f.publishers().size(),
                "publisher exception must preserve publication demand after terrain queue drains");
    }

    @Test
    void nestedReorderPublisherCannotBeOverwrittenByOuterOldPublisher() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger events = new AtomicInteger();
        f.onSend(event -> {
            if (event.getPacket() instanceof NetworkChunkPublisherUpdatePacket
                    && events.getAndIncrement() == 0) {
                f.player.x = 1600;
                assertTrue(f.player.orderChunks());
            }
        });

        assertTrue(f.player.orderChunks());

        List<NetworkChunkPublisherUpdatePacket> publishers = f.publishers();
        assertFalse(publishers.isEmpty());
        assertEquals(1600, publishers.get(publishers.size() - 1).position.x,
                "latest accepted publisher must describe nested current ordering, not outer old center");
    }

    @Test
    void unloadEntityPacketWorldSwitchCannotRemoveDestinationLoadQueueEntry() throws Exception {
        Fixture f = new Fixture();
        Level destination = mock(Level.class);
        when(destination.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
        long coordinate = Level.chunkHash(100, 0);
        f.player.usedChunks.put(coordinate, true);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        field(entity, "hasSpawned", new HashMap<>(Map.of(1, f.player)));
        when(f.original.getChunkEntities(100, 0, false)).thenReturn(Map.of(1L, entity));
        AtomicInteger removes = new AtomicInteger();
        AtomicInteger nestedSize = new AtomicInteger();
        f.onSend(event -> {
            if (event.getPacket() instanceof RemoveEntityPacket && removes.getAndIncrement() == 0) {
                f.player.preSwitchLevel();
                f.player.level = destination;
                f.player.x = 1600;
                assertTrue(f.player.orderChunks());
                assertTrue(f.player.loadQueue.contains(coordinate), "real destination order owns its center");
                nestedSize.set(f.player.loadQueue.size());
            }
        });

        assertTrue(f.player.orderChunks());

        assertSame(destination, f.player.level);
        assertTrue(removes.get() > 0, "must pass real Entity.despawnFrom packet event");
        assertTrue(f.player.loadQueue.contains(coordinate),
                "old-world unload continuation must not remove newly ordered destination center");
        assertEquals(nestedSize.get(), f.player.loadQueue.size(),
                "old order must preserve destination load queue after nested switch/reorder");
    }

    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final Level original = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final NetworkPlayerSession network = mock(NetworkPlayerSession.class);
        final List<DataPacket> admitted = new ArrayList<>();

        Fixture() throws Exception {
            field(player, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
            field(player, "loadQueue", new LongLinkedOpenHashSet());
            field(player, "pendingChunkRetries", Player.newPendingRetryQueue());
            field(player, "server", server);
            field(player, "networkSession", network);
            field(player, "gameVersion", GameVersion.V1_21_70);
            player.level = original;
            player.connected = true;
            player.spawned = true;
            player.protocol = GameVersion.V1_21_70.getProtocol();
            player.chunkRadius = 3;
            player.viewDistance = 12;
            when(player.getLoaderId()).thenReturn(1);
            server.callDataPkSendEv = true;
            when(server.getPluginManager()).thenReturn(plugins);
            when(original.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
            doAnswer(call -> { admitted.add(call.getArgument(0)); return null; })
                    .when(network).sendPacket(any(DataPacket.class));
        }

        void onSend(Consumer<DataPacketSendEvent> listener) {
            doAnswer(call -> {
                Event event = call.getArgument(0);
                if (event instanceof DataPacketSendEvent send) listener.accept(send);
                return null;
            }).when(plugins).callEvent(any(Event.class));
        }

        List<NetworkChunkPublisherUpdatePacket> publishers() {
            return admitted.stream().filter(NetworkChunkPublisherUpdatePacket.class::isInstance)
                    .map(NetworkChunkPublisherUpdatePacket.class::cast).toList();
        }
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
