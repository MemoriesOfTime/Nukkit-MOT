package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A running farm must not invalidate every initial chunk delivery forever. */
@Isolated("MockServer")
@Timeout(60)
class ChunkMutationStarvationTest {
    private static final int X = -3, Z = -5;
    private static final long INDEX = Level.chunkHash(X, Z);

    @BeforeAll static void init() { MockServer.init(); }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false,589", "LEVELDB,true,589", "ANVIL,false,589", "ANVIL,true,589",
            "LEVELDB,false,2225", "LEVELDB,true,2225", "ANVIL,false,2225", "ANVIL,true,2225"})
    void continuouslyMutatedNativeChunkStillReachesWaitingPlayer(String kind, boolean cached, int protocol) throws Exception {
        Fixture f = new Fixture(kind, cached, protocol);
        f.level.requestChunk(X, Z, f.player);
        for (int tick = 1; tick <= 4; tick++) {
            f.advance(tick);
            if (tick <= 3) assertEquals(0, f.sent.get(), "Never send a snapshot older than the live chunk");
        }
        assertEquals(1, f.sent.get(), "A continuously changing chunk must have bounded delivery latency");
        assertTrue(f.asyncSubmissions.get() <= 3, "Do not serialize and discard the same hot chunk forever");
        assertTrue(f.server.asyncChunkSending, "Fallback must never disable async sending globally");
        f.process();
        assertEquals(1, f.sent.get(), "Accepted recipients must not receive duplicate full chunks");
    }

    @Test
    void twoHotChunksShareOneMainFallbackPerTick() throws Exception {
        Fixture f = new Fixture("LEVELDB", false, 2225);
        Player second = f.addPlayer(X + 2, Z, 2225, 2);
        f.level.requestChunk(X, Z, f.player);
        f.level.requestChunk(X + 2, Z, second);
        for (int tick = 1; tick <= 3; tick++) f.advance(tick);
        assertEquals(0, f.sent.get());
        f.advance(4);
        assertEquals(1, f.sent.get(), "Only one coordinate may fall back in this tick");
        f.advance(5);
        assertEquals(2, f.sent.get(), "The other hot coordinate must progress next tick");
        assertEquals(2, new java.util.HashSet<>(f.deliveries).size());
    }

    @Test
    void oneHotCoordinateGroupsProtocolsWithoutDuplicateDelivery() throws Exception {
        Fixture f = new Fixture("LEVELDB", true, 2225);
        Player older = f.addPlayer(X, Z, 589, 2);
        f.level.requestChunk(X, Z, f.player);
        f.level.requestChunk(X, Z, older);
        for (int tick = 1; tick <= 4; tick++) f.advance(tick);
        assertEquals(2, f.sent.get());
        assertEquals(3, f.asyncSubmissions.get(), "Protocols must share the native serializer invocation");
        assertEquals(2, new java.util.HashSet<>(f.deliveries).size());
    }

    @Test
    void lastRecipientDepartureRetiresRetryStateAndNewArrivalStartsAsync() throws Exception {
        Fixture f = new Fixture("LEVELDB", false, 2225);
        f.level.requestChunk(X, Z, f.player);
        f.advance(1);
        f.advance(2);
        f.player.usedChunks.clear();
        f.process();
        int submissions = f.asyncSubmissions.get();
        Player arrival = f.addPlayer(X, Z, 2225, 3);
        f.level.requestChunk(X, Z, arrival);
        f.process();
        assertEquals(submissions + 1, f.asyncSubmissions.get());
        assertEquals(0, f.sent.get());
    }

    @Test
    void oldCallbackCannotChargeTimedOutReplacementWithStaleRetries() throws Exception {
        Fixture f = new Fixture("LEVELDB", false, 2225);
        f.level.requestChunk(X, Z, f.player);
        when(f.server.getTick()).thenReturn(1);
        f.process();
        f.work.remove().run();
        Object old = ((Queue<?>) field(f.level, "asyncChunkRequestCallbackQueue")).remove();
        when(f.server.getTick()).thenReturn(300);
        f.level.requestChunk(X, Z, f.player);
        f.process();
        f.source.setBlock(1, 1, 1, BlockID.DIRT);
        f.deliver(old);
        assertEquals(0, f.sent.get());
        assertEquals(1, f.work.size(), "Late old result must leave the replacement serializer alone");
    }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false,2225", "LEVELDB,true,2225", "ANVIL,false,2225", "ANVIL,true,2225"})
    void savingDoesNotMakeAnOldNetworkSnapshotFreshAgain(String kind, boolean cached, int protocol) throws Exception {
        Fixture f = new Fixture(kind, cached, protocol);
        f.source.setChanged(false);
        f.level.requestChunk(X, Z, f.player);
        f.process();
        f.work.remove().run();
        f.source.setBlock(1, 1, 1, BlockID.DIRT);
        f.source.setChanged(false);
        assertEquals(0, f.source.getChanges(), "Save-counter ABA must not validate old terrain");
        f.deliverResults();
        assertEquals(0, f.sent.get());
        f.process();
        f.work.remove().run();
        f.deliverResults();
        assertEquals(1, f.sent.get(), "A stable fresh snapshot still completes asynchronously");
    }

    @Test
    void replacingChunkWithTheSameRevisionRejectsItsOldSnapshot() throws Exception {
        Fixture f = new Fixture("LEVELDB", false, 2225);
        f.level.requestChunk(X, Z, f.player);
        f.process();
        f.work.remove().run();
        f.terrain.put(INDEX, f.source.cloneForChunkSending());
        f.deliverResults();
        assertEquals(0, f.sent.get());
        f.process();
        f.work.remove().run();
        f.deliverResults();
        assertEquals(1, f.sent.get());
    }

    @Test
    void mutationDuringCompressionRejectsThePacketBeforePublication() throws Exception {
        Fixture f = new Fixture("LEVELDB", true, 2225);
        f.level.requestChunk(X, Z, f.player);
        f.process();
        f.work.remove().run();
        try (var packets = mockStatic(Player.class)) {
            packets.when(() -> Player.getChunkCacheFromData(any(GameVersion.class), anyInt(), anyInt(),
                    anyInt(), any(byte[].class), anyInt())).thenAnswer(call -> {
                        f.source.setBlock(1, 1, 1, BlockID.DIRT);
                        return new cn.nukkit.network.protocol.BatchPacket();
                    });
            f.deliverResults();
        }
        assertEquals(0, f.sent.get());
        assertNull(f.source.getChunkPacket(GameVersion.byProtocol(2225, false)));
    }

    @Test
    void mutationDuringCachePublicationRejectsAndRemovesThePacket() throws Exception {
        Fixture f = new Fixture("LEVELDB", true, 2225);
        BaseChunk source = spy(f.source);
        f.terrain.put(INDEX, source);
        doAnswer(call -> {
            call.callRealMethod();
            source.setBlock(1, 1, 1, BlockID.DIRT);
            return null;
        }).when(source).setChunkPacket(any(GameVersion.class), any(cn.nukkit.network.protocol.BatchPacket.class));
        f.level.requestChunk(X, Z, f.player);
        f.process();
        f.work.remove().run();
        f.deliverResults();
        assertEquals(0, f.sent.get());
        assertNull(source.getChunkPacket(GameVersion.byProtocol(2225, false)));
    }

    private static final class Fixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final Player player;
        final BaseChunk source;
        final LevelProvider provider;
        final Map<Long, BaseChunk> terrain = new HashMap<>();
        final List<String> deliveries = new ArrayList<>();
        final Queue<Runnable> work = new ArrayDeque<>();
        final AtomicInteger sent = new AtomicInteger(), asyncSubmissions = new AtomicInteger();

        Fixture(String kind, boolean cached, int protocol) throws Exception {
            assertEquals(Level.class, level.getClass());
            server.asyncChunkSending = true;
            server.cacheChunks = cached;
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            set(level, "server", server);
            set(level, "dimensionData", new DimensionData(0, 0, 15));
            doReturn(0).when(level).getDimension();
            doReturn(new DimensionData(0, 0, 15)).when(level).getDimensionData();
            if (kind.equals("LEVELDB")) {
                LevelDBProvider db = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
                set(db, "level", level);
                doReturn(0).when(db).getMinBlockY();
                doReturn(15).when(db).getMaxBlockY();
                provider = db;
                source = LevelDBChunk.getEmptyChunk(X, Z, db);
            } else {
                Anvil anvil = mock(Anvil.class, CALLS_REAL_METHODS);
                set(anvil, "level", level);
                set(anvil, "lastChunk", new ThreadLocal<>());
                set(anvil, "chunks", new java.util.concurrent.ConcurrentHashMap<>());
                provider = anvil;
                source = Chunk.getEmptyChunk(X, Z, anvil);
            }
            source.setBlock(1, 1, 1, BlockID.STONE);
            terrain.put(INDEX, source);
            set(level, "provider", provider);
            doAnswer(call -> terrain.get(Level.chunkHash(call.getArgument(0), call.getArgument(1))))
                    .when(provider).getChunk(anyInt(), anyInt(), eq(false));
            doAnswer(call -> terrain.get(Level.chunkHash(call.getArgument(0), call.getArgument(1))))
                    .when(level).getChunk(anyInt(), anyInt());
            doAnswer(call -> terrain.get(Level.chunkHash(call.getArgument(0), call.getArgument(1))))
                    .when(level).getChunkIfLoaded(anyInt(), anyInt());
            doReturn("starvation-test").when(level).getName();
            doReturn(0).when(level).getDimension();
            doReturn(new DimensionData(0, 0, 15)).when(level).getDimensionData();
            doReturn(false).when(level).antiXrayEnabled();
            doReturn(false).when(level).isClientCacheDeliveryWanted();
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests", "chunkRequestEpochs"})
                set(level, name, new Object2ObjectOpenHashMap<>());
            set(level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(level, "asyncChunkRequestCallbackQueue", new ConcurrentLinkedQueue<>());
            set(level, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
            ExecutorService executor = mock(ExecutorService.class);
            doAnswer(call -> { asyncSubmissions.incrementAndGet(); work.add(call.getArgument(0)); return null; })
                    .when(executor).execute(any(Runnable.class));
            set(level, "asyncChuckExecutor", executor);
            player = addPlayer(X, Z, protocol, 1);
        }

        Player addPlayer(int x, int z, int protocol, int loader) throws Exception {
            long index = Level.chunkHash(x, z);
            if (!terrain.containsKey(index)) {
                BaseChunk chunk = provider instanceof LevelDBProvider db ? LevelDBChunk.getEmptyChunk(x, z, db)
                        : Chunk.getEmptyChunk(x, z, provider);
                chunk.setBlock(1, 1, 1, BlockID.STONE);
                terrain.put(index, chunk);
            }
            Player recipient = mock(Player.class);
            set(recipient, "usedChunks", new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Boolean>());
            recipient.usedChunks.put(index, false);
            when(recipient.getLoaderId()).thenReturn(loader);
            when(recipient.getGameVersion()).thenReturn(GameVersion.byProtocol(protocol, false));
            when(recipient.getLevel()).thenReturn(level);
            when(recipient.isConnected()).thenReturn(true);
            doAnswer(call -> { sent.incrementAndGet(); deliveries.add(x + "," + z + "#" + loader); return null; })
                    .when(recipient).sendChunk(eq(x), eq(z), any(DataPacket.class));
            doAnswer(call -> { sent.incrementAndGet(); deliveries.add(x + "," + z + "#" + loader); return null; })
                    .when(recipient).sendChunk(eq(x), eq(z), anyInt(), any(byte[].class), eq(0));
            return recipient;
        }

        void advance(int tick) throws Exception {
            when(server.getTick()).thenReturn(tick);
            process();
            while (!work.isEmpty()) work.remove().run();
            // Every async result is older than the live terrain when MAIN handles it.
            for (BaseChunk chunk : terrain.values())
                chunk.setBlock(1, 1, 1, tick % 2 == 0 ? BlockID.STONE : BlockID.DIRT);
            deliverResults();
        }

        void process() throws Exception {
            Method method = Level.class.getDeclaredMethod("processChunkRequest");
            method.setAccessible(true);
            method.invoke(level);
        }

        void deliverResults() throws Exception {
            Queue<?> queue = (Queue<?>) field(level, "asyncChunkRequestCallbackQueue");
            Object item;
            while ((item = queue.poll()) != null) deliver(item);
        }

        void deliver(Object item) {
            level.handleChunkRequestCallback((NetworkChunkSerializerCallbackData) item);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
