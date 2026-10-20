package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.ChunkRequestToken;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.utils.Zlib;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkDeferredCompressionTest {
    static final int X = 3, Z = 5, COUNT = 1;
    static final long INDEX = Level.chunkHash(X, Z);
    static final byte[] PAYLOAD = {1, 2, 3, 4}, BIOMES = {5, 6}, ENTITIES = {7, 8};
    static final List<byte[]> BLOBS = List.of(new byte[]{9, 10});
    enum Mode { FULL, PULL, BLOB, MIXED }

    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"PULL", "BLOB", "MIXED"})
    void structuredModesNeverPrepareUnusedFullBatch(Mode mode) throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player first = f.player(1, mode == Mode.MIXED ? Mode.FULL : mode);
        Player pull = mode == Mode.MIXED ? f.player(2, Mode.PULL) : null;
        if (pull != null) f.request(first, pull);
        else f.request(first);
        try (var compression = mockStatic(Player.class)) {
            f.enqueue();
            compression.verifyNoInteractions();
            f.tick();
            compression.verifyNoInteractions();
        }
        assertTrue(f.work.isEmpty());
        assertFalse(f.tasks().contains(INDEX));
        assertFalse(f.owners().containsKey(INDEX));
        if (mode == Mode.PULL) {
            ArgumentCaptor<DataPacket> packet = ArgumentCaptor.forClass(DataPacket.class);
            verify(first).sendChunk(eq(X), eq(Z), packet.capture());
            LevelChunkPacket shell = assertInstanceOf(LevelChunkPacket.class, packet.getValue());
            assertFalse(shell.cacheEnabled);
            assertTrue(shell.requestSubChunks);
            assertEquals(COUNT, shell.subChunkLimit);
            assertArrayEquals(new byte[]{5, 6, 0}, shell.data, "biomes followed by the border-block terminator");
        } else if (mode == Mode.BLOB) {
            ArgumentCaptor<DataPacket> packet = ArgumentCaptor.forClass(DataPacket.class);
            verify(first).sendChunk(eq(X), eq(Z), packet.capture());
            LevelChunkPacket full = assertInstanceOf(LevelChunkPacket.class, packet.getValue());
            assertTrue(full.cacheEnabled);
            assertEquals(BLOBS.size() + 1, full.blobIds.length);
        } else {
            verify(first).sendChunk(X, Z, COUNT, PAYLOAD, 0);
            verify(pull).sendChunk(eq(X), eq(Z), any(LevelChunkPacket.class));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void departedRecipientCannotReceiveDelayedBytesOrLeaveAnOrphan(boolean beforeSelection) throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        if (beforeSelection) {
            when(player.isConnected()).thenReturn(false);
            try (var compression = mockStatic(Player.class)) {
                f.tick();
                compression.verifyNoInteractions();
            }
            assertTrue(f.work.isEmpty());
        } else {
            f.tick();
            when(player.isConnected()).thenReturn(false);
            f.runWorker();
            f.tick();
        }
        assertFalse(f.tasks().contains(INDEX));
        assertFalse(f.owners().containsKey(INDEX));
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        verify(f.chunk, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
        verify(f.provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(X), eq(Z));
    }

    static Stream<GameVersion> versions() {
        return Arrays.stream(GameVersion.values()).filter(v -> v.getProtocol() >= GameVersion.V1_20_0.getProtocol()
                && v.getProtocol() <= GameVersion.V1_26_50.getProtocol());
    }

    @ParameterizedTest @MethodSource("versions")
    void fullDeliveryUsesWorkerAndPreservesBytes(GameVersion version) throws Exception {
        Fixture f = new Fixture(version);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        byte[] expected = Player.getChunkCacheFromData(version, X, Z, COUNT, PAYLOAD, 0).payload;
        f.enqueue();
        f.tick();
        assertEquals(1, f.work.size());
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        f.runWorker();
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        f.tick();
        ArgumentCaptor<DataPacket> packet = ArgumentCaptor.forClass(DataPacket.class);
        verify(player).sendChunk(eq(X), eq(Z), packet.capture());
        assertArrayEquals(expected, assertInstanceOf(BatchPacket.class, packet.getValue()).payload);
        verify(f.chunk).setChunkPacket(version, (BatchPacket) packet.getValue());
        assertFalse(f.tasks().contains(INDEX));
    }

    @Test
    void fullCompressionRunsOffMainAndOnlyPublishesOnMain() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        Thread main = Thread.currentThread();
        BatchPacket encoded = new BatchPacket();
        AtomicReference<Thread> compressedOn = new AtomicReference<>();
        try (var compression = mockStatic(Player.class)) {
            f.enqueue();
            f.tick();
            compression.verifyNoInteractions();
        }
        ExecutorService actualWorker = Executors.newSingleThreadExecutor();
        try {
            actualWorker.submit(() -> {
                try (var compression = mockStatic(Player.class)) {
                    compression.when(() -> Player.getChunkCacheFromData(f.version, X, Z, COUNT, PAYLOAD, 0))
                            .thenAnswer(call -> { compressedOn.set(Thread.currentThread()); return encoded; });
                    f.work.remove().run();
                }
            }).get(10, TimeUnit.SECONDS);
        } finally {
            actualWorker.shutdownNow();
        }
        assertNotSame(main, compressedOn.get());
        assertNotNull(compressedOn.get());
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        doAnswer(call -> { assertSame(main, Thread.currentThread()); return null; })
                .when(player).sendChunk(X, Z, encoded);
        f.tick();
        verify(player).sendChunk(X, Z, encoded);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void replacementOwnerSurvivesDelayedSuccessAndFailure(boolean failure) throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player old = f.player(1, Mode.FULL);
        f.request(old);
        f.enqueue();
        f.tick();
        Player replacement = f.player(1, Mode.FULL);
        Int2ObjectMap<Player> owner = new Int2ObjectLinkedOpenHashMap<>();
        owner.put(1, replacement);
        f.owners().put(INDEX, owner);
        f.runWorker(failure);
        f.tick();
        verify(old, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        verify(replacement, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        assertSame(owner, f.owners().get(INDEX));
        assertTrue(f.tasks().contains(INDEX));
        verify(f.chunk, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void replacedGenerationSurvivesDelayedSuccessAndFailure(boolean failure) throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        f.tick();
        f.tasks().remove(INDEX);
        f.pending().add(INDEX);
        f.tick();
        assertTrue(f.tasks().contains(INDEX));
        f.runWorker(failure);
        f.tick();
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        assertTrue(f.tasks().contains(INDEX));
        assertTrue(f.owners().containsKey(INDEX));
        verify(f.chunk, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
    }

    @Test
    void sameMapLoaderReplacementDoesNotReceiveOldBytes() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player old = f.player(1, Mode.FULL), replacement = f.player(1, Mode.FULL);
        f.request(old);
        f.enqueue();
        f.tick();
        f.owners().get(INDEX).put(1, replacement);
        f.runWorker();
        f.tick();
        verify(old, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        verify(replacement, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        assertSame(replacement, f.owners().get(INDEX).get(1));
        verify(f.provider, atLeastOnce()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(X), eq(Z));
    }

    @Test
    void resetEpochRejectsOldBytesWithoutLosingNewSubscription() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        f.tick();
        set(player, "chunkDeliveryEpoch", 1L);
        f.runWorker();
        f.tick();
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        assertTrue(player.usedChunks.containsKey(INDEX));
        verify(f.chunk, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
        verify(f.provider, atLeastOnce()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(X), eq(Z));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mutationOrReplacementDuringWorkerCannotPublishPersistentCache(boolean replacement) throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        f.tick();
        if (replacement) {
            BaseFullChunk newer = mock(BaseFullChunk.class);
            when(newer.getMutationRevision()).thenReturn(7L);
            doReturn(newer).when(f.level).getChunkIfLoaded(X, Z);
        } else {
            when(f.chunk.getMutationRevision()).thenReturn(8L);
            when(f.chunk.getChanges()).thenReturn(0L);
        }
        f.runWorker();
        f.tick();
        verify(f.chunk, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
        verify(f.level.getChunkIfLoaded(X, Z), never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @Test
    void modeChangeDuringCompressionUsesRetainedRawMetadata() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        f.tick();
        when(player.isSubChunkPullMode()).thenReturn(true);
        f.runWorker();
        f.tick();
        ArgumentCaptor<DataPacket> packet = ArgumentCaptor.forClass(DataPacket.class);
        verify(player).sendChunk(eq(X), eq(Z), packet.capture());
        LevelChunkPacket shell = assertInstanceOf(LevelChunkPacket.class, packet.getValue());
        assertTrue(shell.requestSubChunks);
        assertEquals(COUNT, shell.subChunkLimit);
        assertArrayEquals(new byte[]{5, 6, 0}, shell.data, "retained biomes followed by the border-block terminator");
        verify(f.chunk, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
    }

    @Test
    void workerFailureRetriesOnlyCurrentRequestOnMain() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        f.tick();
        f.runWorker(true);
        assertTrue(f.tasks().contains(INDEX));
        f.tick();
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        assertSame(player, f.owners().get(INDEX).get(1));
        verify(f.provider, atLeastOnce()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(X), eq(Z));
    }

    @Test
    void rejectedWorkerSubmissionRetiresOnlyCapturedTaskAndSchedulesRetry() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        RejectedExecutionException failure = new RejectedExecutionException("worker stopping");
        doThrow(failure).when(f.worker).execute(any(Runnable.class));
        f.enqueue();
        assertSame(failure, assertThrows(RejectedExecutionException.class, f::tick));
        assertFalse(f.tasks().contains(INDEX));
        assertTrue(f.pending().contains(INDEX));
        assertSame(player, f.owners().get(INDEX).get(1));
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @Test
    void reentrantWorkerRejectionCannotRetireAReplacementGeneration() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        RejectedExecutionException failure = new RejectedExecutionException("request replaced during submission");
        doAnswer(call -> {
            f.level.onAsyncChunkRequestFailed(f.version, X, Z);
            f.level.requestChunk(X, Z, player);
            f.tick();
            throw failure;
        }).when(f.worker).execute(any(Runnable.class));
        f.enqueue();
        assertSame(failure, assertThrows(RejectedExecutionException.class, f::tick));
        assertTrue(f.tasks().contains(INDEX), "submission failure must not retire the newer task");
        assertSame(player, f.owners().get(INDEX).get(1));
        verify(f.provider).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(X), eq(Z));
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void disabledCacheBeforeSelectionDoesNotScheduleCompression(boolean disabledBeforeEnqueue) throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        if (disabledBeforeEnqueue) f.server.cacheChunks = false;
        try (var compression = mockStatic(Player.class)) {
            f.enqueue();
            f.server.cacheChunks = false;
            f.tick();
            compression.verifyNoInteractions();
        }
        assertTrue(f.work.isEmpty());
        verify(player).sendChunk(X, Z, COUNT, PAYLOAD, 0);
    }

    @Test
    void singleThreadProviderRetainsMainThreadCompressionFallback() throws Exception {
        Fixture f = new Fixture(GameVersion.V1_26_50);
        Player player = f.player(1, Mode.FULL);
        f.request(player);
        f.enqueue();
        f.tick();
        Zlib.setProvider(1);
        try (var compression = mockStatic(Player.class)) {
            f.runWorker();
            compression.verifyNoInteractions();
            BatchPacket packet = new BatchPacket();
            compression.when(() -> Player.getChunkCacheFromData(f.version, X, Z, COUNT, PAYLOAD, 0)).thenReturn(packet);
            f.tick();
            verify(player).sendChunk(X, Z, packet);
        } finally {
            Zlib.setProvider(2);
        }
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field field = (target instanceof Player ? Player.class : Level.class).getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static final class Fixture {
        final GameVersion version;
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final LevelProvider provider = mock(LevelProvider.class);
        final ExecutorService worker = mock(ExecutorService.class);
        final Queue<Object> callbacks = new ConcurrentLinkedQueue<>();
        final Queue<Runnable> work = new ConcurrentLinkedQueue<>();
        final ChunkRequestToken token;
        int tick = 1;

        Fixture(GameVersion version) throws Exception {
            this.version = version;
            server.cacheChunks = true;
            server.asyncChunkSending = true;
            server.chunksPerTick = 4;
            when(server.getClientCacheBlobStore()).thenReturn(new ClientCacheBlobStore(1024));
            var logger = Server.getInstance().getLogger();
            when(server.getLogger()).thenReturn(logger);
            when(chunk.getMutationRevision()).thenReturn(7L);
            when(chunk.getChanges()).thenReturn(7L);
            token = new ChunkRequestToken(chunk);
            set(level, "server", server);
            set(level, "provider", provider);
            doReturn(0).when(level).getDimension();
            doReturn(chunk).when(level).getChunkIfLoaded(X, Z);
            doReturn(chunk).when(level).getChunk(X, Z);
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"})
                set(level, name, new Object2ObjectOpenHashMap<>());
            set(level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(level, "asyncChunkRequestCallbackQueue", callbacks);
            set(level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, "players", new PlayerRangeMap());
            set(level, "entities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, "chunkTickList", new Long2IntOpenHashMap());
            set(level, "changedBlocks", new Long2ObjectOpenHashMap<>());
            set(level, "dimensionData", DimensionData.LEGACY_DIMENSION);
            set(level, "lightQueue", new ConcurrentHashMap<>());
            set(level, "updateQueue", new BlockUpdateScheduler(level, 0));
            set(level, "normalUpdateQueueSize", new AtomicInteger());
            for (String name : new String[]{"normalUpdateQueue", "updateBlockEntities",
                    "asyncChunkRequestFailedQueue", "completedChunkLoads"})
                set(level, name, new ConcurrentLinkedQueue<>());
            set(level, "chunkPackets", new ConcurrentHashMap<>());
            doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(worker).execute(any());
            set(level, "asyncChuckExecutor", worker);
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            level.gameRules.refresh();
        }

        Player player(int loader, Mode mode) throws Exception {
            Player player = mock(Player.class, call -> call.getMethod().getName().equals("getChunkDeliveryEpoch")
                    ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
            set(player, "usedChunks", new HashMap<Long, Boolean>());
            player.usedChunks.put(INDEX, false);
            when(player.getLoaderId()).thenReturn(loader);
            when(player.getGameVersion()).thenReturn(version);
            when(player.getLevel()).thenReturn(level);
            when(player.isConnected()).thenReturn(true);
            when(player.isSubChunkPullMode()).thenReturn(mode == Mode.PULL);
            when(player.isClientBlobCacheActive()).thenReturn(mode == Mode.BLOB);
            when(player.tryOpenBlobTransaction(any(long[].class))).thenReturn(true);
            return player;
        }

        void request(Player... players) throws Exception {
            for (Player player : players) level.requestChunk(X, Z, player);
            tasks().add(INDEX);
            pending().remove(INDEX);
        }
        void enqueue() {
            level.asyncChunkRequestCallback(version, 7, X, Z, COUNT, PAYLOAD, BLOBS, BIOMES, ENTITIES, token);
        }
        void tick() { level.doTick(tick++); }
        void runWorker() { work.remove().run(); }
        void runWorker(boolean failure) {
            if (!failure) { runWorker(); return; }
            try (var compression = mockStatic(Player.class)) {
                compression.when(() -> Player.getChunkCacheFromData(version, X, Z, COUNT, PAYLOAD, 0))
                        .thenThrow(new IllegalStateException("compression failed"));
                runWorker();
            }
        }
        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, Int2ObjectMap<Player>> owners() throws Exception {
            return (ConcurrentMap<Long, Int2ObjectMap<Player>>) bucket("getChunkSendQueue");
        }
        LongSet tasks() throws Exception { return (LongSet) bucket("getChunkSendTasks"); }
        LongSet pending() throws Exception { return (LongSet) bucket("getPendingChunkRequests"); }
        Object bucket(String name) throws Exception {
            Method method = Level.class.getDeclaredMethod(name, GameVersion.class);
            method.setAccessible(true);
            return method.invoke(level, version);
        }
    }
}
