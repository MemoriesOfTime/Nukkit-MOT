package cn.nukkit.entity;

import cn.nukkit.block.Block;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.RouteFinderThreadPool;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkerSearchRecoveryP13Test {
    @Test
    void targetFailurePropagatesAndReleasesTheRealSearchState() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("planned target read failure");
        f.targetFailure.set(failure);
        RouteFinderSearchTask task = queued(f.route);
        assertSame(failure, assertThrows(IllegalStateException.class, task::run));
        assertAll(() -> assertFalse(f.route.isSearching()),
                () -> assertTrue(f.route.isFinished()),
                () -> assertFalse(f.route.hasPendingSearch()));
        queued(f.route).run();
        f.assertHealthyPath();
    }

    @Test
    void terrainFailureAllowsTheNextNativeMovementTickToScheduleRecovery() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("planned loaded terrain read failure");
        f.terrainFailure.set(failure);
        RouteFinderSearchTask task = queued(f.route);
        assertSame(failure, assertThrows(IllegalStateException.class, task::run));
        assertFalse(f.route.hasPendingSearch(), "task owner release alone does not prove search recovery");
        List<RouteFinderSearchTask> submitted = new ArrayList<>();
        int queriesBeforeTick = f.terrainQueries.get();
        try (MockedStatic<RouteFinderThreadPool> pool = mockStatic(RouteFinderThreadPool.class)) {
            pool.when(() -> RouteFinderThreadPool.executeRouteFinderThread(any()))
                    .thenAnswer(call -> { submitted.add(call.getArgument(0)); return null; });
            f.entity.updateMove(1);
        }
        System.out.println("p13 native_retry_submissions=" + submitted.size()
                + " recovery_tick_world_queries=" + (f.terrainQueries.get() - queriesBeforeTick));
        assertEquals(1, submitted.size(), "failed search must not permanently block ten-tick admission");
        assertEquals(queriesBeforeTick, f.terrainQueries.get(), "movement tick must not query terrain for recovery");
        reserve(submitted.get(0));
        submitted.get(0).run();
        f.assertHealthyPath();
    }

    @Test
    void fatalSearchErrorPropagatesWithoutRetainingBusyState() throws Exception {
        Fixture f = new Fixture();
        AssertionError failure = new AssertionError("planned fatal terrain read error");
        f.terrainFailure.set(failure);
        RouteFinderSearchTask task = queued(f.route);
        assertSame(failure, assertThrows(AssertionError.class, task::run));
        assertAll(() -> assertFalse(f.route.isSearching()),
                () -> assertTrue(f.route.isFinished()),
                () -> assertFalse(f.route.hasPendingSearch()));
        queued(f.route).run();
        f.assertHealthyPath();
    }

    @Test
    void absentTargetStillFinishesWithoutTerrainQueriesOrNodes() throws Exception {
        Fixture f = new Fixture();
        f.target.set(null);
        queued(f.route).run();
        assertTrue(f.route.isFinished());
        assertFalse(f.route.isSearching());
        assertFalse(f.route.hasPendingSearch());
        assertFalse(f.route.hasCurrentNode());
        assertEquals(0, f.terrainQueries.get());
    }

    @Test
    void interruptedDirectSearchStillFinishesWithoutPublishingNodes() {
        Fixture f = new Fixture();
        f.route.setDestination(f.target.get());
        f.route.interrupt();
        f.route.search();
        assertTrue(f.route.isFinished());
        assertFalse(f.route.isSearching());
        assertTrue(f.route.isInterrupted());
        assertFalse(f.route.hasCurrentNode());
        assertEquals(0, f.terrainQueries.get());
    }

    private static RouteFinderSearchTask queued(WalkerRouteFinder route) throws Exception {
        RouteFinderSearchTask task = new RouteFinderSearchTask(route);
        reserve(task);
        return task;
    }

    private static void reserve(RouteFinderSearchTask task) throws Exception {
        Method method = RouteFinderSearchTask.class.getDeclaredMethod("tryQueue");
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(task));
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final EntityWalking entity = mock(EntityWalking.class, CALLS_REAL_METHODS);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final AtomicReference<Entity> target = new AtomicReference<>(liveTarget());
        final AtomicReference<Throwable> targetFailure = new AtomicReference<>();
        final AtomicReference<Throwable> terrainFailure = new AtomicReference<>();
        final AtomicInteger terrainQueries = new AtomicInteger();
        final WalkerRouteFinder route;

        private static Entity liveTarget() {
            Entity target = mock(Entity.class, CALLS_REAL_METHODS);
            target.x = 6.5;
            target.y = 64;
            target.z = 4.5;
            return target;
        }

        Fixture() {
            entity.age = 10;
            entity.riding = mock(Entity.class); // End native updateMove before unrelated physics.
            entity.chunk = chunk;
            doReturn(level).when(entity).getLevel();
            doReturn(true).when(entity).isInTickingRange();
            doReturn(false).when(entity).isImmobile();
            doReturn(true).when(entity).isKnockback();
            doReturn(0.6f).when(entity).getWidth();
            doReturn(1.8f).when(entity).getHeight();
            doReturn(1f).when(entity).getScale();
            doAnswer(call -> {
                Throwable failure = targetFailure.getAndSet(null);
                if (failure != null) throw failure;
                return target.get();
            }).when(entity).getTarget();
            when(level.getProvider()).thenReturn(mock(LevelProvider.class));
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(stone.getY()).thenReturn(63.0);
            Block air = mock(Block.class);
            when(air.getId()).thenReturn(Block.AIR);
            when(air.canPassThrough()).thenReturn(true);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(false))).thenAnswer(call -> {
                terrainQueries.incrementAndGet();
                Throwable failure = terrainFailure.getAndSet(null);
                if (failure != null) throw failure;
                return call.<Integer>getArgument(2) <= 63 ? stone : air;
            });
            when(level.hasCollision(eq(entity), any(), eq(false))).thenReturn(false);
            route = new WalkerRouteFinder(entity, new Vector3(0.5, 64, 0.5));
            entity.route = route;
        }

        void assertHealthyPath() {
            assertTrue(route.isFinished());
            assertFalse(route.isSearching());
            assertFalse(route.hasPendingSearch());
            assertTrue(route.isReachable());
            assertTrue(route.hasCurrentNode());
            Vector3 last = route.getCurrentNode().getVector3();
            while (route.hasNext()) last = route.next();
            assertEquals(new Vector3(target.get().x, target.get().y, target.get().z), last);
            verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }
    }
}
