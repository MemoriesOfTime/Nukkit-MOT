package cn.nukkit.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ZlibConcurrentCompressionTest {
    @Test
    void providerChangeWaitsForActiveWorkerAndThenDisablesWorkerCompression() throws Exception {
        Zlib.setProvider(2);
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch active = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch switching = new CountDownLatch(1);
        try {
            var compression = workers.submit(() -> Zlib.withConcurrentCompression(() -> {
                active.countDown();
                await(release);
                return true;
            }));
            assertTrue(active.await(5, TimeUnit.SECONDS));
            var change = workers.submit(() -> {
                switching.countDown();
                Zlib.setProvider(1);
            });
            assertTrue(switching.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> change.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(compression.get(5, TimeUnit.SECONDS));
            change.get(5, TimeUnit.SECONDS);
            AtomicBoolean called = new AtomicBoolean();
            assertNull(Zlib.withConcurrentCompression(() -> {
                called.set(true);
                return true;
            }));
            assertFalse(called.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            Zlib.setProvider(2);
        }
    }

    @Test
    void independentWorkersCompressConcurrently() throws Exception {
        Zlib.setProvider(2);
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch active = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var first = workers.submit(() -> Zlib.withConcurrentCompression(() -> {
                active.countDown();
                await(release);
                return true;
            }));
            var second = workers.submit(() -> Zlib.withConcurrentCompression(() -> {
                active.countDown();
                await(release);
                return true;
            }));
            assertTrue(active.await(5, TimeUnit.SECONDS), "workers must share the compression read lock");
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
