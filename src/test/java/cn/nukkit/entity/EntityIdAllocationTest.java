package cn.nukkit.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link Entity#nextEntityId()} 的原子分配与 {@link Entity#entityCount} 的兼容语义。
 * <p>
 * Verifies atomic allocation via {@link Entity#nextEntityId()} and the compatibility
 * semantics of the {@link Entity#entityCount} field (must stay a plain writable long).
 */
public class EntityIdAllocationTest {

    private static final int THREADS = 8;
    private static final int IDS_PER_THREAD = 10_000;

    @Test
    void sequentialAllocationMatchesLegacyPostIncrementSemantics() {
        long saved = Entity.entityCount;
        try {
            Entity.entityCount = 100;
            // 旧语义 this.id = entityCount++：id 取旧值，计数推进到旧值+1
            // Legacy semantics: id takes the previous value, then the counter advances
            assertEquals(100, Entity.nextEntityId());
            assertEquals(101, Entity.nextEntityId());
            assertEquals(102, Entity.entityCount);
        } finally {
            Entity.entityCount = saved;
        }
    }

    @Test
    void pluginWriteTakesEffectOnSubsequentAllocation() {
        long saved = Entity.entityCount;
        try {
            // 插件直接赋值是合法用法，必须继续生效（单一事实源）
            // Direct plugin assignment must keep working (single source of truth)
            Entity.entityCount = 5_000_000;
            assertEquals(5_000_000, Entity.nextEntityId());
            assertEquals(5_000_001, Entity.nextEntityId());
        } finally {
            Entity.entityCount = saved;
        }
    }

    @Test
    void concurrentAllocationYieldsUniqueIds() throws InterruptedException {
        long saved = Entity.entityCount;
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        try {
            for (int t = 0; t < THREADS; t++) {
                pool.execute(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < IDS_PER_THREAD; i++) {
                        ids.add(Entity.nextEntityId());
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
            assertEquals(THREADS * IDS_PER_THREAD, ids.size(), "parallel allocation must not produce duplicate ids");
            assertEquals(saved + THREADS * IDS_PER_THREAD, Entity.entityCount);
        } finally {
            Entity.entityCount = saved;
            pool.shutdownNow();
        }
    }
}
