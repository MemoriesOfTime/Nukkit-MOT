package cn.nukkit.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSchedulerQueueTest {

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
