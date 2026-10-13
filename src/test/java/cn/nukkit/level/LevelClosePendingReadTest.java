package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelClosePendingReadTest {
    @Test
    void skipsQueuedReadsButDrainsActiveReadBeforeSavingAndClosing() throws Exception {
        MockServer.init();
        Server server = mock(Server.class);
        server.asyncChunkSending = true;
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        when(server.getLevels()).thenReturn(new HashMap<>());
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.isOffThreadChunkReadSupported()).thenReturn(true);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        CountDownLatch enteredRead = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        when(provider.readChunkOffThread(anyInt(), anyInt())).thenAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) {
                enteredRead.countDown();
                assertTrue(releaseRead.await(5, TimeUnit.SECONDS));
            }
            return chunk;
        });
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16)) {
            @Override public void shutdown() {
                super.shutdown();
                shutdownStarted.countDown();
            }
        };
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        set(level, "server", server);
        set(level, "provider", provider);
        set(level, "providerLock", new ReentrantReadWriteLock());
        set(level, "asyncChunkLoadExecutor", executor);
        set(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        set(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        set(level, "generators", new ThreadLocal<>());
        set(level, "autoSave", true);
        doReturn(true).when(level).save(true);

        ExecutorService closer = Executors.newSingleThreadExecutor();
        try {
            assertTrue(level.requestChunkLoadAsync(0, 0));
            assertTrue(enteredRead.await(5, TimeUnit.SECONDS));
            for (int x = 1; x <= 16; x++) assertTrue(level.requestChunkLoadAsync(x, 0));

            Future<?> closed = closer.submit(level::close);
            assertTrue(shutdownStarted.await(5, TimeUnit.SECONDS));
            assertFalse(closed.isDone(), "close must wait for the provider read already in flight");
            verify(provider, never()).close();
            verify(level, never()).save(true);
            assertFalse(level.requestChunkLoadAsync(99, 0), "shutdown rejects new reads");

            releaseRead.countDown();
            closed.get(5, TimeUnit.SECONDS);
            assertEquals(1, reads.get(), "all 16 queued read-only requests must skip disk IO");
            verify(provider, times(1)).readChunkOffThread(anyInt(), anyInt());
            InOrder saveOrder = inOrder(level, provider);
            saveOrder.verify(level).save(true);
            saveOrder.verify(provider).close();
            assertNull(level.getProvider());
            for (Level.PendingChunkLoad pending : level.completedChunkLoads) level.mountChunk(pending);
            verify(provider, never()).putChunkIfAbsent(anyInt(), anyInt(), any());
        } finally {
            releaseRead.countDown();
            executor.shutdownNow();
            closer.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(closer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
