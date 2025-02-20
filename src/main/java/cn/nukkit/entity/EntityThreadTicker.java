package cn.nukkit.entity;

import cn.nukkit.utils.ThrowableRunnable;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EntityThreadTicker {
    private static final EntityThreadTicker INSTANCE = new EntityThreadTicker();

    private static long timestamp = 0;
    private static final AtomicInteger lastCallCount = new AtomicInteger(0);
    private static final AtomicInteger callCount = new AtomicInteger(0);
    private static final int[] executionTime = new int[20];

    private Lock lock = new ReentrantLock();
    private Executor executor = Executors.newSingleThreadExecutor();

    public synchronized void tickEntities(ThrowableRunnable runnable) {
        if (lock.tryLock()) {
            lock.unlock();

            executor.execute(() -> {
                lock.lock();
                try {
                    callCount.incrementAndGet();
                    if(timestamp != (System.currentTimeMillis() / 1000)) {
                        lastCallCount.set(callCount.get());
                        callCount.set(0);
                        timestamp = System.currentTimeMillis() / 1000;
                    }
                    var instant = Instant.now();
                    runnable.run();
                    int delta = (int) (Instant.now().toEpochMilli() - instant.toEpochMilli());
                    for (int index = executionTime.length - 2; index > 0; index--) {
                        executionTime[index] = executionTime[index + 1];
                    }
                    executionTime[0] = delta;
                } finally {
                    lock.unlock();
                }
            });
        }
    }

    public static int getLastCallCount() {
        return lastCallCount.get();
    }

    public static int getExecutionTime() {
        int time = 0;

        for(int i : executionTime) {
            time+=i;
        }

        return time / 20;
    }

    public static EntityThreadTicker getInstance() {
        return INSTANCE;
    }
}