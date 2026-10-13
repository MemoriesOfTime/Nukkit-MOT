package cn.nukkit.level;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.Test;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.LightingFixture;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockLightReadRetryTest {
    private static final long A = Level.chunkHash(0, 0), B = Level.chunkHash(1, 0);

    @Test
    void transientFullQueueRepairsColdNeighbourOnLaterLightingTicks() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.emitter(BlockID.GLOWSTONE);
            Map<Long, Set<Integer>> changes = f.change(A, 15);
            f.lightingTick(changes); f.assertOneRejectedTicket(); f.freeExecutor();
            f.advance(100);
            assertNotNull(f.chunks.get(B), "queuefree must revive the original cold neighbour without an external request");
            assertTrue(f.chunks.get(B).isInitialized());
            assertEquals(14, f.chunks.get(B).getBlockLight(0, 64, 8));
            assertEquals(2, f.opens.get()); assertEquals(1, f.reads.get()); assertEquals(2, f.closes.get());
            f.assertNoRetryState();
        }
    }

    @Test
    void retainedColdRootBucketResumesAndIsConsumedAfterNativeMount() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.emitter(BlockID.GLOWSTONE); f.chunks.get(A).setBlockLight(15, 64, 8, 15);
            Map<Long, Set<Integer>> changes = f.change(B, 0);
            f.lightingTick(changes); f.assertOneRejectedTicket(); assertTrue(changes.containsKey(B));
            f.freeExecutor();
            for (int i = 0; i < 100; i++) { f.lightingTick(changes); if (!f.level.pendingChunkLoads.isEmpty()) Thread.sleep(1); }
            assertNotNull(f.chunks.get(B)); assertTrue(changes.isEmpty());
            assertEquals(14, f.chunks.get(B).getBlockLight(0, 64, 8));
            assertEquals(2, f.opens.get()); assertEquals(2, f.closes.get()); f.assertNoRetryState();
        }
    }

    @Test
    void sustainedFullQueueHasOneGlobalAttemptPerTickWithCooldownAndFifoFairness() throws Exception {
        long c = Level.chunkHash(-1, 0), d = Level.chunkHash(0, 1);
        try (Fixture f = new Fixture(c, d)) {
            f.saturate(); f.emitter(BlockID.GLOWSTONE); f.boundary.mounted(f.chunks.get(A), true);
            assertEquals(3, f.opens.get());
            for (int i = 0; i < 60; i++) {
                int before = f.opens.get(); f.lightingTick(new HashMap<>());
                assertTrue(f.opens.get() - before <= 1, "one global retry admission per lighting tick");
            }
            assertEquals(0, f.reads.get()); assertEquals(f.opens.get(), f.closes.get());
            assertTrue(f.opens.get() <= 12, "saturation must not become a busy loop");
            assertTrue(f.openedHashes.size() >= 6, "the original rejected hashes must receive later attempts");
            assertEquals(f.openedHashes.subList(0, 3), f.openedHashes.subList(3, 6), "FIFO fairness across original hashes");
            Map<Long, Integer> previous = new HashMap<>();
            for (int i = 0; i < f.openedHashes.size(); i++) {
                Integer prior = previous.put(f.openedHashes.get(i), f.openedTicks.get(i));
                if (prior != null) assertTrue(f.openedTicks.get(i) - prior >= 20, "per-coordinate rejection cooldown");
            }
            int saturatedOpens = f.opens.get();
            f.freeExecutor(); f.advance(100);
            assertNotNull(f.chunks.get(B)); assertNotNull(f.chunks.get(c)); assertNotNull(f.chunks.get(d));
            assertEquals(3, f.reads.get()); f.assertNoRetryState();
            System.out.println("LIGHT_RETRY_BOUND ticks=60 global_max=1 cooldown_min=20 full_queue_opens=" + saturatedOpens);
        }
    }

    @Test
    void losingAllOriginatingRootsDoesNotReadFromAirHaloOrStaleChunks() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.emitter(BlockID.GLOWSTONE); f.boundary.mounted(f.chunks.get(A), true);
            f.chunks.remove(A); f.boundary.unloaded(0, 0); f.freeExecutor(); f.advance(100);
            f.assertNoReadOrPending(); assertEquals(1, f.opens.get()); f.assertNoRetryState();
        }
    }

    @Test
    void replacingRootGenerationWithoutFreshRequestDoesNotAuthorizeOldRetry() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true);
            f.chunks.put(A, f.chunk(0, 0)); f.freeExecutor(); f.advance(100);
            f.assertNoReadOrPending(); assertEquals(1, f.opens.get()); f.assertNoRetryState();
        }
    }

    @Test
    void freshRootGenerationCanReauthorizeTheSameRejectedHash() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true);
            FixtureChunk replacement = f.chunk(0, 0); f.chunks.put(A, replacement);
            f.emitter(BlockID.GLOWSTONE); f.boundary.mounted(replacement, true);
            assertEquals(1, f.opens.get(), "refresh ownership without duplicate admission during backpressure");
            f.freeExecutor(); f.advance(100);
            assertNotNull(f.chunks.get(B)); assertEquals(14, f.chunks.get(B).getBlockLight(0, 64, 8));
            assertEquals(2, f.opens.get()); f.assertNoRetryState();
        }
    }

    @Test
    void differentProviderEpochCannotOpenAnOldRetryTicket() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true);
            LevelProvider replacement = mock(LevelProvider.class);
            when(replacement.getLevel()).thenReturn(f.level);
            when(replacement.isOffThreadChunkReadSupported()).thenReturn(true);
            when(replacement.getLoadedChunk(anyLong())).thenAnswer(call -> f.chunks.get(call.getArgument(0)));
            doThrow(new AssertionError("stale provider retry must not open a ticket")).when(replacement).openChunkRead(anyInt(), anyInt());
            field(f.level, "provider", replacement); f.freeExecutor(); f.advance(100);
            verify(replacement, never()).openChunkRead(anyInt(), anyInt());
            f.assertNoReadOrPending(); assertEquals(1, f.opens.get()); f.assertNoRetryState();
        }
    }

    @Test
    void closingLevelCancelsDueWorkWithoutOpeningAnotherTicket() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true);
            field(f.level, "chunkLoadsClosing", true); f.freeExecutor(); f.advance(100);
            f.assertNoReadOrPending(); assertEquals(1, f.opens.get()); f.assertNoRetryState();
        }
    }

    @Test
    void changedBucketGenerationWithoutNewRequestCannotAuthorizeAColdRoot() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); Map<Long, Set<Integer>> changes = f.change(B, 0);
            f.lightingTick(changes); f.assertOneRejectedTicket();
            changes.put(B, new java.util.HashSet<>(changes.get(B))); f.freeExecutor(); f.advance(100);
            f.assertNoReadOrPending(); assertEquals(1, f.opens.get()); f.assertNoRetryState();
        }
    }

    @Test
    void outsideInitialHaloKeepsItsActualProducingChunkAuthority() throws Exception {
        long target = Level.chunkHash(4, 0);
        try (Fixture f = new Fixture(target)) {
            f.chunks.put(B, f.chunk(1, 0)); // original halo is entirely loaded
            for (int x = 3; x <= 5; x++) for (int z = -1; z <= 1; z++) {
                long hash = Level.chunkHash(x, z); if (hash != target) f.chunks.put(hash, f.chunk(x, z));
            }
            FixtureChunk producer = f.chunks.get(Level.chunkHash(3, 0));
            producer.ids[LightingFixture.index(15, 64, 8)] = BlockID.GLOWSTONE;
            producer.setBlockLight(15, 64, 8, 15);
            f.saturate(); deferOwned(f.boundary, 64, 64, 8, List.of(A), producer);
            assertEquals(1, f.opens.get()); assertEquals(List.of(target), f.openedHashes);
            f.chunks.remove(A); f.boundary.unloaded(0, 0); // outside producer remains live
            f.freeExecutor(); f.advance(100);
            assertNotNull(f.chunks.get(target)); assertEquals(14, f.chunks.get(target).getBlockLight(0, 64, 8));
            assertEquals(2, f.opens.get()); assertEquals(1, f.reads.get()); f.assertNoRetryState();
        }
    }

    @Test
    void disabledReadAndAcceptedDuplicateHaveNoRetryStateOrFutureListener() throws Exception {
        try (Fixture f = new Fixture()) {
            Server server = (Server) value(f.level, "server"); server.asyncChunkLoadCompletion = false;
            f.boundary.mounted(f.chunks.get(A), true); assertEquals(0, f.opens.get());
            assertNull(value(f.boundary, "rejectedReads")); f.advance(100); assertEquals(0, f.opens.get());
        }
        try (Fixture f = new Fixture()) {
            f.blockOnly(); f.boundary.mounted(f.chunks.get(A), true);
            assertEquals(1, f.opens.get()); assertNull(value(f.boundary, "rejectedReads"));
            f.boundary.mounted(f.chunks.get(A), true); assertEquals(1, f.opens.get());
            assertEquals(1, f.level.pendingChunkLoads.get(B).completion.getNumberOfDependents(), "only the original minimal stage depends on completion");
            f.freeExecutor(); f.advance(100); assertNotNull(f.chunks.get(B));
        }
    }

    @Test
    void discardedProviderEpochAllowsFreshOwnedSourceToAdmitWithoutOldRead() throws Exception {
        try (Fixture f = new Fixture()) {
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true);
            LevelProvider replacement = mock(LevelProvider.class);
            when(replacement.getLevel()).thenReturn(f.level);
            when(replacement.isOffThreadChunkReadSupported()).thenReturn(true);
            when(replacement.getLoadedChunk(anyLong())).thenAnswer(call -> f.chunks.get(call.getArgument(0)));
            field(f.level, "provider", replacement); f.freeExecutor(); f.advance(30);
            verify(replacement, never()).openChunkRead(anyInt(), anyInt());
            assertEquals(0, f.reads.get()); assertEquals(1, f.opens.get()); f.assertNoRetryState();
            FixtureChunk newRoot = new FixtureChunk(replacement, 0, 0);
            newRoot.ids[LightingFixture.index(15, 64, 8)] = BlockID.GLOWSTONE;
            f.chunks.put(A, newRoot);
            AtomicInteger newOpens = new AtomicInteger(), newReads = new AtomicInteger(), newCloses = new AtomicInteger();
            when(replacement.openChunkRead(1, 0)).thenAnswer(call -> {
                newOpens.incrementAndGet();
                return new ChunkReadTicket() {
                    @Override public BaseFullChunk read() { assertNotSame(f.main, Thread.currentThread()); newReads.incrementAndGet(); return new FixtureChunk(replacement, 1, 0); }
                    @Override public BaseFullChunk tryMount(BaseFullChunk chunk) { assertSame(f.main, Thread.currentThread()); f.chunks.put(B, (FixtureChunk) chunk); return chunk; }
                    @Override public void close() { assertEquals(1, newCloses.incrementAndGet()); }
                };
            });
            f.boundary.mounted(newRoot, true); f.advance(100);
            assertEquals(1, f.opens.get()); assertEquals(0, f.reads.get());
            assertEquals(1, newOpens.get()); assertEquals(1, newReads.get()); assertEquals(1, newCloses.get());
            assertNotNull(f.chunks.get(B)); assertTrue(f.chunks.get(B).isInitialized());
            assertEquals(14, f.chunks.get(B).getBlockLight(0, 64, 8)); f.assertNoRetryState();
        }
    }

    @Test
    void admittedFailedReadKeepsNativeBackoffAndDisabledReadDoesNotRepeat() throws Exception {
        try (Fixture f = new Fixture()) {
            doReturn(0L).when(f.level).chunkLoadRetryClock(); f.failReads = true;
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true); f.freeExecutor(); f.advance(100);
            assertEquals(2, f.opens.get()); assertEquals(1, f.reads.get()); assertEquals(2, f.closes.get());
            assertNotNull(f.level.chunkLoadFailures.getIfPresent(B));
            assertEquals(ChunkLoadResult.Status.FAILED, f.level.requestChunkLoadAsyncResult(1, 0).toCompletableFuture().get().status());
            f.advance(100); assertEquals(2, f.opens.get()); assertEquals(1, f.reads.get()); f.assertNoRetryState();
        }
        try (Fixture f = new Fixture()) {
            f.saturate(); f.boundary.mounted(f.chunks.get(A), true);
            Server server = (Server) value(f.level, "server"); server.asyncChunkLoadCompletion = false;
            f.freeExecutor(); f.advance(100);
            assertEquals(1, f.opens.get()); assertEquals(0, f.reads.get()); f.assertNoRetryState();
        }
    }

    @Test
    void providerChangedInsideRejectedAdmissionDoesNotStrandFreshBucketRequest() throws Exception {
        try (Fixture f = new Fixture()) {
            LevelProvider replacement = mock(LevelProvider.class);
            when(replacement.getLevel()).thenReturn(f.level);
            when(replacement.getLoadedChunk(anyLong())).thenAnswer(call -> f.chunks.get(call.getArgument(0)));
            when(replacement.isOffThreadChunkReadSupported()).thenReturn(true);
            AtomicInteger newOpens = new AtomicInteger(), newReads = new AtomicInteger(), newCloses = new AtomicInteger();
            when(replacement.openChunkRead(1, 0)).thenAnswer(call -> {
                newOpens.incrementAndGet();
                return new ChunkReadTicket() {
                    @Override public BaseFullChunk read() { assertNotSame(f.main, Thread.currentThread()); newReads.incrementAndGet(); return new FixtureChunk(replacement, 1, 0); }
                    @Override public BaseFullChunk tryMount(BaseFullChunk chunk) { assertSame(f.main, Thread.currentThread()); f.chunks.put(B, (FixtureChunk) chunk); return chunk; }
                    @Override public void close() { assertEquals(1, newCloses.incrementAndGet()); }
                };
            });
            doAnswer(call -> {
                f.opens.incrementAndGet(); field(f.level, "provider", replacement);
                return new ChunkReadTicket() {
                    @Override public BaseFullChunk read() { throw new AssertionError("old provider ticket must not read"); }
                    @Override public BaseFullChunk tryMount(BaseFullChunk chunk) { throw new AssertionError("old provider ticket must not mount"); }
                    @Override public void close() { assertEquals(1, f.closes.incrementAndGet()); }
                };
            }).when(f.provider).openChunkRead(1, 0);
            f.saturate(); Map<Long, Set<Integer>> bucket = f.change(B, 0);
            requestOwned(f.boundary, B, bucket); f.assertOneRejectedTicket(); f.freeExecutor();
            requestOwned(f.boundary, B, bucket);
            assertEquals(1, newOpens.get(), "stale rejected admission must release only its attempt marker for a fresh provider request");
            f.advance(80); assertEquals(1, newReads.get()); assertEquals(1, newCloses.get());
            assertNotNull(f.chunks.get(B)); assertTrue(f.chunks.get(B).isInitialized());
            assertEquals(0, f.reads.get()); f.assertNoRetryState();
        }
    }

    // New ownership overloads are GREEN-only checks. Baseline native retry cases use existing entrypoints.
    private static void deferOwned(BlockLightBoundary boundary, int x, int y, int z,
            List<Long> roots, BaseFullChunk source) throws Exception {
        var method = assertDoesNotThrow(() -> BlockLightBoundary.class.getDeclaredMethod(
                "defer", int.class, int.class, int.class, List.class, BaseFullChunk.class));
        method.setAccessible(true);
        method.invoke(boundary, x, y, z, roots, source);
    }

    private static void requestOwned(BlockLightBoundary boundary, long hash,
            Map<Long, Set<Integer>> bucket) throws Exception {
        var method = assertDoesNotThrow(() -> BlockLightBoundary.class.getDeclaredMethod(
                "request", long.class, Map.class));
        method.setAccessible(true);
        method.invoke(boundary, hash, bucket);
    }

    private static Object value(Object target, String name) throws Exception {
        Field field;
        try { field = target.getClass() == BlockLightBoundary.class ? BlockLightBoundary.class.getDeclaredField(name) : Level.class.getDeclaredField(name); }
        catch (NoSuchFieldException baseline) { if (name.equals("rejectedReads")) return null; throw baseline; }
        field.setAccessible(true); return field.get(target);
    }
    private static final class FixtureChunk extends LightingFixture {
        final LevelProvider owner;
        FixtureChunk(LevelProvider owner, int x, int z) {
            this.owner = owner;
            setPosition(x, z);
            setGenerated(true); setPopulated(true); setLightPopulated(true);
        }
        @Override public LevelProvider getProvider() { return owner; }
    }

    private static final class Fixture implements AutoCloseable {
        final Map<Long, FixtureChunk> chunks = new ConcurrentHashMap<>();
        final Set<Long> expectedCold = new java.util.HashSet<>();
        final List<Long> openedHashes = new ArrayList<>();
        final List<Integer> openedTicks = new ArrayList<>();
        int tick;
        boolean failReads;
        final AtomicInteger opens = new AtomicInteger(), reads = new AtomicInteger(), closes = new AtomicInteger();
        final List<ChunkReadTicket> tickets = new ArrayList<>();
        final Thread main = Thread.currentThread();
        final CountDownLatch release = new CountDownLatch(1);
        final Level level;
        final LevelProvider provider;
        final BlockLightBoundary boundary;
        final ThreadPoolExecutor executor;

        Fixture(long... coldHashes) throws Exception {
            expectedCold.add(B);
            for (long hash : coldHashes) expectedCold.add(hash);
            MockServer.init();
            Server server = mock(Server.class);
            server.asyncChunkSending = true; server.asyncChunkLoadCompletion = true; server.lightUpdates = true;
            when(server.isPrimaryThread()).thenAnswer(call -> Thread.currentThread() == main);
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            provider = mock(LevelProvider.class, withSettings().stubOnly());
            level = mock(Level.class, withSettings().defaultAnswer(CALLS_REAL_METHODS).stubOnly());
            field(level, "server", server); field(level, "provider", provider);
            field(level, "pendingChunkLoads", new ConcurrentHashMap<>());
            field(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
            field(level, "chunkLoadFailures", Caffeine.newBuilder().maximumSize(1024).build());
            field(level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
            field(level, "unloadQueue", new Long2ObjectNonBlockingMap<>());
            doReturn(DimensionEnum.OVERWORLD.getDimensionData()).when(level).getDimensionData();
            // Pure cache/range predicates are fixture state; update/queue/read/mount/tick stay native.
            doAnswer(call -> chunks.get(Level.chunkHash(call.getArgument(0), call.getArgument(1)))).when(level).getChunkIfLoaded(anyInt(), anyInt());
            doAnswer(call -> { int y = call.getArgument(0); return y >= 48 && y <= 79; }).when(level).isYInRange(anyInt());
            // Two vertical sections contain the full radius of this y=64 emitter.
            doReturn(48).when(level).getMinBlockY(); doReturn(79).when(level).getMaxBlockY();
            doThrow(new AssertionError("synchronous cold disk IO from lighting")).when(level).getChunk(anyInt(), anyInt(), anyBoolean());
            when(provider.getLevel()).thenReturn(level);
            when(provider.getMinBlockY()).thenReturn(48); when(provider.getMaxBlockY()).thenReturn(79);
            when(provider.isOffThreadChunkReadSupported()).thenReturn(true);
            when(provider.getLoadedChunk(anyLong())).thenAnswer(call -> chunks.get(call.getArgument(0)));
            when(provider.openChunkRead(anyInt(), anyInt())).thenAnswer(call -> {
                int x = call.getArgument(0), z = call.getArgument(1);
                long hash = Level.chunkHash(x, z);
                assertTrue(expectedCold.contains(hash), "fixture must not expand a read halo: " + x + "," + z);
                openedHashes.add(hash); openedTicks.add(tick);
                opens.incrementAndGet();
                ChunkReadTicket ticket = new ChunkReadTicket() {
                    boolean closed;
                    @Override public BaseFullChunk read() {
                        assertNotSame(main, Thread.currentThread(), "no inline cold read");
                        reads.incrementAndGet();
                        if (failReads) throw new java.io.UncheckedIOException(new java.io.IOException("controlled fixture read failure"));
                        return chunk(x, z);
                    }
                    @Override public BaseFullChunk tryMount(BaseFullChunk candidate) {
                        assertSame(main, Thread.currentThread());
                        FixtureChunk existing = chunks.putIfAbsent(hash, (FixtureChunk) candidate);
                        return existing == null ? candidate : existing;
                    }
                    @Override public void close() {
                        assertFalse(closed, "ticket closes once"); closed = true; closes.incrementAndGet();
                    }
                };
                tickets.add(ticket); return ticket;
            });
            ThreadFactory threads = action -> new Thread(action, "external-light-retry-proof-worker");
            ThreadPoolExecutor pool;
            try {
                Class<?> type = Class.forName("cn.nukkit.level.ChunkReadExecutor");
                Constructor<?> constructor = type.getDeclaredConstructor(int.class, ThreadFactory.class);
                constructor.setAccessible(true);
                pool = (ThreadPoolExecutor) constructor.newInstance(16, threads);
            } catch (ClassNotFoundException baseline) {
                pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16), threads);
            }
            executor = pool; field(level, "asyncChunkLoadExecutor", executor);
            boundary = new BlockLightBoundary(level); field(level, "blockLightBoundary", boundary);
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) {
                long hash = Level.chunkHash(x, z);
                if (!expectedCold.contains(hash)) chunks.put(hash, chunk(x, z));
            }
        }
        FixtureChunk chunk(int x, int z) { return new FixtureChunk(provider, x, z); }
        void emitter(int id) { chunks.get(A).ids[LightingFixture.index(15, 64, 8)] = id; }
        Map<Long, Set<Integer>> change(long hash, int localX) {
            Map<Long, Set<Integer>> map = new HashMap<>();
            map.put(hash, Set.of(Level.localBlockHash((Level.getHashX(hash) << 4) + localX, 64,
                    (Level.getHashZ(hash) << 4) + 8, level.getDimensionData()))); return map;
        }
        void blockOnly() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            executor.execute(() -> { started.countDown();
                try { assertTrue(release.await(180, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
        }
        void saturate() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            executor.execute(() -> {
                started.countDown();
                try { assertTrue(release.await(180, TimeUnit.SECONDS), "finite worker release"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 16; i++) executor.execute(() -> {});
            assertEquals(0, executor.getQueue().remainingCapacity());
        }
        void assertOneRejectedTicket() {
            assertEquals(1, opens.get()); assertEquals(1, closes.get()); assertEquals(0, reads.get());
            assertTrue(level.pendingChunkLoads.isEmpty());
            assertTrue(level.completedChunkLoads.isEmpty());
        }
        void freeExecutor() throws Exception {
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(16, executor.getQueue().remainingCapacity()); assertEquals(0, executor.getActiveCount());
        }
        void lightingTick(Map<Long, Set<Integer>> changes) {
            tick++;
            level.updateBlockLight(changes); boundary.tick(); level.retryFailedChunkReads();
            for (Level.PendingChunkLoad pending; (pending = level.completedChunkLoads.poll()) != null;) level.mountChunk(pending);
        }
        void assertNoReadOrPending() {
            assertEquals(0, reads.get()); assertTrue(level.pendingChunkLoads.isEmpty());
            assertTrue(level.completedChunkLoads.isEmpty()); assertNull(chunks.get(B));
        }
        void naturalTargetRead() throws Exception {
            CompletionStage<ChunkLoadResult> stage = level.requestChunkLoadAsyncResult(1, 0);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (level.completedChunkLoads.isEmpty() && System.nanoTime() < deadline) Thread.sleep(1);
            assertFalse(level.completedChunkLoads.isEmpty());
            lightingTick(new HashMap<>());
            assertEquals(ChunkLoadResult.Status.LOADED, stage.toCompletableFuture().get(5, TimeUnit.SECONDS).status());
            assertTrue(chunks.get(B).isInitialized());
            drainLight();
            assertEquals(2, opens.get()); assertEquals(1, reads.get()); assertEquals(2, closes.get());
        }
        void advance(int count) throws Exception {
            for (int i = 0; i < count; i++) {
                lightingTick(new HashMap<>());
                if (!level.pendingChunkLoads.isEmpty()) Thread.sleep(1);
            }
        }
        void assertNoRetryState() throws Exception {
            Object value = value(boundary, "rejectedReads");
            assertTrue(value == null || ((Map<?, ?>) value).isEmpty());
        }
        void drainLight() {
            for (int i = 0; i < 1000 && boundary.hasWork(); i++) lightingTick(new HashMap<>());
            assertFalse(boundary.hasWork(), "positive-control light convergence");
        }
        @Override public void close() {
            release.countDown(); executor.shutdownNow();
            try { assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        }
    }
    private static void field(Level target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
