package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class PlayerDataLockRetentionTest {
    @Test void completedIdentitiesDoNotRemainStronglyReachable() throws Exception {
        var locks = Server.createPlayerDataLocks();
        List<WeakReference<ReentrantLock>> observed = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            observed.add(new WeakReference<>(locks.computeIfAbsent("identity-" + i, k -> new ReentrantLock())));
        }
        for (int attempt = 0; attempt < 100 && observed.stream().anyMatch(r -> r.get() != null); attempt++) {
            System.gc();
            Thread.sleep(20);
        }
        assertTrue(observed.stream().allMatch(r -> r.get() == null), "completed lock objects remain rooted");
        // MapMaker drains cleared references on subsequent reads/writes, not on an idle timer.
        for (int i = 0; i < 100_000 && !locks.isEmpty(); i++) locks.get("maintenance-" + i);
        assertTrue(locks.isEmpty(), "cleared value entries keep identity strings");
    }

    @Test void heldWaitedAndReentrantLocksAreNotReplacedByCollection() throws Exception {
        var locks = Server.createPlayerDataLocks();
        ReentrantLock held = locks.computeIfAbsent("identity", k -> new ReentrantLock());
        held.lock();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch lookedUp = new CountDownLatch(1);
            Future<?> waiter = worker.submit(() -> {
                ReentrantLock lock = locks.computeIfAbsent(new String("identity"), k -> new ReentrantLock());
                assertSame(held, lock);
                lookedUp.countDown();
                lock.lock();
                try { assertSame(lock, locks.computeIfAbsent("identity", k -> new ReentrantLock())); }
                finally { lock.unlock(); }
            });
            assertTrue(lookedUp.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 5; i++) System.gc();
            ReentrantLock nested = locks.computeIfAbsent(new String("identity"), k -> new ReentrantLock());
            assertSame(held, nested);
            nested.lock();
            assertEquals(2, held.getHoldCount());
            nested.unlock();
            assertFalse(waiter.isDone());
            held.unlock();
            waiter.get(5, TimeUnit.SECONDS);
            Reference.reachabilityFence(held);
        } finally {
            if (held.isHeldByCurrentThread()) held.unlock();
            worker.shutdownNow();
        }
    }

    @Test void concurrentLookupAndCollectionNeverSplitTheCriticalSection() throws Exception {
        var locks = Server.createPlayerDataLocks();
        AtomicInteger inSection = new AtomicInteger(), overlaps = new AtomicInteger(), completed = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) futures.add(workers.submit(() -> {
                for (int n = 0; n < 10_000; n++) {
                    ReentrantLock lock = locks.computeIfAbsent(new String("contended"), k -> new ReentrantLock());
                    lock.lock();
                    try {
                        if (inSection.incrementAndGet() != 1) overlaps.incrementAndGet();
                        completed.incrementAndGet();
                        inSection.decrementAndGet();
                    } finally { lock.unlock(); }
                    if (n % 2_500 == 0) System.gc();
                }
            }));
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
            assertEquals(80_000, completed.get());
            assertEquals(0, overlaps.get());
        } finally { workers.shutdownNow(); }
    }
}
