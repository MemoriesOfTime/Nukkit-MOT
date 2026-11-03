package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ServerSchedulerTest {

    @Nested
    class QueueScheduling {

        private ServerScheduler scheduler;

        @BeforeEach
        void createScheduler() {
            // Synchronous tasks need no running Server. AsyncPool starts no workers
            // until a task is submitted, and these tests never submit async tasks.
            scheduler = new ServerScheduler();
            scheduler.mainThreadHeartbeat(1);
        }

        @AfterEach
        void closeScheduler() {
            scheduler.cancelAllTasks();
            scheduler.mainThreadHeartbeat(Integer.MAX_VALUE);
            scheduler.shutdown(0);
        }

        @Test
        void tasksWithTheSameDeadlineRunOnceInSubmissionOrder() {
            List<String> calls = new ArrayList<>();
            TaskHandler first = scheduler.scheduleDelayedTask(null, () -> calls.add("first"), 4);
            TaskHandler second = scheduler.scheduleDelayedTask(null, () -> calls.add("second"), 4);
            TaskHandler third = scheduler.scheduleDelayedTask(null, () -> calls.add("third"), 4);

            scheduler.mainThreadHeartbeat(2);
            scheduler.mainThreadHeartbeat(3);
            scheduler.mainThreadHeartbeat(4);
            assertTrue(calls.isEmpty());

            scheduler.mainThreadHeartbeat(5);
            scheduler.mainThreadHeartbeat(6);
            assertEquals(List.of("first", "second", "third"), calls);
            assertFalse(scheduler.isQueued(first.getTaskId()));
            assertFalse(scheduler.isQueued(second.getTaskId()));
            assertFalse(scheduler.isQueued(third.getTaskId()));
        }

        @Test
        void taskSubmittedInsideCallbackWaitsForTheNextHeartbeat() {
            List<String> calls = new ArrayList<>();
            scheduler.scheduleTask(null, () -> {
                calls.add("outer");
                scheduler.scheduleTask(null, () -> calls.add("nested"));
            });

            scheduler.mainThreadHeartbeat(2);
            assertEquals(List.of("outer"), calls);
            scheduler.mainThreadHeartbeat(3);
            assertEquals(List.of("outer", "nested"), calls);
            scheduler.mainThreadHeartbeat(4);
            assertEquals(List.of("outer", "nested"), calls);
        }

        @Test
        void repeatingTaskKeepsItsPeriodAndCancellationStopsTheNextRun() {
            List<Integer> ticks = new ArrayList<>();
            Task task = new Task() {
                @Override
                public void onRun(int currentTick) {
                    ticks.add(currentTick);
                }
            };
            TaskHandler handler = scheduler.scheduleRepeatingTask(task, 2);

            scheduler.mainThreadHeartbeat(2);
            scheduler.mainThreadHeartbeat(3);
            scheduler.mainThreadHeartbeat(4);
            assertEquals(List.of(2, 4), ticks);

            scheduler.cancelTask(handler.getTaskId());
            scheduler.mainThreadHeartbeat(5);
            scheduler.mainThreadHeartbeat(6);
            scheduler.mainThreadHeartbeat(7);
            assertEquals(List.of(2, 4), ticks);
            assertFalse(scheduler.isQueued(handler.getTaskId()));
        }

        @Test
        void selfCancellationCallsOnCancelOnceAndNeverRunsAgain() {
            List<Integer> ticks = new ArrayList<>();
            List<String> cancellations = new ArrayList<>();
            Task task = new Task() {
                @Override
                public void onRun(int currentTick) {
                    ticks.add(currentTick);
                    cancel();
                }

                @Override
                public void onCancel() {
                    cancellations.add("cancelled");
                }
            };
            TaskHandler handler = scheduler.scheduleRepeatingTask(task, 1);

            scheduler.mainThreadHeartbeat(2);
            scheduler.mainThreadHeartbeat(3);
            scheduler.mainThreadHeartbeat(4);
            assertEquals(List.of(2), ticks);
            assertEquals(List.of("cancelled"), cancellations);
            assertFalse(scheduler.isQueued(handler.getTaskId()));
        }

        @Test
        void cancelledDelayedTaskDoesNotRunOrDisplaceItsBucketNeighbours() {
            List<String> calls = new ArrayList<>();
            scheduler.scheduleDelayedTask(null, () -> calls.add("first"), 3);
            TaskHandler cancelled = scheduler.scheduleDelayedTask(null, () -> calls.add("cancelled"), 3);
            scheduler.scheduleDelayedTask(null, () -> calls.add("last"), 3);
            scheduler.mainThreadHeartbeat(2);
            scheduler.cancelTask(cancelled.getTaskId());

            scheduler.mainThreadHeartbeat(3);
            scheduler.mainThreadHeartbeat(4);
            assertEquals(List.of("first", "last"), calls);
            assertFalse(scheduler.isQueued(cancelled.getTaskId()));
        }

        @Test
        void largeIntegerTickJumpDrainsAllExpiredBucketsExactlyOnce() {
            List<String> calls = new ArrayList<>();
            scheduler.scheduleDelayedTask(null, () -> calls.add("first"), 2);
            scheduler.scheduleDelayedTask(null, () -> calls.add("second"), 4);
            scheduler.scheduleDelayedTask(null, () -> calls.add("third"), 6);
            scheduler.mainThreadHeartbeat(2);
            assertTrue(calls.isEmpty());

            // A gap greater than the number of buckets selects the scheduler's
            // existing catch-up traversal. Its order between buckets is unspecified.
            scheduler.mainThreadHeartbeat(20);
            assertEquals(List.of("first", "second", "third"), calls.stream().sorted().toList());
            assertEquals(0, scheduler.getQueueSize());
            scheduler.mainThreadHeartbeat(21);
            assertEquals(3, calls.size());
        }
    }

    @Nested
    class TickGapCatchUp {

        @Test
        void smallIntegerTickGapRunsEveryDueBucketAndKeepsEqualTickFifo() {
            ServerScheduler scheduler = new ServerScheduler();
            List<String> calls = new ArrayList<>();
            try {
                scheduler.mainThreadHeartbeat(1);
                scheduler.scheduleDelayedTask(null, () -> calls.add("tick3-first"), 2);
                scheduler.scheduleDelayedTask(null, () -> calls.add("tick3-second"), 2);
                scheduler.scheduleDelayedTask(null, () -> calls.add("tick4"), 3);
                scheduler.scheduleDelayedTask(null, () -> calls.add("tick5"), 4);
                scheduler.mainThreadHeartbeat(2);
                assertEquals(List.of(), calls);

                // Gap=2 and bucketCount=3 takes the bounded tick loop, not the
                // large-gap map traversal. Both expired buckets must be consumed.
                scheduler.mainThreadHeartbeat(4);
                assertEquals(List.of("tick3-first", "tick3-second", "tick4"), calls);
                scheduler.mainThreadHeartbeat(5);
                scheduler.mainThreadHeartbeat(6);
                assertEquals(List.of("tick3-first", "tick3-second", "tick4", "tick5"), calls);
                assertEquals(0, scheduler.getQueueSize());
            } finally {
                scheduler.cancelAllTasks();
                scheduler.mainThreadHeartbeat(Integer.MAX_VALUE);
                scheduler.shutdown(0);
            }
        }

        @Test
        void repeatingTaskInSkippedBucketRunsOnceThenResumesFromTheNextHeartbeat() {
            ServerScheduler scheduler = new ServerScheduler();
            List<Integer> calls = new ArrayList<>();
            Task repeating = new Task() {
                @Override
                public void onRun(int currentTick) {
                    calls.add(currentTick);
                }
            };
            try {
                scheduler.mainThreadHeartbeat(1);
                scheduler.scheduleDelayedRepeatingTask(repeating, 2, 1);
                scheduler.scheduleDelayedTask(null, () -> { }, 3);
                scheduler.scheduleDelayedTask(null, () -> { }, 4);
                scheduler.mainThreadHeartbeat(2);
                scheduler.mainThreadHeartbeat(4);
                assertEquals(List.of(3), calls);

                // Repeats still pass through pending. They do not run recursively
                // during catch-up and keep the existing clamp to the next heartbeat.
                scheduler.mainThreadHeartbeat(5);
                scheduler.mainThreadHeartbeat(6);
                assertEquals(List.of(3, 5, 6), calls);
            } finally {
                scheduler.cancelAllTasks();
                scheduler.mainThreadHeartbeat(Integer.MAX_VALUE);
                scheduler.shutdown(0);
            }
        }
    }

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
    @Nested
    class VirtualTaskDispatch {

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

    @Nested
    class CancelledTaskRetention {

        @Test void allCancellationRoutesReleaseLongDelayedTasksOnNextHeartbeat() throws Exception {
            ServerScheduler scheduler = new ServerScheduler();
            AtomicInteger runs = new AtomicInteger();
            Plugin owner = mock(Plugin.class);
            TaskHandler byId = scheduler.scheduleDelayedTask(owner, runs::incrementAndGet, 20 * 86400);
            TaskHandler direct = scheduler.scheduleDelayedTask(runs::incrementAndGet, 20 * 86400);
            TaskHandler removed = scheduler.scheduleDelayedTask(runs::incrementAndGet, 20 * 86400);
            TaskHandler byPlugin = scheduler.scheduleDelayedTask(owner, runs::incrementAndGet, 20 * 86400);
            TaskHandler survivor = scheduler.scheduleDelayedTask(runs::incrementAndGet, 20 * 86400);
            scheduler.mainThreadHeartbeat(1);
            assertEquals(5, scheduler.getQueueSize());
            scheduler.cancelTask(byId.getTaskId());
            direct.cancel();
            removed.remove();
            byPlugin.cancel();
            scheduler.mainThreadHeartbeat(2);
            assertEquals(1, scheduler.getQueueSize());
            assertEquals(1, tasks(scheduler).size());
            assertTrue(scheduler.isQueued(survivor.getTaskId()));
            scheduler.mainThreadHeartbeat(20 * 86400);
            assertEquals(1, runs.get());
            scheduler.cancelAllTasks();
        }

        @Test void pluginCancellationAndCancellationBeforeAdmissionDoNotKeepClosures() throws Exception {
            ServerScheduler scheduler = new ServerScheduler();
            Plugin owner = mock(Plugin.class);
            for (int i = 0; i < 1000; i++) scheduler.scheduleDelayedTask(owner, () -> fail("cancelled task ran"), 1_000_000);
            scheduler.cancelTask(owner);
            scheduler.mainThreadHeartbeat(1);
            assertEquals(0, scheduler.getQueueSize());
            assertTrue(tasks(scheduler).isEmpty());
            scheduler.cancelAllTasks();
        }

        @Test void workerThreadCancellationOnlyChangesBucketsOnMainHeartbeat() throws Exception {
            ServerScheduler scheduler = new ServerScheduler();
            TaskHandler task = scheduler.scheduleDelayedTask(() -> fail("cancelled task ran"), 1_000_000);
            scheduler.mainThreadHeartbeat(1);
            Thread worker = new Thread(task::cancel);
            worker.start(); worker.join();
            assertEquals(1, scheduler.getQueueSize());
            scheduler.mainThreadHeartbeat(2);
            assertEquals(0, scheduler.getQueueSize());
            assertTrue(tasks(scheduler).isEmpty());
            scheduler.cancelAllTasks();
        }

        @Test void cancellationFailureStillReleasesTaskAndRemoveSkipsOnCancel() {
            ServerScheduler scheduler = new ServerScheduler();
            AtomicInteger cancelled = new AtomicInteger();
            Task broken = new Task() {
                public void onRun(int tick) { fail("cancelled task ran"); }
                public void onCancel() { cancelled.incrementAndGet(); throw new IllegalStateException("expected"); }
            };
            TaskHandler handler = scheduler.scheduleDelayedTask(broken, 1_000_000);
            scheduler.mainThreadHeartbeat(1);
            assertThrows(IllegalStateException.class, handler::cancel);
            handler.cancel();
            scheduler.mainThreadHeartbeat(2);
            assertEquals(0, scheduler.getQueueSize());
            assertEquals(1, cancelled.get());
            TaskHandler silent = scheduler.scheduleDelayedTask(broken, 1_000_000);
            silent.remove();
            scheduler.mainThreadHeartbeat(3);
            assertEquals(1, cancelled.get());
            assertEquals(0, scheduler.getQueueSize());
            scheduler.cancelAllTasks();
        }

        private static Map<?, ?> tasks(ServerScheduler scheduler) throws Exception {
            Field field = ServerScheduler.class.getDeclaredField("taskMap");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(scheduler);
        }
    }
}
