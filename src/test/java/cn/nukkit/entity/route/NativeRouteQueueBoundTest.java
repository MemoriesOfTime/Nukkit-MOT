package cn.nukkit.entity.route;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeRouteQueueBoundTest {
    private static ThreadPoolExecutor executor() throws Exception {
        Field field = RouteFinderThreadPool.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(null);
    }

    @Test void nativeQueueHasFiniteBudgetAndPreservesSingleSearchWorker() throws Exception {
        ThreadPoolExecutor executor = executor();
        assertTrue(executor.getQueue().remainingCapacity() <= 1024,
                "native route backlog must be bounded independently of mob count");
        assertEquals(1, executor.getMaximumPoolSize(), "bounded queue must not increase native search concurrency");
    }

    @Test void acceptedDistinctRoutesKeepFifoWhileDuplicateAdmissionIsCoalesced() throws Exception {
        ThreadPoolExecutor executor = executor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> order = new ArrayList<>();
        executor.execute(() -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            RouteFinder first = mock(RouteFinder.class, CALLS_REAL_METHODS);
            RouteFinder second = mock(RouteFinder.class, CALLS_REAL_METHODS);
            RouteFinder third = mock(RouteFinder.class, CALLS_REAL_METHODS);
            doAnswer(call -> { order.add("first"); return null; }).when(first).research();
            doAnswer(call -> { order.add("second"); return null; }).when(second).research();
            doAnswer(call -> { order.add("third"); return null; }).when(third).research();
            RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(first));
            RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(second));
            RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(first));
            RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(third));
            assertEquals(3, executor.getQueue().size());
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(List.of("first", "second", "third"), order);
            assertFalse(first.hasPendingSearch());
            assertFalse(second.hasPendingSearch());
            assertFalse(third.hasPendingSearch());
        } finally {
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        }
    }

    @Test void saturatedPublicAdmissionDropsTaskWithoutMainThreadSearchOrLeakedClaim() throws Exception {
        ThreadPoolExecutor executor = executor();
        if (executor.getQueue().remainingCapacity() > 1024) return;
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            while (executor.getQueue().offer(() -> {})) { }
            RouteFinder route = mock(RouteFinder.class, CALLS_REAL_METHODS);
            assertDoesNotThrow(() -> RouteFinderThreadPool.executeRouteFinderThread(new RouteFinderSearchTask(route)));
            Method pending = RouteFinder.class.getMethod("hasPendingSearch");
            assertEquals(false, pending.invoke(route));
            verify(route, never()).research();
        } finally {
            executor.getQueue().clear();
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        }
    }
}
