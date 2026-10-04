package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.event.level.ChunkLoadEvent;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Completion means a current, initialized chunk, never a queued or decoded snapshot. */
class AsyncChunkLoadCompletionTest {

    private static final int X = 3;
    private static final int Z = 5;
    private static final long HASH = Level.chunkHash(X, Z);

    private final Map<Long, BaseFullChunk> loaded = new ConcurrentHashMap<>();
    private Server server;
    private PluginManager plugins;
    private LevelProvider provider;
    private QueuedExecutor executor;
    private Level level;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        this.plugins = mock(PluginManager.class);
        this.server = mock(Server.class);
        this.server.asyncChunkSending = true;
        this.server.asyncChunkLoadCompletion = true;
        this.server.lightUpdates = false;
        Thread main = Thread.currentThread();
        when(this.server.isPrimaryThread()).thenAnswer(ignored -> Thread.currentThread() == main);
        when(this.server.getPluginManager()).thenReturn(this.plugins);
        when(this.server.getLogger()).thenReturn(mock(MainLogger.class));
        this.provider = mock(LevelProvider.class);
        when(this.provider.isOffThreadChunkReadSupported()).thenReturn(true);
        when(this.provider.getLoadedChunk(anyLong())).thenAnswer(call -> this.loaded.get(call.getArgument(0)));
        when(this.provider.isChunkLoaded(anyLong())).thenAnswer(call -> this.loaded.containsKey(call.getArgument(0)));
        this.executor = new QueuedExecutor();
        this.level = mock(Level.class, Mockito.CALLS_REAL_METHODS);
        field(this.level, "server", this.server);
        field(this.level, "provider", this.provider);
        field(this.level, "asyncChunkLoadExecutor", this.executor);
        field(this.level, "asyncChuckExecutor", this.executor);
        field(this.level, "pendingChunkLoads", new ConcurrentHashMap<>());
        field(this.level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        field(this.level, "chunkLoadFailures", Caffeine.newBuilder().maximumSize(1024).build());
        field(this.level, "unloadQueue", new Long2ObjectNonBlockingMap<Long>());
        field(this.level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
        field(this.level, "playerLoaders", new Long2ObjectNonBlockingMap<>());
        field(this.level, "loaders", new Int2ObjectOpenHashMap<>());
        field(this.level, "loaderCounter", new Int2IntOpenHashMap());
    }

    @Test
    void coldLightingRootUsesTheCompletionAPIWithoutInlineReadOrMountCascade() throws Exception {
        this.server.lightUpdates = true;
        when(this.provider.getMinBlockY()).thenReturn(-64);
        when(this.provider.getMaxBlockY()).thenReturn(319);
        when(this.level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        BaseFullChunk chunk = chunk();
        ChunkReadTicket ticket = ticket(chunk);
        Map<Long, java.util.Set<Integer>> changes = new java.util.HashMap<>();
        changes.put(HASH, java.util.Set.of(Level.localBlockHash(X << 4, 64, Z << 4, this.level.getDimensionData())));
        this.level.updateBlockLight(changes);
        assertTrue(changes.containsKey(HASH));
        assertEquals(1, this.level.pendingChunkLoads.size());
        verify(ticket, never()).read();
        verify(this.provider, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        this.executor.runNext();
        assertFalse(this.loaded.containsKey(HASH));
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        assertSame(chunk, this.loaded.get(HASH));
        assertTrue(this.level.pendingChunkLoads.isEmpty(), "light-only mount recursively requested neighbours");
        this.level.updateBlockLight(changes);
        assertTrue(changes.isEmpty());
        verify(ticket, times(1)).read();
        verify(this.provider, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void concurrentAndReentrantRequestsShareCompletionAfterMainThreadLifecycle() throws Exception {
        BaseFullChunk chunk = chunk();
        ChunkReadTicket ticket = ticket(chunk);
        CompletionStage<ChunkLoadResult> first = this.level.requestChunkLoadAsyncResult(X, Z);
        assertSame(first, this.level.requestChunkLoadAsyncResult(X, Z));
        assertFalse(first.toCompletableFuture().isDone());
        verify(ticket, never()).read();
        this.executor.runNext();
        assertFalse(first.toCompletableFuture().isDone(), "decode is not a mounted-chunk ACK");

        AtomicBoolean eventSeen = new AtomicBoolean();
        AtomicBoolean initialized = new AtomicBoolean();
        doAnswer(call -> {
            assertFalse(first.toCompletableFuture().isDone());
            assertSame(first, this.level.requestChunkLoadAsyncResult(X, Z), "load event must share the unfinished request");
            eventSeen.set(true);
            return null;
        }).when(this.plugins).callEvent(any(ChunkLoadEvent.class));
        doAnswer(call -> {
            assertTrue(eventSeen.get());
            assertFalse(first.toCompletableFuture().isDone());
            initialized(chunk);
            initialized.set(true);
            return null;
        }).when(chunk).initChunk();
        Thread main = Thread.currentThread();
        AtomicReference<Thread> completionThread = new AtomicReference<>();
        first.thenAccept(result -> {
            assertTrue(initialized.get());
            completionThread.set(Thread.currentThread());
        });

        this.level.mountChunk(this.level.completedChunkLoads.remove());
        ChunkLoadResult result = first.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.LOADED, result.status());
        assertSame(chunk, result.chunk());
        assertSame(main, completionThread.get());
        assertSame(chunk, this.loaded.get(HASH));
        assertTrue(this.level.pendingChunkLoads.isEmpty());
        verify(ticket, times(1)).read();
        verify(chunk, times(1)).initChunk();
        verify(this.plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
    }

    @Test
    void subscriberCancellationCannotCancelTheSharedLoad() throws Exception {
        BaseFullChunk chunk = chunk();
        ticket(chunk);
        CompletionStage<ChunkLoadResult> shared = this.level.requestChunkLoadAsyncResult(X, Z);
        CompletableFuture<ChunkLoadResult> subscriber = shared.toCompletableFuture();
        assertTrue(subscriber.cancel(true));
        assertSame(shared, this.level.requestChunkLoadAsyncResult(X, Z));
        this.executor.runNext();
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        assertTrue(shared.toCompletableFuture().get(5, TimeUnit.SECONDS).isSuccess());
        verify(chunk, times(1)).initChunk();
    }

    @Test
    void onlyProvenAbsenceCreatesAnEmptyChunk() throws Exception {
        BaseFullChunk empty = chunk();
        ticket(null);
        when(this.provider.getEmptyChunk(X, Z)).thenReturn(empty);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        ChunkLoadResult result = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.CREATED, result.status());
        assertSame(empty, result.chunk());
        verify(empty, times(1)).initChunk();
    }

    @Test
    void readFailureCannotMountOrGenerateEmptyTerrain() throws Exception {
        ChunkReadTicket ticket = ticket(null);
        IllegalStateException failure = new IllegalStateException("disk read failed");
        when(ticket.read()).thenThrow(failure);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        assertFalse(request.toCompletableFuture().isDone());
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        ChunkLoadResult result = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.FAILED, result.status());
        assertSame(failure, result.failure());
        assertNull(result.chunk());
        assertTrue(this.loaded.isEmpty());
        verify(ticket, never()).tryMount(any());
        verify(ticket, atLeastOnce()).close();
        verify(this.provider, never()).getEmptyChunk(anyInt(), anyInt());
        verify(this.provider, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void executorRejectionNeverRunsIoOnTheCaller() throws Exception {
        ChunkReadTicket ticket = ticket(chunk());
        this.executor.shutdown();
        ChunkLoadResult result = this.level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.REJECTED, result.status());
        assertFalse(result.isSuccess());
        assertTrue(this.level.pendingChunkLoads.isEmpty());
        verify(ticket, never()).read();
        verify(ticket, atLeastOnce()).close();
        verify(this.provider, never()).readChunkOffThread(anyInt(), anyInt());
        verify(this.provider, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void staleTicketCannotPublishDecodedTerrain() throws Exception {
        BaseFullChunk decoded = chunk();
        ChunkReadTicket ticket = ticket(decoded);
        doReturn(null).when(ticket).tryMount(decoded);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        assertEquals(ChunkLoadResult.Status.CANCELLED, request.toCompletableFuture().get(5, TimeUnit.SECONDS).status());
        assertTrue(this.loaded.isEmpty());
        verify(decoded, never()).initChunk();
    }

    @Test
    void providerReplacementCancelsItsOldRequest() throws Exception {
        BaseFullChunk decoded = chunk();
        ChunkReadTicket ticket = ticket(decoded);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        field(this.level, "provider", mock(LevelProvider.class));
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        assertEquals(ChunkLoadResult.Status.CANCELLED, request.toCompletableFuture().get(5, TimeUnit.SECONDS).status());
        verify(ticket, never()).tryMount(any());
        verify(decoded, never()).initChunk();
    }

    @Test
    void oldCompletionCannotRemoveOrCompleteANewerRequest() throws Exception {
        BaseFullChunk decoded = chunk();
        ticket(decoded);
        CompletionStage<ChunkLoadResult> oldStage = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        Level.PendingChunkLoad old = this.level.completedChunkLoads.remove();
        assertTrue(this.level.pendingChunkLoads.remove(HASH, old));
        ticket(decoded);
        CompletionStage<ChunkLoadResult> nextStage = this.level.requestChunkLoadAsyncResult(X, Z);
        Level.PendingChunkLoad next = this.level.pendingChunkLoads.get(HASH);
        assertNotSame(oldStage, nextStage);
        this.level.mountChunk(old);
        assertSame(next, this.level.pendingChunkLoads.get(HASH));
        assertEquals(ChunkLoadResult.Status.CANCELLED, oldStage.toCompletableFuture().get(5, TimeUnit.SECONDS).status());
        assertFalse(nextStage.toCompletableFuture().isDone());
        this.executor.runNext();
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        assertTrue(nextStage.toCompletableFuture().get(5, TimeUnit.SECONDS).isSuccess());
        verify(decoded, times(1)).initChunk();
    }

    @Test
    void existingCanonicalChunkWinsWithoutASecondInitialization() throws Exception {
        BaseFullChunk decoded = chunk();
        BaseFullChunk winner = chunk();
        initialized(winner);
        ticket(decoded);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        this.loaded.put(HASH, winner);
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        ChunkLoadResult result = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.LOADED, result.status());
        assertSame(winner, result.chunk());
        verify(decoded, never()).initChunk();
        verify(winner, never()).initChunk();
        verify(this.plugins, never()).callEvent(any());
    }

    @Test
    void failedInitializationRemainsFailedForLaterRequestsWithoutRetryingLifecycle() throws Exception {
        BaseFullChunk chunk = chunk();
        IllegalStateException failure = new IllegalStateException("entity initialization failed");
        doThrow(failure).when(chunk).initChunk();
        ticket(chunk);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        this.level.mountChunk(this.level.completedChunkLoads.remove());

        ChunkLoadResult first = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.FAILED, first.status());
        assertSame(failure, first.failure());
        assertSame(chunk, this.loaded.get(HASH), "a partial lifecycle must not silently discard live state");
        for (int i = 0; i < 3; i++) {
            ChunkLoadResult later = this.level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(ChunkLoadResult.Status.FAILED, later.status());
            assertSame(failure, later.failure());
        }
        verify(chunk, times(1)).initChunk();
        verify(this.plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
        verify(this.provider, times(1)).openChunkRead(X, Z);
    }

    @Test
    void failedSynchronousInitializationCannotBeRetriedByTheTypedCachePath() throws Exception {
        BaseFullChunk chunk = chunk();
        IllegalStateException failure = new IllegalStateException("partial synchronous entity initialization");
        doThrow(failure).when(chunk).initChunk();
        when(this.provider.getChunk(X, Z, true)).thenAnswer(call -> {
            this.loaded.put(HASH, chunk);
            return chunk;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> this.level.getChunk(X, Z, true)));
        assertSame(chunk, this.loaded.get(HASH));
        ChunkLoadResult result = this.level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.FAILED, result.status());
        assertSame(failure, result.failure());
        verify(chunk, times(1)).initChunk();
        verify(this.plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
        verify(this.provider, never()).openChunkRead(anyInt(), anyInt());
    }

    @Test
    void cachedProviderDirectChunkInitializesExactlyOnceBeforeTypedSuccess() throws Exception {
        BaseFullChunk chunk = chunk();
        this.loaded.put(HASH, chunk);
        CompletionStage<ChunkLoadResult> first = this.level.requestChunkLoadAsyncResult(X, Z);
        ChunkLoadResult loaded = first.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.LOADED, loaded.status());
        assertSame(chunk, loaded.chunk());
        assertTrue(chunk.isInitialized());
        ChunkLoadResult second = this.level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.LOADED, second.status());
        assertSame(chunk, second.chunk());
        verify(chunk, times(1)).initChunk();
        verify(this.provider, never()).openChunkRead(anyInt(), anyInt());
        verify(this.provider, never()).readChunkOffThread(anyInt(), anyInt());
    }

    @Test
    void pendingSynchronousReadDefersPublicationWithoutBlockingTheMainThread() throws Exception {
        BaseFullChunk decoded = chunk();
        BaseFullChunk synchronous = chunk();
        ChunkReadTicket ticket = ticket(decoded);
        CompletionStage<ChunkLoadResult> request = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(this.provider.getChunk(X, Z, true)).thenAnswer(call -> {
            reading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            BaseFullChunk winner = this.loaded.putIfAbsent(HASH, synchronous);
            return winner == null ? synchronous : winner;
        });
        CompletableFuture<BaseFullChunk> loadedSync = new CompletableFuture<>();
        Thread legacy = new Thread(() -> {
            try { loadedSync.complete(this.level.getChunk(X, Z, true)); }
            catch (Throwable failure) { loadedSync.completeExceptionally(failure); }
        }, "legacy-level-read-test");
        legacy.start();
        try {
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            Level.PendingChunkLoad pending = this.level.completedChunkLoads.remove();
            assertTimeout(Duration.ofMillis(500), () -> this.level.mountChunk(pending));
            assertFalse(request.toCompletableFuture().isDone());
            assertSame(pending, this.level.pendingChunkLoads.get(HASH));
            verify(ticket, never()).tryMount(any());
            release.countDown();
            assertSame(synchronous, loadedSync.get(5, TimeUnit.SECONDS));
            this.level.mountChunk(this.level.completedChunkLoads.remove());
            ChunkLoadResult result = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(ChunkLoadResult.Status.LOADED, result.status());
            assertSame(synchronous, result.chunk());
            verify(synchronous, times(1)).initChunk();
            verify(decoded, never()).initChunk();
            verify(this.plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
        } finally {
            release.countDown();
            legacy.join(5_000);
        }
    }

    @Test
    void cachedInitializedChunkWaitsForActiveSynchronousLoaderCallbacks() throws Exception {
        BaseFullChunk chunk = chunk();
        ChunkLoader loader = mock(ChunkLoader.class);
        doReturn(true).when(this.level).isChunkInUse(HASH);
        doReturn(new ChunkLoader[]{loader}).when(this.level).getChunkLoaders(X, Z);
        CountDownLatch notifying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            assertTrue(chunk.isInitialized());
            notifying.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(loader).onChunkLoaded(chunk);
        when(this.provider.getChunk(X, Z, true)).thenAnswer(call -> {
            this.loaded.put(HASH, chunk);
            return chunk;
        });
        CompletableFuture<BaseFullChunk> loadedSync = new CompletableFuture<>();
        Thread legacy = new Thread(() -> {
            try { loadedSync.complete(this.level.getChunk(X, Z, true)); }
            catch (Throwable failure) { loadedSync.completeExceptionally(failure); }
        }, "legacy-loader-callback-test");
        legacy.start();
        try {
            assertTrue(notifying.await(5, TimeUnit.SECONDS));
            CompletionStage<ChunkLoadResult> request = assertTimeout(Duration.ofMillis(500),
                    () -> this.level.requestChunkLoadAsyncResult(X, Z));
            assertFalse(request.toCompletableFuture().isDone(), "initialized cache is not the lifecycle ACK");
            release.countDown();
            assertSame(chunk, loadedSync.get(5, TimeUnit.SECONDS));
            this.level.mountChunk(this.level.completedChunkLoads.remove());
            ChunkLoadResult result = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(ChunkLoadResult.Status.LOADED, result.status());
            assertSame(chunk, result.chunk());
            verify(chunk, times(1)).initChunk();
            verify(loader, times(1)).onChunkLoaded(chunk);
            verify(this.plugins, times(1)).callEvent(any(ChunkLoadEvent.class));
            verify(this.provider, never()).openChunkRead(anyInt(), anyInt());
        } finally {
            release.countDown();
            legacy.join(5_000);
        }
    }

    @Test
    void disabledCompletionContractDoesNotSilentlyEnqueueWork() throws Exception {
        this.server.asyncChunkLoadCompletion = false;
        ChunkLoadResult result = this.level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(ChunkLoadResult.Status.DISABLED, result.status());
        assertTrue(this.level.pendingChunkLoads.isEmpty());
        verify(this.provider, never()).openChunkRead(anyInt(), anyInt());
    }

    @Test
    void retriesBackOffOneToSixtySecondsLogEveryAttemptAndOnlyOneStackThenRecover() throws Exception {
        long now = 1_000_000;
        int[] seconds = {1, 2, 4, 8, 16, 32, 60, 60};
        for (int attempt = 0; attempt < seconds.length; attempt++) {
            doReturn(now).when(this.level).chunkLoadRetryClock();
            ChunkReadTicket ticket = ticket(null);
            when(ticket.read()).thenThrow(new IllegalStateException("failure " + attempt));
            CompletionStage<ChunkLoadResult> failed = this.level.requestChunkLoadAsyncResult(X, Z);
            this.executor.runNext();
            this.level.mountChunk(this.level.completedChunkLoads.remove());
            assertEquals(ChunkLoadResult.Status.FAILED, failed.toCompletableFuture().join().status());
            assertEquals(attempt + 1, this.level.getChunkLoadFailureCount(X, Z));
            assertTrue(this.level.isChunkLoadBackedOff(X, Z));
            long next = now + TimeUnit.SECONDS.toNanos(seconds[attempt]);
            doReturn(next - 1).when(this.level).chunkLoadRetryClock();
            for (int tick = 0; tick < 20; tick++) {
                assertSame(failed, this.level.requestChunkLoadAsyncResult(X, Z));
                assertTrue(this.level.requestChunkLoadAsync(X, Z));
            }
            assertTrue(this.executor.tasks.isEmpty());
            verify(ticket).read();
            now = next;
        }
        verify(this.server.getLogger(), times(1)).error(anyString(), any(Throwable.class));
        verify(this.server.getLogger(), times(7)).error(anyString());
        doReturn(now).when(this.level).chunkLoadRetryClock();
        BaseFullChunk recovered = chunk();
        ticket(recovered);
        CompletionStage<ChunkLoadResult> ready = this.level.requestChunkLoadAsyncResult(X, Z);
        this.executor.runNext();
        this.level.mountChunk(this.level.completedChunkLoads.remove());
        assertTrue(ready.toCompletableFuture().join().isSuccess());
        assertNull(this.level.chunkLoadFailures.getIfPresent(HASH));
        assertFalse(this.level.isChunkLoadBackedOff(X, Z));
        // Recovery resets backoff, but does not permit another stack trace for this coordinate.
        this.level.reportChunkLoadFailure(X, Z, new IllegalStateException("later failure"));
        assertEquals(9, this.level.getChunkLoadFailureCount(X, Z));
        verify(this.server.getLogger(), times(1)).error(anyString(), any(Throwable.class));
    }

    @Test
    void synchronousQuarantineNeverRunsLifecycle() throws Exception {
        BaseFullChunk failed = chunk();
        failed.markChunkReadFailure(new IllegalStateException("corrupt"));
        when(this.provider.getChunk(X, Z, true)).thenAnswer(call -> {
            this.loaded.put(HASH, failed);
            return failed;
        });
        assertSame(failed, this.level.getChunk(X, Z, true));
        assertTrue(this.level.loadChunk(X, Z, true));
        assertSame(failed, this.level.getChunk(X, Z, true));
        verify(failed, never()).initChunk();
        verify(failed, never()).replayDeferredBlockUpdates();
        verify(this.plugins, never()).callEvent(any());
    }

    @Test
    void eachCoordinateGetsAStackAndRepeatLinesAreRateLimitedWithSuppressedCounts() {
        doReturn(1_000_000L).when(this.level).chunkLoadRetryClock();
        this.level.reportChunkLoadFailure(X, Z, new IllegalStateException("first"));
        this.level.reportChunkLoadFailure(X + 1, Z, new IllegalStateException("another chunk"));
        for (int i = 0; i < 20; i++) this.level.reportChunkLoadFailure(X, Z, new IllegalStateException("burst"));
        verify(this.server.getLogger(), times(2)).error(anyString(), any(Throwable.class));
        verify(this.server.getLogger(), never()).error(anyString());
        assertEquals(21, this.level.getChunkLoadFailureCount(X, Z));
        assertEquals(1, this.level.getChunkLoadFailureCount(X + 1, Z));
        doReturn(1_001_000_000L).when(this.level).chunkLoadRetryClock();
        this.level.reportChunkLoadFailure(X, Z, new IllegalStateException("retry"));
        verify(this.server.getLogger()).error(contains("failures=22, since last log=21"));
        this.level.reportChunkLoadFailure(X, Z, new IllegalStateException("same instant"));
        verify(this.server.getLogger(), times(1)).error(anyString());
        verify(this.server.getLogger(), times(2)).error(anyString(), any(Throwable.class));
    }

    private BaseFullChunk chunk() {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.getProvider()).thenReturn(this.provider);
        when(chunk.getX()).thenReturn(X);
        when(chunk.getZ()).thenReturn(Z);
        when(chunk.isInitialized()).thenCallRealMethod();
        when(chunk.getChunkLoadFailure()).thenCallRealMethod();
        doCallRealMethod().when(chunk).markChunkLoadFailure(any());
        doCallRealMethod().when(chunk).markChunkReadFailure(any());
        when(chunk.isReadFailurePlaceholder()).thenCallRealMethod();
        doAnswer(call -> {
            initialized(chunk);
            return null;
        }).when(chunk).initChunk();
        return chunk;
    }

    private static void initialized(BaseFullChunk chunk) throws Exception {
        Field initialized = BaseFullChunk.class.getDeclaredField("isInit");
        initialized.setAccessible(true);
        initialized.setBoolean(chunk, true);
    }

    private ChunkReadTicket ticket(BaseFullChunk decoded) {
        ChunkReadTicket ticket = mock(ChunkReadTicket.class);
        when(ticket.read()).thenReturn(decoded);
        when(ticket.tryMount(any())).thenAnswer(call -> {
            BaseFullChunk candidate = call.getArgument(0);
            BaseFullChunk winner = this.loaded.putIfAbsent(HASH, candidate);
            return winner == null ? candidate : winner;
        });
        when(this.provider.openChunkRead(X, Z)).thenReturn(ticket);
        return ticket;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override public void shutdown() { this.shutdown = true; }
        @Override public List<Runnable> shutdownNow() { this.shutdown = true; return List.copyOf(this.tasks); }
        @Override public boolean isShutdown() { return this.shutdown; }
        @Override public boolean isTerminated() { return this.shutdown && this.tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return this.isTerminated(); }

        @Override
        public void execute(Runnable task) {
            if (this.shutdown) throw new RejectedExecutionException("test reader is closed");
            this.tasks.add(task);
        }

        private void runNext() throws Exception {
            Runnable task = this.tasks.remove();
            Thread worker = new Thread(task, "chunk-completion-test-io");
            worker.start();
            worker.join(5_000);
            assertFalse(worker.isAlive(), "test IO task must finish before the simulated main tick");
        }
    }
}
