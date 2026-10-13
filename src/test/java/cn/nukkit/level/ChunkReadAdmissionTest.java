package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.utils.MainLogger;
import com.github.benmanes.caffeine.cache.Caffeine;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkReadAdmissionTest {
    @Test
    void saturatedReadAdmissionClosesEachTicketWithoutExceptionOrInlineIo() throws Exception {
        try (Fixture fixture = new Fixture(2)) {
            fixture.saturate();
            Path file = Files.createTempFile("chunk-read-admission", ".jfr");
            try (Recording recording = new Recording()) {
                recording.enable("jdk.JavaExceptionThrow").withStackTrace();
                recording.start();
                for (int i = 0; i < 3; i++) {
                    ChunkLoadResult result = fixture.level.requestChunkLoadAsyncResult(i, 7)
                            .toCompletableFuture().get(5, TimeUnit.SECONDS);
                    assertEquals(ChunkLoadResult.Status.REJECTED, result.status());
                    assertFalse(result.isSuccess());
                    assertTrue(fixture.level.pendingChunkLoads.isEmpty());
                }
                recording.stop();
                recording.dump(file);
                long rejected = RecordingFile.readAllEvents(file).stream()
                        .filter(ChunkReadAdmissionTest::isRejectedExecutionException).count();
                assertEquals(0, rejected, "owned full-queue admission must not allocate RejectedExecutionException");
                assertEquals(3, fixture.tickets.size());
                for (ChunkReadTicket ticket : fixture.tickets) {
                    verify(ticket, times(1)).close();
                    verify(ticket, never()).read();
                    verify(ticket, never()).tryMount(any());
                }
                verify(fixture.provider, never()).readChunkOffThread(anyInt(), anyInt());
                verify(fixture.provider, never()).getChunk(anyInt(), anyInt(), anyBoolean());
                assertTrue(fixture.level.completedChunkLoads.isEmpty());
                assertEquals(2, fixture.executor.getQueue().size());
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    private static boolean isRejectedExecutionException(RecordedEvent event) {
        return event.getEventType().getName().equals("jdk.JavaExceptionThrow")
                && event.getClass("thrownClass").getName().equals(RejectedExecutionException.class.getName());
    }

    @Test
    void capacityReturningAllowsOnlyExplicitLaterRequestAndKeepsWorkerFifo() throws Exception {
        try (Fixture fixture = new Fixture(2)) {
            fixture.blockWorker();
            var first = fixture.level.requestChunkLoadAsyncResult(1, 7);
            var second = fixture.level.requestChunkLoadAsyncResult(2, 7);
            assertFalse(first.toCompletableFuture().isDone());
            assertFalse(second.toCompletableFuture().isDone());
            assertEquals(ChunkLoadResult.Status.REJECTED, fixture.level.requestChunkLoadAsyncResult(3, 7)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).status());
            fixture.release.countDown();
            fixture.awaitCompleted(2);
            assertEquals(List.of(1, 2), fixture.readOrder);
            assertEquals(3, fixture.tickets.size(), "rejection must not schedule a hidden retry");
            assertFalse(first.toCompletableFuture().isDone(), "decode is not mount acknowledgement");
            assertFalse(second.toCompletableFuture().isDone());
            var explicit = fixture.level.requestChunkLoadAsyncResult(3, 7);
            fixture.awaitCompleted(3);
            assertEquals(List.of(1, 2, 3), fixture.readOrder);
            assertFalse(explicit.toCompletableFuture().isDone());
            assertEquals(4, fixture.tickets.size());
            assertEquals(3, fixture.level.pendingChunkLoads.size());
            verify(fixture.tickets.get(2), times(1)).close();
            verify(fixture.tickets.get(2), never()).read();
        }
    }

    @Test
    void duplicateCoordinatesShareOneAcceptedReadAndStage() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            fixture.blockWorker();
            var first = fixture.level.requestChunkLoadAsyncResult(1, 7);
            assertSame(first, fixture.level.requestChunkLoadAsyncResult(1, 7));
            assertEquals(1, fixture.tickets.size());
            assertEquals(1, fixture.executor.getQueue().size());
            fixture.release.countDown();
            fixture.awaitCompleted(1);
            verify(fixture.tickets.get(0), times(1)).read();
            assertFalse(first.toCompletableFuture().isDone());
        }
    }

    @Test
    void shutdownRejectsButClosingLevelCancelsBeforeTicketAcquisition() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            fixture.executor.shutdown();
            assertEquals(ChunkLoadResult.Status.REJECTED, fixture.level.requestChunkLoadAsyncResult(1, 7)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).status());
            assertEquals(1, fixture.tickets.size());
            verify(fixture.tickets.get(0), times(1)).close();
            field(fixture.level, "chunkLoadsClosing", true);
            assertEquals(ChunkLoadResult.Status.CANCELLED, fixture.level.requestChunkLoadAsyncResult(2, 7)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).status());
            assertEquals(1, fixture.tickets.size());
            assertTrue(fixture.level.pendingChunkLoads.isEmpty());
        }
    }

    @Test
    void ordinaryExecutorExecuteKeepsThrowingRejectionContract() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            fixture.executor.shutdown();
            assertThrows(RejectedExecutionException.class, () -> fixture.executor.execute(() -> {}));
        }
    }

    @Test
    void customExecutorRejectionRetainsFallbackAndExactTicketClose() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            ThreadPoolExecutor custom = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1));
            custom.shutdown();
            field(fixture.level, "asyncChunkLoadExecutor", custom);
            assertEquals(ChunkLoadResult.Status.REJECTED, fixture.level.requestChunkLoadAsyncResult(1, 7)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).status());
            verify(fixture.tickets.get(0), times(1)).close();
            verify(fixture.tickets.get(0), never()).read();
            assertTrue(fixture.level.pendingChunkLoads.isEmpty());
        }
    }

    private static void field(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static final class Fixture implements AutoCloseable {
        final Level level;
        final LevelProvider provider;
        final ThreadPoolExecutor executor;
        final List<ChunkReadTicket> tickets = new ArrayList<>();
        final List<Integer> readOrder = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        Fixture(int capacity) throws Exception {
            MockServer.init();
            Server server = mock(Server.class);
            server.asyncChunkSending = true;
            server.asyncChunkLoadCompletion = true;
            when(server.isPrimaryThread()).thenReturn(true);
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            provider = mock(LevelProvider.class);
            when(provider.isOffThreadChunkReadSupported()).thenReturn(true);
            when(provider.openChunkRead(anyInt(), anyInt())).thenAnswer(call -> {
                int x = call.getArgument(0);
                ChunkReadTicket ticket = mock(ChunkReadTicket.class);
                when(ticket.read()).thenAnswer(read -> {
                    readOrder.add(x);
                    return mock(BaseFullChunk.class);
                });
                tickets.add(ticket);
                return ticket;
            });
            level = mock(Level.class, Mockito.CALLS_REAL_METHODS);
            field(level, "server", server);
            field(level, "provider", provider);
            when(provider.getLevel()).thenReturn(level);
            field(level, "pendingChunkLoads", new ConcurrentHashMap<>());
            field(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
            field(level, "chunkLoadFailures", Caffeine.newBuilder().maximumSize(1024).build());
            ThreadFactory threads = action -> new Thread(action, "bounded-read-admission-test");
            executor = productionExecutor(capacity, threads);
            field(level, "asyncChunkLoadExecutor", executor);
        }

        private static ThreadPoolExecutor productionExecutor(int capacity, ThreadFactory threads) throws Exception {
            try {
                Class<?> type = Class.forName("cn.nukkit.level.ChunkReadExecutor");
                Constructor<?> constructor = type.getDeclaredConstructor(int.class, ThreadFactory.class);
                constructor.setAccessible(true);
                return (ThreadPoolExecutor) constructor.newInstance(capacity, threads);
            } catch (ClassNotFoundException baseline) {
                return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(capacity), threads);
            }
        }

        void blockWorker() throws Exception {
            executor.execute(() -> {
                started.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS), "worker release timed out");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
        }

        void saturate() throws Exception {
            blockWorker();
            int capacity = executor.getQueue().remainingCapacity();
            for (int i = 0; i < capacity; i++) executor.execute(() -> {});
        }

        void awaitCompleted(int count) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (level.completedChunkLoads.size() < count && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(count, level.completedChunkLoads.size(), "reads did not finish before deadline");
        }

        @Override public void close() {
            release.countDown();
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            for (Level.PendingChunkLoad pending : level.pendingChunkLoads.values()) {
                if (pending.ticket != null) pending.ticket.close();
            }
        }
    }
}
