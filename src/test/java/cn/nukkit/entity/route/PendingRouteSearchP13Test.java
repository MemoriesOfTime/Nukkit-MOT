package cn.nukkit.entity.route;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityWalking;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingRouteSearchP13Test {
    @Test
    void blockedWorkerCoalescesOneHundredRequestsAndKeepsTheLatestLiveTarget() throws Exception {
        try (Fixture f = new Fixture()) {
            f.blockWorker();
            ProbeRoute route = f.route(false);
            Entity previous = mock(Entity.class);
            Entity latest = mock(Entity.class);
            route.liveTarget.set(previous);
            for (int i = 0; i < 100; i++) submit(f.executor, new RouteFinderSearchTask(route));
            int queued = f.executor.getQueue().size();
            route.liveTarget.set(latest);
            f.releaseWorker();
            f.drain();
            System.out.println("p13 duplicate_queue_size=" + queued + " searches=" + route.searches.get());
            assertAll(() -> assertEquals(1, queued),
                    () -> assertEquals(1, route.searches.get()),
                    () -> assertSame(latest, route.observedTarget.get()));
        }
    }

    @Test
    void differentRoutesKeepIndependentQueueSlots() throws Exception {
        try (Fixture f = new Fixture()) {
            f.blockWorker();
            ProbeRoute first = f.route(false);
            ProbeRoute second = f.route(false);
            submit(f.executor, new RouteFinderSearchTask(first));
            submit(f.executor, new RouteFinderSearchTask(second));
            assertEquals(2, f.executor.getQueue().size());
            f.releaseWorker();
            f.drain();
            assertEquals(1, first.searches.get());
            assertEquals(1, second.searches.get());
        }
    }

    @Test
    void completedTaskCanBeScheduledAgain() throws Exception {
        try (Fixture f = new Fixture()) {
            ProbeRoute route = f.route(false);
            submit(f.executor, new RouteFinderSearchTask(route));
            f.drain();
            assertFalse(pending(route));
            submit(f.executor, new RouteFinderSearchTask(route));
            f.drain();
            assertEquals(2, route.searches.get());
            assertFalse(pending(route));
        }
    }

    @Test
    void taskFailureReleasesItsReservation() throws Exception {
        try (Fixture f = new Fixture()) {
            ProbeRoute route = f.route(false);
            route.failNext.set(true);
            submit(f.executor, new RouteFinderSearchTask(route));
            assertInstanceOf(IllegalStateException.class, f.failures.poll(5, TimeUnit.SECONDS));
            f.drain();
            assertFalse(pending(route));
            submit(f.executor, new RouteFinderSearchTask(route));
            f.drain();
            assertEquals(2, route.searches.get());
        }
    }

    @Test
    void rejectedAdmissionReleasesReservationForAnotherExecutor() throws Exception {
        try (Fixture stopped = new Fixture(); Fixture running = new Fixture()) {
            ProbeRoute route = running.route(false);
            stopped.executor.shutdown();
            assertThrows(RejectedExecutionException.class,
                    () -> submit(stopped.executor, new RouteFinderSearchTask(route)));
            assertFalse(pending(route));
            submit(running.executor, new RouteFinderSearchTask(route));
            running.drain();
            assertEquals(1, route.searches.get());
        }
    }

    @Test
    void failedWorkerCreationAfterQueueOfferDoesNotLeaveAnUnownedTask() throws Exception {
        try (Fixture f = new Fixture()) {
            ProbeRoute route = f.route(false);
            AtomicInteger attempts = new AtomicInteger();
            var normalFactory = f.executor.getThreadFactory();
            AssertionError failure = new AssertionError("planned worker creation failure");
            f.executor.setThreadFactory(runnable -> {
                if (attempts.incrementAndGet() == 1) return null;
                throw failure;
            });
            assertSame(failure, assertThrows(AssertionError.class,
                    () -> submit(f.executor, new RouteFinderSearchTask(route))));
            assertEquals(2, attempts.get(), "failure must occur after queue.offer");
            assertFalse(pending(route));
            assertEquals(0, f.executor.getQueue().size(), "failed admission must remove the queued task");
            f.executor.setThreadFactory(normalFactory);
            submit(f.executor, new RouteFinderSearchTask(route));
            f.drain();
            assertEquals(1, route.searches.get());
        }
    }

    @Test
    void failedAdmissionCannotReleaseATaskAlreadyStartedByAReplacementWorker() throws Exception {
        InspectingQueue queue = new InspectingQueue();
        CountDownLatch crashFirstWorker = new CountDownLatch(1);
        CountDownLatch firstWorkerEntered = new CountDownLatch(1);
        CountDownLatch failingFactoryEntered = new CountDownLatch(1);
        CountDownLatch failFactory = new CountDownLatch(1);
        AtomicReference<Thread> firstWorker = new AtomicReference<>();
        AtomicReference<Throwable> submissionFailure = new AtomicReference<>();
        AtomicReference<Throwable> inspectionFailure = new AtomicReference<>();
        Thread submitting = null;
        Thread inspecting = null;
        try (Fixture f = new Fixture(queue)) {
            assertTrue(f.executor.getMaximumPoolSize() >= 2);
            AtomicInteger creations = new AtomicInteger();
            var normalFactory = f.executor.getThreadFactory();
            AssertionError admissionError = new AssertionError("planned concurrent worker creation failure");
            f.executor.setThreadFactory(runnable -> {
                int creation = creations.incrementAndGet();
                if (creation == 2) {
                    failingFactoryEntered.countDown();
                    await(failFactory);
                    throw admissionError;
                }
                Thread thread = normalFactory.newThread(runnable);
                if (creation == 1) firstWorker.set(thread);
                return thread;
            });
            f.executor.execute(() -> {
                firstWorkerEntered.countDown();
                await(crashFirstWorker);
                throw new IllegalStateException("planned abrupt worker exit");
            });
            assertTrue(firstWorkerEntered.await(5, TimeUnit.SECONDS));
            queue.holdInspection = true;
            inspecting = new Thread(() -> {
                try { f.executor.getTaskCount(); }
                catch (Throwable failure) { inspectionFailure.set(failure); }
            }, "p13-pool-inspection");
            inspecting.start();
            assertTrue(queue.inspectionEntered.await(5, TimeUnit.SECONDS));
            queue.afterOffer = () -> {
                crashFirstWorker.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean exited = false;
                while (!exited && System.nanoTime() < deadline) {
                    for (StackTraceElement frame : firstWorker.get().getStackTrace()) {
                        if (frame.getClassName().equals(ThreadPoolExecutor.class.getName())
                                && frame.getMethodName().equals("processWorkerExit")
                                && firstWorker.get().getState() == Thread.State.WAITING) exited = true;
                    }
                    if (!exited) Thread.yield();
                }
                assertTrue(exited, "first worker must decrement its count before execute rechecks it");
            };
            ProbeRoute route = f.route(true);
            submitting = new Thread(() -> {
                try { submit(f.executor, new RouteFinderSearchTask(route)); }
                catch (Throwable failure) { submissionFailure.set(failure); }
            }, "p13-failing-admission");
            submitting.start();
            assertTrue(failingFactoryEntered.await(5, TimeUnit.SECONDS));
            queue.releaseInspection.countDown();
            assertTrue(route.entered.await(5, TimeUnit.SECONDS));
            failFactory.countDown();
            submitting.join(5000);
            assertFalse(submitting.isAlive());
            assertSame(admissionError, submissionFailure.get());
            assertTrue(pending(route), "replacement worker already owns the active search");
            submit(f.executor, new RouteFinderSearchTask(route));
            assertEquals(0, f.executor.getQueue().size(), "active search must still reject duplicates");
            route.release.countDown();
            f.drain();
            assertEquals(1, route.searches.get());
            assertFalse(pending(route));
        } finally {
            queue.releaseInspection.countDown();
            crashFirstWorker.countDown();
            failFactory.countDown();
            if (submitting != null) {
                submitting.join(5000);
                assertFalse(submitting.isAlive());
            }
            if (inspecting != null) {
                inspecting.join(5000);
                assertFalse(inspecting.isAlive());
            }
        }
        assertNull(inspectionFailure.get());
    }

    @Test
    void activeShutdownKeepsOwnershipUntilTheWorkerActuallyExits() throws Exception {
        try (Fixture f = new Fixture(); Fixture later = new Fixture()) {
            ProbeRoute route = f.route(true);
            route.finishAfterInterrupt = true;
            submit(f.executor, new RouteFinderSearchTask(route));
            assertTrue(route.entered.await(5, TimeUnit.SECONDS));
            List<Runnable> cancelled = f.executor.shutdownNow();
            assertTrue(cancelled.isEmpty());
            discard(cancelled);
            assertTrue(route.interrupted.await(5, TimeUnit.SECONDS));
            assertTrue(pending(route), "an active worker still owns its route during shutdown");
            route.release.countDown();
            assertTrue(f.executor.awaitTermination(5, TimeUnit.SECONDS));
            assertFalse(pending(route));
            submit(later.executor, new RouteFinderSearchTask(route));
            later.drain();
            assertEquals(2, route.searches.get());
        }
    }

    @Test
    void staleCompletedTaskCannotReleaseANewerReservation() throws Exception {
        try (Fixture f = new Fixture()) {
            ProbeRoute route = f.route(false);
            RouteFinderSearchTask oldTask = new RouteFinderSearchTask(route);
            submit(f.executor, oldTask);
            f.drain();
            f.blockWorker();
            submit(f.executor, new RouteFinderSearchTask(route));
            discard(List.of(oldTask));
            assertTrue(pending(route), "cleanup of the old owner must not clear the new owner");
            assertEquals(1, f.executor.getQueue().size());
            f.releaseWorker();
            f.drain();
            assertEquals(2, route.searches.get());
            assertFalse(pending(route));
        }
    }

    @Test
    void queuedCancellationReleasesOnlyQueuedOwners() throws Exception {
        try (Fixture f = new Fixture(); Fixture later = new Fixture()) {
            f.blockWorker();
            ProbeRoute route = f.route(false);
            submit(f.executor, new RouteFinderSearchTask(route));
            List<Runnable> cancelled = f.executor.shutdownNow();
            assertEquals(1, cancelled.size());
            discard(cancelled);
            cancelled.get(0).run(); // A dequeued-but-not-started cancellation must remain harmless.
            assertFalse(pending(route));
            assertEquals(0, route.searches.get());
            assertTrue(f.executor.awaitTermination(5, TimeUnit.SECONDS));
            submit(later.executor, new RouteFinderSearchTask(route));
            later.drain();
            assertEquals(1, route.searches.get());
        }
    }

    @Test
    void unreservedDirectTaskCannotReleaseAnotherQueuedOwner() throws Exception {
        try (Fixture f = new Fixture()) {
            f.blockWorker();
            ProbeRoute route = f.route(false);
            submit(f.executor, new RouteFinderSearchTask(route));
            new RouteFinderSearchTask(route).run();
            assertTrue(pending(route), "only the queued task owns this reservation");
            assertEquals(1, f.executor.getQueue().size());
            f.releaseWorker();
            f.drain();
            assertEquals(2, route.searches.get());
            assertFalse(pending(route));
        }
    }

    @Test
    void nullRouteRemainsANoop() throws Exception {
        try (Fixture f = new Fixture()) {
            submit(f.executor, new RouteFinderSearchTask(null));
            f.drain();
            assertNull(f.failures.poll());
        }
    }

    private static void submit(ThreadPoolExecutor executor, RouteFinderSearchTask task) throws Exception {
        try {
            Method method = RouteFinderThreadPool.class.getDeclaredMethod("submit", ThreadPoolExecutor.class, RouteFinderSearchTask.class);
            method.setAccessible(true);
            method.invoke(null, executor, task);
        } catch (NoSuchMethodException oldVersion) {
            executor.execute(task); // Exact native admission before reservation support.
        } catch (InvocationTargetException error) {
            rethrow(error.getCause());
        }
    }

    private static boolean pending(RouteFinder route) throws Exception {
        try {
            return (boolean) RouteFinder.class.getMethod("hasPendingSearch").invoke(route);
        } catch (NoSuchMethodException oldVersion) {
            return false;
        }
    }

    private static void discard(List<Runnable> tasks) throws Exception {
        try {
            Method method = RouteFinderThreadPool.class.getDeclaredMethod("discardQueued", List.class);
            method.setAccessible(true);
            method.invoke(null, tasks);
        } catch (NoSuchMethodException oldVersion) {
            // The old shutdown discarded the queue without any route bookkeeping.
        } catch (InvocationTargetException error) {
            rethrow(error.getCause());
        }
    }

    private static void rethrow(Throwable error) throws Exception {
        if (error instanceof RuntimeException runtime) throw runtime;
        if (error instanceof Error fatal) throw fatal;
        throw new AssertionError(error);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class InspectingQueue extends LinkedBlockingQueue<Runnable> {
        final CountDownLatch inspectionEntered = new CountDownLatch(1);
        final CountDownLatch releaseInspection = new CountDownLatch(1);
        volatile boolean holdInspection;
        volatile Runnable afterOffer;

        @Override public int size() {
            if (holdInspection) {
                inspectionEntered.countDown();
                await(releaseInspection);
            }
            return super.size();
        }

        @Override public boolean offer(Runnable task) {
            boolean offered = super.offer(task);
            Runnable hook = afterOffer;
            afterOffer = null;
            if (hook != null) hook.run();
            return offered;
        }
    }

    private static final class ProbeRoute extends RouteFinder {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final AtomicInteger searches = new AtomicInteger();
        final AtomicBoolean failNext = new AtomicBoolean();
        final AtomicReference<Entity> liveTarget;
        final AtomicReference<Entity> observedTarget = new AtomicReference<>();
        final boolean blocked;
        volatile boolean finishAfterInterrupt;

        ProbeRoute(EntityWalking entity, AtomicReference<Entity> liveTarget, boolean blocked) {
            super(entity);
            this.liveTarget = liveTarget;
            this.blocked = blocked;
        }

        @Override public void search() {
            searching = true;
            searches.incrementAndGet();
            entered.countDown();
            try {
                if (failNext.getAndSet(false)) throw new IllegalStateException("planned search failure");
                if (blocked) {
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException cancelled) {
                        if (!finishAfterInterrupt) throw cancelled;
                        interrupted.countDown();
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                        Thread.currentThread().interrupt();
                    }
                }
                observedTarget.set(entity.getTarget());
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            } finally {
                searching = false;
                finished = true;
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        final ThreadPoolExecutor executor;
        final List<ProbeRoute> routes = new ArrayList<>();
        ProbeRoute blocker;

        Fixture() { this(new LinkedBlockingQueue<>()); }

        Fixture(BlockingQueue<Runnable> queue) {
            executor = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(),
                    1, TimeUnit.SECONDS, queue, runnable -> {
                        Thread thread = new Thread(runnable, "p13-route-fixture");
                        thread.setUncaughtExceptionHandler((worker, failure) -> failures.add(failure));
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
        }

        ProbeRoute route(boolean blocked) {
            EntityWalking entity = mock(EntityWalking.class);
            AtomicReference<Entity> target = new AtomicReference<>();
            when(entity.getTarget()).thenAnswer(call -> target.get());
            ProbeRoute route = new ProbeRoute(entity, target, blocked);
            routes.add(route);
            return route;
        }

        void blockWorker() throws Exception {
            blocker = route(true);
            submit(executor, new RouteFinderSearchTask(blocker));
            assertTrue(blocker.entered.await(5, TimeUnit.SECONDS));
        }
        void releaseWorker() { blocker.release.countDown(); }
        void drain() throws Exception { executor.submit(() -> {}).get(5, TimeUnit.SECONDS); }
        @Override public void close() throws Exception {
            for (ProbeRoute route : routes) route.release.countDown();
            discard(executor.shutdownNow());
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
