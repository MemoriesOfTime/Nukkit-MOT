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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        set(Level.class, level, "chunkSendGeneration", new Long2LongOpenHashMap());
        chunk = mock(BaseFullChunk.class);
        doReturn(chunk).when(level).getChunk(3, 5);
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);
        doReturn(0).when(level).getDimension();
        player = mock(Player.class);
        set(Player.class, player, "usedChunks", new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
        player.usedChunks.put(hash, Boolean.FALSE);
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
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
        verify(level, never()).getChunk(3, 5);
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @Test
    void removesDisconnectedRequestBeforeSnapshot() throws Exception {
        level.requestChunk(3, 5, player);
        when(player.isConnected()).thenReturn(false);
        process();
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
        verify(level, never()).getChunk(3, 5);
    }

    @Test
    void removesUnsubscribedRequestBeforeSnapshot() throws Exception {
        level.requestChunk(3, 5, player);
        player.usedChunks.clear();
        process();
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
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
        set(Player.class, departed, "usedChunks", new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
        departed.usedChunks.put(hash, Boolean.FALSE);
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
        verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
    }

    /**
     * 超时重试必须自增代次并以新代次重新提交：仍在飞的旧任务回调随后会被 isSupersededChunkCallback 丢弃。
     * 首轮提交即携带序号 1（而非 0），配合首跳到 2 的代次，首轮超时前的旧任务从第一轮起就可被丢弃。
     * <p>
     * A timeout retry must bump the generation and resubmit with it, so the still-in-flight older
     * task's callback is dropped by isSupersededChunkCallback afterwards. The first submission already
     * carries sequence 1 (never 0); combined with the first bump landing at 2, even a pre-first-timeout
     * task is droppable from the first retry on.
     */
    @Test
    void timeoutRetryBumpsGenerationAndResubmitsWithIt() throws Exception {
        when(server.getLogger()).thenReturn(mock(cn.nukkit.utils.MainLogger.class));
        level.requestChunk(3, 5, player);
        process();
        verify(provider).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(3), eq(5), eq(1L));

        // 200 tick 无回调 → 强制重试,代次首跳到 2(首轮提交带 1)
        // No callback within 200 ticks → forced retry, the generation first jumps to 2 (submissions carry 1)
        when(server.getTick()).thenReturn(300);
        level.requestChunk(3, 5, player);
        process();

        verify(provider).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(3), eq(5), eq(2L));
        // 仍卡在旧任务里的那次序列化(提交时带的 1)现在确实可被判旧丢弃
        // The wedged older serialization (which carried 1 at submit) is now genuinely droppable
        Assertions.assertTrue(level.isSupersededChunkCallback(callbackWithGeneration(1L)));
    }

    /**
     * 只有严格旧于当前代次的回调会被丢弃；序号 0（未回传代次的第三方 provider / 插件直调 3 参）永不丢弃。
     * <p>
     * Only callbacks strictly older than the current generation are dropped; sequence 0 (third-party
     * providers that don't echo it / plugin calls to the 3-arg overload) is never dropped.
     */
    @Test
    void dropsOnlyCallbacksOlderThanCurrentGeneration() throws Exception {
        Long2LongOpenHashMap generations = get(Level.class, level, "chunkSendGeneration");
        generations.put(hash, 2L);

        Assertions.assertTrue(level.isSupersededChunkCallback(callbackWithGeneration(1L)));
        Assertions.assertFalse(level.isSupersededChunkCallback(callbackWithGeneration(2L)));
        Assertions.assertFalse(level.isSupersededChunkCallback(callbackWithGeneration(0L)));
    }

    private static cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData
    callbackWithGeneration(long generation) {
        return new cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData(
                GameVersion.V1_20_0, 0L, 3, 5, 1, new byte[]{0}, null, null, null, generation);
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

    private static <T> T get(Class<?> type, Object target, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
