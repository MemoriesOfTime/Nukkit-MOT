package cn.nukkit.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerSchedulerTickGapTest {

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
