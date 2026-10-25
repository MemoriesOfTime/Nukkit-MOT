package cn.nukkit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 共享世界 I/O 弹性池测试:负载下增长到上限、线程命名/daemon、上限创建即固定、懒建契约
 * <p>
 * Elastic shared world I/O pool tests: growth to the cap under load, thread naming/daemon,
 * the cap being fixed at creation, and the lazy-creation contract
 *
 * @author LT_Name
 */
public class WorldIOPoolAdaptiveTest {

    @Test
    public void poolGrowsToParallelismUnderBurst() throws Exception {
        ForkJoinPool pool = Server.createWorldIOPool(4, "TestLane");
        CountDownLatch release = new CountDownLatch(1);
        try {
            CountDownLatch allStarted = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) {
                pool.execute(() -> {
                    allStarted.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            // 4 个阻塞任务并发起步 = 线程数已增长到上限
            // Four blocking tasks starting concurrently proves growth to the cap
            Assertions.assertTrue(allStarted.await(5, TimeUnit.SECONDS), "all four workers should run concurrently");
            Assertions.assertEquals(4, pool.getPoolSize(), "workers should grow to the parallelism cap");

            // 满载后新任务排队,不再超上限开线程;仅断言 poolSize 是同义反复,须确认它确实没跑
            // With the cap saturated, extra submissions queue instead of spawning more threads;
            // asserting poolSize alone is a tautology, so prove the extra task really did not run
            CountDownLatch extraRan = new CountDownLatch(1);
            pool.execute(extraRan::countDown);
            Assertions.assertFalse(extraRan.await(200, TimeUnit.MILLISECONDS),
                    "the 5th task must queue behind the cap instead of running on a new worker");
            Assertions.assertEquals(4, pool.getPoolSize(), "pool must never exceed the parallelism cap");

            // 释放后排队任务补位执行 / the queued task runs once a worker frees up
            release.countDown();
            Assertions.assertTrue(extraRan.await(5, TimeUnit.SECONDS), "the queued task should run after release");
        } finally {
            release.countDown();
            pool.shutdown();
            Assertions.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void workersAreDaemonAndNamed() throws Exception {
        ForkJoinPool pool = Server.createWorldIOPool(1, "TestLane");
        try {
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            pool.execute(() -> {
                worker.set(Thread.currentThread());
                done.countDown();
            });
            Assertions.assertTrue(done.await(5, TimeUnit.SECONDS));

            Thread thread = worker.get();
            Assertions.assertNotNull(thread);
            Assertions.assertTrue(thread.isDaemon(), "workers must be daemon so a wedged task cannot block exit");
            Assertions.assertTrue(thread.getName().startsWith("TestLane Worker-"), "unexpected worker name: " + thread.getName());
        } finally {
            pool.shutdown();
            Assertions.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void parallelismCapIsFixedAtCreation() throws Exception {
        ForkJoinPool pool = Server.createWorldIOPool(3, "TestLane");
        try {
            // 上限在创建时确定;JDK17 无 setParallelism,配置变更需重启生效
            // The cap is fixed at creation; JDK 17 has no setParallelism, so config changes need a restart
            Assertions.assertEquals(3, pool.getParallelism());
        } finally {
            pool.shutdown();
            Assertions.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * 懒建契约:async-chunks 关闭时两个池都返回 null(调用方回退同步路径);打开后按需创建且只创建一次。
     * <p>
     * Lazy contract: both pools return null while async-chunks is off (callers fall back to the sync
     * path); once on, each is created on demand exactly once.
     */
    @Test
    public void poolsAreLazilyCreatedOnlyWhileAsyncChunksIsEnabled() throws Exception {
        Server server = Mockito.mock(Server.class, Mockito.CALLS_REAL_METHODS);

        Assertions.assertNull(server.getChunkSerializationPool(), "null while async-chunks is off");
        Assertions.assertNull(server.getWorldChunkLoadPool(), "null while async-chunks is off");

        setField(server, "asyncChunkSending", true);
        ForkJoinPool serialization = (ForkJoinPool) server.getChunkSerializationPool();
        ForkJoinPool load = (ForkJoinPool) server.getWorldChunkLoadPool();
        try {
            Assertions.assertNotNull(serialization);
            Assertions.assertNotNull(load);
            Assertions.assertSame(serialization, server.getChunkSerializationPool(), "the pool must be created only once");
            Assertions.assertSame(load, server.getWorldChunkLoadPool(), "the pool must be created only once");
            // 上限按核数自动确定,这里只校验下界与加载 lane 的上界
            // The cap comes from the core count; verify the lower bound and the load lane's upper bound
            Assertions.assertTrue(serialization.getParallelism() >= 2);
            Assertions.assertTrue(load.getParallelism() >= 2 && load.getParallelism() <= 4);
        } finally {
            serialization.shutdownNow();
            load.shutdownNow();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Server.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
