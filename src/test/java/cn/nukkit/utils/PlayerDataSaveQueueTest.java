package cn.nukkit.utils;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class PlayerDataSaveQueueTest {
    static final class Manual implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void next() { assertFalse(tasks.isEmpty()); tasks.remove().run(); }
    }

    @Test void fifoActivatesOnlyEligibleWorkAndUnrelatedProfileCanProceed() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 8)) {
            List<Integer> order = new ArrayList<>();
            queue.submit("a", () -> order.add(1)); queue.submit("A", () -> order.add(2));
            queue.submit("a", () -> order.add(3)); queue.submit("b", () -> order.add(4));
            assertEquals(2, writes.tasks.size(), "successors must not occupy workers awaiting predecessors");
            writes.next(); writes.next(); writes.next(); writes.next();
            assertEquals(List.of(1, 4, 2, 3), order);
        }
    }

    @Test void readAfterWriteWaitsForAckAndCompletedObservationIsInvalidatedAtNewAdmission() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 4)) {
            int[] disk = {0}; queue.submit("p", () -> disk[0] = 1);
            var read = queue.read(Set.of("p"), current -> disk[0]);
            assertTrue(reads.tasks.isEmpty()); writes.next(); reads.next();
            assertEquals(1, read.completion().toCompletableFuture().join()); assertTrue(read.isCurrent());
            var own = queue.admit("p", () -> disk[0] = 2);
            assertFalse(read.isCurrent()); assertTrue(queue.isLatest(own));
            queue.admit("p", () -> disk[0] = 3); assertFalse(queue.isLatest(own));
            read.close(); writes.next(); writes.next();
        }
    }

    @Test void failedSaveBlocksReadButNewSuccessfulCheckpointHealsTheSameIdentityAtFullStateCapacity() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 1)) {
            queue.submit("a", () -> { throw new IOException("failed a"); }); writes.next();
            queue.submit("b", () -> { throw new IOException("failed b"); }); writes.next();
            assertFalse(queue.admit("new", () -> 0).accepted());
            var refusedRead = queue.read(Set.of("a"), current -> fail("old bytes must not be read"));
            assertThrows(CompletionException.class, () -> refusedRead.completion().toCompletableFuture().join()); refusedRead.close();
            var healing = queue.admit("a", () -> 7); assertTrue(healing.accepted()); writes.next();
            assertEquals(7, healing.completion().toCompletableFuture().join());
            var recovered = queue.read(Set.of("a"), current -> 7); reads.next();
            assertEquals(7, recovered.completion().toCompletableFuture().join()); recovered.close();
            // Failure b remains explicit; closing never calls it healthy.
            assertFalse(queue.close(Duration.ZERO));
        }
    }

    @Test void executorRejectionNeverRunsInlineAndRetainsFailedIdentity() {
        AtomicInteger io = new AtomicInteger();
        Executor rejected = task -> { throw new RejectedExecutionException("provider full"); };
        try (var queue = new PlayerDataSaveQueue(rejected, Runnable::run, 2)) {
            var write = queue.admit("p", () -> io.incrementAndGet());
            assertThrows(CompletionException.class, () -> write.completion().toCompletableFuture().join());
            assertEquals(0, io.get());
            var read = queue.read(Set.of("p"), current -> fail("rejected save is not an ACK"));
            assertThrows(CompletionException.class, () -> read.completion().toCompletableFuture().join()); read.close();
            assertFalse(queue.close(Duration.ZERO));
        }
    }

    @Test void cancelledRunningReadKeepsCreditUntilPhysicalExitAndShutdownDoesNotGuessAck() throws Exception {
        var writes = new Manual(); ExecutorService readers = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var queue = new PlayerDataSaveQueue(writes, readers, 1);
        try {
            var read = queue.read(Set.of("a"), current -> { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return 1; });
            assertTrue(started.await(3, TimeUnit.SECONDS)); read.close();
            var excess = queue.read(Set.of("b"), current -> 2);
            assertThrows(CompletionException.class, () -> excess.completion().toCompletableFuture().join()); excess.close();
            assertFalse(queue.close(Duration.ZERO), "running IO remains unresolved");
        } finally { release.countDown(); readers.shutdown(); assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void cancelledReadWaitingOnPredecessorCannotGrowUnboundedCompletionNodes() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 1)) {
            queue.submit("a", () -> 1);
            var waiting = queue.read(Set.of("a"), current -> 2); waiting.close();
            var overCapacity = queue.read(Set.of("a"), current -> fail("cancelled dependency still consumes credit"));
            assertThrows(CompletionException.class, () -> overCapacity.completion().toCompletableFuture().join()); overCapacity.close();
            assertTrue(reads.tasks.isEmpty()); writes.next();
            var recovered = queue.read(Set.of("a"), current -> 3); reads.next();
            assertEquals(3, recovered.completion().toCompletableFuture().join()); recovered.close();
        }
    }

    @Test void atomicUnionMigrationOrdersAllKeysAndRejectsLateWritesUntilItsAck() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 8)) {
            List<String> operations = new ArrayList<>();
            queue.submit("old", () -> operations.add("old save"));
            var migration = queue.migrate("old", Set.of("old", "new", "name"), () -> operations.add("migration"));
            assertFalse(queue.admit("old", () -> fail("late old lifecycle write")).accepted());
            var read = queue.read(Set.of("new", "name"), current -> operations.size());
            assertTrue(reads.tasks.isEmpty()); assertEquals(1, writes.tasks.size());
            writes.next(); writes.next(); reads.next();
            assertEquals(List.of("old save", "migration"), operations);
            assertTrue(migration.completion().toCompletableFuture().join());
            read.close();
            // Explicit new API request after ACK is permitted; no unbounded lifetime alias tombstones.
            assertTrue(queue.admit("old", () -> operations.add("new explicit request")).accepted()); writes.next();
        }
    }

    @Test void unknownMigrationRequiresMatchingIdentityAndAnExplicitVerifiedRecoveryOperation() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 8)) {
            var failed = queue.migrateVerified("old", Set.of("old", "new", "name"), recovering -> {
                assertFalse(recovering); throw new IOException("rename visible; directory force failed");
            }, null);
            writes.next(); assertThrows(CompletionException.class, () -> failed.completion().toCompletableFuture().join());
            AtomicInteger incorrect = new AtomicInteger();
            var wrong = queue.migrateVerified("new", Set.of("old", "new", "name"), recovering -> incorrect.incrementAndGet(), null);
            assertThrows(CompletionException.class, () -> wrong.completion().toCompletableFuture().join()); assertEquals(0, incorrect.get());
            var retry = queue.migrateVerified("old", Set.of("old", "new", "name"), recovering -> {
                assertTrue(recovering); return "fresh target publication ACK";
            }, null);
            writes.next(); assertEquals("fresh target publication ACK", retry.completion().toCompletableFuture().join());
            var observed = queue.read(Set.of("new"), current -> 1); reads.next();
            assertEquals(1, observed.completion().toCompletableFuture().join()); observed.close();
        }
    }

    @Test void reverseMigrationsRegisterWithoutLockCycleAndUnknownAckCannotBeHealedByAnOrdinaryWrite() {
        var writes = new Manual(); var reads = new Manual();
        try (var queue = new PlayerDataSaveQueue(writes, reads, 8)) {
            var first = queue.migrate("a", Set.of("a", "b"), () -> 1);
            var reverse = queue.migrate("b", Set.of("b", "a"), () -> { throw new IOException("directory fsync after rename"); });
            assertEquals(1, writes.tasks.size()); writes.next(); writes.next();
            assertEquals(1, first.completion().toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> reverse.completion().toCompletableFuture().join());
            AtomicInteger unsafe = new AtomicInteger(); var write = queue.admit("a", unsafe::incrementAndGet);
            assertThrows(CompletionException.class, () -> write.completion().toCompletableFuture().join()); assertEquals(0, unsafe.get());
            assertFalse(queue.close(Duration.ZERO));
        }
    }
}
