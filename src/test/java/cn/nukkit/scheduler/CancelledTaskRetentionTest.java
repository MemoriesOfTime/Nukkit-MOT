package cn.nukkit.scheduler;

import cn.nukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancelledTaskRetentionTest {
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
