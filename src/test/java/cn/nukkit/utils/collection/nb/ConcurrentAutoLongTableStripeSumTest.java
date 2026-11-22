package cn.nukkit.utils.collection.nb;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentAutoLongTableStripeSumTest {

    @Test
    void stripedUpdatesSetResetAndSerializationPreserveExactSum() throws Exception {
        ConcurrentAutoLongTable counter = new ConcurrentAutoLongTable();
        assertEquals(0L, counter.get());
        assertEquals(16, counter.internal_size());

        counter.add(25L);
        counter.decrement();
        counter.increment();
        counter.add(-5L);
        assertEquals(20L, counter.get());
        assertEquals(20, counter.intValue());
        assertEquals(20L, counter.longValue());
        assertEquals(20L, counter.estimate_get());
        assertEquals("20", counter.toString());

        counter.set(-20L);
        assertEquals(4, counter.internal_size());
        counter.decrement();
        assertEquals(-21L, counter.get());

        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<?>>(8);
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int i = 0; i < 10_000; i++) {
                        counter.increment();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long expected = -21L + 80_000L;
        assertEquals(expected, counter.get());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(counter);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ConcurrentAutoLongTable restored = (ConcurrentAutoLongTable) in.readObject();
            assertEquals(expected, restored.get());
            restored.add(Long.MAX_VALUE - expected);
            assertEquals(Long.MAX_VALUE, restored.get());
            restored.increment();
            assertEquals(Long.MIN_VALUE, restored.get());
        }

        assertTrue(ObjectStreamClass.lookup(ConcurrentAutoLongTable.class).getSerialVersionUID() != 0L);
    }
}
