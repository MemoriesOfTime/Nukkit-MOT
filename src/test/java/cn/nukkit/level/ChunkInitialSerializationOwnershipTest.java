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
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallback;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A timeout retires the initial serializer, even when its retry reuses the same player. */
@Isolated("MockServer and static serializer/compression fixtures")
@Timeout(20)
class ChunkInitialSerializationOwnershipTest {
    private static final GameVersion VERSION = GameVersion.V1_26_50;
    private static final int X = 3, Z = 5;
    private static final long INDEX = Level.chunkHash(X, Z);
    private static final byte[] OLD_PAYLOAD = {11, 12}, NEW_PAYLOAD = {31, 32};
    private static final byte[] OLD_BIOMES = {21, 22}, NEW_BIOMES = {41, 42};

    enum ProviderKind { LEVELDB, ANVIL }
    enum Mode { PULL_RAW, FULL_UNCACHED, FULL_CACHED }

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,PULL_RAW", "ANVIL,PULL_RAW", "LEVELDB,FULL_UNCACHED", "ANVIL,FULL_UNCACHED"})
    void retiredInitialSerializerCannotCompleteTheAcceptedRetry(ProviderKind provider, Mode mode) throws Exception {
        exerciseTimeout(provider, mode);
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void cachedFullDeliveryRetainsItsExistingFifoProtection(ProviderKind provider) throws Exception {
        exerciseTimeout(provider, Mode.FULL_CACHED);
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,PULL_RAW", "ANVIL,PULL_RAW", "LEVELDB,FULL_UNCACHED", "ANVIL,FULL_UNCACHED",
            "LEVELDB,FULL_CACHED", "ANVIL,FULL_CACHED"})
    void retiredSerializerFailureCannotClearTheAcceptedRetry(ProviderKind provider, Mode mode) throws Exception {
        Fixture f = new Fixture(provider, mode);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, true);
            Int2ObjectMap<Player> retryOwner = f.acceptRetryAfterTimeout();
            f.work.remove().run();
            assertEquals(1, f.serialized.size());
            assertSame(f.oldCopy, f.serialized.get(0));
            f.level.doTick(202);
            assertAll("an old serializer failure must not retire the accepted retry",
                    () -> assertTrue(f.sent.isEmpty()),
                    () -> assertTrue(f.tasks().contains(INDEX)),
                    () -> assertSame(retryOwner, f.owners().get(INDEX)));

            f.drainWork(203);
            assertEquals(2, f.serialized.size());
            assertSame(f.newCopy, f.serialized.get(1));
            assertEquals(1, f.sent.size());
            f.assertPacket(f.sent.get(0), true);
            assertFalse(f.tasks().contains(INDEX));
            assertFalse(f.owners().containsKey(INDEX));
        }
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,PULL_RAW", "ANVIL,PULL_RAW", "LEVELDB,FULL_UNCACHED", "ANVIL,FULL_UNCACHED",
            "LEVELDB,FULL_CACHED", "ANVIL,FULL_CACHED"})
    void recipientJoiningBeforeTimeoutSharesTheAcceptedSerialization(ProviderKind provider, Mode mode) throws Exception {
        Fixture f = new Fixture(provider, mode);
        Player joining = mock(Player.class);
        f.configurePlayer(joining, 2);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, false);
            f.requestAt(1);
            f.requestAt(2, joining);
            assertEquals(1, f.work.size(), "joining recipient must share the in-flight serializer");
            f.drainWork(3);

            verify(f.provider).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z), anyLong());
            assertEquals(1, f.serialized.size());
            assertSame(f.oldCopy, f.serialized.get(0));
            verify(f.player).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            verify(joining).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            assertEquals(2, f.sent.size());
            for (DataPacket packet : f.sent) f.assertPacket(packet, false);
            assertFalse(f.tasks().contains(INDEX));
            assertFalse(f.owners().containsKey(INDEX));
            verify(f.server.getLogger(), never()).error(anyString(), any(Throwable.class));
        }
    }

    @Test
    void partialSerializerFailureSettlesEachNativeProtocolWithoutRetiringAReplacement() throws Exception {
        Fixture f = new Fixture(ProviderKind.LEVELDB, Mode.FULL_CACHED);
        Player other = mock(Player.class);
        f.configurePlayer(other, 2);
        when(other.getGameVersion()).thenReturn(GameVersion.V1_20_0);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                    any(), eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true))).thenAnswer(call -> {
                ObjectSet<GameVersion> versions = call.getArgument(0);
                assertEquals(2, versions.size(), "both protocol buckets must share one provider job");
                assertSame(f.oldCopy, call.getArgument(1));
                BinaryStream stream = new BinaryStream();
                stream.put(OLD_PAYLOAD);
                Consumer<NetworkChunkSerializerCallback> callback = call.getArgument(2);
                callback.accept(new NetworkChunkSerializerCallback(VERSION, stream, 2,
                        List.of(new byte[]{51}), OLD_BIOMES, new byte[0]));
                throw new IllegalStateException("second protocol failed after the first callback");
            });
            compression.when(() -> Player.getChunkCacheFromData(eq(VERSION), eq(X), eq(Z), anyInt(),
                    any(byte[].class), eq(0))).thenAnswer(call -> {
                BatchPacket packet = new BatchPacket();
                packet.payload = call.getArgument(4);
                return packet;
            });

            when(f.server.getTick()).thenReturn(1);
            f.level.requestChunk(X, Z, f.player);
            f.level.requestChunk(X, Z, other);
            Method process = Level.class.getDeclaredMethod("processChunkRequest");
            process.setAccessible(true);
            process.invoke(f.level);
            assertEquals(1, f.work.size());
            f.work.remove().run();
            f.level.doTick(1);
            // Native upstream compresses on MAIN, so the first protocol has already completed.
            // Unlike the private deferred-compression pipeline, there is no second worker/generation.
            assertTrue(f.work.isEmpty());
            assertEquals(1, f.sent.size());
            f.assertPacket(f.sent.get(0), false);
            assertFalse(f.tasks().contains(INDEX));
            LongSet otherTasks = (LongSet) f.bucket("getChunkSendTasks", GameVersion.V1_20_0);
            assertFalse(otherTasks.contains(INDEX));
            assertFalse(((Long2LongMap) field(f.level, "chunkSendTaskStartTick")).containsKey(INDEX));
            assertTrue(((Queue<?>) field(f.level, "asyncChunkRequestCallbackQueue")).isEmpty());
            assertTrue(((Queue<?>) field(f.level, "asyncChunkRequestFailedQueue")).isEmpty());

            // A new request for the failed protocol receives its own cohort and remains pending.
            f.requestAt(2, other);
            assertEquals(1, f.work.size());
            assertTrue(otherTasks.contains(INDEX));
            f.level.doTick(2);
            assertTrue(otherTasks.contains(INDEX));
            assertEquals(1, f.sent.size(), "settled callbacks must not reappear for the new request");
        }
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false", "LEVELDB,true", "ANVIL,false", "ANVIL,true"})
    void customLevelKeepsItsLegacyFailureHook(ProviderKind provider, boolean async) throws Exception {
        FailureHookLevel custom = mock(FailureHookLevel.class, CALLS_REAL_METHODS);
        Fixture f = new Fixture(provider, Mode.FULL_UNCACHED, custom);
        f.server.asyncChunkSending = async;
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                    any(), eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true)))
                    .thenThrow(new IllegalStateException("custom level serializer failure"));
            f.requestAt(1);
            if (async) {
                assertEquals(1, f.work.size());
                f.work.remove().run();
            }
            assertTrue(f.work.isEmpty());
            serializer.verify(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class),
                    same(async ? f.oldCopy : f.source), any(), eq(false),
                    same(DimensionData.LEGACY_DIMENSION), eq(true)));
            assertEquals(1, custom.failures, "the pre-existing custom override must actually execute");
            verify(custom).onAsyncChunkRequestFailed(VERSION, X, Z);
            f.level.doTick(2);
            assertTrue(f.sent.isEmpty());
            assertFalse(f.tasks().contains(INDEX), "delegating the legacy hook must still retire its failed task");
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void oneProtocolsFailureDoesNotDisableAnotherProtocolsTimeout(ProviderKind provider) throws Exception {
        Fixture f = new Fixture(provider, Mode.FULL_UNCACHED);
        GameVersion otherVersion = GameVersion.V1_20_0;
        Player other = mock(Player.class);
        f.configurePlayer(other, 2);
        when(other.getGameVersion()).thenReturn(otherVersion);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, true);
            f.requestAt(1);
            f.requestAt(100, other);
            LongSet otherTasks = (LongSet) f.bucket("getChunkSendTasks", otherVersion);
            assertNotSame(f.tasks(), otherTasks, "the clients must use distinct normalized protocol buckets");
            assertTrue(otherTasks.contains(INDEX));
            assertEquals(2, f.work.size());

            // Only the first protocol fails; the second serializer is still queued.
            f.work.remove().run();
            assertEquals(1, f.serialized.size());
            assertSame(f.oldCopy, f.serialized.get(0));
            f.level.doTick(100);
            assertFalse(f.tasks().contains(INDEX));
            assertTrue(otherTasks.contains(INDEX));
            assertTrue(f.sent.isEmpty());

            // Its original tick100 deadline must survive cleanup of the other protocol.
            f.requestAt(300, other);
            verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z), anyLong());
            f.requestAt(301, other);
            verify(f.provider, times(3)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z), anyLong());
            assertEquals(2, f.work.size(), "the queued second serializer and its timeout retry remain");
            assertTrue(otherTasks.contains(INDEX));
        }
    }

    @Test
    void anotherProtocolsTimeoutDoesNotDiscardTheStillCurrentOwner() throws Exception {
        Fixture f = new Fixture(ProviderKind.LEVELDB, Mode.FULL_UNCACHED);
        Player other = mock(Player.class);
        f.configurePlayer(other, 2);
        when(other.getGameVersion()).thenReturn(GameVersion.V1_20_0);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, false);
            f.requestAt(1);
            f.requestAt(100, other);
            // The other protocol retries at tick301, advancing the coordinate-wide sequence.
            f.requestAt(301, other);
            assertEquals(3, f.work.size());
            f.work.remove().run();
            f.level.doTick(301);
            assertEquals(1, f.sent.size(), "the first protocol still owns its unchanged cohort");
            f.assertPacket(f.sent.get(0), false);
            assertFalse(f.tasks().contains(INDEX));
            assertTrue(((LongSet) f.bucket("getChunkSendTasks", GameVersion.V1_20_0)).contains(INDEX));
            assertEquals(301L, ((Long2LongMap) field(f.level, "chunkSendTaskStartTick")).get(INDEX));
        }
    }

    @ParameterizedTest
    @CsvSource({"PULL_RAW,false", "PULL_RAW,true", "FULL_CACHED,false"})
    void lastRecipientDepartureRetiresAllOutstandingDeliveryState(Mode mode, boolean failOld) throws Exception {
        Fixture f = new Fixture(ProviderKind.LEVELDB, mode);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, failOld);
            f.requestAt(1);
            if (mode == Mode.FULL_CACHED) {
                f.work.remove().run();
                assertEquals(1, ((Queue<?>) field(f.level, "asyncChunkRequestCallbackQueue")).size(),
                        "the native full-batch callback must be queued before recipient departure");
            }

            f.player.usedChunks.clear();
            f.requestAt(2);
            assertFalse(f.owners().containsKey(INDEX), "pending processing must prune the departed recipient");
            f.drainWork(3);
            f.level.doTick(4);

            assertEquals(1, f.serialized.size());
            assertSame(f.oldCopy, f.serialized.get(0));
            assertTrue(f.sent.isEmpty());
            f.assertNoOutstandingDelivery();
        }
    }

    private void exerciseTimeout(ProviderKind provider, Mode mode) throws Exception {
        Fixture f = new Fixture(provider, mode);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class);
             MockedStatic<Player> compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, false);
            Int2ObjectMap<Player> retryOwner = f.acceptRetryAfterTimeout();

            // Complete the OLD provider closure first, exactly as the single worker does.
            f.work.remove().run();
            assertEquals(1, f.serialized.size());
            assertSame(f.oldCopy, f.serialized.get(0));
            verify(f.level, never()).onAsyncChunkRequestFailed(any(GameVersion.class), anyInt(), anyInt());
            f.level.doTick(202);
            assertAll("an old serializer must leave the accepted retry intact",
                    () -> assertTrue(f.sent.isEmpty(), "retired snapshot must not be sent"),
                    () -> assertTrue(f.tasks().contains(INDEX), "new task must remain accepted"),
                    () -> assertSame(retryOwner, f.owners().get(INDEX), "new owner must remain queued"));

            // Preserve FIFO, including any full-batch compression jobs enqueued on MAIN.
            // This also exercises the existing cached-FULL protection without assuming
            // whether a future fix discards the old snapshot before compression.
            f.drainWork(203);
            verify(f.level, never()).onAsyncChunkRequestFailed(any(GameVersion.class), anyInt(), anyInt());
            assertEquals(2, f.serialized.size(), "both actual provider snapshots must be serialized");
            assertSame(f.oldCopy, f.serialized.get(0));
            assertSame(f.newCopy, f.serialized.get(1));
            assertEquals(1, f.sent.size(), "only the accepted retry may complete");
            f.assertPacket(f.sent.get(0), true);
            assertFalse(f.tasks().contains(INDEX));
            assertFalse(f.owners().containsKey(INDEX));
            verify(f.server.getLogger(), never()).error(anyString(), any(Throwable.class));
        }
    }

    private static final class Fixture {
        final Mode mode;
        final Level level;
        final Server server = mock(Server.class);
        final Player player = mock(Player.class);
        final LevelProvider provider;
        final BaseChunk source, oldCopy, newCopy;
        final Queue<Runnable> work = new ConcurrentLinkedQueue<>();
        final List<BaseChunk> serialized = new ArrayList<>();
        final List<DataPacket> sent = new ArrayList<>();

        Fixture(ProviderKind kind, Mode mode) throws Exception {
            this(kind, mode, mock(Level.class, CALLS_REAL_METHODS));
            assertEquals(Level.class, level.getClass(), "exercise the native structured callback branch");
        }

        Fixture(ProviderKind kind, Mode mode, Level level) throws Exception {
            this.mode = mode;
            this.level = level;
            server.asyncChunkSending = true;
            server.cacheChunks = mode != Mode.FULL_UNCACHED;
            server.chunksPerTick = 4;
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            set(level, Level.class, "server", server);
            if (kind == ProviderKind.LEVELDB) {
                LevelDBProvider leveldb = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
                provider = leveldb;
                source = mock(LevelDBChunk.class);
                oldCopy = mock(LevelDBChunk.class);
                newCopy = mock(LevelDBChunk.class);
                set(provider, LevelDBProvider.class, "level", level);
            } else {
                provider = mock(Anvil.class, CALLS_REAL_METHODS);
                source = mock(Chunk.class);
                oldCopy = mock(Chunk.class);
                newCopy = mock(Chunk.class);
                set(provider, BaseLevelProvider.class, "level", level);
            }
            doReturn(source).when(provider).getChunk(X, Z, false);
            doReturn(oldCopy, newCopy).when(source).cloneForChunkSending();
            when(source.getChanges()).thenReturn(7L);
            set(level, Level.class, "provider", provider);
            doReturn(source).when(level).getChunk(X, Z);
            doReturn(source).when(level).getChunkIfLoaded(X, Z);
            doReturn("test").when(level).getName();
            doReturn(0).when(level).getDimension();
            doReturn(DimensionData.LEGACY_DIMENSION).when(level).getDimensionData();
            doReturn(false).when(level).antiXrayEnabled();
            doReturn(true).when(level).isClientCacheDeliveryWanted();

            ExecutorService executor = mock(ExecutorService.class);
            doAnswer(call -> { work.add(call.getArgument(0)); return null; })
                    .when(executor).execute(any(Runnable.class));
            set(level, Level.class, "asyncChuckExecutor", executor);
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"})
                set(level, Level.class, name, new Object2ObjectOpenHashMap<>());
            set(level, Level.class, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(level, Level.class, "chunkSendGeneration", new Long2LongOpenHashMap());
            for (String name : new String[]{"asyncChunkRequestCallbackQueue", "asyncChunkRequestFailedQueue",
                    "normalUpdateQueue", "updateBlockEntities", "completedChunkLoads"})
                set(level, Level.class, name, new ConcurrentLinkedQueue<>());
            set(level, Level.class, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, Level.class, "entities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, Level.class, "players", new PlayerRangeMap());
            set(level, Level.class, "chunkTickList", new Long2IntOpenHashMap());
            set(level, Level.class, "changedBlocks", new Long2ObjectOpenHashMap<>());
            set(level, Level.class, "dimensionData", DimensionData.LEGACY_DIMENSION);
            set(level, Level.class, "lightQueue", new ConcurrentHashMap<>());
            set(level, Level.class, "updateQueue", new BlockUpdateScheduler(level, 0));
            set(level, Level.class, "normalUpdateQueueSize", new AtomicInteger());
            set(level, Level.class, "chunkPackets", new ConcurrentHashMap<>());
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            level.gameRules.refresh();

            configurePlayer(player, 1);
        }

        void configurePlayer(Player player, int loader) throws Exception {
            player.level = level;
            set(player, Player.class, "connected", true);
            set(player, Player.class, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
            player.usedChunks.put(INDEX, false);
            when(player.getLoaderId()).thenReturn(loader);
            when(player.getGameVersion()).thenReturn(VERSION);
            when(player.getLevel()).thenReturn(level);
            when(player.isConnected()).thenReturn(true);
            when(player.isSubChunkPullMode()).thenReturn(mode == Mode.PULL_RAW);
            doAnswer(call -> { sent.add(call.getArgument(2)); return null; })
                    .when(player).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            doCallRealMethod().when(player).sendChunk(eq(X), eq(Z), anyInt(), any(byte[].class), eq(0));
        }

        void stubOutputs(MockedStatic<NetworkChunkSerializer> serializer, MockedStatic<Player> compression,
                         boolean failOld) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                    any(), eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true))).thenAnswer(call -> {
                BaseChunk copy = call.getArgument(1);
                assertTrue(copy == oldCopy || copy == newCopy, "serialize the captured clone, not live terrain");
                serialized.add(copy);
                boolean fresh = copy == newCopy;
                if (!fresh && failOld) throw new IllegalStateException("old snapshot serialization failed");
                BinaryStream stream = new BinaryStream();
                stream.put(fresh ? NEW_PAYLOAD : OLD_PAYLOAD);
                Consumer<NetworkChunkSerializerCallback> callback = call.getArgument(2);
                callback.accept(new NetworkChunkSerializerCallback(VERSION, stream, fresh ? 3 : 2,
                        List.of(new byte[]{51}), fresh ? NEW_BIOMES : OLD_BIOMES, new byte[0]));
                return null;
            });
            compression.when(() -> Player.getChunkCacheFromData(eq(VERSION), eq(X), eq(Z), anyInt(),
                    any(byte[].class), eq(0))).thenAnswer(call -> {
                BatchPacket packet = new BatchPacket();
                packet.payload = call.getArgument(4);
                return packet;
            });
        }

        void requestAt(int tick) throws Exception {
            requestAt(tick, player);
        }

        void requestAt(int tick, Player recipient) throws Exception {
            when(server.getTick()).thenReturn(tick);
            level.requestChunk(X, Z, recipient);
            Method process = Level.class.getDeclaredMethod("processChunkRequest");
            process.setAccessible(true);
            process.invoke(level);
        }

        Int2ObjectMap<Player> acceptRetryAfterTimeout() throws Exception {
            requestAt(1);
            assertEquals(1, work.size());
            assertSame(player, owners().get(INDEX).get(1));
            // Strictly greater than 200 ticks: the boundary must not start another job.
            requestAt(201);
            assertEquals(1, work.size());
            verify(provider).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z), anyLong());
            when(source.getChanges()).thenReturn(8L);
            requestAt(202);
            assertEquals(2, work.size());
            verify(provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z), anyLong());
            Int2ObjectMap<Player> retryOwner = owners().get(INDEX);
            assertNotNull(retryOwner);
            assertSame(player, retryOwner.get(1), "same recipient, newly accepted request");
            return retryOwner;
        }

        void drainWork(int firstTick) {
            for (int jobs = 0; !work.isEmpty() && jobs < 8; jobs++) {
                work.remove().run();
                level.doTick(firstTick + jobs);
            }
            assertTrue(work.isEmpty(), "worker must settle without recursive retries");
        }

        void assertPacket(DataPacket packet, boolean fresh) {
            byte[] expectedPayload = fresh ? NEW_PAYLOAD : OLD_PAYLOAD;
            int expectedSubChunks = fresh ? 3 : 2;
            if (mode == Mode.FULL_CACHED) {
                assertArrayEquals(expectedPayload, assertInstanceOf(BatchPacket.class, packet).payload);
                return;
            }
            LevelChunkPacket column = assertInstanceOf(LevelChunkPacket.class, packet);
            assertEquals(X, column.chunkX);
            assertEquals(Z, column.chunkZ);
            assertFalse(column.cacheEnabled);
            assertEquals(mode == Mode.PULL_RAW, column.requestSubChunks);
            if (mode == Mode.PULL_RAW) {
                assertEquals(expectedSubChunks, column.subChunkLimit);
                assertArrayEquals(fresh ? new byte[]{41, 42, 0} : new byte[]{21, 22, 0}, column.data,
                        "captured biomes plus the border byte");
            } else {
                assertEquals(expectedSubChunks, column.subChunkCount);
                assertArrayEquals(expectedPayload, column.data);
            }
        }

        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, Int2ObjectMap<Player>> owners() throws Exception {
            return (ConcurrentMap<Long, Int2ObjectMap<Player>>) bucket("getChunkSendQueue");
        }

        LongSet tasks() throws Exception { return (LongSet) bucket("getChunkSendTasks"); }

        void assertNoOutstandingDelivery() throws Exception {
            assertAll("departed recipients must not leave delivery ownership behind",
                    () -> assertFalse(tasks().contains(INDEX)),
                    () -> assertFalse(owners().containsKey(INDEX)),
                    () -> assertFalse(((LongSet) bucket("getPendingChunkRequests")).contains(INDEX)),
                    () -> assertFalse(((Long2LongMap) field(level, "chunkSendTaskStartTick")).containsKey(INDEX)),
                    () -> assertTrue(((Queue<?>) field(level, "asyncChunkRequestCallbackQueue")).isEmpty()),
                    () -> assertTrue(((Queue<?>) field(level, "asyncChunkRequestFailedQueue")).isEmpty()));
        }

        private Object bucket(String name) throws Exception {
            return bucket(name, VERSION);
        }

        private Object bucket(String name, GameVersion version) throws Exception {
            Method method = Level.class.getDeclaredMethod(name, GameVersion.class);
            method.setAccessible(true);
            return method.invoke(level, version);
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

    private static Object field(Level level, String name) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(level);
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
