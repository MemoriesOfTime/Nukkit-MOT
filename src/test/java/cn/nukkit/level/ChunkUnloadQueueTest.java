package cn.nukkit.level;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 到期队列按入队顺序卸载：队首未到期即停，force 忽略宽限期与单次计数。
 * <p>The due queue unloads oldest first and stops at the first chunk that is not due; a forced
 * pass ignores both the grace period and the count cap.
 */
class ChunkUnloadQueueTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final ChunkUnloadQueue queue = new ChunkUnloadQueue(this.clock::get);
    private final Probe target = new Probe();

    @Test
    void aPassStopsAtTheFirstChunkThatIsNotDue() {
        for (int x = 0; x < 1000; x++) {
            this.queue.request(Level.chunkHash(x, 7));
        }

        assertTrue(this.queue.drain(this.target, false, 50, Long.MAX_VALUE));

        assertTrue(this.target.attempts.isEmpty());
        assertEquals(0, this.target.inUseChecks, "只检查队首，不遍历整条队列 / only the head is inspected");
        assertEquals(1000, this.queue.size());
    }

    @Test
    void dueChunksLeaveOldestFirstAndTheRestWaits() {
        long first = Level.chunkHash(1, 0);
        long second = Level.chunkHash(2, 0);
        long last = Level.chunkHash(3, 0);
        this.queue.request(first);
        this.clock.incrementAndGet();
        this.queue.request(second);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);
        this.queue.request(last);

        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertEquals(List.of(first, second), this.target.attempts);
        assertFalse(this.queue.contains(first));
        assertFalse(this.queue.contains(second));
        assertTrue(this.queue.contains(last));
    }

    @Test
    void queueingAChunkAgainRestartsItsGracePeriod() {
        long hash = Level.chunkHash(6, 6);
        this.queue.request(hash);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS - 1);
        this.queue.request(hash);
        this.clock.incrementAndGet();

        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertTrue(this.target.attempts.isEmpty(), "重新入队必须重新计满宽限期 / re-queueing restarts the grace period");
        assertTrue(this.queue.contains(hash));
    }

    @Test
    void queueingAChunkAgainMovesItToTheTail() {
        long first = Level.chunkHash(4, 4);
        long second = Level.chunkHash(5, 4);
        this.queue.request(first);
        this.clock.incrementAndGet();
        this.queue.request(second);
        this.clock.incrementAndGet();
        this.queue.request(first);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertEquals(List.of(second, first), this.target.attempts);
    }

    @Test
    void aRefusedUnloadWaitsAFullGracePeriodInsteadOfRetryingEveryPass() {
        long hash = Level.chunkHash(7, 7);
        this.target.refuse = true;
        this.queue.request(hash);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS - 1);
        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertEquals(1, this.target.attempts.size(), "被拒后必须重新计满宽限期 / a refused unload waits a full grace");
        assertTrue(this.queue.contains(hash));

        this.clock.incrementAndGet();
        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);
        assertEquals(2, this.target.attempts.size());
    }

    @Test
    void aChunkTakenBackByALoaderLeavesTheQueue() {
        long hash = Level.chunkHash(8, 8);
        this.queue.request(hash);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);
        this.target.inUse.add(hash);

        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertTrue(this.target.attempts.isEmpty());
        assertFalse(this.queue.contains(hash), "释放 loader 时由 unregisterChunkLoader 重新入队 / releasing a loader re-queues it");
    }

    @Test
    void aForcedPassIgnoresGraceAndCount() {
        for (int x = 0; x < 120; x++) {
            this.queue.request(Level.chunkHash(x, 3));
        }

        this.queue.drain(this.target, true, 50, Long.MAX_VALUE);

        assertEquals(120, this.target.attempts.size(), "force 不受单次计数限制 / a forced pass is not capped");
        assertTrue(this.queue.isEmpty());
    }

    @Test
    void aForcedPassBypassesBackpressure() {
        this.queue.request(Level.chunkHash(1, 9));
        this.queue.request(Level.chunkHash(2, 9));
        this.target.backlogAfter = 0;

        this.queue.drain(this.target, true, 50, Long.MAX_VALUE);

        assertEquals(2, this.target.attempts.size());
        assertTrue(this.queue.isEmpty());
    }

    @Test
    void aCountLimitedPassStopsAtItsLimit() {
        for (int x = 0; x < 5; x++) {
            this.queue.request(Level.chunkHash(x, 1));
        }
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        this.queue.drain(this.target, false, 3, Long.MAX_VALUE);

        assertEquals(3, this.target.attempts.size());
        assertEquals(2, this.queue.size());
    }

    @Test
    void theDeadlineStopsThePassAfterTheCurrentUnload() {
        long first = Level.chunkHash(9, 0);
        long second = Level.chunkHash(10, 0);
        this.queue.request(first);
        this.queue.request(second);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);

        assertFalse(this.queue.drain(this.target, false, 50, this.clock.get()));

        assertEquals(List.of(first), this.target.attempts, "deadline 在完成一次卸载后生效 / the deadline is checked after an unload");
        assertTrue(this.queue.contains(second));
    }

    @Test
    void backpressureStopsBetweenWholeUnloadsAndResumes() {
        long first = Level.chunkHash(11, 0);
        long second = Level.chunkHash(12, 0);
        long third = Level.chunkHash(13, 0);
        this.queue.request(first);
        this.queue.request(second);
        this.queue.request(third);
        this.clock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS);
        this.target.backlogAfter = 1;

        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertEquals(List.of(first), this.target.attempts, "写积压只阻挡下一次卸载 / backpressure gates the next unload");
        assertEquals(2, this.queue.size());

        this.target.backlogAfter = Integer.MAX_VALUE;
        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertEquals(List.of(first, second, third), this.target.attempts);
        assertTrue(this.queue.isEmpty());
    }

    @Test
    void sameMillisecondBurstDoesNotExtendTheRealGracePeriod() {
        long time = this.clock.get();
        for (int x = 0; x < 2048; x++) {
            this.queue.request(Level.chunkHash(x, 5));
        }

        this.clock.set(time + ChunkUnloadQueue.GRACE_MILLIS - 1);
        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);
        assertTrue(this.target.attempts.isEmpty());

        this.clock.set(time + ChunkUnloadQueue.GRACE_MILLIS);
        this.queue.drain(this.target, false, 50, Long.MAX_VALUE);

        assertEquals(50, this.target.attempts.size());
        assertEquals(2048 - 50, this.queue.size());
    }

    /** 记录卸载调用与 Level 侧判断，让队列测试不依赖 Level。/ Records unload calls and Level-side decisions. */
    private static final class Probe implements ChunkUnloadQueue.Target {

        private final List<Long> attempts = new ArrayList<>();
        private final Set<Long> inUse = new HashSet<>();
        private int inUseChecks;
        private int completed;
        private int backlogAfter = Integer.MAX_VALUE;
        private boolean refuse;

        @Override
        public boolean isChunkInUse(long hash) {
            this.inUseChecks++;
            return this.inUse.contains(hash);
        }

        @Override
        public boolean isChunkSaveBacklogged() {
            return this.completed >= this.backlogAfter;
        }

        @Override
        public boolean unloadChunk(long hash) {
            this.attempts.add(hash);
            if (this.refuse) {
                return false;
            }
            this.completed++;
            return true;
        }
    }
}
