package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkSendRecipientTest {
    private Level level;
    private LevelProvider provider;
    private BaseFullChunk chunk;
    private Player player;
    private Server server;
    private final long hash = Level.chunkHash(3, 5);

    @BeforeEach
    void setUp() throws Exception {
        level = mock(Level.class, CALLS_REAL_METHODS);
        provider = mock(LevelProvider.class);
        server = mock(Server.class);
        when(server.getTick()).thenReturn(1);
        set(Level.class, level, "server", server);
        set(Level.class, level, "provider", provider);
        for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"}) {
            set(Level.class, level, name, new Object2ObjectOpenHashMap<>());
        }
        set(Level.class, level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
        chunk = mock(BaseFullChunk.class);
        doReturn(chunk).when(level).getChunk(3, 5);
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);
        doReturn(0).when(level).getDimension();
        player = mock(Player.class);
        set(Player.class, player, "usedChunks", new HashMap<Long, Boolean>());
        player.usedChunks.put(hash, false);
        when(player.getLoaderId()).thenReturn(1);
        when(player.getGameVersion()).thenReturn(GameVersion.V1_20_0);
        when(player.isConnected()).thenReturn(true);
        when(player.getLevel()).thenReturn(level);
    }

    @Test
    void removesOtherWorldRequestBeforeSnapshotOrDiskLookup() throws Exception {
        level.requestChunk(3, 5, player);
        when(player.getLevel()).thenReturn(mock(Level.class));
        process();
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt());
        verify(level, never()).getChunk(3, 5);
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @Test
    void removesDisconnectedRequestBeforeSnapshot() throws Exception {
        level.requestChunk(3, 5, player);
        when(player.isConnected()).thenReturn(false);
        process();
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt());
        verify(level, never()).getChunk(3, 5);
    }

    @Test
    void removesUnsubscribedRequestBeforeSnapshot() throws Exception {
        level.requestChunk(3, 5, player);
        player.usedChunks.clear();
        process();
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateCallbackNeverSendsSameCoordinatesIntoAnotherWorld(boolean cached) throws Exception {
        server.cacheChunks = cached;
        level.requestChunk(3, 5, player);
        process();
        when(player.getLevel()).thenReturn(mock(Level.class));
        try (var packets = mockStatic(Player.class)) {
            packets.when(() -> Player.getChunkCacheFromData(any(GameVersion.class), anyInt(),
                    anyInt(), anyInt(), any(byte[].class), anyInt())).thenReturn(new BatchPacket());
            level.chunkRequestCallback(GameVersion.V1_20_0, 0, 3, 5, 1, new byte[]{0});
        }
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        verify(player, never()).sendChunk(anyInt(), anyInt(), anyInt(), any(byte[].class), anyInt());
    }

    @Test
    void stillServesRemainingRecipientWhenAnotherPlayerLeaves() throws Exception {
        Player departed = mock(Player.class);
        set(Player.class, departed, "usedChunks", new HashMap<Long, Boolean>());
        departed.usedChunks.put(hash, false);
        when(departed.getLoaderId()).thenReturn(2);
        when(departed.getGameVersion()).thenReturn(GameVersion.V1_20_0);
        when(departed.isConnected()).thenReturn(true);
        when(departed.getLevel()).thenReturn(mock(Level.class));
        level.requestChunk(3, 5, departed);
        level.requestChunk(3, 5, player);
        BatchPacket cached = new BatchPacket();
        when(chunk.getChunkPacket(GameVersion.V1_20_0)).thenReturn(cached);
        process();
        verify(player).sendChunk(3, 5, cached);
        verify(departed, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt());
    }

    private void process() throws Exception {
        var method = Level.class.getDeclaredMethod("processChunkRequest");
        method.setAccessible(true);
        method.invoke(level);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
