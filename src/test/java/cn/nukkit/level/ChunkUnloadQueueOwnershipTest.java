package cn.nukkit.level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 入队与结算按 revision 归属：同毫秒替换不会被旧请求的结算覆盖，时钟在队列监视器内读取。
 * <p>Admission and settlement are revision-owned: a same-millisecond replacement keeps its own
 * request, and admissions read the clock under the queue monitor.
 */
class ChunkUnloadQueueOwnershipTest {

    @Test
    void aSameMillisecondReplacementKeepsItsOwnGrace() {
        AtomicLong clock = new AtomicLong(50_000L);
        ChunkUnloadQueue queue = new ChunkUnloadQueue(clock::get);
        long hash = Level.chunkHash(12, 4);
        List<Long> attempts = new ArrayList<>();
        ChunkUnloadQueue.Target target = new ChunkUnloadQueue.Target() {
            @Override
            public boolean isChunkInUse(long candidate) {
                return false;
            }

            @Override
            public boolean isChunkSaveBacklogged() {
                return false;
            }

            @Override
            public boolean unloadChunk(long candidate) {
                attempts.add(candidate);
                // 事件内取消并同毫秒重新入队，结算发生在 100ms 之后
                queue.cancel(hash);
                queue.request(hash);
                clock.addAndGet(100);
                return false;
            }
        };
        queue.request(hash);
        clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        queue.drain(target, false, 50, Long.MAX_VALUE);

        assertTrue(queue.contains(hash), "结算旧请求不得移除替换请求 / settling the old attempt must keep the replacement");
        clock.set(50_000L + 2 * ChunkUnloadQueue.GRACE_MILLIS - 1);
        queue.drain(target, false, 50, Long.MAX_VALUE);
        assertEquals(1, attempts.size(), "旧请求的结算不得推迟替换请求的宽限期 / a stale settlement must not restart the replacement's grace");

        clock.incrementAndGet();
        queue.drain(target, false, 50, Long.MAX_VALUE);
        assertEquals(2, attempts.size());
    }

    @Test
    void aLoaderCallbackCannotRemoveASameMillisecondReplacement() {
        AtomicLong clock = new AtomicLong(50_000L);
        ChunkUnloadQueue queue = new ChunkUnloadQueue(clock::get);
        long hash = Level.chunkHash(6, 9);
        AtomicInteger inUseCalls = new AtomicInteger();
        List<Long> attempts = new ArrayList<>();
        ChunkUnloadQueue.Target target = new ChunkUnloadQueue.Target() {
            @Override
            public boolean isChunkInUse(long candidate) {
                if (inUseCalls.incrementAndGet() > 1) {
                    return false;
                }
                // 检查期间 loader 取消旧请求并同毫秒重新入队
                queue.cancel(hash);
                queue.request(hash);
                clock.addAndGet(100);
                return true;
            }

            @Override
            public boolean isChunkSaveBacklogged() {
                return false;
            }

            @Override
            public boolean unloadChunk(long candidate) {
                attempts.add(candidate);
                return true;
            }
        };
        queue.request(hash);
        clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        queue.drain(target, false, 50, Long.MAX_VALUE);

        assertTrue(queue.contains(hash), "移除旧请求不得连带删除替换请求 / removing the old request must keep the replacement");
        assertTrue(attempts.isEmpty());

        clock.set(50_000L + 2 * ChunkUnloadQueue.GRACE_MILLIS);
        queue.drain(target, false, 50, Long.MAX_VALUE);
        assertEquals(1, attempts.size(), "替换请求仍按自己的宽限期到期 / the replacement keeps its own grace");
    }

    @Test
    void aCancellationDuringAnUnloadIsNotUndoneBySettlement() {
        AtomicLong clock = new AtomicLong(1_000L);
        ChunkUnloadQueue queue = new ChunkUnloadQueue(clock::get);
        long hash = Level.chunkHash(2, 2);
        List<Long> attempts = new ArrayList<>();
        ChunkUnloadQueue.Target target = new ChunkUnloadQueue.Target() {
            @Override
            public boolean isChunkInUse(long candidate) {
                return false;
            }

            @Override
            public boolean isChunkSaveBacklogged() {
                return false;
            }

            @Override
            public boolean unloadChunk(long candidate) {
                attempts.add(candidate);
                queue.cancel(hash);
                return true;
            }
        };
        queue.request(hash);
        clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        queue.drain(target, false, 50, Long.MAX_VALUE);

        assertEquals(List.of(hash), attempts);
        assertFalse(queue.contains(hash));
    }

    @Test
    @Timeout(15)
    void concurrentAdmissionsReadTheClockInsideTheQueueMonitor() throws Exception {
        AtomicReference<ChunkUnloadQueue> holder = new AtomicReference<>();
        AtomicBoolean outsideMonitor = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        ChunkUnloadQueue queue = new ChunkUnloadQueue(() -> {
            ChunkUnloadQueue current = holder.get();
            if (current == null || !Thread.holdsLock(current)) {
                outsideMonitor.set(true);
            }
            return reads.incrementAndGet();
        });
        holder.set(queue);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            for (int worker = 0; worker < 2; worker++) {
                int offset = worker * 1000;
                workers.execute(() -> {
                    try {
                        start.await();
                        for (int x = 0; x < 100; x++) {
                            queue.request(Level.chunkHash(offset + x, 5));
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                });
            }
            start.countDown();
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertFalse(outsideMonitor.get(), "入队必须在队列监视器内读取时钟 / admissions read the clock under the queue monitor");
        assertEquals(200, queue.size());
        assertEquals(200, reads.get());
    }
}
