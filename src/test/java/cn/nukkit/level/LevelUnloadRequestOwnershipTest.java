package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admission order and request ownership must not depend on distinct millisecond timestamps. */
class LevelUnloadRequestOwnershipTest {
    @BeforeEach void initialize() { MockServer.init(); }

    @Test @Timeout(15)
    void parallelAdmissionsReadTheClockInsideTheQueueMonitor() throws Exception {
        Level level = level();
        Long2LongLinkedOpenHashMap queue = queue(level);
        long first = Level.chunkHash(1, 7), second = Level.chunkHash(2, 7);
        CountDownLatch firstClockRead = new CountDownLatch(1), secondFinished = new CountDownLatch(1);
        AtomicBoolean clockOutsideMonitor = new AtomicBoolean();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var earlier = workers.submit(() -> {
                try (var clock = Mockito.mockStatic(Level.class, Mockito.CALLS_REAL_METHODS)) {
                    clock.when(Level::unloadClock).thenAnswer(call -> {
                        boolean held = Thread.holdsLock(queue);
                        if (!held) clockOutsideMonitor.set(true);
                        firstClockRead.countDown();
                        // Reproduce the old inversion deterministically: an unlocked read can be
                        // paused until the later admission has appended. A locked read cannot.
                        if (!held) assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
                        return 100L;
                    });
                    assertTrue(level.unloadChunkRequest(1, 7, false));
                }
            });
            var later = workers.submit(() -> {
                try {
                    assertTrue(firstClockRead.await(5, TimeUnit.SECONDS));
                    try (var clock = Mockito.mockStatic(Level.class, Mockito.CALLS_REAL_METHODS)) {
                        clock.when(Level::unloadClock).thenAnswer(call -> {
                            if (!Thread.holdsLock(queue)) clockOutsideMonitor.set(true);
                            return 101L;
                        });
                        assertTrue(level.unloadChunkRequest(2, 7, false));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
                } finally { secondFinished.countDown(); }
            });
            earlier.get(7, TimeUnit.SECONDS); later.get(7, TimeUnit.SECONDS);
            assertFalse(clockOutsideMonitor.get());
            assertArrayEquals(new long[]{first, second}, queue.keySet().toLongArray());
            assertEquals(100L, queue.get(first));
            assertEquals(101L, queue.get(second));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void sameMillisecondReplacementKeepsItsOwnAdmission(boolean cancelFirst, boolean removed) throws Exception {
        Level level = level();
        long hash = Level.chunkHash(12, 4), time = 50_000L;
        AtomicInteger reads = new AtomicInteger();
        try (var clock = Mockito.mockStatic(Level.class, Mockito.CALLS_REAL_METHODS)) {
            // Admission, pass cutoff and replacement all see the same millisecond. A stale
            // refused settlement would perform a fourth read and restart the new grace at +100.
            clock.when(Level::unloadClock).thenAnswer(call -> reads.incrementAndGet() <= 3 ? time : time + 100);
            assertTrue(level.unloadChunkRequest(12, 4, false));
            doAnswer(call -> {
                if (cancelFirst) level.cancelUnloadChunkRequest(hash);
                assertTrue(level.unloadChunkRequest(12, 4, false));
                return removed;
            }).when(level).unloadChunk(12, 4, true);

            level.unloadChunks(1, true);

            assertTrue(level.isUnloadChunkRequested(hash), "settling the old attempt must retain its replacement");
            assertEquals(time, queue(level).get(hash), "a refused old attempt must not restart the replacement's grace");
            assertEquals(3, reads.get(), "stale settlement must not read a new retry timestamp");
            verify(level, times(1)).unloadChunk(12, 4, true);
        }
    }

    @Test
    void loaderCallbackCannotRemoveASameMillisecondReplacement() throws Exception {
        Level level = level();
        long hash = Level.chunkHash(6, 9), time = 50_000L;
        try (var clock = Mockito.mockStatic(Level.class, Mockito.CALLS_REAL_METHODS)) {
            clock.when(Level::unloadClock).thenReturn(time);
            assertTrue(level.unloadChunkRequest(6, 9, false));
            doAnswer(call -> {
                level.cancelUnloadChunkRequest(hash);
                assertTrue(level.unloadChunkRequest(6, 9, false));
                return true;
            }).when(level).isChunkInUse(hash);

            level.unloadChunks(1, true);

            assertTrue(level.isUnloadChunkRequested(hash));
            assertEquals(time, queue(level).get(hash));
            verify(level, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
        }
    }

    @Test
    void sameMillisecondBurstDoesNotExtendTheRealGracePeriod() throws Exception {
        Level level = level();
        long time = 50_000L;
        try (var clock = Mockito.mockStatic(Level.class, Mockito.CALLS_REAL_METHODS)) {
            clock.when(Level::unloadClock).thenReturn(time);
            for (int x = 0; x < 2048; x++) assertTrue(level.unloadChunkRequest(x, 5, false));
            for (long queuedAt : queue(level).values()) assertEquals(time, queuedAt);

            clock.when(Level::unloadClock).thenReturn(time + Level.CHUNK_UNLOAD_DELAY_MILLIS - 1);
            level.unloadChunks(50, false);
            verify(level, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());

            clock.when(Level::unloadClock).thenReturn(time + Level.CHUNK_UNLOAD_DELAY_MILLIS);
            level.unloadChunks(50, false);
            verify(level, times(50)).unloadChunk(anyInt(), anyInt(), anyBoolean());
            assertEquals(2048 - 50, queue(level).size());
        }
    }

    private static Level level() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        set(level, "server", mock(Server.class));
        set(level, "provider", mock(LevelProvider.class));
        set(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        set(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        set(level, "unloadQueue", new Long2LongLinkedOpenHashMap());
        set(level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
        set(level, "playerLoaders", new Long2ObjectNonBlockingMap<>());
        set(level, "loaders", new Int2ObjectOpenHashMap<>());
        set(level, "loaderCounter", new Int2IntOpenHashMap());
        doReturn(false).when(level).isSpawnChunk(anyInt(), anyInt());
        doReturn(true).when(level).unloadChunk(anyInt(), anyInt(), anyBoolean());
        return level;
    }

    private static Long2LongLinkedOpenHashMap queue(Level level) throws Exception {
        Field field = Level.class.getDeclaredField("unloadQueue"); field.setAccessible(true);
        return (Long2LongLinkedOpenHashMap) field.get(level);
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name); field.setAccessible(true); field.set(level, value);
    }
}
