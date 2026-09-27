package cn.nukkit.utils.collection.nb;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class Long2ObjectNonBlockingMapValuesSnapshotTest {
    private static void matches(Long2ObjectNonBlockingMap<Object> map) {
        var expected = new IdentityHashMap<Object, Integer>();
        map.values().forEach(value -> expected.merge(value, 1, Integer::sum));
        var actual = new IdentityHashMap<Object, Integer>();
        map.valuesSnapshot().forEach(value -> actual.merge(value, 1, Integer::sum));
        assertEquals(expected, actual);
        assertSame(map.valuesSnapshot(), map.valuesSnapshot(), "stable membership reuses the array");
    }

    @Test
    void zeroAndOrdinaryKeysReuseNoOpsButInvalidateIdentityReplacements() {
        for (long key : new long[]{0, 42}) {
            var map = new Long2ObjectNonBlockingMap<Object>();
            Object first = new String("equal");
            Object replacement = new String("equal");
            map.put(key, first);
            var original = map.valuesSnapshot();
            assertThrows(UnsupportedOperationException.class, () -> original.set(0, replacement));
            map.put(key, first);
            map.putIfAbsent(key, replacement);
            map.replace(key, first, first);
            map.remove(key, new Object());
            map.remove(99_999L);
            map.remove(99_999L, new Object());
            map.replace(key, new Object(), replacement);
            assertSame(original, map.valuesSnapshot(), "no successful identity change");
            assertSame(first, map.replace(key, replacement));
            assertNotSame(original, map.valuesSnapshot());
            assertSame(replacement, map.valuesSnapshot().get(0), "equals is insufficient for invalidation");
            assertSame(first, original.get(0), "already acquired snapshots stay immutable");
            map.remove(key);
            map.put(key, first);
            assertSame(first, map.valuesSnapshot().get(0));
        }
    }

    @Test
    void allPublicMutationRoutesInvalidateIncludingViewsAndDefaultComputations() {
        List<Consumer<Long2ObjectNonBlockingMap<Object>>> changes = List.of(
                map -> map.put(7L, new Object()),
                map -> map.putAll(Map.of(7L, new Object())),
                map -> map.putIfAbsent(7L, new Object()),
                map -> map.remove(1L),
                map -> map.remove(Long.valueOf(1)),
                map -> map.remove(1L, map.get(1L)),
                map -> map.replace(1L, new Object()),
                map -> map.replace(1L, map.get(1L), new Object()),
                map -> map.compute(1L, (key, value) -> new Object()),
                map -> map.computeIfAbsent(7L, key -> new Object()),
                map -> map.computeIfPresent(1L, (key, value) -> new Object()),
                map -> map.merge(1L, new Object(), (old, value) -> value),
                map -> map.replaceAll((key, value) -> new Object()),
                map -> { var it = map.values().iterator(); it.next(); it.remove(); },
                map -> map.values().remove(map.get(1L)),
                map -> map.keySet().remove(1L),
                map -> { var it = map.keySet().iterator(); it.nextLong(); it.remove(); },
                map -> { var it = map.entrySet().iterator(); it.next(); it.remove(); },
                map -> map.entrySet().iterator().next().setValue(new Object()),
                map -> map.fastEntrySet().iterator().next().setValue(new Object()),
                map -> { var it = map.fastEntrySet().iterator(); it.next(); it.remove(); },
                map -> map.entrySet().remove(Map.entry(1L, map.get(1L))),
                map -> map.values().clear(),
                map -> map.keySet().clear(),
                map -> map.entrySet().clear(),
                Long2ObjectNonBlockingMap::clear,
                map -> map.clear(true));
        for (int i = 0; i < changes.size(); i++) {
            var map = new Long2ObjectNonBlockingMap<Object>();
            map.put(0L, new Object());
            map.put(1L, new Object());
            var original = map.valuesSnapshot();
            changes.get(i).accept(map);
            assertNotSame(original, map.valuesSnapshot(), "route " + i);
            matches(map);
        }
    }

    @Test
    void duplicateValuesAndChurnKeepMembershipAndDoNotChangeOldSnapshots() {
        var map = new Long2ObjectNonBlockingMap<Object>(1);
        Object repeated = new Object();
        for (long i = 0; i < 2048; i++) map.put(i, repeated);
        var original = map.valuesSnapshot();
        assertEquals(2048, original.size());
        for (long i = 0; i < 2048; i++) map.remove(i);
        map.put(9000L, repeated);
        assertEquals(List.of(repeated), map.valuesSnapshot());
        assertEquals(2048, original.size());
        matches(map);
    }

    @Test
    void completedWriteCannotBeHiddenByRacingSnapshotPublication() throws Exception {
        var map = new BlockingMap();
        Object before = new Object();
        Object after = new Object();
        map.put(1L, before);
        map.block.set(true);
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Object>> building = executor.submit(map::valuesSnapshot);
            assertTrue(map.captured.await(10, TimeUnit.SECONDS));
            map.put(1L, after);
            map.proceed.countDown();
            // A build overlapping the write may return its old weak snapshot, but cannot cache it.
            assertSame(before, building.get(10, TimeUnit.SECONDS).get(0));
            assertSame(after, map.valuesSnapshot().get(0));
            assertSame(map.valuesSnapshot(), map.valuesSnapshot());
        } finally {
            map.proceed.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReadersNeverKeepACompletedGenerationStale() throws Exception {
        var map = new Long2ObjectNonBlockingMap<Object>();
        var executor = Executors.newFixedThreadPool(2);
        AtomicBoolean running = new AtomicBoolean(true);
        try {
            Future<?> racing = executor.submit(() -> {
                while (running.get()) map.valuesSnapshot();
            });
            for (int i = 0; i < 2000; i++) {
                Object latest = new Object();
                map.put(1L, latest);
                Future<?> afterWrite = executor.submit(() -> assertSame(latest, map.valuesSnapshot().get(0)));
                afterWrite.get(10, TimeUnit.SECONDS);
            }
            running.set(false);
            racing.get(10, TimeUnit.SECONDS);
            map.clear();
            assertTrue(map.valuesSnapshot().isEmpty());
        } finally {
            running.set(false);
            executor.shutdownNow();
        }
    }

    @Test
    void cloneAndSerializationDoNotShareCacheOrMutationState() throws Exception {
        var map = new Long2ObjectNonBlockingMap<String>();
        map.put(0L, "zero");
        map.put(1L, "one");
        var original = map.valuesSnapshot();
        var clone = map.clone();
        clone.remove(1L);
        assertEquals(2, map.valuesSnapshot().size());
        assertSame(original, map.valuesSnapshot());
        assertEquals(List.of("zero"), clone.valuesSnapshot());
        var bytes = new ByteArrayOutputStream();
        try (var stream = new ObjectOutputStream(bytes)) { stream.writeObject(map); }
        Long2ObjectNonBlockingMap<?> restored;
        try (var stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Long2ObjectNonBlockingMap<?>) stream.readObject();
        }
        assertEquals(new HashSet<>(original), new HashSet<>(restored.valuesSnapshot()));
        restored.clear(true);
        assertTrue(restored.valuesSnapshot().isEmpty());
        assertEquals(2, map.valuesSnapshot().size());
    }

    private static final class BlockingMap extends Long2ObjectNonBlockingMap<Object> {
        final AtomicBoolean block = new AtomicBoolean();
        final CountDownLatch captured = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        @Override
        public Collection<Object> values() {
            Collection<Object> delegate = super.values();
            return new AbstractCollection<>() {
                @Override public Iterator<Object> iterator() { return delegate.iterator(); }
                @Override public int size() { return delegate.size(); }
                @Override public Object[] toArray() {
                    Object[] values = delegate.toArray();
                    if (block.compareAndSet(true, false)) {
                        captured.countDown();
                        try {
                            if (!proceed.await(10, TimeUnit.SECONDS)) throw new AssertionError("writer did not finish");
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(failure);
                        }
                    }
                    return values;
                }
            };
        }
    }
}
