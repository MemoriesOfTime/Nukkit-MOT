package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.level.ChunkLoadEvent;
import cn.nukkit.event.level.ChunkUnloadEvent;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.iq80.leveldb.DBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real LevelDB reads and compare-and-replace publication under an occupied player ticket. */
class ReviewV3PlaceholderRecoveryTest {
    private static final int X = 3, Z = 7;
    private static final long HASH = Level.chunkHash(X, Z);
    private static final byte[] CORRUPT_VERSION = {1, 2};
    @TempDir Path directory;
    private final AtomicLong clock = new AtomicLong();
    private final QueuedExecutor executor = new QueuedExecutor();
    private final PluginManager plugins = mock(PluginManager.class);
    private final Player holder = mock(Player.class);
    private Level level;
    private Server server;
    private LevelDBProvider provider;
    private boolean previousSending, previousCompletion;
    private byte[] versionKey, validVersion;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        Server global = Server.getInstance();
        previousSending = global.asyncChunkSending;
        previousCompletion = global.asyncChunkLoadCompletion;
        global.asyncChunkSending = true;
        global.asyncChunkLoadCompletion = true;
        global.levelDbCache = 8;
        global.useNativeLevelDB = false;
        global.maxPendingChunkWrites = 128;
        server = mock(Server.class);
        server.asyncChunkSending = true;
        server.asyncChunkLoadCompletion = true;
        Thread main = Thread.currentThread();
        when(server.isPrimaryThread()).thenAnswer(ignored -> Thread.currentThread() == main);
        when(server.getPluginManager()).thenReturn(plugins);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        doAnswer(ignored -> {
            assertSame(main, Thread.currentThread(), "lifecycle must run on the server thread");
            return null;
        }).when(plugins).callEvent(any(ChunkLoadEvent.class));
        level = mock(Level.class, CALLS_REAL_METHODS);
        field("server", server);
        field("asyncChunkLoadExecutor", executor);
        field("pendingChunkLoads", new ConcurrentHashMap<>());
        field("completedChunkLoads", new ConcurrentLinkedQueue<>());
        field("chunkLoadFailures", Caffeine.newBuilder().build());
        field("unloadQueue", new Long2ObjectNonBlockingMap<Long>());
        field("chunkLoaders", new Long2ObjectNonBlockingMap<>());
        field("playerLoaders", new Long2ObjectNonBlockingMap<>());
        field("loaders", new Int2ObjectOpenHashMap<>());
        field("loaderCounter", new Int2IntOpenHashMap());
        doReturn(DimensionEnum.OVERWORLD.getDimensionData()).when(level).getDimensionData();
        doReturn(Level.DIMENSION_OVERWORLD).when(level).getDimension();
        doReturn(java.util.Set.of()).when(level).getPendingBlockUpdates(any(cn.nukkit.level.format.FullChunk.class));
        doAnswer(ignored -> clock.get()).when(level).chunkLoadRetryClock();
        LevelDBProvider.generate(directory.toString(), "occupied-placeholder", 404L, Flat.class);
        provider = new LevelDBProvider(level, directory.toString());
        field("provider", provider);
        LevelDBChunk seed = provider.getEmptyChunk(X, Z);
        seed.setGenerated(true);
        seed.setPopulated(true);
        seed.setBlockId(1, 64, 1, BlockID.STONE);
        provider.saveChunkSync(X, Z, seed);
        versionKey = LevelDBKey.VERSION.getKey(X, Z, 0);
        validVersion = provider.getDatabase().get(versionKey);
        assertNotNull(validVersion);
        when(holder.getLoaderId()).thenReturn(42);
        level.registerChunkLoader(holder, X, Z, false);
        assertTrue(level.isChunkInUse(HASH));
        assertSame(holder, level.getChunkPlayers(X, Z).get(42));
    }

    @AfterEach
    void tearDown() {
        if (provider != null) provider.close();
        Server.getInstance().asyncChunkSending = previousSending;
        Server.getInstance().asyncChunkLoadCompletion = previousCompletion;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void occupiedPlaceholderRetriesWithBackoffAndMountsRepairedDiskOnce(boolean asyncSending) throws Exception {
        server.asyncChunkSending = asyncSending;
        Server.getInstance().asyncChunkSending = asyncSending;
        BaseFullChunk placeholder = corruptAndLoad();
        assertTrue(level.isChunkLoadBackedOff(X, Z));
        assertPlaceholderUnsavable(placeholder);
        clock.set(TimeUnit.SECONDS.toNanos(1) - 1);
        level.retryFailedChunkReads();
        assertTrue(executor.tasks.isEmpty(), "the initial synchronous failure backs off for one second");
        clock.incrementAndGet();
        assertSame(placeholder, level.getChunkIfLoaded(X, Z));
        assertTrue(executor.tasks.isEmpty(), "read-only lookup must not admit disk work even when retry is due");
        level.retryFailedChunkReads();
        assertEquals(1, executor.tasks.size(), "server tick recovers a held stand-in without an accessor request");
        touchAllReadEntrypoints(placeholder);
        assertEquals(1, executor.tasks.size(), "all readers share one retry while the player keeps its ticket");
        CompletionStage<ChunkLoadResult> failedRetry = level.requestChunkLoadAsyncResult(X, Z);
        completeRead();
        assertEquals(ChunkLoadResult.Status.FAILED, failedRetry.toCompletableFuture().join().status());
        assertSame(placeholder, provider.getLoadedChunk(HASH));
        assertArrayEquals(CORRUPT_VERSION, provider.getDatabase().get(versionKey));
        assertTrue(level.isChunkInUse(HASH));
        verify(holder, never()).onChunkLoaded(any());
        verify(plugins, never()).callEvent(any(ChunkLoadEvent.class));

        provider.getDatabase().put(versionKey, validVersion);
        clock.set(TimeUnit.SECONDS.toNanos(3) - 1);
        level.retryFailedChunkReads();
        assertTrue(executor.tasks.isEmpty(), "the second failure backs off for two more seconds");
        clock.incrementAndGet();
        level.retryFailedChunkReads();
        CompletionStage<ChunkLoadResult> recovery = level.requestChunkLoadAsyncResult(X, Z);
        executor.runNext();
        assertFalse(recovery.toCompletableFuture().isDone());
        assertSame(placeholder, provider.getLoadedChunk(HASH), "worker decode cannot replace the live slot");
        assertArrayEquals(validVersion, provider.getDatabase().get(versionKey));
        level.mountChunk(level.completedChunkLoads.remove());
        ChunkLoadResult result = recovery.toCompletableFuture().join();
        assertEquals(ChunkLoadResult.Status.LOADED, result.status());
        BaseFullChunk real = result.chunk();
        assertNotSame(placeholder, real);
        assertSame(real, provider.getLoadedChunk(HASH));
        assertTrue(real.isInitialized());
        assertTrue(real.isGenerated());
        assertEquals(BlockID.STONE, real.getBlockId(1, 64, 1), "the stand-in never overwrites stored terrain");
        assertFalse(real.isReadFailurePlaceholder());
        assertTrue(level.isChunkInUse(HASH));
        assertFalse(level.isChunkLoadBackedOff(X, Z));
        assertSame(real, level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().join().chunk());
        touchAllReadEntrypoints(real);
        assertTrue(executor.tasks.isEmpty());
        verify(holder, times(1)).onChunkLoaded(real);
        verify(plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
    }

    @Test
    void provenAbsenceReplacesOccupiedPlaceholderWithCreatedChunk() throws Exception {
        BaseFullChunk placeholder = corruptAndLoad();
        provider.getDatabase().delete(versionKey);
        provider.getDatabase().delete(LevelDBKey.VERSION_OLD.getKey(X, Z, 0));
        // Absence must be unambiguous: remove terrain left by the seeded version too.
        try (var iterator = provider.getDatabase().iterator()) {
            for (iterator.seekToFirst(); iterator.hasNext();) {
                var entry = iterator.next();
                byte[] key = entry.getKey();
                if (key.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(key, 8),
                        java.util.Arrays.copyOf(versionKey, 8))) provider.getDatabase().delete(key);
            }
        }
        clock.set(TimeUnit.SECONDS.toNanos(1));
        CompletionStage<ChunkLoadResult> request = level.requestChunkLoadAsyncResult(X, Z);
        completeRead();
        ChunkLoadResult result = request.toCompletableFuture().join();
        assertEquals(ChunkLoadResult.Status.CREATED, result.status());
        assertNotSame(placeholder, result.chunk());
        assertSame(result.chunk(), provider.getLoadedChunk(HASH));
        assertTrue(result.chunk().isInitialized());
        assertFalse(result.chunk().isGenerated());
        assertFalse(result.chunk().isReadFailurePlaceholder());
        assertTrue(level.isChunkInUse(HASH));
        assertNull(provider.getDatabase().get(versionKey));
        verify(holder, times(1)).onChunkLoaded(result.chunk());
    }

    @Test
    void newerCanonicalChunkWinsAgainstDecodedPlaceholderRetry() throws Exception {
        corruptAndLoad();
        provider.getDatabase().put(versionKey, validVersion);
        clock.set(TimeUnit.SECONDS.toNanos(1));
        CompletionStage<ChunkLoadResult> request = level.requestChunkLoadAsyncResult(X, Z);
        executor.runNext();
        Level.PendingChunkLoad pending = level.completedChunkLoads.remove();
        LevelDBChunk replacement = provider.getEmptyChunk(X, Z);
        replacement.setGenerated(true);
        replacement.setBlockId(1, 64, 1, BlockID.DIRT);
        provider.setChunk(X, Z, replacement);
        level.mountChunk(pending);
        ChunkLoadResult result = request.toCompletableFuture().join();
        assertEquals(ChunkLoadResult.Status.LOADED, result.status());
        assertSame(replacement, result.chunk(), "an existing canonical winner may complete the request");
        assertSame(replacement, provider.getLoadedChunk(HASH));
        assertEquals(BlockID.DIRT, replacement.getBlockId(1, 64, 1));
        assertFalse(pending.chunk.isInitialized(), "the stale decoded snapshot must never initialize");
        verify(holder, times(1)).onChunkLoaded(replacement);
        verify(plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
    }

    @Test
    void placeholderWithLiveEntityCannotBeReplacedOrDiscarded() throws Exception {
        BaseFullChunk placeholder = corruptAndLoad();
        Entity entity = mock(Entity.class);
        placeholder.addEntity(entity);
        provider.getDatabase().put(versionKey, validVersion);
        clock.set(TimeUnit.SECONDS.toNanos(1));
        CompletionStage<ChunkLoadResult> request = level.requestChunkLoadAsyncResult(X, Z);
        completeRead();
        assertEquals(ChunkLoadResult.Status.FAILED, request.toCompletableFuture().join().status());
        assertSame(placeholder, provider.getLoadedChunk(HASH));
        assertSame(entity, placeholder.getEntities().get(entity.getId()));
        verify(holder, never()).onChunkLoaded(any());
        verify(plugins, never()).callEvent(any(ChunkLoadEvent.class));

        placeholder.removeEntity(entity);
        clock.set(TimeUnit.SECONDS.toNanos(2));
        level.retryFailedChunkReads();
        assertEquals(1, executor.tasks.size(), "refused replacement must retain automatic recovery scheduling");
        CompletionStage<ChunkLoadResult> retry = level.pendingChunkLoads.get(HASH).result;
        completeRead();
        ChunkLoadResult loaded = retry.toCompletableFuture().join();
        assertEquals(ChunkLoadResult.Status.LOADED, loaded.status());
        assertNotSame(placeholder, loaded.chunk());
        assertEquals(BlockID.STONE, loaded.chunk().getBlockId(1, 64, 1));
        verify(holder, times(1)).onChunkLoaded(loaded.chunk());
    }

    @Test
    void cancelledUnloadDoesNotConsumeHeldPlaceholderRecoveryForever() throws Exception {
        BaseFullChunk placeholder = corruptAndLoad();
        provider.getDatabase().put(versionKey, validVersion);
        clock.set(TimeUnit.SECONDS.toNanos(1));
        level.retryFailedChunkReads();
        CompletionStage<ChunkLoadResult> cancelled = level.pendingChunkLoads.get(HASH).result;
        executor.runNext();
        doAnswer(call -> {
            ChunkUnloadEvent event = call.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(plugins).callEvent(any(ChunkUnloadEvent.class));
        assertFalse(level.unloadChunk(X, Z, false, false));
        level.mountChunk(level.completedChunkLoads.remove());
        assertEquals(ChunkLoadResult.Status.CANCELLED, cancelled.toCompletableFuture().join().status());
        assertSame(placeholder, provider.getLoadedChunk(HASH));
        assertTrue(level.isChunkInUse(HASH));

        clock.set(TimeUnit.SECONDS.toNanos(2));
        level.retryFailedChunkReads();
        assertEquals(1, executor.tasks.size(), "cancelled unload must leave the held stand-in retryable by ticks");
        CompletionStage<ChunkLoadResult> retry = level.pendingChunkLoads.get(HASH).result;
        completeRead();
        ChunkLoadResult loaded = retry.toCompletableFuture().join();
        assertEquals(ChunkLoadResult.Status.LOADED, loaded.status());
        assertNotSame(placeholder, loaded.chunk());
        verify(holder, times(1)).onChunkLoaded(loaded.chunk());
    }

    @Test
    void unloadedSynchronousPlaceholderStillReportsFailedBackoffWithoutReading() {
        corruptAndLoad();
        assertTrue(provider.unloadChunk(X, Z, false));
        assertNull(provider.getLoadedChunk(HASH));
        ChunkLoadResult result = level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().join();
        assertEquals(ChunkLoadResult.Status.FAILED, result.status());
        assertNotNull(result.failure());
        assertTrue(executor.tasks.isEmpty());
        assertArrayEquals(CORRUPT_VERSION, provider.getDatabase().get(versionKey));
    }

    private BaseFullChunk corruptAndLoad() {
        provider.getDatabase().put(versionKey, CORRUPT_VERSION);
        BaseFullChunk placeholder = level.getChunk(X, Z, true);
        assertTrue(placeholder.isReadFailurePlaceholder());
        assertFalse(placeholder.isGenerated());
        assertFalse(placeholder.isInitialized());
        assertSame(placeholder, provider.getLoadedChunk(HASH));
        assertArrayEquals(CORRUPT_VERSION, provider.getDatabase().get(versionKey));
        return placeholder;
    }

    private void assertPlaceholderUnsavable(BaseFullChunk placeholder) {
        placeholder.setBlockId(1, 64, 1, BlockID.DIRT);
        assertThrows(DBException.class, () -> provider.saveChunkSync(X, Z, placeholder));
        assertThrows(CompletionException.class, () -> provider.saveChunkFuture(X, Z, placeholder).join());
        assertArrayEquals(CORRUPT_VERSION, provider.getDatabase().get(versionKey));
    }

    private void touchAllReadEntrypoints(BaseFullChunk expected) {
        assertSame(expected, level.getChunkIfLoaded(X, Z));
        assertSame(expected, level.getChunk(X, Z, true));
        assertTrue(level.loadChunk(X, Z, true));
    }

    private void completeRead() throws Exception {
        executor.runNext();
        level.mountChunk(level.completedChunkLoads.remove());
    }

    private void field(String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.copyOf(tasks); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return tasks.isEmpty(); }
        @Override public void execute(Runnable task) { tasks.add(task); }
        private void runNext() throws Exception {
            Thread worker = new Thread(tasks.remove(), "placeholder-recovery-test-io");
            worker.start();
            worker.join(5_000);
            assertFalse(worker.isAlive(), "reader did not finish before simulated server tick");
        }
    }
}
