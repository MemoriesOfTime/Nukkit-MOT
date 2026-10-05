package cn.nukkit.scheduler;

import cn.nukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ServerScheduler 的虚拟线程分发：isVirtual()=true 的 AsyncTask 在虚拟线程池可用时
 * 运行于虚拟线程（线程名前缀断言，测试字节码为 17 不能直接调 Thread.isVirtual()），
 * 不可用时自动回退平台线程池；onCompletion 回收与 shutdown 不回归。
 * <p>
 * Verifies virtual thread dispatch in ServerScheduler: an AsyncTask with isVirtual()=true
 * runs on a virtual thread when the pool is available (asserted via thread name prefix,
 * since test bytecode at 17 cannot call Thread.isVirtual() directly), falls back to the
 * platform pool otherwise; onCompletion collection and shutdown keep working.
 */
public class ServerSchedulerVirtualTaskTest {

    private ServerScheduler scheduler;

    @AfterEach
    public void tearDown() {
        if (scheduler != null) {
            scheduler.cancelAllTasks();
            scheduler.shutdown(2);
        }
    }

    @Test
    public void testVirtualAsyncTaskDispatch() throws Exception {
        scheduler = new ServerScheduler();
        boolean virtualActive = scheduler.isVirtualThreadsEnabled();
        assertEquals(VirtualThreadService.getInstance().isSupported(), virtualActive,
                "未传配置时默认启用状态应与 JVM 支持一致");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicBoolean completionRan = new AtomicBoolean(false);
        AsyncTask task = new AsyncTask() {
            @Override
            protected boolean isVirtual() {
                return true;
            }

            @Override
            public void onRun() {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            }

            @Override
            public void onCompletion(Server server) {
                completionRan.set(true);
            }
        };

        scheduler.scheduleAsyncTask(task);
        scheduler.mainThreadHeartbeat(1);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "虚拟任务未在 5s 内执行");

        if (virtualActive) {
            assertTrue(threadName.get().startsWith("Nukkit Virtual Task #"),
                    "应在虚拟线程池上执行, 实际线程: " + threadName.get());
        } else {
            assertTrue(threadName.get().startsWith("Nukkit Asynchronous Task Handler"),
                    "JVM 不支持时应回退平台线程池, 实际线程: " + threadName.get());
        }

        // 完成任务经 FINISHED_LIST 由主线程心跳回收。countDown 唤醒本线程时 offer 可能尚未发生
        // （onRun 返回前后存在窗口），须像真实主循环一样持续心跳直到回收，单次心跳会漏收
        // Finished tasks are collected via FINISHED_LIST on the main-thread heartbeat. The offer
        // may lag the countDown wakeup (window around onRun's return), so keep ticking until
        // collected like the real tick loop; a single heartbeat can miss it
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!completionRan.get()) {
            assertTrue(System.nanoTime() < deadline, "onCompletion 未在 5s 内被回收执行");
            scheduler.mainThreadHeartbeat(2);
            LockSupport.parkNanos(1_000_000);
        }
        assertTrue(task.isFinished());
    }

    @Test
    public void testPlainAsyncTaskStaysOnPlatformPool() throws Exception {
        scheduler = new ServerScheduler();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AsyncTask task = new AsyncTask() {
            @Override
            public void onRun() {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            }
        };

        scheduler.scheduleAsyncTask(task);
        scheduler.mainThreadHeartbeat(1);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("Nukkit Asynchronous Task Handler"),
                "非虚拟任务应始终走平台线程池, 实际线程: " + threadName.get());
    }

    @Test
    public void testDisabledByConfigFallsBack() throws Exception {
        scheduler = new ServerScheduler(false);
        org.junit.jupiter.api.Assertions.assertFalse(scheduler.isVirtualThreadsEnabled(),
                "配置关闭时虚拟线程池不应创建");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AsyncTask task = new AsyncTask() {
            @Override
            protected boolean isVirtual() {
                return true;
            }

            @Override
            public void onRun() {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            }
        };

        scheduler.scheduleAsyncTask(task);
        scheduler.mainThreadHeartbeat(1);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("Nukkit Asynchronous Task Handler"),
                "配置关闭时虚拟任务应回退平台线程池, 实际线程: " + threadName.get());
    }

    @Test
    public void testVirtualRequiresAsynchronous() {
        scheduler = new ServerScheduler();
        assertThrows(cn.nukkit.utils.PluginException.class,
                () -> scheduler.scheduleTask(null, () -> { }, false, true),
                "virtual && !asynchronous 应拒绝注册");
    }
}
