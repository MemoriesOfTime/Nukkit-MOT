package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Accepted chunk demand must keep progressing without another player request. */
@Isolated("MockServer")
@Timeout(45)
class PendingChunkRequestLifecycleTest {
    private static final int X = 3, Z = 5;
    private static final long INDEX = Level.chunkHash(X, Z);
    private static final GameVersion VERSION = GameVersion.V1_20_0, OTHER = GameVersion.V1_26_50;

    @BeforeAll static void initialize() { MockServer.init(); }

    @Test
    void currentFailureRetriesWithoutAnotherRequestAndKeepsOtherProtocolInFlight() throws Exception {
        Fixture f = new Fixture();
        f.request(X, VERSION, 1);
        f.request(X, OTHER, 2);
        f.process();
        long epoch = f.epoch(VERSION, INDEX);
        f.level.onAsyncChunkRequestFailed(VERSION, X, Z, epoch);
        f.level.drainFailedChunkRequests();
        f.process();
        assertEquals(2, f.submissions.size(), "The original pending demand must retry autonomously");
        assertEquals(Set.of(VERSION), f.submissions.get(1).protocols());
        assertNotEquals(epoch, f.epoch(VERSION, INDEX));
        assertEquals(epoch, f.epoch(OTHER, INDEX), "Failure must not retire another protocol");
        assertTrue(f.tasks(OTHER).contains(INDEX));
    }

    @Test
    void watchdogRetriesAnAcceptedTaskWithoutAnotherRequest() throws Exception {
        Fixture f = new Fixture();
        f.request(X, VERSION, 1);
        f.process();
        long epoch = f.epoch(VERSION, INDEX);
        when(f.server.getTick()).thenReturn(300);
        f.process();
        assertEquals(1, f.submissions.size(), "Exactly 200 ticks is not expired");
        when(f.server.getTick()).thenReturn(301);
        f.process();
        assertEquals(2, f.submissions.size());
        assertNotEquals(epoch, f.epoch(VERSION, INDEX));
    }

    @Test
    void departureRetiresTheAcceptedTaskWithoutAnotherRequest() throws Exception {
        Fixture f = new Fixture();
        Player player = f.request(X, VERSION, 1);
        f.process();
        Int2ObjectMap<Player> oldRecipients = f.queues(VERSION).get(INDEX);
        when(player.isConnected()).thenReturn(false);
        f.process();
        assertTrue(oldRecipients.isEmpty(), "The departed player must not remain strongly held");
        assertFalse(f.queues(VERSION).containsKey(INDEX));
        assertFalse(f.tasks(VERSION).contains(INDEX));
        assertEquals(0L, f.epoch(VERSION, INDEX));
        assertFalse(f.pending(VERSION).contains(INDEX));
        assertEquals(1, f.submissions.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recipientHookPreservesARequestForAnotherCoordinateOrProtocol(boolean differentProtocol) throws Exception {
        Fixture f = new Fixture();
        Player first = f.request(X, VERSION, 1);
        Player second = f.player(X + 1, differentProtocol ? OTHER : VERSION, 2);
        AtomicBoolean once = new AtomicBoolean();
        when(first.isConnected()).thenAnswer(call -> {
            if (once.compareAndSet(false, true)) f.level.requestChunk(X + 1, Z, second);
            return true;
        });
        f.process();
        f.process();
        assertEquals(1, f.submissions.stream().filter(s -> s.x() == X + 1).count(),
                "A reentrant request must be submitted exactly once by the next pass");
    }

    @Test
    void aLaterPreflightFailureReleasesEarlierNeverSubmittedReservations() throws Exception {
        Fixture f = new Fixture();
        Map<Long, Player> recipients = new HashMap<>();
        recipients.put(INDEX, f.request(X, VERSION, 1));
        recipients.put(Level.chunkHash(X + 1, Z), f.request(X + 1, VERSION, 2));
        long[] order = f.pending(VERSION).toLongArray();
        AtomicBoolean once = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("recipient preflight failure");
        when(recipients.get(order[1]).isConnected()).thenAnswer(call -> {
            if (once.compareAndSet(false, true)) throw failure;
            return true;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        assertFalse(f.tasks(VERSION).contains(order[0]), "No provider ran: a task without a timeout must not survive");
        assertTrue(f.pending(VERSION).contains(order[0]));
        assertTrue(f.submissions.isEmpty());
        f.process();
        assertEquals(2, f.submissions.size(), "Both original demands must remain serviceable");
    }

    @Test
    void preflightFailureKeepsAReentrantSubmissionOfAnEarlierReservation() throws Exception {
        Fixture f = new Fixture();
        Map<Long, Player> recipients = new HashMap<>();
        recipients.put(INDEX, f.request(X, VERSION, 1));
        recipients.put(Level.chunkHash(X + 1, Z), f.request(X + 1, VERSION, 2));
        long[] order = f.pending(VERSION).toLongArray();
        long accepted = order[0];
        int acceptedX = Level.getHashX(accepted);
        AtomicBoolean once = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("preflight failed after another submission");
        when(recipients.get(order[1]).isConnected()).thenAnswer(call -> {
            if (once.compareAndSet(false, true)) {
                f.level.onAsyncChunkRequestFailed(VERSION, acceptedX, Z, 0L);
                f.level.drainFailedChunkRequests();
                f.level.requestChunk(acceptedX, Z, recipients.get(accepted));
                f.process();
                throw failure;
            }
            return true;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        assertEquals(1, f.submissions.size());
        assertEquals(f.submissions.get(0).epoch(), f.epoch(VERSION, accepted));
        assertTrue(f.tasks(VERSION).contains(accepted), "A new epoch owns the same recipient map now");
        f.process();
        assertEquals(2, f.submissions.size());
        assertEquals(1, f.submissions.stream().filter(submission -> submission.x() == acceptedX).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lookup", "provider"})
    void submissionFailureRestoresBothTheCurrentAndUnvisitedCoordinateGroups(String boundary) throws Exception {
        Fixture f = new Fixture();
        for (int x : new int[]{X, X + 1}) {
            f.request(x, VERSION, x * 2);
            f.request(x, OTHER, x * 2 + 1);
        }
        AtomicBoolean once = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException(boundary + " submission failure");
        if (boundary.equals("lookup")) {
            doAnswer(call -> {
                if (once.compareAndSet(false, true)) throw failure;
                return f.chunk;
            }).when(f.level).getChunk(anyInt(), anyInt());
        } else {
            doAnswer(call -> {
                if (once.compareAndSet(false, true)) throw failure;
                f.record(call.getArgument(0), call.getArgument(1), call.getArgument(3));
                return null;
            }).when(f.provider).requestChunkTask(any(ObjectSet.class), anyInt(), anyInt(), anyLong());
        }
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        for (GameVersion version : new GameVersion[]{VERSION, OTHER}) {
            for (int x : new int[]{X, X + 1}) {
                long index = Level.chunkHash(x, Z);
                assertFalse(f.tasks(version).contains(index), "Aborted submissions must not strand reserved tasks");
                assertEquals(0L, f.epoch(version, index));
                assertTrue(f.pending(version).contains(index));
            }
        }
        f.process();
        assertEquals(2, f.submissions.size());
        for (Submission submission : f.submissions) assertEquals(Set.of(VERSION, OTHER), submission.protocols());
    }

    @Test
    void aThrowingOldProviderDoesNotRollBackAReentrantReplacementEpoch() throws Exception {
        Fixture f = new Fixture();
        Player player = f.request(X, VERSION, 1);
        AtomicBoolean once = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("old provider failed after reentry");
        doAnswer(call -> {
            long sequence = call.getArgument(3);
            f.record(call.getArgument(0), call.getArgument(1), sequence);
            if (once.compareAndSet(false, true)) {
                f.level.onAsyncChunkRequestFailed(VERSION, X, Z, sequence);
                f.level.drainFailedChunkRequests();
                f.level.requestChunk(X, Z, player);
                f.process();
                throw failure;
            }
            return null;
        }).when(f.provider).requestChunkTask(any(ObjectSet.class), anyInt(), anyInt(), anyLong());
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        assertEquals(2, f.submissions.size());
        assertEquals(f.submissions.get(1).epoch(), f.epoch(VERSION, INDEX));
        assertTrue(f.tasks(VERSION).contains(INDEX));
        f.process();
        assertEquals(2, f.submissions.size(), "Live replacement must not be submitted twice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"leveldb", "anvil"})
    void nativeCloneFailureForTwoProtocolsIsRetriedWithoutAnotherRequest(String kind) throws Exception {
        Fixture f = new Fixture();
        BaseChunk source = f.nativeProvider(kind, false);
        AtomicBoolean once = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("native snapshot clone failure");
        doAnswer(call -> {
            if (once.compareAndSet(false, true)) throw failure;
            return call.callRealMethod();
        }).when(source).cloneForChunkSending();
        f.request(X, VERSION, 1);
        f.request(X, OTHER, 2);
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        for (GameVersion version : new GameVersion[]{VERSION, OTHER}) {
            assertFalse(f.tasks(version).contains(INDEX));
            assertTrue(f.pending(version).contains(INDEX));
        }
        f.process();
        assertEquals(1, f.work.size(), "The native provider coalesces both protocol retries");
        f.work.remove().run();
        f.deliverNativeCallbacks();
        assertEquals(2, f.sent.get(), "Recovery must actually deliver both native payloads");
    }

    @ParameterizedTest
    @ValueSource(strings = {"leveldb", "anvil"})
    void enablingAsyncOnALevelWithoutAnExecutorKeepsTheExistingNativeSyncFallback(String kind) throws Exception {
        Fixture f = new Fixture();
        f.nativeProvider(kind, true);
        f.request(X, VERSION, 1);
        f.process();
        assertEquals(1, f.sent.get());
        assertTrue(f.server.asyncChunkSending, "No global option change is needed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"before-ack", "after-ack", "renewed-demand", "legacy-refresh",
            "changed-loader", "replacement-player"})
    void partialCachedDeliveryRetainsOnlyUnacknowledgedInitialDemand(String phase) throws Exception {
        Fixture f = new Fixture();
        Player a = f.nativePlayer(1), b = f.nativePlayer(2);
        f.level.requestChunk(X, Z, a);
        f.level.requestChunk(X, Z, b);
        Player[] order = f.queues(VERSION).get(INDEX).values().toArray(Player.EMPTY_ARRAY);
        Player first = order[0], second = order[1];
        if (phase.equals("legacy-refresh")) first.usedChunks.put(INDEX, true);
        BatchPacket packet = new BatchPacket();
        when(f.chunk.getChunkPacket(VERSION)).thenReturn(packet);
        AtomicBoolean once = new AtomicBoolean();
        AtomicReference<Player> replacement = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("cached recipient delivery failure");
        if (phase.equals("after-ack")) {
            doAnswer(call -> {
                call.callRealMethod();
                if (once.compareAndSet(false, true)) throw failure;
                return null;
            }).when(second).sendChunk(eq(X), eq(Z), any(DataPacket.class));
        } else {
            doAnswer(call -> {
                if (once.compareAndSet(false, true)) {
                    if (phase.equals("renewed-demand")) first.usedChunks.put(INDEX, false);
                    if (phase.equals("changed-loader")) doReturn(first.getLoaderId() + 100).when(first).getLoaderId();
                    if (phase.equals("replacement-player")) {
                        replacement.set(f.nativePlayer(first.getLoaderId()));
                        f.level.requestChunk(X, Z, replacement.get());
                    }
                    throw failure;
                }
                return true;
            }).when(second).dataPacket(any(DataPacket.class));
        }
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        assertEquals(1, field(first, "chunksSent"), "The first native packet was accepted");
        f.process();
        assertEquals(phase.equals("renewed-demand") || phase.equals("legacy-refresh") ? 2 : 1,
                field(first, "chunksSent"), "Accepted initial FALSE demand must not be repeated");
        assertEquals(1, field(second, "chunksSent"), "The throwing receiver must still progress exactly once");
        if (replacement.get() != null) {
            assertEquals(1, field(replacement.get(), "chunksSent"), "A new player at the same loader key must survive cleanup");
            assertEquals(Boolean.TRUE, replacement.get().usedChunks.get(INDEX));
        }
        assertEquals(Boolean.TRUE, first.usedChunks.get(INDEX));
        assertEquals(Boolean.TRUE, second.usedChunks.get(INDEX));
        assertFalse(f.tasks(VERSION).contains(INDEX));
        assertFalse(f.queues(VERSION).containsKey(INDEX));
    }

    @Test
    void cachedFailureDoesNotRemoveRecipientsFromAReentrantEpoch() throws Exception {
        Fixture f = new Fixture();
        Player a = f.nativePlayer(1), b = f.nativePlayer(2);
        f.level.requestChunk(X, Z, a);
        f.level.requestChunk(X, Z, b);
        Int2ObjectMap<Player> recipients = f.queues(VERSION).get(INDEX);
        Player[] order = recipients.values().toArray(Player.EMPTY_ARRAY);
        Player first = order[0], second = order[1];
        when(f.chunk.getChunkPacket(VERSION)).thenReturn(new BatchPacket());
        IllegalStateException failure = new IllegalStateException("old cached delivery failed after reentry");
        doAnswer(call -> {
            f.level.onAsyncChunkRequestFailed(VERSION, X, Z, 0L);
            f.level.drainFailedChunkRequests();
            when(f.chunk.getChunkPacket(VERSION)).thenReturn(null);
            f.level.requestChunk(X, Z, first);
            f.process();
            throw failure;
        }).when(second).dataPacket(any(DataPacket.class));
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        assertEquals(1, f.submissions.size());
        assertNotEquals(0L, f.epoch(VERSION, INDEX));
        assertSame(recipients, f.queues(VERSION).get(INDEX));
        assertSame(first, recipients.get(first.getLoaderId()), "The new epoch owns this accepted player slot");
        assertTrue(f.tasks(VERSION).contains(INDEX));
        f.process();
        assertEquals(1, f.submissions.size(), "The old failure must not force another submission");
    }

    @Test
    void cachedFailureKeepsTheOriginalExceptionWhenAcceptanceCleanupFails() throws Exception {
        Fixture f = new Fixture();
        Player a = f.nativePlayer(1), b = f.nativePlayer(2);
        f.level.requestChunk(X, Z, a);
        f.level.requestChunk(X, Z, b);
        Player[] order = f.queues(VERSION).get(INDEX).values().toArray(Player.EMPTY_ARRAY);
        Player first = order[0], second = order[1];
        AtomicBoolean cleanup = new AtomicBoolean();
        IllegalStateException cleanupFailure = new IllegalStateException("acceptance lookup failed");
        set(first, "usedChunks", new HashMap<Long, Boolean>(first.usedChunks) {
            @Override public Boolean get(Object key) {
                if (cleanup.get()) throw cleanupFailure;
                return super.get(key);
            }
        });
        when(f.chunk.getChunkPacket(VERSION)).thenReturn(new BatchPacket());
        IllegalStateException failure = new IllegalStateException("original packet delivery failed");
        doAnswer(call -> {
            cleanup.set(true);
            throw failure;
        }).when(second).dataPacket(any(DataPacket.class));
        assertSame(failure, assertThrows(IllegalStateException.class, f::process));
        assertArrayEquals(new Throwable[]{cleanupFailure}, failure.getSuppressed());
    }

    @ParameterizedTest
    @CsvSource({"raw,before-ack", "raw,after-ack", "blob,before-ack", "blob,after-ack",
            "blob-fallback,before-ack", "blob-fallback,after-ack", "pull-raw,before-ack", "pull-raw,after-ack",
            "pull-cache,before-ack", "pull-cache,after-ack", "pull-fallback,before-ack", "pull-fallback,after-ack"})
    void partialSerializerDeliveryRetriesWithoutRepeatingNativeAcceptance(String mode, String phase) throws Exception {
        Fixture f = new Fixture();
        Player a = f.nativePlayer(1), b = f.nativePlayer(2);
        boolean pull = mode.startsWith("pull");
        boolean blob = mode.startsWith("blob") || mode.equals("pull-cache");
        boolean cacheAccepted = !mode.equals("blob-fallback");
        for (Player player : new Player[]{a, b}) {
            doReturn(pull).when(player).isSubChunkPullMode();
            doReturn(blob).when(player).isClientBlobCacheActive();
            doReturn(cacheAccepted).when(player).tryOpenBlobTransaction(any(long[].class));
            doReturn(true).when(player).tryTrackClientCacheBlob(anyLong(), any(byte[].class));
            f.level.requestChunk(X, Z, player);
        }
        when(f.server.getClientCacheBlobStore()).thenReturn(mock(ClientCacheBlobStore.class));
        f.process();
        Player[] order = f.queues(VERSION).get(INDEX).values().toArray(Player.EMPTY_ARRAY);
        Player first = order[0], second = order[1];
        AtomicBoolean once = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException(mode + " recipient delivery failure");
        if (phase.equals("after-ack")) {
            doAnswer(call -> {
                call.callRealMethod();
                if (once.compareAndSet(false, true)) throw failure;
                return null;
            }).when(second).sendChunk(eq(X), eq(Z), any(DataPacket.class));
        } else {
            doAnswer(call -> {
                if (once.compareAndSet(false, true)) throw failure;
                return true;
            }).when(second).dataPacket(any(DataPacket.class));
        }
        byte[] biomes = mode.equals("pull-fallback") ? null : new byte[]{1};
        List<byte[]> subChunks = List.of(new byte[]{2});
        long firstEpoch = f.epoch(VERSION, INDEX);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.level.chunkRequestCallback(
                VERSION, 0L, X, Z, 1, new byte[]{3}, subChunks, biomes, null, firstEpoch)));
        assertEquals(1, field(first, "chunksSent"));
        when(f.server.getTick()).thenReturn(301);
        f.process();
        long retryEpoch = f.epoch(VERSION, INDEX);
        if (phase.equals("before-ack")) {
            assertNotEquals(firstEpoch, retryEpoch, "Watchdog must retry without another player request");
            assertEquals(2, f.submissions.size());
            f.level.chunkRequestCallback(VERSION, 0L, X, Z, 1, new byte[]{3}, subChunks, biomes, null, retryEpoch);
        } else {
            assertEquals(0L, retryEpoch, "Both accepted players need no serializer retry");
            assertEquals(1, f.submissions.size());
        }
        assertEquals(1, field(first, "chunksSent"), "Already accepted initial demand must not repeat");
        assertEquals(1, field(second, "chunksSent"));
        assertEquals(Boolean.TRUE, first.usedChunks.get(INDEX));
        assertEquals(Boolean.TRUE, second.usedChunks.get(INDEX));
        assertFalse(f.tasks(VERSION).contains(INDEX));
        assertFalse(f.queues(VERSION).containsKey(INDEX));
        org.mockito.ArgumentCaptor<DataPacket> packet = org.mockito.ArgumentCaptor.forClass(DataPacket.class);
        verify(first).dataPacket(packet.capture());
        LevelChunkPacket column = assertInstanceOf(LevelChunkPacket.class, packet.getValue());
        assertEquals(pull && !mode.equals("pull-fallback"), column.requestSubChunks);
        assertEquals(mode.equals("blob") || mode.equals("pull-cache"), column.cacheEnabled);
    }

    private record Submission(Set<GameVersion> protocols, int x, long epoch) { }

    private static final class Fixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        LevelProvider provider = mock(LevelProvider.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final List<Submission> submissions = new ArrayList<>();
        final Queue<Runnable> work = new ArrayDeque<>();
        final AtomicInteger sent = new AtomicInteger();

        Fixture() throws Exception {
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests", "chunkRequestEpochs"})
                set(level, name, new Object2ObjectOpenHashMap<>());
            set(level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(level, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
            set(level, "asyncChunkRequestCallbackQueue", new ConcurrentLinkedQueue<>());
            set(level, "server", server);
            set(level, "provider", provider);
            server.asyncChunkSending = true;
            when(server.getTick()).thenReturn(100);
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            doReturn("pending-fixture").when(level).getName();
            doReturn(0).when(level).getDimension();
            doReturn(new DimensionData(0, 0, 15)).when(level).getDimensionData();
            doReturn(chunk).when(level).getChunk(anyInt(), anyInt());
            doReturn(chunk).when(level).getChunkIfLoaded(anyInt(), anyInt());
            doAnswer(call -> {
                record(call.getArgument(0), call.getArgument(1), call.getArgument(3));
                return null;
            }).when(provider).requestChunkTask(any(ObjectSet.class), anyInt(), anyInt(), anyLong());
        }

        void record(ObjectSet<GameVersion> protocols, int x, long epoch) {
            submissions.add(new Submission(Set.copyOf(protocols), x, epoch));
        }

        Player player(int x, GameVersion version, int loader) throws Exception {
            Player player = mock(Player.class);
            long index = Level.chunkHash(x, Z);
            set(player, "usedChunks", new HashMap<Long, Boolean>(Map.of(index, false)));
            when(player.getLoaderId()).thenReturn(loader);
            when(player.getGameVersion()).thenReturn(version);
            when(player.isConnected()).thenReturn(true);
            when(player.getLevel()).thenReturn(level);
            doAnswer(call -> { sent.incrementAndGet(); return null; }).when(player)
                    .sendChunk(eq(x), eq(Z), anyInt(), any(byte[].class), eq(0));
            doAnswer(call -> { sent.incrementAndGet(); return null; }).when(player)
                    .sendChunk(eq(x), eq(Z), any(DataPacket.class));
            return player;
        }

        Player nativePlayer(int loader) throws Exception {
            Player player = mock(Player.class, CALLS_REAL_METHODS);
            set(player, "usedChunks", new HashMap<Long, Boolean>(Map.of(INDEX, false)));
            set(player, "loadQueue", new LongLinkedOpenHashSet());
            set(player, "connected", true);
            set(player, "level", level);
            set(player, "server", server);
            doReturn(loader).when(player).getLoaderId();
            doReturn(VERSION).when(player).getGameVersion();
            doReturn(true).when(player).dataPacket(any(DataPacket.class));
            doReturn(false).when(player).isSubChunkPullMode();
            doReturn(false).when(player).isClientBlobCacheActive();
            doReturn(Map.of()).when(level).getChunkBlockEntities(X, Z);
            return player;
        }

        Player request(int x, GameVersion version, int loader) throws Exception {
            Player player = player(x, version, loader);
            level.requestChunk(x, Z, player);
            return player;
        }

        BaseChunk nativeProvider(String kind, boolean noExecutor) throws Exception {
            BaseChunk source;
            if (kind.equals("leveldb")) {
                LevelDBProvider db = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
                set(db, "level", level);
                doReturn(server).when(db).getServer();
                doReturn(0).when(db).getMinBlockY();
                doReturn(15).when(db).getMaxBlockY();
                provider = db;
                source = spy(LevelDBChunk.getEmptyChunk(X, Z, provider));
            } else {
                Anvil anvil = mock(Anvil.class, CALLS_REAL_METHODS);
                set(anvil, "level", level);
                doReturn(server).when(anvil).getServer();
                provider = anvil;
                source = spy(cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(X, Z, provider));
                source.setLegacyConnectionsFixed(true);
            }
            source.setBlock(1, 1, 1, BlockID.STONE);
            set(level, "provider", provider);
            doReturn(source).when(provider).getChunk(X, Z, false);
            doReturn(source).when(level).getChunk(X, Z);
            doReturn(source).when(level).getChunkIfLoaded(X, Z);
            doReturn(false).when(level).antiXrayEnabled();
            doReturn(false).when(level).isClientCacheDeliveryWanted();
            ExecutorService executor = mock(ExecutorService.class);
            doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
            set(level, "asyncChuckExecutor", noExecutor ? null : executor);
            return source;
        }

        @SuppressWarnings("unchecked")
        LongSet tasks(GameVersion version) throws Exception {
            var method = Level.class.getDeclaredMethod("getChunkSendTasks", GameVersion.class);
            method.setAccessible(true);
            return (LongSet) method.invoke(level, version);
        }

        LongSet pending(GameVersion version) throws Exception {
            var method = Level.class.getDeclaredMethod("getPendingChunkRequests", GameVersion.class);
            method.setAccessible(true);
            return (LongSet) method.invoke(level, version);
        }

        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, Int2ObjectMap<Player>> queues(GameVersion version) throws Exception {
            var method = Level.class.getDeclaredMethod("getChunkSendQueue", GameVersion.class);
            method.setAccessible(true);
            return (ConcurrentMap<Long, Int2ObjectMap<Player>>) method.invoke(level, version);
        }

        @SuppressWarnings("unchecked")
        long epoch(GameVersion version, long index) throws Exception {
            var all = (Map<GameVersion, Long2LongOpenHashMap>) field(level, "chunkRequestEpochs");
            return all.containsKey(version) ? all.get(version).get(index) : 0L;
        }

        void deliverNativeCallbacks() throws Exception {
            Queue<?> callbacks = (Queue<?>) field(level, "asyncChunkRequestCallbackQueue");
            while (!callbacks.isEmpty()) {
                var data = (NetworkChunkSerializerCallbackData) callbacks.remove();
                level.chunkRequestCallback(data.getGameVersion(), data.getTimestamp(), data.getX(), data.getZ(),
                        data.getSubChunkCount(), data.getPayload(), data.getSubChunkBlobs(), data.getBiomes(), data.getBlockEntities(), data.getSequence());
            }
        }

        void process() throws Exception {
            var method = Level.class.getDeclaredMethod("processChunkRequest");
            method.setAccessible(true);
            try { method.invoke(level); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                if (failure.getCause() instanceof Error error) throw error;
                throw failure;
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
