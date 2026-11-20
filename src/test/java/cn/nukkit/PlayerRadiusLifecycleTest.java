package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.network.process.processor.common.RequestChunkRadiusProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.NetworkChunkPublisherUpdatePacket;
import cn.nukkit.network.protocol.RequestChunkRadiusPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerRadiusLifecycleTest {
    @Test
    void clientCanGrowAgainAfterShrinkingWithinConfiguredLimit() throws Exception {
        Fixture f = new Fixture();
        f.requestRadius(3);
        assertEquals(3, f.player.getViewDistance());
        f.requestRadius(8);
        assertEquals(8, f.player.getViewDistance(),
                "client radius changes must clamp against configured maximum, not previous request");
    }

    @Test
    void fullyDeliveredRadiusShrinkStillPublishesNewRadius() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.player.orderChunks());
        for (long index : f.player.loadQueue) f.player.usedChunks.put(index, Boolean.TRUE);
        f.player.loadQueue.clear();
        f.sent.clear();
        f.requestRadius(3);
        assertTrue(f.player.orderChunks());
        assertTrue(f.player.loadQueue.isEmpty(), "all retained chunks already delivered");
        List<NetworkChunkPublisherUpdatePacket> publishers = f.sent.stream()
                .filter(NetworkChunkPublisherUpdatePacket.class::isInstance)
                .map(NetworkChunkPublisherUpdatePacket.class::cast).toList();
        assertEquals(1, publishers.size(), "publisher radius must not depend on pending terrain");
        assertEquals(3 << 4, publishers.get(0).radius);
    }

    @Test
    void fullyDeliveredUnchangedViewDoesNotSendIdlePublisherUpdates() throws Exception {
        Fixture f = new Fixture();
        f.player.orderChunks();
        for (long index : f.player.loadQueue) f.player.usedChunks.put(index, Boolean.TRUE);
        f.player.loadQueue.clear();
        f.sent.clear();
        f.player.orderChunks();
        assertTrue(f.sent.stream().noneMatch(NetworkChunkPublisherUpdatePacket.class::isInstance));
    }

    @Test
    void maximumRemainsHostControlledAfterClientShrink() throws Exception {
        Fixture f = new Fixture();
        f.player.setViewDistance(6);
        f.requestRadius(3);
        f.requestRadius(99);
        assertEquals(6, f.player.getViewDistance(), "host limit survives smaller client radius");
    }

    private static final class Fixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final Level level = mock(Level.class);
        final List<DataPacket> sent = new ArrayList<>();

        Fixture() throws Exception {
            field(player, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
            field(player, "loadQueue", new LongLinkedOpenHashSet());
            field(player, "server", server);
            player.level = level;
            player.connected = true;
            player.spawned = true;
            player.protocol = 748;
            player.viewDistance = 12;
            player.chunkRadius = 12;
            when(level.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
            doAnswer(call -> {
                sent.add(call.getArgument(0));
                return true;
            }).when(player).dataPacket(any(DataPacket.class));
            doCallRealMethod().when(player).orderChunks();
            doCallRealMethod().when(player).getViewDistance();
            doCallRealMethod().when(player).setViewDistance(anyInt());
            doCallRealMethod().when(player).unloadChunk(anyInt(), anyInt());
            doCallRealMethod().when(player).unloadChunk(anyInt(), anyInt(), nullable(Level.class));
        }

        void requestRadius(int radius) {
            RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
            packet.radius = radius;
            RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        }
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
