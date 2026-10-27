package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkSerializationFailureGenerationTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private static final int X = 3, Z = 5;
    private static final long INDEX = Level.chunkHash(X, Z);

    enum ProviderKind { ANVIL, LEVELDB }
    enum Mode { ASYNC, DISABLED, NO_EXECUTOR, REJECTED }

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void lateFailureCannotRetireTheAcceptedTimeoutRetry(ProviderKind kind) throws Exception {
        Fixture f = new Fixture(kind, Mode.ASYNC, mock(Level.class, CALLS_REAL_METHODS));
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
            failSerialization(serializer);
            f.acceptTimeoutRetry();
            assertEquals(2, f.work.size());

            f.work.remove().run();
            f.level.doTick(203);
            assertTrue(f.tasks().contains(INDEX), "old failure must leave the accepted retry active");
            assertEquals(202L, f.starts.get(INDEX), "old failure must preserve the retry timer");
            assertEquals(2L, f.generations.get(INDEX));

            f.work.remove().run();
            f.level.doTick(204);
            assertFalse(f.tasks().contains(INDEX), "current failure still permits a fresh request");
            assertFalse(f.starts.containsKey(INDEX));
            f.requestAt(205);
            assertEquals(1, f.work.size(), "the failed current request can be resubmitted");
        }
    }

    @ParameterizedTest
    @CsvSource({"ANVIL,DISABLED", "LEVELDB,DISABLED", "ANVIL,NO_EXECUTOR", "LEVELDB,NO_EXECUTOR",
            "ANVIL,REJECTED", "LEVELDB,REJECTED"})
    void queuedSynchronousFailureCannotRetireALaterRetry(ProviderKind kind, Mode mode) throws Exception {
        Fixture f = new Fixture(kind, mode, mock(Level.class, CALLS_REAL_METHODS));
        AtomicInteger serializations = new AtomicInteger();
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                    any(), anyBoolean(), any(DimensionData.class), anyBoolean())).thenAnswer(call -> {
                if (serializations.incrementAndGet() == 1) throw new IllegalStateException("initial serializer failed");
                return null;
            });
            f.acceptTimeoutRetry();
            assertEquals(2, serializations.get());
            f.level.doTick(203);
            assertTrue(f.tasks().contains(INDEX));
            assertEquals(202L, f.starts.get(INDEX));
        }
    }

    @ParameterizedTest
    @CsvSource({"ANVIL,ASYNC", "LEVELDB,ASYNC", "ANVIL,DISABLED", "LEVELDB,DISABLED",
            "ANVIL,NO_EXECUTOR", "LEVELDB,NO_EXECUTOR", "ANVIL,REJECTED", "LEVELDB,REJECTED"})
    void currentFailureClearsTheTaskAndTimer(ProviderKind kind, Mode mode) throws Exception {
        Fixture f = new Fixture(kind, mode, mock(Level.class, CALLS_REAL_METHODS));
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
            failSerialization(serializer);
            f.requestAt(1);
            if (mode == Mode.ASYNC) f.work.remove().run();
            assertTrue(f.tasks().contains(INDEX), "worker must only enqueue failure");
            f.level.doTick(2);
            assertFalse(f.tasks().contains(INDEX));
            assertFalse(f.starts.containsKey(INDEX));
        }
    }

    @ParameterizedTest
    @CsvSource({"ANVIL,ASYNC", "LEVELDB,ASYNC", "ANVIL,DISABLED", "LEVELDB,DISABLED",
            "ANVIL,NO_EXECUTOR", "LEVELDB,NO_EXECUTOR", "ANVIL,REJECTED", "LEVELDB,REJECTED"})
    void customLevelStillReceivesItsLegacyFailureHook(ProviderKind kind, Mode mode) throws Exception {
        FailureHookLevel level = mock(FailureHookLevel.class, CALLS_REAL_METHODS);
        Fixture f = new Fixture(kind, mode, level);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
            failSerialization(serializer);
            f.requestAt(1);
            if (mode == Mode.ASYNC) f.work.remove().run();
            assertEquals(1, level.failures);
            f.level.doTick(2);
            assertFalse(f.tasks().contains(INDEX));
        }
    }

    @Test
    void legacyFailureWithoutAGenerationKeepsItsExistingBehavior() throws Exception {
        Fixture f = new Fixture(ProviderKind.ANVIL, Mode.ASYNC, mock(Level.class, CALLS_REAL_METHODS));
        f.acceptTimeoutRetry();
        f.level.onAsyncChunkRequestFailed(VERSION, X, Z);
        f.level.doTick(203);
        assertFalse(f.tasks().contains(INDEX));
        assertFalse(f.starts.containsKey(INDEX));
    }

    private static void failSerialization(MockedStatic<NetworkChunkSerializer> serializer) {
        serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                any(), anyBoolean(), any(DimensionData.class), anyBoolean()))
                .thenThrow(new IllegalStateException("controlled serializer failure"));
    }

    private static final class Fixture {
        final Level level;
        final Server server = mock(Server.class);
        final Player player = mock(Player.class);
        final Queue<Runnable> work = new ConcurrentLinkedQueue<>();
        final Long2LongOpenHashMap starts = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap generations = new Long2LongOpenHashMap();

        Fixture(ProviderKind kind, Mode mode, Level level) throws Exception {
            this.level = level;
            if (!(level instanceof FailureHookLevel)) assertEquals(Level.class, level.getClass());
            server.asyncChunkSending = mode != Mode.DISABLED;
            server.cacheChunks = false;
            server.chunksPerTick = 4;
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            set(level, Level.class, "server", server);

            LevelProvider provider;
            BaseChunk source;
            if (kind == ProviderKind.ANVIL) {
                provider = mock(Anvil.class, CALLS_REAL_METHODS);
                source = mock(Chunk.class);
                set(provider, BaseLevelProvider.class, "level", level);
            } else {
                provider = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
                source = mock(LevelDBChunk.class);
                set(provider, LevelDBProvider.class, "level", level);
            }
            doReturn(source).when(provider).getChunk(X, Z, false);
            doReturn(source).when(source).cloneForChunkSending();
            set(level, Level.class, "provider", provider);
            doReturn(source).when(level).getChunk(X, Z);
            doReturn(source).when(level).getChunkIfLoaded(X, Z);
            doReturn("test").when(level).getName();
            doReturn(0).when(level).getDimension();
            doReturn(DimensionData.LEGACY_DIMENSION).when(level).getDimensionData();
            doReturn(false).when(level).antiXrayEnabled();
            doReturn(false).when(level).isClientCacheDeliveryWanted();

            ExecutorService executor = mock(ExecutorService.class);
            if (mode == Mode.REJECTED) {
                doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));
            } else {
                doAnswer(call -> { work.add(call.getArgument(0)); return null; })
                        .when(executor).execute(any(Runnable.class));
            }
            set(level, Level.class, "asyncChuckExecutor", mode == Mode.NO_EXECUTOR ? null : executor);
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"})
                set(level, Level.class, name, new Object2ObjectOpenHashMap<>());
            set(level, Level.class, "chunkSendTaskStartTick", starts);
            set(level, Level.class, "chunkSendGeneration", generations);
            for (String name : new String[]{"asyncChunkRequestCallbackQueue", "asyncChunkRequestFailedQueue",
                    "normalUpdateQueue", "updateBlockEntities", "completedChunkLoads"})
                set(level, Level.class, name, new ConcurrentLinkedQueue<>());
            set(level, Level.class, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, Level.class, "players", new PlayerRangeMap());
            set(level, Level.class, "chunkTickList", new Long2IntOpenHashMap());
            set(level, Level.class, "changedBlocks", new Long2ObjectOpenHashMap<>());
            set(level, Level.class, "lightQueue", new ConcurrentHashMap<>());
            set(level, Level.class, "updateQueue", new BlockUpdateScheduler(level, 0));
            set(level, Level.class, "chunkPackets", new ConcurrentHashMap<>());
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            level.gameRules.refresh();

            set(player, Player.class, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
            player.usedChunks.put(INDEX, false);
            when(player.getLoaderId()).thenReturn(1);
            when(player.getGameVersion()).thenReturn(VERSION);
            when(player.getLevel()).thenReturn(level);
            when(player.isConnected()).thenReturn(true);
        }

        void requestAt(int tick) throws Exception {
            when(server.getTick()).thenReturn(tick);
            level.requestChunk(X, Z, player);
            var process = Level.class.getDeclaredMethod("processChunkRequest");
            process.setAccessible(true);
            process.invoke(level);
        }

        void acceptTimeoutRetry() throws Exception {
            requestAt(1);
            requestAt(201);
            assertEquals(0L, generations.get(INDEX), "the exact 200-tick boundary is not expired");
            requestAt(202);
            assertTrue(tasks().contains(INDEX));
            assertEquals(2L, generations.get(INDEX));
            assertEquals(202L, starts.get(INDEX));
        }

        LongSet tasks() throws Exception {
            var method = Level.class.getDeclaredMethod("getChunkSendTasks", GameVersion.class);
            method.setAccessible(true);
            return (LongSet) method.invoke(level, VERSION);
        }
    }

    static class FailureHookLevel extends Level {
        int failures;

        FailureHookLevel() {
            super(null, null, null, null);
        }

        @Override
        public void onAsyncChunkRequestFailed(GameVersion version, int x, int z) {
            failures++;
            super.onAsyncChunkRequestFailed(version, x, z);
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
