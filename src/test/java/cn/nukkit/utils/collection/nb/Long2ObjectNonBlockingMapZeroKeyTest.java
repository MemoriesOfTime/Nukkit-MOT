package cn.nukkit.utils.collection.nb;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class Long2ObjectNonBlockingMapZeroKeyTest {
    @Test
    void zeroKeyRetriesAfterAConcurrentEqualReplacementInsteadOfReportingAnUnperformedWrite() throws Exception {
        checkConcurrentReplacement(true);
    }

    @Test
    void zeroKeyDoesNotOverwriteANonMatchingValueAfterLosingCompareAndSet() throws Exception {
        checkConcurrentReplacement(false);
    }

    @Test
    void zeroKeyPutRemoveAndPutIfAbsentKeepTheirReturnValueContracts() {
        var map = new Long2ObjectNonBlockingMap<Object>();
        Object first = new Object();
        Object second = new Object();
        assertNull(map.putIfAbsent(0L, first));
        assertSame(first, map.putIfAbsent(0L, second));
        assertSame(first, map.get(0L));
        assertSame(first, map.put(0L, second));
        assertSame(second, map.remove(0L));
        assertNull(map.remove(0L));
        assertNull(map.putIfAbsent(0L, first));
        assertSame(first, map.get(0L));
        assertFalse(map.remove(0L, second));
        assertTrue(map.remove(0L, first));
        assertNull(map.get(0L));
    }

    private static void checkConcurrentReplacement(boolean matching) throws Exception {
        var map = new Long2ObjectNonBlockingMap<Object>();
        Object original = new Object();
        Object replacement = new Object();
        CountDownLatch compared = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        Object expected = new Object() {
            @Override
            public boolean equals(Object other) {
                if (other != original) return other == this;
                compared.countDown();
                try {
                    if (!proceed.await(10, TimeUnit.SECONDS)) throw new AssertionError("writer did not proceed");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                return true;
            }
        };
        Object concurrent = matching ? expected : new Object();
        map.put(0L, original);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> map.replace(0L, expected, replacement));
            assertTrue(compared.await(10, TimeUnit.SECONDS));
            assertSame(original, map.put(0L, concurrent));
            proceed.countDown();
            assertEquals(matching, result.get(10, TimeUnit.SECONDS));
            assertSame(matching ? replacement : concurrent, map.get(0L));
        } finally {
            proceed.countDown();
            executor.shutdownNow();
        }
    }
}
