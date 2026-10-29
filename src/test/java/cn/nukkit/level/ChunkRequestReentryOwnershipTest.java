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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Provider/lookup reentry cannot transfer an old dispatch to an accepted replacement. */
@Isolated("MockServer and static serializer fixtures")
@Timeout(20)
class ChunkRequestReentryOwnershipTest {
    private static final GameVersion VERSION = GameVersion.V1_26_50;
    private static final GameVersion SIBLING_VERSION = GameVersion.V1_20_0;
    private static final int X = 3, Z = 5;
    private static final long INDEX = Level.chunkHash(X, Z);
    private static final byte[] OLD_PAYLOAD = {11, 12}, NEW_PAYLOAD = {31, 32};

    enum ProviderKind { LEVELDB, ANVIL }
    enum Boundary { LEVEL_LOOKUP, PROVIDER_LOOKUP }

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false", "LEVELDB,true", "ANVIL,false", "ANVIL,true"})
    void lookupBeforeProviderCannotSubmitTheReplacementTwice(ProviderKind kind, boolean async) throws Exception {
        exercise(kind, async, Boundary.LEVEL_LOOKUP);
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false", "LEVELDB,true", "ANVIL,false", "ANVIL,true"})
    void providerLookupCannotBindItsOldResultToTheReplacement(ProviderKind kind, boolean async) throws Exception {
        exercise(kind, async, Boundary.PROVIDER_LOOKUP);
    }

    private static void exercise(ProviderKind kind, boolean async, Boundary boundary) throws Exception {
        Fixture f = new Fixture(kind, boundary);
        try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
            f.stubSerializer(serializer);
            if (boundary == Boundary.LEVEL_LOOKUP) {
                // The coordinate-wide start tick can belong to another protocol bucket.
                // Its worker is deliberately parked, as on a busy executor; no private
                // owner/task/timestamp state is manufactured to force the timeout.
                f.server.asyncChunkSending = true;
                f.requestAt(1, f.sibling);
                assertEquals(1, f.work.size());
                f.work.clear();
                assertTrue(f.tasks(SIBLING_VERSION).contains(INDEX));
            }
            f.server.asyncChunkSending = async;
            f.armed = true;
            f.requestAt(1, f.player);
            assertTrue(f.reentered, "lookup must accept a real replacement at tick 202");

            if (async) {
                assertNotNull(f.replacementOwner);
                assertSame(f.replacementOwner, f.owners().get(INDEX));
                assertTrue(f.tasks(VERSION).contains(INDEX));
                if (boundary == Boundary.PROVIDER_LOOKUP && f.work.size() > 1) {
                    // Completion order is controlled independently of submission order.
                    // The old invocation entered the provider before the nested retry;
                    // already-entered work may finish, but cannot complete that retry.
                    f.work.remove(f.work.size() - 1).run();
                    f.level.doTick(202);
                    assertAll("late outer result leaves the nested request intact",
                            () -> assertTrue(f.sent.isEmpty()),
                            () -> assertSame(f.replacementOwner, f.owners().get(INDEX)),
                            () -> assertTrue(f.tasks(VERSION).contains(INDEX)));
                }
                while (!f.work.isEmpty()) {
                    f.work.remove(0).run();
                    f.level.doTick(203);
                }
            }

            verify(f.server.getLogger(), never()).error(anyString(), any(Throwable.class));
            assertEquals(1, f.freshSerializations, "the current snapshot is serialized exactly once");
            assertEquals(1, f.sent.size(), "only the accepted replacement is delivered");
            LevelChunkPacket packet = assertInstanceOf(LevelChunkPacket.class, f.sent.get(0));
            assertArrayEquals(NEW_PAYLOAD, packet.data);
            assertFalse(f.tasks(VERSION).contains(INDEX));
            assertFalse(f.owners().containsKey(INDEX));
            if (boundary == Boundary.LEVEL_LOOKUP) {
                // One parked sibling plus the nested replacement; the retired outer
                // lookup must not enter the provider with the replacement's context.
                verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z), anyLong());
            }
        }
    }

    private static final class Fixture {
        final Boundary boundary;
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final Player player = mock(Player.class), sibling = mock(Player.class);
        final LevelProvider provider;
        final BaseChunk oldSource, newSource, oldCopy, newCopy;
        final List<Runnable> work = new ArrayList<>();
        final List<DataPacket> sent = new ArrayList<>();
        BaseChunk current;
        Int2ObjectMap<Player> replacementOwner;
        boolean armed, reentered;
        int freshSerializations;

        Fixture(ProviderKind kind, Boundary boundary) throws Exception {
            this.boundary = boundary;
            assertEquals(Level.class, level.getClass());
            server.cacheChunks = false;
            server.chunksPerTick = 4;
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            set(level, Level.class, "server", server);
            if (kind == ProviderKind.LEVELDB) {
                LevelDBProvider leveldb = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
                provider = leveldb;
                oldSource = mock(LevelDBChunk.class);
                newSource = mock(LevelDBChunk.class);
                oldCopy = mock(LevelDBChunk.class);
                newCopy = mock(LevelDBChunk.class);
                set(provider, LevelDBProvider.class, "level", level);
            } else {
                provider = mock(Anvil.class, CALLS_REAL_METHODS);
                oldSource = mock(Chunk.class);
                newSource = mock(Chunk.class);
                oldCopy = mock(Chunk.class);
                newCopy = mock(Chunk.class);
                set(provider, BaseLevelProvider.class, "level", level);
            }
            current = oldSource;
            doReturn(oldCopy).when(oldSource).cloneForChunkSending();
            doReturn(newCopy).when(newSource).cloneForChunkSending();
            for (BaseChunk chunk : new BaseChunk[]{oldSource, newSource}) {
                when(chunk.getChanges()).thenReturn(7L);
            }
            set(level, Level.class, "provider", provider);
            doAnswer(call -> lookup(Boundary.LEVEL_LOOKUP)).when(level).getChunk(X, Z);
            doAnswer(call -> lookup(Boundary.PROVIDER_LOOKUP)).when(provider).getChunk(X, Z, false);
            doAnswer(call -> current).when(level).getChunkIfLoaded(X, Z);
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
            initializePlayer(player, 1, VERSION);
            initializePlayer(sibling, 2, SIBLING_VERSION);
            doAnswer(call -> { sent.add(call.getArgument(2)); return null; })
                    .when(player).sendChunk(eq(X), eq(Z), any(DataPacket.class));
            doCallRealMethod().when(player).sendChunk(eq(X), eq(Z), anyInt(), any(byte[].class), eq(0));
        }

        private void initializePlayer(Player recipient, int loader, GameVersion version) throws Exception {
            recipient.level = level;
            set(recipient, Player.class, "connected", true);
            set(recipient, Player.class, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
            recipient.usedChunks.put(INDEX, false);
            when(recipient.getLoaderId()).thenReturn(loader);
            when(recipient.getGameVersion()).thenReturn(version);
            when(recipient.getLevel()).thenReturn(level);
            when(recipient.isConnected()).thenReturn(true);
        }

        private BaseChunk lookup(Boundary at) throws Exception {
            BaseChunk result = current;
            if (armed && boundary == at) {
                armed = false;
                reentered = true;
                current = newSource;
                requestAt(202, player);
                replacementOwner = owners().get(INDEX);
            }
            return result;
        }

        void stubSerializer(MockedStatic<NetworkChunkSerializer> serializer) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                    any(), eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true))).thenAnswer(call -> {
                BaseChunk input = call.getArgument(1);
                boolean fresh = input == newSource || input == newCopy;
                ObjectSet<GameVersion> protocols = call.getArgument(0);
                if (fresh && protocols.contains(VERSION)) ++freshSerializations;
                Consumer<NetworkChunkSerializerCallback> callback = call.getArgument(2);
                for (GameVersion version : protocols) {
                    BinaryStream stream = new BinaryStream();
                    stream.put(fresh ? NEW_PAYLOAD : OLD_PAYLOAD);
                    callback.accept(new NetworkChunkSerializerCallback(version, stream, 2,
                            List.of(new byte[]{51}), new byte[]{41, 42}, new byte[0]));
                }
                return null;
            });
        }

        void requestAt(int tick, Player recipient) throws Exception {
            when(server.getTick()).thenReturn(tick);
            level.requestChunk(X, Z, recipient);
            Method process = Level.class.getDeclaredMethod("processChunkRequest");
            process.setAccessible(true);
            try {
                process.invoke(level);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Error error) throw error;
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw failure;
            }
        }

        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, Int2ObjectMap<Player>> owners() throws Exception {
            return (ConcurrentMap<Long, Int2ObjectMap<Player>>) bucket("getChunkSendQueue", VERSION);
        }

        LongSet tasks(GameVersion version) throws Exception {
            return (LongSet) bucket("getChunkSendTasks", version);
        }

        private Object bucket(String name, GameVersion version) throws Exception {
            Method method = Level.class.getDeclaredMethod(name, GameVersion.class);
            method.setAccessible(true);
            return method.invoke(level, version);
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
