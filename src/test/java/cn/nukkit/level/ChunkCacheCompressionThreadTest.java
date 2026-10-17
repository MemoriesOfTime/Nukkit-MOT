package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.utils.Zlib;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkCacheCompressionThreadTest {
    @ParameterizedTest
    @CsvSource({"true,true,7,true", "true,false,7,false", "true,true,8,false", "false,true,7,true"})
    void publishesPreparedPacketOrRawFallbackOnMain(boolean cacheOnWorker, boolean cacheOnMain,
                                                    long changes, boolean shouldCache) throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        server.cacheChunks = cacheOnWorker;
        set(level, "server", server);
        for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"}) {
            set(level, name, new Object2ObjectOpenHashMap<>());
        }
        set(level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
        Queue<Object> queue = new ConcurrentLinkedQueue<>();
        set(level, "asyncChunkRequestCallbackQueue", queue);
        doReturn(0).when(level).getDimension();
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.getChanges()).thenReturn(changes);
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);
        Player recipient = mock(Player.class);
        var used = Player.class.getDeclaredField("usedChunks");
        used.setAccessible(true);
        used.set(recipient, new java.util.HashMap<Long, Boolean>());
        recipient.usedChunks.put(Level.chunkHash(3, 5), false);
        when(recipient.getLoaderId()).thenReturn(1);
        when(recipient.getGameVersion()).thenReturn(GameVersion.V1_20_0);
        when(recipient.getLevel()).thenReturn(level);
        when(recipient.isConnected()).thenReturn(true);
        level.requestChunk(3, 5, recipient);
        var tasks = Level.class.getDeclaredMethod("getChunkSendTasks", GameVersion.class);
        tasks.setAccessible(true);
        ((LongSet) tasks.invoke(level, GameVersion.V1_20_0)).add(Level.chunkHash(3, 5));
        byte[] payload = {1, 2, 3};
        BatchPacket packet = new BatchPacket();
        try (var packets = mockStatic(Player.class)) {
            packets.when(() -> Player.getChunkCacheFromData(GameVersion.V1_20_0, 3, 5, 4, payload, 0))
                    .thenReturn(packet);
            level.asyncChunkRequestCallback(GameVersion.V1_20_0, 7, 3, 5, 4, payload);
            Object prepared = queue.remove();
            var dataAccessor = prepared.getClass().getDeclaredMethod("data");
            var packetAccessor = prepared.getClass().getDeclaredMethod("packet");
            dataAccessor.setAccessible(true);
            packetAccessor.setAccessible(true);
            var data = (NetworkChunkSerializer.NetworkChunkSerializerCallbackData) dataAccessor.invoke(prepared);
            BatchPacket compressed = (BatchPacket) packetAccessor.invoke(prepared);
            if (cacheOnWorker) { assertNull(data.getPayload()); assertSame(packet, compressed); }
            else { assertSame(payload, data.getPayload()); assertNull(compressed); }
            server.cacheChunks = cacheOnMain;
            var callback = Level.class.getDeclaredMethod("chunkRequestCallback", GameVersion.class,
                    long.class, int.class, int.class, int.class, byte[].class, BatchPacket.class);
            callback.setAccessible(true);
            callback.invoke(level, GameVersion.V1_20_0, 7L, 3, 5, 4, data.getPayload(), compressed);
            packets.verify(() -> Player.getChunkCacheFromData(GameVersion.V1_20_0, 3, 5, 4, payload, 0), times(1));
        }
        verify(recipient).sendChunk(3, 5, packet);
        verify(chunk, times(shouldCache ? 1 : 0)).setChunkPacket(GameVersion.V1_20_0, packet);
    }

    @Test
    void singleThreadCompressorKeepsExistingMainThreadFallback() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        server.cacheChunks = true;
        set(level, "server", server);
        Queue<Object> queue = new ConcurrentLinkedQueue<>();
        set(level, "asyncChunkRequestCallbackQueue", queue);
        Zlib.setProvider(1);
        try (var player = mockStatic(Player.class)) {
            level.asyncChunkRequestCallback(GameVersion.V1_20_0, 7, 3, 5, 4, new byte[]{1, 2});
            player.verifyNoInteractions();
            assertEquals(1, queue.size());
        } finally {
            Zlib.setProvider(2);
        }
    }

    @Test
    void asyncCallbackCompressesBeforeReturningToMainThread() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        server.cacheChunks = true;
        set(level, "server", server);
        Queue<Object> queue = new ConcurrentLinkedQueue<>();
        set(level, "asyncChunkRequestCallbackQueue", queue);
        doReturn(2).when(level).getDimension();
        AtomicReference<Thread> compressionThread = new AtomicReference<>();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try (var player = mockStatic(Player.class)) {
                    player.when(() -> Player.getChunkCacheFromData(any(GameVersion.class), anyInt(),
                            anyInt(), anyInt(), any(byte[].class), anyInt())).thenAnswer(invocation -> {
                        assertEquals(GameVersion.V1_20_0, invocation.getArgument(0));
                        assertEquals(2, (int) invocation.getArgument(5));
                        compressionThread.set(Thread.currentThread());
                        return new BatchPacket();
                    });
                    level.asyncChunkRequestCallback(GameVersion.V1_20_0, 7, 3, 5, 4, new byte[]{1, 2});
                }
            }).get(10, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
        }
        assertNotNull(compressionThread.get(), "Compression must finish on the serializer worker");
        assertNotSame(Thread.currentThread(), compressionThread.get());
        assertEquals(1, queue.size());
    }

    @Test
    void cacheDisabledKeepsRawPayloadAndDoesNotCompressOnWorker() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        set(level, "server", mock(Server.class));
        Queue<Object> queue = new ConcurrentLinkedQueue<>();
        set(level, "asyncChunkRequestCallbackQueue", queue);
        try (var player = mockStatic(Player.class)) {
            level.asyncChunkRequestCallback(GameVersion.V1_20_0, 7, 3, 5, 4, new byte[]{1, 2});
            player.verifyNoInteractions();
        }
        assertEquals(1, queue.size());
    }

    @Test
    void cachedBytesAreIdenticalOnBothThreadsForEverySupportedWindowVersion() throws Exception {
        MockServer.init();
        Server server = Server.getInstance();
        boolean oldSnappy = server.useSnappy;
        int oldLevel = server.networkCompressionLevel;
        server.networkCompressionLevel = 5;
        byte[] payload = new byte[65536];
        new java.util.Random(42).nextBytes(payload);
        int versions = 0;
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            for (boolean snappy : new boolean[]{false, true}) {
                server.useSnappy = snappy;
                for (GameVersion version : GameVersion.values()) {
                    if (version.isNetEase() || version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                            || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
                    for (int dimension : new int[]{0, 1, 2}) {
                        byte[] expected = Player.getChunkCacheFromData(version, -3, 5, 24, payload, dimension).payload;
                        byte[] actual = worker.submit(() -> Player.getChunkCacheFromData(
                                version, -3, 5, 24, payload, dimension).payload).get(10, TimeUnit.SECONDS);
                        assertArrayEquals(expected, actual, version + " dimension=" + dimension + " snappy=" + snappy);
                    }
                    if (!snappy) versions++;
                }
            }
        } finally {
            worker.shutdownNow();
            server.useSnappy = oldSnappy;
            server.networkCompressionLevel = oldLevel;
        }
        assertTrue(versions >= 20);
        System.out.println("Chunk cache wire-equivalence: " + versions + " versions x 3 dimensions x 2 codecs");
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }
}
