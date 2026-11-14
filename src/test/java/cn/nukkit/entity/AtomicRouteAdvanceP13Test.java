package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.route.Node;
import cn.nukkit.entity.route.RouteFinder;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AtomicRouteAdvanceP13Test {
    @Test
    void nativeMovementKeepsItsTargetWhenWorkerClearsPathAfterTheLastCheck() throws Exception {
        try (Fixture f = new Fixture(0.5)) {
            f.controls.rebuildAtLastLegacyCheck = true;
            f.entity.updateMove(1);
            assertNotNull(f.entity.target, "native tick must not overwrite the target with null");
            assertTrue(f.entity.target == f.previousTarget || f.entity.target == f.next,
                    "keep the target or atomically advance the original path");
        }
    }

    @Test
    void nativeMovementDoesNotSkipAReplacementPathUsingArrivalAtTheOldPath() throws Exception {
        try (Fixture f = new Fixture(0.5)) {
            f.controls.rebuildAtLastLegacyCheck = true;
            f.controls.publishReplacement = true;
            f.entity.updateMove(1);
            assertTrue(f.entity.target == f.previousTarget || f.entity.target == f.next,
                    "arrival at the old node cannot justify advancing the replacement path");
        }
    }

    @Test
    void nativeReadyPathUsesOnlyOneLockAcquisitionPerAdvance() throws Exception {
        try (Fixture f = new Fixture(0.5)) {
            RecordingLock lock = new RecordingLock();
            Field field = RouteFinder.class.getDeclaredField("lock");
            field.setAccessible(true);
            field.set(f.route, lock);
            f.entity.updateMove(1);
            assertSame(f.next, f.entity.target);
            System.out.println("p13 advance_lock_acquisitions=" + (lock.reads + lock.writes));
            assertEquals(1, lock.reads + lock.writes, "one route transaction instead of three read locks");
        }
    }

    @Test
    void arrivalRemainsStrictHorizontalOneBlockIncludingNegativeCoordinates() throws Exception {
        for (double x : new double[] {-32.5, 0.5, 32.5}) {
            for (double distance : new double[] {0, 0.99, 1, 1.01}) {
                try (Fixture f = new Fixture(x)) {
                    Vector3 result = advance(f.route, new Vector3(x + distance, 9999, 0.5));
                    if (distance < 1) {
                        assertSame(f.next, result);
                        assertEquals(1, f.route.getCurrent());
                    } else {
                        assertNull(result);
                        assertEquals(0, f.route.getCurrent());
                    }
                }
            }
        }
    }

    @Test
    void emptyPathAndInProgressRebuildKeepThePreviousNativeTarget() throws Exception {
        try (Fixture f = new Fixture(0.5)) {
            RecordingLock lock = new RecordingLock();
            Field field = RouteFinder.class.getDeclaredField("lock");
            field.setAccessible(true);
            field.set(f.route, lock);
            f.route.resetNodes();
            lock.reads = 0;
            lock.writes = 0;
            f.entity.updateMove(1);
            assertSame(f.previousTarget, f.entity.target);
            assertEquals(0, lock.reads + lock.writes, "empty route must keep its lock-free tick skip");
            f.controls.markSearching(true);
            f.entity.updateMove(1);
            assertSame(f.previousTarget, f.entity.target);
            assertEquals(0, lock.reads + lock.writes, "busy route must keep its lock-free tick skip");
        }
    }

    @Test
    void nonFiniteCoordinatesCannotCountAsArrival() throws Exception {
        for (Vector3 position : new Vector3[] {
                new Vector3(Double.NaN, 64, 0.5), new Vector3(0.5, 64, Double.NaN),
                new Vector3(Double.POSITIVE_INFINITY, 64, 0.5)
        }) {
            try (Fixture f = new Fixture(0.5)) {
                assertNull(advance(f.route, position));
                assertEquals(0, f.route.getCurrent());
            }
        }
    }

    @Test
    void arrivalAtFinalWaypointDoesNotAdvanceBeyondThePath() throws Exception {
        try (Fixture f = new Fixture(0.5)) {
            assertSame(f.next, advance(f.route, new Vector3(0.5, 64, 0.5)));
            assertNull(advance(f.route, f.next));
            assertEquals(1, f.route.getCurrent());
        }
    }

    @Test
    void nativeWorkerResetWaitsUntilAtomicArrivalAndAdvanceReleaseThePath() throws Exception {
        try (Fixture f = new Fixture(0.5)) {
            RecordingLock lock = new RecordingLock();
            Field field = RouteFinder.class.getDeclaredField("lock");
            field.setAccessible(true);
            field.set(f.route, lock);
            doAnswer(call -> {
                assertTrue(lock.isWriteLockedByCurrentThread(), "arrival and index mutation need one exclusive lease");
                f.controls.startRebuild(); // Independent of the legacy hasNext() hook.
                assertTrue(f.controls.resetAttempt.await(5, TimeUnit.SECONDS));
                assertEquals(1, f.controls.cleared.getCount(), "worker cannot clear nodes inside this transaction");
                return f.entity.x;
            }).when(f.entity).getX();
            f.entity.updateMove(1);
            assertSame(f.next, f.entity.target);
            assertTrue(f.controls.cleared.await(5, TimeUnit.SECONDS), "worker resumes after the transaction");
            assertTrue(f.route.isSearching());
        }
    }

    // The fallback runs the exact old operation for a before/after semantic check.
    private static Vector3 advance(RouteFinder route, Vector3 position) throws Exception {
        try {
            Method method = RouteFinder.class.getMethod("advanceIfArrived", Vector3.class);
            return (Vector3) method.invoke(route, position);
        } catch (NoSuchMethodException oldVersion) {
            return !route.isSearching() && route.hasCurrentNode() && route.hasArrivedNode(position)
                    && route.hasNext() ? route.next() : null;
        }
    }

    private static Node node(Vector3 position) throws Exception {
        Constructor<Node> constructor = Node.class.getDeclaredConstructor(Vector3.class);
        constructor.setAccessible(true);
        return constructor.newInstance(position);
    }

    private static final class InterleavedRoute {
        final WalkerRouteFinder route;
        final CountDownLatch resetAttempt = new CountDownLatch(1);
        final CountDownLatch cleared = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        boolean rebuildAtLastLegacyCheck;
        boolean publishReplacement;
        int legacyChecks;
        Future<?> pending;

        InterleavedRoute(WalkerRouteFinder route) throws Exception {
            this.route = route;
            // Inline instrumentation retains the actual native runtime class and methods.
            assertSame(WalkerRouteFinder.class, route.getClass());
            doAnswer(call -> {
                boolean result = (boolean) call.callRealMethod();
                if (result && rebuildAtLastLegacyCheck && ++legacyChecks == 2) {
                    startRebuild();
                    assertTrue(cleared.await(5, TimeUnit.SECONDS), "worker must reach rebuild before advance");
                    if (publishReplacement) pending.get(5, TimeUnit.SECONDS);
                }
                return result;
            }).when(route).hasNext();
            doAnswer(call -> {
                resetAttempt.countDown();
                return call.callRealMethod();
            }).when(route).resetNodes();
            doAnswer(call -> {
                markSearching(true);
                try {
                    if (publishReplacement) {
                        route.addNode(node(new Vector3(50.5, 64, 0.5)));
                        route.addNode(node(new Vector3(51.5, 64, 0.5)));
                        cleared.countDown();
                    } else {
                        cleared.countDown();
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    }
                } finally {
                    markSearching(false);
                    Field finished = RouteFinder.class.getDeclaredField("finished");
                    finished.setAccessible(true);
                    finished.setBoolean(route, true);
                }
                return null;
            }).when(route).search();
        }

        void startRebuild() { pending = worker.submit(new RouteFinderSearchTask(route)); }
        void markSearching(boolean value) throws Exception {
            Field searching = RouteFinder.class.getDeclaredField("searching");
            searching.setAccessible(true);
            searching.setBoolean(route, value);
        }
        void closeWorker() throws Exception {
            release.countDown();
            worker.shutdown();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            if (pending != null) pending.get(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingLock extends ReentrantReadWriteLock {
        private static final long serialVersionUID = 1L;
        int reads;
        int writes;
        final ReadLock read = new ReadLock(this) {
            private static final long serialVersionUID = 1L;
            @Override public void lock() { reads++; super.lock(); }
        };
        final WriteLock write = new WriteLock(this) {
            private static final long serialVersionUID = 1L;
            @Override public void lock() { writes++; super.lock(); }
        };
        @Override public ReadLock readLock() { return read; }
        @Override public WriteLock writeLock() { return write; }
    }

    private static final class Fixture implements AutoCloseable {
        final EntityWalking entity = mock(EntityWalking.class, CALLS_REAL_METHODS);
        final Vector3 previousTarget = new Vector3(9.5, 64, 0.5);
        final Vector3 next;
        final WalkerRouteFinder route;
        final InterleavedRoute controls;

        Fixture(double x) throws Exception {
            Level level = mock(Level.class);
            Server server = mock(Server.class);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(false))).thenReturn(stone);
            entity.level = level;
            entity.x = x;
            entity.y = 64;
            entity.z = 0.5;
            entity.age = 1; // Avoid the separate every-ten-ticks submission path.
            entity.onGround = true;
            entity.target = previousTarget;
            doReturn(server).when(entity).getServer();
            doReturn(stone).when(entity).getLevelBlock();
            doReturn(true).when(entity).isInTickingRange();
            doReturn(false).when(entity).isImmobile();
            doReturn(false).when(entity).isKnockback();
            doReturn(false).when(entity).checkJump(anyDouble(), anyDouble());
            doReturn(false).when(entity).move(anyDouble(), anyDouble(), anyDouble());
            doNothing().when(entity).updateMovement();
            route = spy(new WalkerRouteFinder(entity));
            controls = new InterleavedRoute(route);
            next = new Vector3(x + 1, 64, 0.5);
            route.addNode(node(new Vector3(x, 64, 0.5)));
            route.addNode(node(next));
            entity.route = route;
        }

        @Override public void close() throws Exception { controls.closeWorker(); }
    }
}
