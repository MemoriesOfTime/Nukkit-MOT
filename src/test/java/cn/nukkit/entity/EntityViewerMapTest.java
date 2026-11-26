package cn.nukkit.entity;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EntityViewerMapTest {
    private static boolean certified(EntityViewerMap<?, ?> map) {
        return EntityViewerMap.isKnownEmpty(map);
    }

    private static <V> List<V> viewers(EntityViewerMap<?, V> map) {
        List<V> actual = new ArrayList<>();
        if (EntityViewerMap.isKnownEmpty(map)) return actual;
        Iterator<V> iterator = map.values().iterator();
        if (!iterator.hasNext()) EntityViewerMap.noteEmptyIteration(map);
        iterator.forEachRemaining(actual::add);
        return actual;
    }

    @Test
    void ordinaryMutationsAndRemovalOnlyViewsRearmTheEmptyShortcut() {
        var map = new EntityViewerMap<Integer, String>();
        assertTrue(certified(map));
        var keys = map.keySet();
        var values = map.values();
        List<Runnable> removeLast = List.of(
                () -> map.remove(1), () -> map.remove(1, "v"), map::clear,
                () -> keys.remove(1), () -> removeFirst(keys.iterator()),
                () -> removeFirst(values.iterator()), () -> values.removeIf(v -> true),
                () -> map.computeIfPresent(1, (key, value) -> null),
                () -> map.merge(1, "v", (a, b) -> null));
        for (Runnable remove : removeLast) {
            map.put(1, "v");
            assertEquals(List.of("v"), viewers(map));
            remove.run();
            assertEquals(List.of(), viewers(map));
            for (int i = 0; i < 512; i++) assertTrue(certified(map));
        }
        assertThrows(UnsupportedOperationException.class, () -> keys.add(1));
        assertThrows(UnsupportedOperationException.class, () -> values.add("v"));
        assertThrows(NullPointerException.class, () -> map.keySet(null));
        assertTrue(certified(map));
    }

    private static void removeFirst(Iterator<?> iterator) {
        iterator.next();
        iterator.remove();
    }

    @Test
    void directLastRemovalAndClearCertifyBeforeTheNextMovement() {
        var map = new EntityViewerMap<Integer, String>();
        map.put(1, "v");
        map.remove(1);
        assertTrue(certified(map));
        map.put(1, "v");
        assertTrue(map.remove(1, "v"));
        assertTrue(certified(map));
        for (int key = 0; key < 1024; key++) map.put(key, "v");
        map.clear();
        assertTrue(certified(map));
        for (int repeat = 0; repeat < 2048; repeat++) assertTrue(certified(map));
    }

    @Test
    void retainedInsertionCapableViewsKeepNativeAddsAndWriteThroughEntries() {
        var map = new EntityViewerMap<Integer, String>();
        Set<Map.Entry<Integer, String>> entries = map.entrySet();
        assertSame(entries, map.entrySet());
        assertFalse(certified(map));
        assertTrue(entries.add(Map.entry(1, "a")));
        assertFalse(entries.add(Map.entry(1, "b")));
        assertEquals(List.of("b"), viewers(map));
        Map.Entry<Integer, String> entry = entries.iterator().next();
        map.clear();
        assertEquals(List.of(), viewers(map));
        assertEquals("b", entry.setValue("c"));
        assertEquals(List.of("c"), viewers(map));
        entries.clear();
        assertTrue(entries.addAll(Set.of(Map.entry(2, "d"))));
        assertEquals(List.of("d"), viewers(map));

        var keyed = new EntityViewerMap<Integer, String>();
        var writable = keyed.keySet("x");
        assertSame(keyed, writable.getMap());
        assertEquals("x", writable.getMappedValue());
        assertFalse(certified(keyed));
        assertTrue(writable.add(8));
        assertFalse(writable.add(8));
        assertEquals(List.of("x"), viewers(keyed));
        keyed.clear();
        assertTrue(writable.addAll(List.of(3, 4)));
        assertEquals(2, viewers(keyed).size());
    }

    @Test
    void failedAndPartialOperationsUseNativeNullAndCallbackSemantics() {
        var map = new EntityViewerMap<Integer, String>();
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.put(1, null));
        assertThrows(NullPointerException.class, () -> map.putIfAbsent(null, "a"));
        assertThrows(NullPointerException.class, () -> map.computeIfAbsent(null, key -> "a"));
        assertThrows(NullPointerException.class, () -> map.compute(1, null));
        assertThrows(NullPointerException.class, () -> map.merge(1, null, (a, b) -> b));
        assertThrows(IllegalStateException.class, () -> map.compute(1, (k, v) -> {
            throw new IllegalStateException();
        }));
        assertEquals(List.of(), viewers(map));
        assertTrue(certified(map));
        Map<Integer, String> source = new LinkedHashMap<>();
        source.put(1, "a");
        source.put(2, null);
        assertThrows(NullPointerException.class, () -> map.putAll(source));
        assertEquals(List.of("a"), viewers(map));
        assertEquals("a", map.putIfAbsent(1, "b"));
        assertEquals("a", map.replace(1, "b"));
        assertTrue(map.replace(1, "b", "c"));
        map.replaceAll((key, value) -> value + "d");
        assertEquals(List.of("cd"), viewers(map));
    }

    @Test
    void aMovementReadInsideBulkSourceCannotPublishStaleEmptiness() {
        var map = new EntityViewerMap<Integer, String>();
        Map<Integer, String> reentrant = new AbstractMap<>() {
            @Override public Set<Entry<Integer, String>> entrySet() {
                assertTrue(viewers(map).isEmpty());
                map.put(9, "transient");
                map.remove(9);
                assertTrue(viewers(map).isEmpty());
                return Set.of(Map.entry(1, "viewer"));
            }
        };
        map.putAll(reentrant);
        assertEquals(List.of("viewer"), viewers(map));
    }

    @Test
    void competingComputesRemainAtomicAndDoNotLockOutMovementCallbacks() throws Exception {
        var map = new EntityViewerMap<Integer, Integer>();
        var start = new CyclicBarrier(5);
        var entered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> writes = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                writes.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return map.computeIfAbsent(1, key -> {
                        calls.incrementAndGet();
                        entered.countDown();
                        try {
                            assertTrue(resume.await(10, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                        return 7;
                    });
                }));
            }
            start.await(10, TimeUnit.SECONDS);
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertTrue(pool.submit(() -> viewers(map).isEmpty()).get(10, TimeUnit.SECONDS));
            resume.countDown();
            for (Future<Integer> write : writes) assertEquals(7, write.get(10, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals(List.of(7), viewers(map));
        } finally {
            resume.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentChurnCannotCertifyAContinuouslyPresentViewerAsAbsent() throws Exception {
        var map = new EntityViewerMap<Integer, Integer>();
        map.put(0, 0);
        int workers = 6;
        var start = new CyclicBarrier(workers + 1);
        var pool = Executors.newFixedThreadPool(workers + 1);
        try {
            List<Future<?>> work = new ArrayList<>();
            for (int key = 1; key <= workers; key++) {
                int id = key;
                work.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < 10_000; i++) {
                        map.merge(id, id, (a, b) -> b);
                        map.keySet().remove(id);
                    }
                    return null;
                }));
            }
            work.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < 150_000; i++) {
                    assertFalse(EntityViewerMap.isKnownEmpty(map));
                    Iterator<Integer> recipients = map.values().iterator();
                    if (!recipients.hasNext()) EntityViewerMap.noteEmptyIteration(map);
                    assertTrue(recipients.hasNext());
                }
                return null;
            }));
            for (Future<?> task : work) task.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(0), viewers(map));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deserializedNativeViewCanStillInsertIntoItsRestoredMap() throws Exception {
        var original = new EntityViewerMap<Integer, String>();
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(new Object[]{original, original.keySet("shared")});
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var pair = (Object[]) input.readObject();
            var map = (EntityViewerMap<Integer, String>) pair[0];
            var keys = (ConcurrentHashMap.KeySetView<Integer, String>) pair[1];
            assertSame(map, keys.getMap());
            assertFalse(certified(map));
            keys.add(1);
            assertEquals(List.of("shared"), viewers(map));
            map.clear();
            map.compute(2, (k, v) -> "direct");
            assertEquals(List.of("direct"), viewers(map));
        }
    }
}
