package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.route.RouteFinder;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.event.entity.EntityLevelChangeEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkingWorldRouteP13Test {
    @Test
    void successfulNativeWorldSwitchDiscardsTheOldReadyRouteAndForeignGoals() throws Exception {
        Fixture f = new Fixture();
        f.searchCurrent();
        assertTrue(f.entity.route.hasCurrentNode());
        f.entity.target = new Vector3(2.5, 64, 0.5);
        assertTrue(f.entity.switchLevel(f.destination));
        assertSame(f.destination, f.entity.getLevel());
        assertAll(() -> assertSame(f.destination, f.entity.route.getLevel()),
                () -> assertFalse(f.entity.route.hasCurrentNode()),
                () -> assertNull(f.entity.target),
                () -> assertNull(f.entity.followTarget));
    }

    @Test
    void nextSearchReadsOnlyTheDestinationWorld() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.entity.switchLevel(f.destination));
        f.goal.level = f.destination;
        f.entity.followTarget = f.goal;
        f.originQueries.set(0);
        f.destinationQueries.set(0);
        f.searchCurrent();
        assertTrue(f.entity.route.hasCurrentNode());
        System.out.println("p13 post_switch_origin_queries=" + f.originQueries.get()
                + " destination_queries=" + f.destinationQueries.get());
        assertEquals(0, f.originQueries.get());
        assertTrue(f.destinationQueries.get() > 0);
        verify(f.destination, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void cancelledNativeWorldSwitchKeepsRouteAndGoals() throws Exception {
        Fixture f = new Fixture();
        f.searchCurrent();
        RouteFinder route = f.entity.route;
        Vector3 target = f.entity.target;
        f.cancel.set(true);
        assertFalse(f.entity.switchLevel(f.destination));
        assertSame(f.origin, f.entity.getLevel());
        assertSame(route, f.entity.route);
        assertTrue(route.hasCurrentNode());
        assertSame(target, f.entity.target);
        assertSame(f.goal, f.entity.followTarget);
        verify(f.destination, never()).addEntity(any());
    }

    @Test
    void sameLevelSwitchDoesNotInvalidateOrCancelThePendingRoute() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        Vector3 target = f.entity.target;
        assertTrue(f.entity.switchLevel(f.origin));
        assertSame(route, f.entity.route);
        assertTrue(route.hasPendingSearch());
        assertSame(target, f.entity.target);
        assertSame(f.goal, f.entity.followTarget);
        task.run();
        assertTrue(route.hasCurrentNode());
        assertFalse(route.hasPendingSearch());
    }

    @Test
    void targetAndOwnerAlreadyInTheDestinationWorldRemainValid() {
        Fixture f = new Fixture();
        f.goal.level = f.destination;
        f.entity.target = f.goal;
        assertTrue(f.entity.switchLevel(f.destination));
        assertSame(f.goal, f.entity.target);
        assertSame(f.goal, f.entity.followTarget);
        Position localPoint = new Position(6.5, 64, 4.5, f.origin);
        f.entity.target = localPoint;
        f.goal.level = f.origin;
        assertTrue(f.entity.switchLevel(f.origin));
        assertSame(localPoint, f.entity.target);
        assertSame(f.goal, f.entity.followTarget);
    }

    @Test
    void walkingEntitiesWithoutARouteDoNotAcquireNewPathfinding() {
        Fixture f = new Fixture();
        f.entity.route = null;
        f.goal.level = f.destination;
        f.entity.target = f.goal;
        assertTrue(f.entity.switchLevel(f.destination));
        assertNull(f.entity.route);
        assertSame(f.goal, f.entity.followTarget);
        assertSame(f.goal, f.entity.target);
    }

    @Test
    void oldQueuedSearchBecomesANoopAndCannotReleaseTheNewRouteOwner() throws Exception {
        Fixture f = new Fixture();
        RouteFinder old = f.entity.route;
        RouteFinderSearchTask obsolete = queued(old);
        assertTrue(f.entity.switchLevel(f.destination));
        f.goal.level = f.destination;
        f.entity.followTarget = f.goal;
        f.originQueries.set(0);
        RouteFinderSearchTask current = f.entity.route != old ? queued(f.entity.route) : null;
        try {
            obsolete.run();
            System.out.println("p13 cancelled_world_search_origin_queries=" + f.originQueries.get());
            assertEquals(0, f.originQueries.get());
            if (current != null) assertTrue(f.entity.route.hasPendingSearch());
            assertFalse(f.entity.route.hasCurrentNode());
        } finally {
            if (current != null) current.run();
        }
        assertFalse(old.hasPendingSearch());
        assertFalse(f.entity.route.hasPendingSearch());
    }

    @Test
    void runningOldSearchPublishesOnlyIntoItsDetachedFinder() throws Exception {
        Fixture f = new Fixture();
        RouteFinder old = f.entity.route;
        f.blockOriginRead.set(true);
        RouteFinderSearchTask task = queued(old);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { task.run(); }
            catch (Throwable error) { failure.set(error); }
        }, "p13-world-route-worker");
        worker.start();
        try {
            assertTrue(f.originReadEntered.await(5, TimeUnit.SECONDS));
            assertTrue(old.isSearching());
            assertTrue(f.entity.switchLevel(f.destination));
            f.releaseOriginRead.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertNull(failure.get());
            assertTrue(old.hasCurrentNode(), "old running search really reached publication");
            assertFalse(old.hasPendingSearch());
            assertAll(() -> assertSame(f.destination, f.entity.route.getLevel()),
                    () -> assertNotSame(old, f.entity.route),
                    () -> assertFalse(f.entity.route.hasCurrentNode(), "old publication must not reappear in the destination route"));
        } finally {
            f.releaseOriginRead.countDown();
            worker.join(5000);
            if (worker.isAlive()) {
                worker.interrupt();
                worker.join(5000);
            }
            assertFalse(worker.isAlive());
        }
    }

    @Test
    void injectedWalkerSubclassIsNotReplacedWithTheNativeDefault() {
        Fixture f = new Fixture();
        WalkerRouteFinder custom = new CustomWalker(f.entity);
        f.entity.route = custom;
        assertTrue(f.entity.switchLevel(f.destination));
        assertSame(custom, f.entity.route);
    }

    @Test
    void freshFinderIsAllocatedOnlyForASuccessfulActualWorldChange() {
        Fixture f = new Fixture();
        try (MockedConstruction<WalkerRouteFinder> created = mockConstruction(WalkerRouteFinder.class)) {
            assertTrue(f.entity.switchLevel(f.origin));
            assertEquals(0, created.constructed().size());
            f.cancel.set(true);
            assertFalse(f.entity.switchLevel(f.destination));
            assertEquals(0, created.constructed().size());
            f.cancel.set(false);
            assertTrue(f.entity.switchLevel(f.destination));
            System.out.println("p13 world_change_finder_constructions=" + created.constructed().size());
            assertEquals(1, created.constructed().size());
        }
    }

    private static RouteFinderSearchTask queued(RouteFinder route) throws Exception {
        RouteFinderSearchTask task = new RouteFinderSearchTask(route);
        Method reserve = RouteFinderSearchTask.class.getDeclaredMethod("tryQueue");
        reserve.setAccessible(true);
        assertTrue((boolean) reserve.invoke(task));
        return task;
    }

    private static final class CustomWalker extends WalkerRouteFinder {
        CustomWalker(EntityWalking entity) { super(entity); }
    }

    private static final class Fixture {
        final EntityWalking entity = mock(EntityWalking.class, CALLS_REAL_METHODS);
        final EntityCreature goal = mock(EntityCreature.class, CALLS_REAL_METHODS);
        final Level origin = mock(Level.class);
        final Level destination = mock(Level.class);
        final AtomicBoolean cancel = new AtomicBoolean();
        final AtomicInteger originQueries = new AtomicInteger();
        final AtomicInteger destinationQueries = new AtomicInteger();
        final AtomicBoolean blockOriginRead = new AtomicBoolean();
        final CountDownLatch originReadEntered = new CountDownLatch(1);
        final CountDownLatch releaseOriginRead = new CountDownLatch(1);

        Fixture() {
            entity.level = origin;
            entity.x = 0.5;
            entity.y = 64;
            entity.z = 0.5;
            entity.server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(entity.server.getPluginManager()).thenReturn(plugins);
            doAnswer(call -> {
                EntityLevelChangeEvent event = call.getArgument(0);
                event.setCancelled(cancel.get());
                return null;
            }).when(plugins).callEvent(any(EntityLevelChangeEvent.class));
            doNothing().when(entity).despawnFromAll();
            doReturn(0.6f).when(entity).getWidth();
            doReturn(1.8f).when(entity).getHeight();
            doReturn(1f).when(entity).getScale();
            goal.level = origin;
            goal.x = 6.5;
            goal.y = 64;
            goal.z = 4.5;
            entity.followTarget = goal;
            entity.target = new Vector3(1.5, 64, 0.5);
            BaseFullChunk oldChunk = flatWorld(origin, originQueries, true);
            flatWorld(destination, destinationQueries, false);
            entity.chunk = oldChunk;
            entity.route = new WalkerRouteFinder(entity);
        }

        BaseFullChunk flatWorld(Level level, AtomicInteger queries, boolean oldWorld) {
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            LevelProvider provider = mock(LevelProvider.class);
            when(level.getProvider()).thenAnswer(call -> { queries.incrementAndGet(); return provider; });
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(stone.getY()).thenReturn(63.0);
            Block air = mock(Block.class);
            when(air.getId()).thenReturn(Block.AIR);
            when(air.canPassThrough()).thenReturn(true);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(false))).thenAnswer(call -> {
                queries.incrementAndGet();
                if (oldWorld && blockOriginRead.compareAndSet(true, false)) {
                    originReadEntered.countDown();
                    assertTrue(releaseOriginRead.await(5, TimeUnit.SECONDS));
                }
                return call.<Integer>getArgument(2) <= 63 ? stone : air;
            });
            when(level.hasCollision(eq(entity), any(), eq(false))).thenReturn(false);
            return chunk;
        }

        void searchCurrent() throws Exception { queued(entity.route).run(); }
    }
}
