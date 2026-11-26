package cn.nukkit.entity;

import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EntityEffectOccupancyTest {
    private static final Effect FIRST = new Effect(1, "first", 0, 0, 0);
    private static final Effect SECOND = new Effect(2, "second", 0, 0, 0);

    private static final class CountedBacking extends ConcurrentHashMap<Integer, Effect> {
        int probes;

        @Override
        public boolean isEmpty() {
            probes++;
            return super.isEmpty();
        }
    }

    @Test
    void steadyReadsNeverConsultTheBackingCountAfterAnyLastRemoval() {
        var backing = new CountedBacking();
        var map = new Entity.TrackedEffectMap(backing);

        var keys = map.keySet();
        var values = map.values();
        var entries = map.entrySet();
        map.putIfAbsent(1, FIRST);
        var retained = entries.iterator().next();
        assertTrue(map.replace(1, FIRST, SECOND));
        assertSame(SECOND, map.replace(1, FIRST));
        map.replaceAll((key, value) -> SECOND);
        assertSame(SECOND, map.get(1));
        assertFalse(map.remove(1, FIRST));

        List<Runnable> lastRemovals = List.of(
                () -> map.remove(1),
                () -> assertTrue(map.remove(1, FIRST)),
                map::clear,
                () -> keys.remove(1),
                () -> values.remove(FIRST),
                () -> entries.remove(Map.entry(1, FIRST)),
                () -> removeFirst(keys.iterator()),
                () -> removeFirst(values.iterator()),
                () -> removeFirst(entries.iterator()),
                () -> keys.retainAll(Set.of()),
                () -> values.removeIf(value -> true),
                () -> entries.removeAll(Set.of(Map.entry(1, FIRST))),
                () -> map.compute(1, (key, value) -> null),
                () -> map.computeIfPresent(1, (key, value) -> null),
                () -> map.merge(1, SECOND, (oldValue, newValue) -> null));

        map.clear();
        for (Runnable lastRemoval : lastRemovals) {
            assertTrue(map.isEmpty());
            retained.setValue(FIRST);
            assertSame(FIRST, map.computeIfAbsent(1, key -> SECOND));
            assertSame(FIRST, map.get(1));
            int before = backing.probes;
            for (int i = 0; i < 2_000; i++) assertFalse(map.isEmpty());
            assertEquals(before, backing.probes, "occupied reads");
            lastRemoval.run();
            before = backing.probes;
            for (int i = 0; i < 2_000; i++) assertTrue(map.isEmpty());
            assertEquals(before, backing.probes, "formerly occupied reads");
        }
    }

    private static void removeFirst(Iterator<?> iterator) {
        iterator.next();
        iterator.remove();
    }

    @Test
    void reentrantAndFailingInsertionsLeaveOnlyTheirActualMappings() {
        var map = new Entity.TrackedEffectMap();
        Map<Integer, Effect> reentrant = new AbstractMap<>() {
            @Override
            public Set<Entry<Integer, Effect>> entrySet() {
                assertTrue(map.isEmpty());
                map.put(3, SECOND);
                assertFalse(map.isEmpty());
                map.clear();
                assertTrue(map.isEmpty());
                return Set.of(Map.entry(1, FIRST));
            }
        };
        map.putAll(reentrant);
        assertSame(FIRST, map.remove(1));
        assertTrue(map.isEmpty());

        assertThrows(NullPointerException.class, () -> map.put(1, null));
        assertThrows(NullPointerException.class, () -> map.putIfAbsent(null, FIRST));
        assertTrue(map.isEmpty());
        Map<Integer, Effect> partial = new LinkedHashMap<>();
        partial.put(1, FIRST);
        partial.put(2, null);
        assertThrows(NullPointerException.class, () -> map.putAll(partial));
        assertSame(FIRST, map.get(1));
        var retained = map.entrySet().iterator().next();
        map.clear();
        assertThrows(NullPointerException.class, () -> retained.setValue(null));
        assertTrue(map.isEmpty());
        assertSame(FIRST, retained.setValue(SECOND));
        assertSame(SECOND, map.get(1));
    }

    @Test
    void nativeBulkSizingCallbacksKeepTheirOrderOnNewAndPreviouslyUsedTables() {
        var map = new Entity.TrackedEffectMap();
        var nativeMap = new ConcurrentHashMap<Integer, Effect>();
        for (int pass = 0; pass < 2; pass++) {
            List<String> expected = new ArrayList<>();
            List<String> actual = new ArrayList<>();
            nativeMap.putAll(bulkSource(expected));
            map.putAll(bulkSource(actual));
            assertEquals(expected, actual);
            nativeMap.clear();
            map.clear();
        }
    }

    private static Map<Integer, Effect> bulkSource(List<String> trace) {
        return new AbstractMap<>() {
            @Override public int size() {
                trace.add("size");
                return 1;
            }

            @Override public Set<Entry<Integer, Effect>> entrySet() {
                trace.add("entries");
                return Set.of(Map.entry(1, FIRST));
            }
        };
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void retainedRawEntryPreservesItsSnapshotWhenReinsertionThrows() {
        class FallibleKey {
            boolean fail;
            @Override public int hashCode() {
                if (fail) throw new IllegalStateException();
                return 1;
            }
        }
        Map map = new Entity.TrackedEffectMap();
        var key = new FallibleKey();
        map.putAll(Collections.singletonMap(key, FIRST));
        Map.Entry entry = (Map.Entry) map.entrySet().iterator().next();
        key.fail = true;
        assertThrows(IllegalStateException.class, () -> entry.setValue(SECOND));
        assertSame(SECOND, entry.getValue());
        key.fail = false;
        assertSame(FIRST, map.get(key));
    }

    @Test
    void aSingleMissedTraversalDuringPublicationCannotHideTheMapping() {
        class PublishingMap extends ConcurrentHashMap<Integer, Effect> {
            ConcurrentMap<Integer, Effect> tracked;
            boolean publishing;
            int traversals;
            @Override public boolean isEmpty() { return publishing || super.isEmpty(); }
            @Override public Enumeration<Integer> keys() {
                // A moving mapping can escape one weak traversal while its count is still zero.
                if (publishing && traversals++ == 0) return Collections.emptyEnumeration();
                return super.keys();
            }
            @Override public Effect put(Integer key, Effect value) {
                Effect old = super.put(key, value);
                publishing = true;
                try {
                    assertFalse(tracked.isEmpty());
                } finally {
                    publishing = false;
                }
                return old;
            }
        }
        var backing = new PublishingMap();
        backing.tracked = new Entity.TrackedEffectMap(backing);
        backing.tracked.put(1, FIRST);
        assertEquals(2, backing.traversals);
    }

    @Test
    void concurrentViewAndMapWritersCannotHideAContinuouslyPresentKey() throws Exception {
        var map = new Entity.TrackedEffectMap();
        map.put(0, FIRST);
        stressWriters(map, false);
        assertEquals(Map.of(0, FIRST), map);
        assertSame(FIRST, map.remove(0));
        assertTrue(map.isEmpty());
    }

    @Test
    void movingTheLastMappingNeverCreatesAnEmptyObservation() throws Exception {
        var map = new Entity.TrackedEffectMap();
        map.put(0, FIRST);
        stressWriters(map, true);
        assertEquals(Map.of(0, FIRST), map);
    }

    private static void stressWriters(ConcurrentMap<Integer, Effect> map, boolean moveAnchor) throws Exception {
        int writers = 6;
        var start = new CyclicBarrier(writers + 2);
        ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
        try {
            var tasks = new ArrayList<Future<?>>();
            tasks.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                if (moveAnchor) {
                    for (int i = 0; i < 10_000; i++) {
                        map.put(31, FIRST);
                        map.remove(0);
                        map.put(0, FIRST);
                        map.remove(31);
                    }
                }
                return null;
            }));
            for (int key = 1; key <= writers; key++) {
                int id = key;
                tasks.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < 10_000; i++) {
                        map.computeIfAbsent(id, ignored -> SECOND);
                        assertFalse(map.isEmpty());
                        if ((i & 1) == 0) map.keySet().remove(id);
                        else map.entrySet().remove(Map.entry(id, SECOND));
                    }
                    return null;
                }));
            }
            tasks.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < 100_000; i++) {
                    assertFalse(map.isEmpty());
                    if (!moveAnchor) assertSame(FIRST, map.get(0));
                }
                return null;
            }));
            for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
