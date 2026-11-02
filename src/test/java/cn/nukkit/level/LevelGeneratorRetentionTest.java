package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.PopChunkManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LevelGeneratorRetentionTest {
    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Level level() throws Exception {
        // Avoid loading worlds or mocking Level itself: Mockito's invocation history is
        // intentionally unsuitable as the object under a weak-reference regression test.
        Level level = new ObjenesisStd().newInstance(Level.class);
        Server server = mock(Server.class);
        Map<Integer, Level> levels = new HashMap<>();
        levels.put(level.getId(), level);
        when(server.getLevels()).thenReturn(levels);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getGeneratorOptions()).thenReturn(Map.of());
        set(level, "server", server);
        set(level, "provider", provider);
        set(level, "providerLock", new ReentrantReadWriteLock());
        set(level, "generatorClass", TestGenerator.class);
        set(level, "generators", new WeakHashMap<Thread, Generator>());
        set(level, "dimensionData", new DimensionData(0, -64, 319));
        return level;
    }

    @Test
    void generatorsStayDistinctPerWorkerAndAreReused() throws Exception {
        Level level = level();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Generator main = level.getGenerator();
            assertNotNull(main);
            assertSame(main, level.getGenerator());
            Generator async = worker.submit(level::getGenerator).get(5, TimeUnit.SECONDS);
            assertNotSame(main, async);
            assertSame(async, worker.submit(level::getGenerator).get(5, TimeUnit.SECONDS));
            assertEquals(-64, async.getChunkManager().getMinBlockY());
            level.setDimensionData(new DimensionData(0, 0, 127));
            assertEquals(127, async.getChunkManager().getMaxBlockY(), "keep the live dimension supplier");
            level.close();
            Field field = Level.class.getDeclaredField("generators");
            field.setAccessible(true);
            assertTrue(((Map<?, ?>) field.get(level)).isEmpty(), "close clears every worker's cache");
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void closedLevelIsCollectibleWhileGenerationWorkerStaysAlive() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            WeakReference<Level> reference = useAndClose(worker);
            // Ensure the executor no longer has the generation Callable on its stack.
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            awaitCollection(reference);
            assertNull(reference.get(), "an idle generation worker must not retain an unloaded level");
            assertFalse(worker.isShutdown());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void terminatedWorkerDoesNotGrowTheLevelCacheForever() throws Exception {
        Level level = level();
        WeakReference<Thread> worker = useTemporaryWorker(level);
        awaitCollection(worker);
        assertNull(worker.get(), "the level must not retain terminated async workers");
        level.getGenerator(); // Expunge stale weak keys.
        Field field = Level.class.getDeclaredField("generators");
        field.setAccessible(true);
        assertEquals(1, ((Map<?, ?>) field.get(level)).size());
        level.close();
    }

    private static WeakReference<Thread> useTemporaryWorker(Level level) throws InterruptedException {
        Thread thread = new Thread(level::getGenerator);
        thread.start();
        thread.join(5000);
        assertFalse(thread.isAlive());
        return new WeakReference<>(thread);
    }

    @Test
    void failedInitializationIsCachedPerThread() throws Exception {
        Level level = level();
        set(level, "generatorClass", FailingGenerator.class);
        FailingGenerator.attempts.set(0);
        assertNull(level.getGenerator());
        assertNull(level.getGenerator());
        assertEquals(1, FailingGenerator.attempts.get());
        level.close();
    }

    @Test
    void providerCloseFailureStillReleasesEveryCachedGenerator() throws Exception {
        Level level = level();
        level.getGenerator();
        doThrow(new IllegalStateException("test provider close failure")).when(level.getProvider()).close();
        assertDoesNotThrow(level::close);
        Field field = Level.class.getDeclaredField("generators");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(level)).isEmpty());
    }

    @Test
    void closeDoesNotWaitForAnInitializingGeneratorOrRetainItsLevelAfterward() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            WeakReference<Level> reference = closeDuringInitialization(worker);
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            awaitCollection(reference);
            assertNull(reference.get(), "completion after close must not create a worker-owned world root");
        } finally {
            BlockingGenerator.release.countDown();
            worker.shutdownNow();
        }
    }

    private static WeakReference<Level> closeDuringInitialization(ExecutorService worker) throws Exception {
        Level level = level();
        set(level, "generatorClass", BlockingGenerator.class);
        BlockingGenerator.started = new CountDownLatch(1);
        BlockingGenerator.release = new CountDownLatch(1);
        var task = worker.submit(() -> {
            assertNotNull(level.getGenerator());
        });
        assertTrue(BlockingGenerator.started.await(5, TimeUnit.SECONDS));
        // Initialization holds no generator-cache monitor: close must return before release.
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), level::close);
        BlockingGenerator.release.countDown();
        task.get(5, TimeUnit.SECONDS);
        return new WeakReference<>(level);
    }

    private static WeakReference<Level> useAndClose(ExecutorService worker) throws Exception {
        Level level = level();
        worker.submit(() -> {
            assertNotNull(level.getGenerator());
            return null;
        }).get(5, TimeUnit.SECONDS);
        level.close();
        return new WeakReference<>(level);
    }

    private static void awaitCollection(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 40 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(25);
        }
    }

    public static final class FailingGenerator extends TestGenerator {
        static final AtomicInteger attempts = new AtomicInteger();
        public FailingGenerator(Map<String, Object> settings) {
            super(settings);
            attempts.incrementAndGet();
            throw new IllegalStateException("test generator initialization failure");
        }
    }

    public static final class BlockingGenerator extends TestGenerator {
        static CountDownLatch started = new CountDownLatch(1);
        static CountDownLatch release = new CountDownLatch(1);
        public BlockingGenerator(Map<String, Object> settings) { super(settings); }
        @Override public void init(ChunkManager manager, NukkitRandom random) {
            if (manager instanceof PopChunkManager) {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("generator not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            super.init(manager, random);
        }
    }

    public static class TestGenerator extends Generator {
        private ChunkManager manager;
        public TestGenerator(Map<String, Object> settings) {}
        @Override public void init(ChunkManager manager, NukkitRandom random) { this.manager = manager; }
        @Override public int getId() { return TYPE_VOID; }
        @Override public void populateStructure(int x, int z) {}
        @Override public void generateChunk(int x, int z) {}
        @Override public void populateChunk(int x, int z) {}
        @Override public Map<String, Object> getSettings() { return Map.of(); }
        @Override public String getName() { return "retention-test"; }
        @Override public Vector3 getSpawn() { return new Vector3(); }
        @Override public ChunkManager getChunkManager() { return manager; }
    }
}
