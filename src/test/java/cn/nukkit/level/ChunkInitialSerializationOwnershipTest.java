package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
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
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native serializer completions belong to the request accepted before their snapshot. */
@Isolated("MockServer and static serializer/compression fixtures")
@Timeout(20)
class ChunkInitialSerializationOwnershipTest {
    static final GameVersion VERSION = GameVersion.V1_26_50;
    static final int X = 3, Z = 5;
    static final long INDEX = Level.chunkHash(X, Z);
    static final byte[] OLD_PAYLOAD = {11, 12}, NEW_PAYLOAD = {31, 32};
    enum ProviderKind { LEVELDB, ANVIL }
    enum Mode { PULL_RAW, FULL_UNCACHED, FULL_CACHED }

    @BeforeAll static void initializeServer() { MockServer.init(); }

    @ParameterizedTest
    @CsvSource({"LEVELDB,PULL_RAW,false", "ANVIL,PULL_RAW,false", "LEVELDB,FULL_UNCACHED,false",
            "ANVIL,FULL_UNCACHED,false", "LEVELDB,FULL_CACHED,false", "ANVIL,FULL_CACHED,false",
            "LEVELDB,PULL_RAW,true", "ANVIL,PULL_RAW,true", "LEVELDB,FULL_UNCACHED,true",
            "ANVIL,FULL_UNCACHED,true", "LEVELDB,FULL_CACHED,true", "ANVIL,FULL_CACHED,true"})
    void retiredInitialSerializerCannotCompleteOrClearTheAcceptedRetry(
            ProviderKind provider, Mode mode, boolean failOld) throws Exception {
        Fixture f = new Fixture(provider, mode);
        try (var serializer = mockStatic(NetworkChunkSerializer.class); var compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, failOld);
            f.requestAt(1, f.player);
            f.requestAt(201, f.player);
            assertEquals(1, f.work.size(), "timeout is strictly greater than 200 ticks");
            when(f.source.getMutationRevision()).thenReturn(8L);
            f.requestAt(202, f.player);
            assertEquals(2, f.work.size());
            Int2ObjectMap<Player> retry = f.owners().get(INDEX);
            assertSame(f.player, retry.get(1), "retry may have the identical player and epoch");

            f.work.remove().run();
            f.level.doTick(202);
            assertAll("retired serializer must leave the accepted retry intact",
                    () -> assertTrue(f.sent.isEmpty()), () -> assertTrue(f.tasks().contains(INDEX)),
                    () -> assertSame(retry, f.owners().get(INDEX)));
            f.drainWork(203);
            assertEquals(List.of(f.oldCopy, f.newCopy), f.serialized);
            assertEquals(1, f.sent.size());
            f.assertFreshPacket();
            f.assertNoOutstandingDelivery();
        }
    }

    @ParameterizedTest @EnumSource(ProviderKind.class)
    void joiningRecipientSharesTheAcceptedSerialization(ProviderKind provider) throws Exception {
        Fixture f = new Fixture(provider, Mode.FULL_CACHED);
        Player joining = f.player(2);
        try (var serializer = mockStatic(NetworkChunkSerializer.class); var compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, false);
            f.requestAt(1, f.player);
            f.requestAt(2, joining);
            assertEquals(1, f.work.size());
            f.drainWork(3);
            verify(f.provider).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            assertEquals(List.of(f.oldCopy), f.serialized);
            assertEquals(2, f.sent.size());
            verify(joining).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            f.assertNoOutstandingDelivery();
        }
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false", "LEVELDB,true", "ANVIL,false", "ANVIL,true"})
    void remainingRecipientCompletesAfterPeerDepartsDuringInitialOrDeferredWork(
            ProviderKind provider, boolean afterSelection) throws Exception {
        Fixture f = new Fixture(provider, Mode.FULL_CACHED);
        Player survivor = f.player(2);
        try (var serializer = mockStatic(NetworkChunkSerializer.class); var compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, false);
            f.requestAt(1, f.player);
            f.requestAt(2, survivor);
            assertEquals(1, f.work.size(), "both recipients share one initial native request");
            verify(f.provider).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            if (afterSelection) {
                f.work.remove().run();
                f.level.doTick(2);
                assertEquals(1, f.work.size(), "deferred compression captured both recipients");
            }
            f.player.usedChunks.clear();
            if (afterSelection) {
                f.work.remove().run();
                f.level.doTick(3);
                assertTrue(f.sent.isEmpty(), "changed compression cohort must retry before delivery");
                verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
                assertEquals(1, f.work.size(), "replacement serializer starts immediately before tick 200");
            }
            f.drainWork(4);
            verify(f.player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
            verify(survivor).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            verify(f.provider, times(afterSelection ? 2 : 1))
                    .requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            assertEquals(afterSelection ? List.of(f.oldCopy, f.newCopy) : List.of(f.oldCopy), f.serialized);
            assertEquals(1, f.sent.size());
            assertArrayEquals(afterSelection ? NEW_PAYLOAD : OLD_PAYLOAD,
                    assertInstanceOf(BatchPacket.class, f.sent.get(0)).payload);
            f.assertNoOutstandingDelivery();
        }
    }

    @ParameterizedTest @EnumSource(ProviderKind.class)
    void deferredNullSourceRetriesAfterReloadWithoutOrphaningTask(ProviderKind provider) throws Exception {
        Fixture f = new Fixture(provider, Mode.FULL_CACHED);
        try (var serializer = mockStatic(NetworkChunkSerializer.class); var compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, false);
            f.requestAt(1, f.player);
            f.work.remove().run();
            f.level.doTick(1);
            assertEquals(1, f.work.size(), "full compression must be outstanding before unloading");
            Int2ObjectMap<Player> original = f.owners().get(INDEX);
            doReturn(null).when(f.level).getChunkIfLoaded(X, Z);
            BaseChunk fresh = provider == ProviderKind.LEVELDB ? mock(LevelDBChunk.class) : mock(Chunk.class);
            doReturn(f.newCopy).when(fresh).cloneForChunkSending();
            when(fresh.getChanges()).thenReturn(7L);
            when(fresh.getMutationRevision()).thenReturn(7L);
            boolean[] reloaded = {false};
            doAnswer(call -> {
                reloaded[0] = true;
                doReturn(fresh).when(f.level).getChunkIfLoaded(X, Z);
                doReturn(fresh).when(f.provider).getChunk(X, Z, false);
                return fresh;
            }).when(f.level).getChunk(X, Z);

            when(f.server.getTick()).thenReturn(3);
            f.work.remove().run();
            f.level.doTick(3);
            assertTrue(reloaded[0], "retry must reload only after old completion sees no live source");
            assertTrue(f.sent.isEmpty(), "unloaded source cannot deliver its old full batch");
            verify(f.source, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
            verify(fresh, never()).setChunkPacket(any(GameVersion.class), any(BatchPacket.class));
            verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            assertEquals(1, f.work.size(), "retry must start immediately without the 200-tick timeout");
            assertEquals(List.of(f.oldCopy), f.serialized);
            assertTrue(f.tasks().contains(INDEX));
            assertNotSame(original, f.owners().get(INDEX));
            assertSame(f.player, f.owners().get(INDEX).get(1));
            f.drainWork(4);
            assertEquals(List.of(f.oldCopy, f.newCopy), f.serialized);
            assertEquals(1, f.sent.size());
            f.assertFreshPacket();
            verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            f.assertNoOutstandingDelivery();
        }
    }

    @ParameterizedTest @EnumSource(ProviderKind.class)
    void sharedSerializerCompletionPreservesReplacedCohortAndCompletesSibling(ProviderKind provider) throws Exception {
        Fixture f = new Fixture(provider, Mode.FULL_UNCACHED);
        GameVersion other = GameVersion.V1_20_0;
        Player sibling = f.player(2);
        when(sibling.getGameVersion()).thenReturn(other);
        try (var serializer = mockStatic(NetworkChunkSerializer.class)) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class), any(),
                    eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true))).thenAnswer(call -> {
                BaseChunk copy = call.getArgument(1);
                boolean fresh = copy == f.newCopy;
                assertSame(fresh ? f.newCopy : f.oldCopy, copy);
                f.serialized.add(copy);
                ObjectSet<GameVersion> versions = call.getArgument(0);
                assertEquals(fresh ? java.util.Set.of(VERSION) : java.util.Set.of(VERSION, other), versions);
                Consumer<NetworkChunkSerializerCallback> callback = call.getArgument(2);
                for (GameVersion version : versions) {
                    BinaryStream stream = new BinaryStream();
                    stream.put(fresh ? NEW_PAYLOAD : OLD_PAYLOAD);
                    callback.accept(new NetworkChunkSerializerCallback(version, stream, 2, null, null, null));
                }
                return null;
            });
            f.level.requestChunk(X, Z, f.player);
            f.requestAt(1, sibling);
            assertEquals(1, f.work.size(), "one native serializer owns both protocol cohorts");
            verify(f.provider).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            f.requestAt(202, f.player);
            assertEquals(2, f.work.size(), "only A starts a timeout retry");
            Int2ObjectMap<Player> replacement = f.owners().get(INDEX);
            f.work.remove().run();
            f.level.doTick(202);
            verify(f.player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
            verify(sibling).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            assertTrue(f.tasks().contains(INDEX), "old A callback cannot retire its replacement");
            assertSame(replacement, f.owners().get(INDEX));
            assertFalse(((LongSet) f.bucket("getChunkSendTasks", other)).contains(INDEX));
            assertFalse(((ConcurrentMap<?, ?>) f.bucket("getChunkSendQueue", other)).containsKey(INDEX));
            assertEquals(202L, ((Long2LongMap) field(f.level, "chunkSendTaskStartTick")).get(INDEX),
                    "B completion must preserve A's live retry timestamp");
            assertEquals(1, f.sent.size());
            assertArrayEquals(OLD_PAYLOAD, assertInstanceOf(LevelChunkPacket.class, f.sent.get(0)).data);
            f.drainWork(203);
            assertEquals(List.of(f.oldCopy, f.newCopy), f.serialized);
            assertEquals(2, f.sent.size());
            assertArrayEquals(NEW_PAYLOAD, assertInstanceOf(LevelChunkPacket.class, f.sent.get(1)).data);
            verify(f.player).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            verify(sibling).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            f.assertNoOutstandingDelivery();
            assertFalse(((LongSet) f.bucket("getChunkSendTasks", other)).contains(INDEX));
            assertFalse(((ConcurrentMap<?, ?>) f.bucket("getChunkSendQueue", other)).containsKey(INDEX));
            assertFalse(((LongSet) f.bucket("getPendingChunkRequests", other)).contains(INDEX));
        }
    }

    @ParameterizedTest
    @CsvSource({"PULL_RAW,false", "PULL_RAW,true", "FULL_CACHED,false", "FULL_CACHED,true"})
    void lastDepartureRetiresInitialAndDeferredWorkWithoutWaitingForTimeout(Mode mode, boolean failOld) throws Exception {
        Fixture f = new Fixture(ProviderKind.LEVELDB, mode);
        try (var serializer = mockStatic(NetworkChunkSerializer.class); var compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, mode != Mode.FULL_CACHED && failOld);
            if (mode == Mode.FULL_CACHED && failOld) compression.when(() -> Player.getChunkCacheFromData(
                    eq(VERSION), eq(X), eq(Z), anyInt(), eq(OLD_PAYLOAD), eq(0)))
                    .thenThrow(new IllegalStateException("retired compression failed"));
            f.requestAt(1, f.player);
            if (mode == Mode.FULL_CACHED) {
                f.work.remove().run();
                f.level.doTick(1);
                assertEquals(1, f.work.size(), "deferred compression must already be queued");
            }
            f.player.usedChunks.clear();
            f.requestAt(2, f.player);
            f.assertNoOutstandingDelivery();
            Player replacement = f.player(2);
            f.requestAt(3, replacement);
            assertEquals(2, f.work.size(), "new recipient starts while old work is still queued");
            Int2ObjectMap<Player> owner = f.owners().get(INDEX);
            f.work.remove().run();
            f.level.doTick(3);
            assertTrue(f.sent.isEmpty());
            assertTrue(f.tasks().contains(INDEX));
            assertSame(owner, f.owners().get(INDEX), "old completion cannot retire replacement");
            f.drainWork(4);
            assertEquals(1, f.sent.size());
            f.assertFreshPacket();
            f.assertNoOutstandingDelivery();
        }
    }

    @ParameterizedTest @EnumSource(ProviderKind.class)
    void oneProtocolsFailureDoesNotDisableAnotherProtocolsTimeout(ProviderKind provider) throws Exception {
        Fixture f = new Fixture(provider, Mode.FULL_UNCACHED);
        Player sibling = f.player(2);
        GameVersion other = GameVersion.V1_20_0;
        when(sibling.getGameVersion()).thenReturn(other);
        try (var serializer = mockStatic(NetworkChunkSerializer.class); var compression = mockStatic(Player.class)) {
            f.stubOutputs(serializer, compression, true);
            f.requestAt(1, f.player);
            f.requestAt(100, sibling);
            f.work.remove().run();
            f.level.doTick(100);
            assertFalse(f.tasks().contains(INDEX));
            assertTrue(((LongSet) f.bucket("getChunkSendTasks", other)).contains(INDEX));
            f.requestAt(300, sibling);
            verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
            f.requestAt(301, sibling);
            verify(f.provider, times(3)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
        }
    }

    /** Reuse PR's tick fixture instead of importing unrelated production fixture state. */
    static final class Fixture {
        final ChunkDeferredCompressionTest.Fixture state = new ChunkDeferredCompressionTest.Fixture(VERSION);
        final Level level = state.level;
        final Server server = state.server;
        final Queue<Runnable> work = state.work;
        final List<BaseChunk> serialized = new ArrayList<>();
        final List<DataPacket> sent = new ArrayList<>();
        final Mode mode;
        final LevelProvider provider;
        final BaseChunk source, oldCopy, newCopy;
        final Player player;

        Fixture(ProviderKind kind, Mode mode) throws Exception {
            this.mode = mode;
            server.cacheChunks = mode != Mode.FULL_UNCACHED;
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            if (kind == ProviderKind.LEVELDB) {
                provider = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
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
            when(source.getMutationRevision()).thenReturn(7L);
            set(level, Level.class, "provider", provider);
            doReturn(source).when(level).getChunk(X, Z);
            doReturn(source).when(level).getChunkIfLoaded(X, Z);
            doReturn("test").when(level).getName();
            doReturn(DimensionData.LEGACY_DIMENSION).when(level).getDimensionData();
            doReturn(false).when(level).antiXrayEnabled();
            doReturn(true).when(level).isClientCacheDeliveryWanted();
            player = player(1);
        }

        Player player(int loader) throws Exception {
            Player result = state.player(loader, mode == Mode.PULL_RAW
                    ? ChunkDeferredCompressionTest.Mode.PULL : ChunkDeferredCompressionTest.Mode.FULL);
            set(result, Player.class, "connected", true);
            doAnswer(call -> { sent.add(call.getArgument(2)); return null; })
                    .when(result).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            doCallRealMethod().when(result).sendChunk(eq(X), eq(Z), anyInt(), any(byte[].class), eq(0));
            return result;
        }

        void stubOutputs(MockedStatic<NetworkChunkSerializer> serializer, MockedStatic<Player> compression, boolean failOld) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class), any(),
                    eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true))).thenAnswer(call -> {
                BaseChunk copy = call.getArgument(1);
                serialized.add(copy);
                boolean fresh = copy == newCopy;
                if (!fresh && failOld) throw new IllegalStateException("old snapshot serialization failed");
                BinaryStream stream = new BinaryStream();
                stream.put(fresh ? NEW_PAYLOAD : OLD_PAYLOAD);
                Consumer<NetworkChunkSerializerCallback> callback = call.getArgument(2);
                callback.accept(new NetworkChunkSerializerCallback(VERSION, stream, fresh ? 3 : 2,
                        List.of(new byte[]{51}), fresh ? new byte[]{41, 42} : new byte[]{21, 22}, new byte[0]));
                return null;
            });
            compression.when(() -> Player.getChunkCacheFromData(eq(VERSION), eq(X), eq(Z), anyInt(), any(byte[].class), eq(0)))
                    .thenAnswer(call -> { BatchPacket packet = new BatchPacket(); packet.payload = call.getArgument(4); return packet; });
        }

        void requestAt(int tick, Player recipient) throws Exception {
            when(server.getTick()).thenReturn(tick);
            level.requestChunk(X, Z, recipient);
            Method process = Level.class.getDeclaredMethod("processChunkRequest");
            process.setAccessible(true);
            process.invoke(level);
        }

        void drainWork(int tick) {
            for (int jobs = 0; !work.isEmpty() && jobs < 8; jobs++) {
                work.remove().run();
                level.doTick(tick + jobs);
            }
            assertTrue(work.isEmpty(), "worker must settle without recursive retries");
        }

        void assertFreshPacket() {
            DataPacket packet = sent.get(0);
            if (mode == Mode.FULL_CACHED) assertArrayEquals(NEW_PAYLOAD, assertInstanceOf(BatchPacket.class, packet).payload);
            else assertArrayEquals(mode == Mode.PULL_RAW ? new byte[]{41, 42, 0} : NEW_PAYLOAD,
                    assertInstanceOf(LevelChunkPacket.class, packet).data);
        }

        void assertNoOutstandingDelivery() throws Exception {
            assertFalse(tasks().contains(INDEX), "task retired");
            assertFalse(owners().containsKey(INDEX), "recipient owner retired");
            assertFalse(((LongSet) bucket("getPendingChunkRequests", VERSION)).contains(INDEX), "pending retired");
            assertFalse(((Long2LongMap) field(level, "chunkSendTaskStartTick")).containsKey(INDEX), "timestamp retired");
            Map<?, ?> deferred = (Map<?, ?>) field(level, "deferredChunkCompressions");
            if (deferred != null) for (Object bucket : deferred.values())
                assertFalse(((Long2ObjectMap<?>) bucket).containsKey(INDEX), "compression owner retired");
        }

        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, Int2ObjectMap<Player>> owners() throws Exception {
            return (ConcurrentMap<Long, Int2ObjectMap<Player>>) bucket("getChunkSendQueue", VERSION);
        }
        LongSet tasks() throws Exception { return (LongSet) bucket("getChunkSendTasks", VERSION); }
        Object bucket(String name, GameVersion version) throws Exception {
            Method method = Level.class.getDeclaredMethod(name, GameVersion.class);
            method.setAccessible(true);
            return method.invoke(level, version);
        }
    }

    static Object field(Level level, String name) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(level);
    }
    static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
