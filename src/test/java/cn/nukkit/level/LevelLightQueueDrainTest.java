package cn.nukkit.level;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelLightQueueDrainTest {
    @Test
    void failedChunkReadLeavesLaterBucketsQueued() {
        Level level = mock(Level.class);
        Map<Long, Set<Integer>> queue = new LinkedHashMap<>();
        DimensionData dimension = DimensionEnum.OVERWORLD.getDimensionData();
        queue.put(Level.chunkHash(0, 0), new HashSet<>(Set.of(Level.localBlockHash(1, 64, 1, dimension))));
        queue.put(Level.chunkHash(1, 0), new HashSet<>(Set.of(Level.localBlockHash(17, 64, 1, dimension))));
        when(level.getDimensionData()).thenReturn(dimension);
        when(level.getChunk(0, 0, false)).thenThrow(new IllegalStateException("chunk read failed"));
        doCallRealMethod().when(level).updateBlockLight(anyMap());
        assertThrows(IllegalStateException.class, () -> level.updateBlockLight(queue));
        assertFalse(queue.containsKey(Level.chunkHash(0, 0)));
        assertTrue(queue.containsKey(Level.chunkHash(1, 0)), "later chunks must survive a failed bucket");
    }

    @Test
    void anEnqueueBetweenLookupAndAddIsEitherDrainedOrLeftForTheNextTick() throws Exception {
        Level level = mock(Level.class);
        Map<Long, Set<Integer>> queue = new ConcurrentHashMap<>();
        CountDownLatch inAdd = new CountDownLatch(1), releaseAdd = new CountDownLatch(1);
        Set<Integer> drained = ConcurrentHashMap.newKeySet();
        Set<Integer> entries = ConcurrentHashMap.newKeySet();
        DimensionData dimension = DimensionEnum.OVERWORLD.getDimensionData();
        int oldHash = Level.localBlockHash(1, 64, 1, dimension);
        int newHash = Level.localBlockHash(2, 64, 1, dimension);
        entries.add(oldHash);
        queue.put(Level.chunkHash(0, 0), new AbstractSet<>() {
            public int size() { return entries.size(); }
            public boolean add(Integer value) {
                inAdd.countDown();
                try { if (!releaseAdd.await(10, TimeUnit.SECONDS)) throw new AssertionError("producer not released"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return entries.add(value);
            }
            public Iterator<Integer> iterator() {
                Iterator<Integer> inner = entries.iterator();
                return new Iterator<>() {
                    public boolean hasNext() { return inner.hasNext(); }
                    public Integer next() { Integer value = inner.next(); drained.add(value); return value; }
                };
            }
        });
        Field field = Level.class.getDeclaredField("lightQueue"); field.setAccessible(true); field.set(level, queue);
        when(level.getDimensionData()).thenReturn(dimension);
        doCallRealMethod().when(level).addLightUpdate(anyInt(), anyInt(), anyInt());
        doCallRealMethod().when(level).updateBlockLight(anyMap());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread producer = new Thread(() -> { try { level.addLightUpdate(2, 64, 1); } catch (Throwable t) { error.set(t); } });
        Thread consumer = new Thread(() -> { try { level.updateBlockLight(queue); } catch (Throwable t) { error.set(t); } });
        producer.setDaemon(true); consumer.setDaemon(true);
        try {
            producer.start(); assertTrue(inAdd.await(10, TimeUnit.SECONDS)); consumer.start();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean observed = false;
            while (System.nanoTime() < end) {
                if (!consumer.isAlive()) { observed = true; break; }
                var info = ManagementFactory.getThreadMXBean().getThreadInfo(consumer.getId());
                if (info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null
                        && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(queue)) {
                    observed = true; break;
                }
                Thread.yield();
            }
            assertTrue(observed, "consumer must finish or wait on the queue held by producer");
        } finally {
            releaseAdd.countDown(); producer.join(10000); consumer.join(10000);
        }
        assertFalse(producer.isAlive()); assertFalse(consumer.isAlive()); assertNull(error.get());
        boolean pending = queue.values().stream().anyMatch(set -> set.contains(newHash));
        assertTrue(drained.contains(newHash) || pending, "the detached Set swallowed a completed enqueue");
    }
}
