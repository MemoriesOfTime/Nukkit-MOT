package cn.nukkit.utils.collection.nb;

import cn.nukkit.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Player.usedChunks keys are Level.chunkHash values. Long.hashCode folds them to x ^ z, so
 * a view square fills a few dozen ConcurrentHashMap bins and every bin becomes a tree.
 * The non-blocking long map hashes the full 64-bit key; its open-addressing probe stays short.
 */
class ChunkHashKeyMapTest {
    private static final int[][] CENTERS = {{0, 0}, {3, 5}, {-2, -3}, {-1000, 1000},
            {12345, -6789}, {-30000, -30000}, {1 << 20, -(1 << 20)}};

    private static List<Long> square(int centerX, int centerZ, int radius) {
        List<Long> keys = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
            keys.add(Level.chunkHash(centerX + dx, centerZ + dz));
        return keys;
    }

    /** Longest linear-probe run when the keys are inserted with the map's own slot function. */
    private static int longestProbe(List<Long> keys, int length) {
        boolean[] used = new boolean[length];
        int longest = 0;
        for (long key : keys) {
            int index = Long2ObjectNonBlockingMap.hash(key) & (length - 1), probes = 1;
            while (used[index]) { index = (index + 1) & (length - 1); probes++; }
            used[index] = true;
            longest = Math.max(longest, probes);
        }
        return longest;
    }

    /** Largest ConcurrentHashMap bin for boxed Long keys (its spread function, same table length). */
    private static int largestConcurrentBin(List<Long> keys, int length) {
        int[] bins = new int[length];
        int largest = 0;
        for (Long key : keys) {
            int h = key.hashCode();
            int bin = ((h ^ (h >>> 16)) & 0x7fffffff) & (length - 1);
            largest = Math.max(largest, ++bins[bin]);
        }
        return largest;
    }

    @Test void viewSquaresDoNotClusterInTheNonBlockingMap() {
        for (int[] center : CENTERS) {
            for (int radius : new int[]{8, 16}) {
                List<Long> keys = square(center[0], center[1], radius);
                // The map resizes before it is half full: check every table that can hold the square.
                for (int length = Integer.highestOneBit(keys.size() * 2 - 1) << 1; length <= 16384; length <<= 1) {
                    assertTrue(longestProbe(keys, length) <= 8, "probe run for " + center[0] + "," + center[1]
                            + " r=" + radius + " len=" + length);
                }
                Long2ObjectNonBlockingMap<Boolean> map = new Long2ObjectNonBlockingMap<>();
                for (long key : keys) assertNull(map.put(key, Boolean.TRUE));
                assertEquals(keys.size(), map.size());
                for (long key : keys) assertSame(Boolean.TRUE, map.get((Object) key));
            }
        }
    }

    @Test void boxedLongKeysDegenerateInConcurrentHashMapBins() {
        // The measured cause: x ^ z leaves at most a few dozen bins for a 17x17 view square,
        // well past the treeify threshold of eight entries per bin.
        for (int[] center : new int[][]{{0, 0}, {3, 5}, {-2, -3}}) {
            assertTrue(largestConcurrentBin(square(center[0], center[1], 8), 512) > 8);
        }
    }

    @Test void mapContractMatchesWhatPlayerUses() {
        Map<Long, Boolean> used = new Long2ObjectNonBlockingMap<>();
        long origin = Level.chunkHash(0, 0), a = Level.chunkHash(3, 5), b = Level.chunkHash(-2, -3);
        assertNull(used.put(origin, Boolean.FALSE));
        assertNull(used.put(a, Boolean.TRUE));
        assertNull(used.put(b, Boolean.FALSE));
        assertSame(Boolean.FALSE, used.put(origin, Boolean.TRUE));
        assertSame(Boolean.TRUE, used.get(origin), "chunk (0,0) hashes to the map's reserved key");
        assertTrue(used.get(b) == Boolean.FALSE, "Player compares values by identity");
        assertTrue(used.containsKey(a));
        assertFalse(used.containsKey(Level.chunkHash(9, 9)));
        assertNull(used.get(Level.chunkHash(9, 9)));
        assertNull(used.get("not a long"));
        assertSame(Boolean.FALSE, used.getOrDefault(Level.chunkHash(9, 9), Boolean.FALSE));
        assertSame(Boolean.TRUE, used.getOrDefault(a, Boolean.FALSE));
        assertEquals(3, used.size());
        int seen = 0;
        for (long key : used.keySet()) {
            seen++;
            if (key == a) assertSame(Boolean.TRUE, used.remove(key));
        }
        assertEquals(3, seen);
        assertFalse(used.containsKey(a));
        Iterator<Long> keys = used.keySet().iterator();
        long removed = keys.next();
        keys.remove();
        assertFalse(used.containsKey(removed));
        assertEquals(1, used.size());
        for (Map.Entry<Long, Boolean> entry : used.entrySet()) assertTrue(used.containsKey(entry.getKey()));
        used.clear();
        assertTrue(used.isEmpty());
        assertEquals(0, used.size());
        assertThrows(NullPointerException.class, () -> used.put(a, null));
    }

    @Test void concurrentWritersNeverBreakOrStallMainThreadIteration() throws Exception {
        Map<Long, Boolean> used = new Long2ObjectNonBlockingMap<>();
        for (long key : square(0, 0, 8)) used.put(key, Boolean.FALSE);
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(2);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int w = 0; w < 2; w++) {
                tasks.add(writers.submit(() -> {
                    started.countDown();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    while (!stop.get()) {
                        long key = Level.chunkHash(random.nextInt(-40, 40), random.nextInt(-40, 40));
                        if (random.nextBoolean()) used.put(key, random.nextBoolean() ? Boolean.TRUE : Boolean.FALSE);
                        else used.remove(key);
                    }
                }));
            }
            assertTrue(started.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            int passes = 0;
            while (System.nanoTime() < deadline) {
                int visited = 0;
                for (long key : used.keySet()) {
                    // A pass is bounded: the 80x80 key space plus the reserved key.
                    assertTrue(++visited <= 80 * 80 + 1, "iteration does not terminate");
                    Boolean value = used.get(key);
                    if (value == Boolean.FALSE) used.remove(key);
                }
                for (Map.Entry<Long, Boolean> entry : used.entrySet()) assertNotNull(entry.getValue());
                passes++;
            }
            assertTrue(passes > 0);
            stop.set(true);
            for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
        } finally {
            stop.set(true);
            writers.shutdownNow();
        }
    }
}
