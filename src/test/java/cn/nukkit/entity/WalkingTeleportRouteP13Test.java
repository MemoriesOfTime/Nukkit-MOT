package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.entity.mob.EntityWalkingMob;
import cn.nukkit.entity.route.RouteFinder;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.event.entity.EntityLevelChangeEvent;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
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

class WalkingTeleportRouteP13Test {
    @Test
    void successfulNativeSameWorldTeleportDropsTheReadyPathAndPlainWaypoint() throws Exception {
        Fixture f = new Fixture();
        f.searchCurrent();
        RouteFinder old = f.entity.route;
        assertTrue(old.hasCurrentNode());
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        assertEquals(8.5, f.entity.x);
        assertSame(f.origin, f.entity.getLevel());
        assertAll(() -> assertNotSame(old, f.entity.route),
                () -> assertFalse(f.entity.route.hasCurrentNode()),
                () -> assertSame(f.goal, f.entity.target),
                () -> assertSame(f.goal, f.entity.followTarget));
    }

    @Test
    void nextNativeMovementTickCannotReinstateAnObsoleteDetourWaypoint() throws Exception {
        Fixture f = new Fixture();
        when(f.origin.hasCollision(eq(f.entity), any(), eq(false))).thenAnswer(call -> {
            AxisAlignedBB box = call.getArgument(1);
            return box.getMinX() < 3 && box.getMaxX() > 2 && box.getMinZ() < 2 && box.getMaxZ() > -2;
        });
        f.searchCurrent();
        // The real search must produce a detour, rather than a synthetic path.
        Vector3 detour = f.entity.route.advanceIfArrived(f.entity);
        assertNotNull(detour);
        assertNotEquals(f.goal, detour);
        f.entity.target = detour;
        f.entity.moveMultiplier = 1;
        f.entity.noRotateTicks = 1;
        when(f.entity.server.getMobAiEnabled()).thenReturn(true);
        doReturn(true).when(f.goal).isAlive();
        doReturn(true).when(f.goal).canBeFollowed();
        doReturn(1.0).when(f.entity).getSpeed();
        doReturn(false).when(f.entity).checkJump(anyDouble(), anyDouble());
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        f.entity.updateMove(1);
        org.mockito.ArgumentCaptor<Double> movedX = org.mockito.ArgumentCaptor.forClass(Double.class);
        org.mockito.ArgumentCaptor<Double> movedY = org.mockito.ArgumentCaptor.forClass(Double.class);
        org.mockito.ArgumentCaptor<Double> movedZ = org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(f.entity).move(movedX.capture(), movedY.capture(), movedZ.capture());
        assertTrue(movedX.getValue() < 0 && movedZ.getValue() < 0);
        double headingRatio = movedX.getValue() / movedZ.getValue();
        System.out.println("p13 teleport_motion_x_over_z=" + headingRatio);
        System.out.println("p13 stale_teleport_waypoint_selected=" + (f.entity.target == detour ? 1 : 0));
        assertEquals((f.goal.x - f.entity.x) / (f.goal.z - f.entity.z), headingRatio, 1.0e-12,
                "real updateMove must steer to the current goal instead of the pre-teleport detour");
        assertSame(f.goal, f.entity.target, "the native post-teleport tick must keep the goal, not the old detour");
        assertSame(f.goal, f.entity.followTarget);
    }

    @Test
    void obsoleteQueuedSearchBecomesANoopAfterTeleport() throws Exception {
        Fixture f = new Fixture();
        RouteFinder old = f.entity.route;
        RouteFinderSearchTask obsolete = queued(old);
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        f.originQueries.set(0);
        RouteFinderSearchTask fresh = f.entity.route != old ? queued(f.entity.route) : null;
        try {
            obsolete.run();
            System.out.println("p13 cancelled_teleport_search_queries=" + f.originQueries.get());
            assertEquals(0, f.originQueries.get());
            if (fresh != null) assertTrue(f.entity.route.hasPendingSearch());
            assertFalse(f.entity.route.hasCurrentNode());
        } finally {
            if (fresh != null) fresh.run();
        }
        assertFalse(old.hasPendingSearch());
    }

    @Test
    void runningSearchCannotPublishIntoThePostTeleportFinder() throws Exception {
        Fixture f = new Fixture();
        RouteFinder old = f.entity.route;
        f.blockOriginRead.set(true);
        RouteFinderSearchTask task = queued(old);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { task.run(); }
            catch (Throwable error) { failure.set(error); }
        }, "p13-teleport-route-worker");
        worker.start();
        try {
            assertTrue(f.originReadEntered.await(5, TimeUnit.SECONDS));
            assertTrue(old.isSearching());
            assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
            f.releaseOriginRead.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertNull(failure.get());
            assertTrue(old.hasCurrentNode(), "the old real search reached publication");
            assertAll(() -> assertNotSame(old, f.entity.route),
                    () -> assertFalse(f.entity.route.hasCurrentNode()),
                    () -> assertSame(f.origin, f.entity.route.getLevel()));
        } finally {
            f.releaseOriginRead.countDown();
            worker.join(5000);
            if (worker.isAlive()) { worker.interrupt(); worker.join(5000); }
            assertFalse(worker.isAlive());
        }
    }

    @Test
    void cancelledTeleportKeepsCoordinatesRouteAndPendingOwner() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        Vector3 target = f.entity.target;
        f.cancel.set(true);
        assertFalse(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        assertEquals(0.5, f.entity.x);
        assertSame(route, f.entity.route);
        assertSame(target, f.entity.target);
        assertSame(f.goal, f.entity.followTarget);
        assertTrue(route.hasPendingSearch());
        task.run();
        assertTrue(route.hasCurrentNode());
    }

    @Test
    void refusedPositionOfClosedEntityDoesNotInvalidateTheRoute() {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        Vector3 target = f.entity.target;
        f.entity.closed = true;
        assertFalse(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        assertSame(route, f.entity.route);
        assertSame(target, f.entity.target);
        assertEquals(0.5, f.entity.x);
    }

    @Test
    void samePositionRotationOnlyTeleportPreservesThePendingRoute() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        Vector3 target = f.entity.target;
        assertTrue(f.entity.teleport(new Location(0.5, 64, 0.5, 90, 20, f.origin), TeleportCause.UNKNOWN));
        assertSame(route, f.entity.route);
        assertSame(target, f.entity.target);
        assertEquals(90, f.entity.yaw);
        assertTrue(route.hasPendingSearch());
        task.run();
    }

    @Test
    void eventRedirectToTheCurrentPositionDoesNotAllocateAFinder() {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        f.redirect.set(new Location(0.5, 64, 0.5, 0, 0, f.origin));
        try (MockedConstruction<WalkerRouteFinder> created = mockConstruction(WalkerRouteFinder.class)) {
            assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
            assertSame(route, f.entity.route);
            assertEquals(0, created.constructed().size());
            assertEquals(0.5, f.entity.x);
        }
    }

    @Test
    void eventRedirectToAnotherWorldConstructsOnlyTheExistingWorldFinder() {
        Fixture f = new Fixture();
        f.redirect.set(new Location(8.5, 64, 8.5, 0, 0, f.destination));
        try (MockedConstruction<WalkerRouteFinder> created = mockConstruction(WalkerRouteFinder.class)) {
            assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
            assertSame(f.destination, f.entity.getLevel());
            assertEquals(1, created.constructed().size());
        }
    }

    @Test
    void sameWorldPositionGoalsAndOwnerStayAvailable() {
        for (boolean entityGoal : new boolean[] {false, true}) {
            Fixture f = new Fixture();
            Position target = entityGoal ? f.goal : new Position(6.5, 64, 4.5, f.origin);
            f.entity.target = target;
            assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), null));
            assertSame(target, f.entity.target);
            assertSame(f.goal, f.entity.followTarget);
        }
    }

    @Test
    void realPostTeleportCheckTargetKeepsTheLiveGoalWithoutRescanning() throws Exception {
        Fixture f = new Fixture(EntityEnderman.class);
        java.lang.reflect.Field angry = EntityEnderman.class.getDeclaredField("angry");
        angry.setAccessible(true);
        angry.setInt(f.entity, 200);
        f.searchCurrent();
        f.entity.lookupForTarget = true;
        f.entity.noRotateTicks = 1;
        when(f.entity.server.getMobAiEnabled()).thenReturn(true);
        doReturn(true).when(f.goal).isAlive();
        doReturn(true).when(f.goal).canBeFollowed();
        assertTrue(f.entity.targetOption(f.goal, f.entity.distanceSquared(f.goal)));
        doReturn(false).when(f.entity).checkJump(anyDouble(), anyDouble());
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        f.entity.updateMove(1);
        assertSame(f.goal, f.entity.followTarget);
        assertSame(f.goal, f.entity.target);
        verify(f.origin, never()).getNearbyEntities(any(), any(), anyBoolean(), anyBoolean());
        assertTrue(((EntityEnderman) f.entity).isAngry());
    }

    @Test
    void aNonAngryEndermanStillRejectsTheGoalAfterTeleport() {
        Fixture f = new Fixture(EntityEnderman.class);
        doReturn(true).when(f.goal).isAlive();
        doReturn(true).when(f.goal).canBeFollowed();
        when(f.origin.getNearbyEntities(any(), any(), anyBoolean(), anyBoolean())).thenReturn(new Entity[0]);
        assertFalse(f.entity.targetOption(f.goal, f.entity.distanceSquared(f.goal)));
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        f.entity.checkTarget();
        assertNull(f.entity.followTarget);
        assertNull(f.entity.getTarget());
        assertNotSame(f.goal, f.entity.target);
        assertFalse(((EntityEnderman) f.entity).isAngry());
        verify(f.origin).getNearbyEntities(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void aFinderInstalledDuringTheTeleportEventIsNotOverwritten() {
        Fixture f = new Fixture();
        RouteFinder installed = new WalkerRouteFinder(f.entity, new Vector3(8.5, 64, 8.5));
        f.eventRoute.set(installed);
        Vector3 target = f.entity.target;
        try (MockedConstruction<WalkerRouteFinder> created = mockConstruction(WalkerRouteFinder.class)) {
            assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
            assertSame(installed, f.entity.route);
            assertSame(target, f.entity.target);
            assertEquals(0, created.constructed().size());
        }
    }

    @Test
    void anObsoletePlainWaypointWithoutAFollowGoalIsCleared() {
        Fixture f = new Fixture();
        f.entity.followTarget = null;
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
        assertNull(f.entity.target);
        assertFalse(f.entity.route.hasCurrentNode());
    }

    @Test
    void entitiesWithoutARouteDoNotAcquirePathfindingOrLoseTheirGoal() {
        Fixture f = new Fixture();
        f.entity.route = null;
        Vector3 target = f.entity.target;
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), null));
        assertNull(f.entity.route);
        assertSame(target, f.entity.target);
        assertSame(f.goal, f.entity.followTarget);
    }

    @Test
    void customWalkerSubclassKeepsItsRouteAndGoal() {
        Fixture f = new Fixture();
        RouteFinder route = new CustomWalker(f.entity);
        f.entity.route = route;
        Vector3 target = f.entity.target;
        assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), null));
        assertSame(route, f.entity.route);
        assertSame(target, f.entity.target);
    }

    @Test
    void ordinarySetPositionKeepsTheExistingPendingFinder() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        Vector3 target = f.entity.target;
        assertTrue(f.entity.setPosition(new Vector3(1.5, 64, 0.5)));
        assertSame(route, f.entity.route);
        assertSame(target, f.entity.target);
        assertTrue(route.hasPendingSearch());
        task.run();
    }

    @Test
    void onlyActualSameWorldDisplacementConstructsAFinder() {
        Fixture f = new Fixture();
        try (MockedConstruction<WalkerRouteFinder> created = mockConstruction(WalkerRouteFinder.class)) {
            assertTrue(f.entity.teleport(new Vector3(0.5, 64, 0.5), null));
            f.cancel.set(true);
            assertFalse(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
            assertEquals(0, created.constructed().size());
            f.cancel.set(false);
            assertTrue(f.entity.teleport(new Vector3(8.5, 64, 8.5), TeleportCause.UNKNOWN));
            System.out.println("p13 same_world_teleport_finder_constructions=" + created.constructed().size());
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
        final EntityWalking entity;
        final EntityCreature goal = mock(EntityCreature.class, CALLS_REAL_METHODS);
        final Level origin = mock(Level.class);
        final Level destination = mock(Level.class);
        final AtomicBoolean cancel = new AtomicBoolean();
        final AtomicReference<cn.nukkit.level.Location> redirect = new AtomicReference<>();
        final AtomicReference<RouteFinder> eventRoute = new AtomicReference<>();
        final AtomicInteger originQueries = new AtomicInteger();
        final AtomicInteger destinationQueries = new AtomicInteger();
        final AtomicBoolean blockOriginRead = new AtomicBoolean();
        final CountDownLatch originReadEntered = new CountDownLatch(1);
        final CountDownLatch releaseOriginRead = new CountDownLatch(1);

        Fixture() { this(EntityWalkingMob.class); }

        Fixture(Class<? extends EntityWalking> type) {
            entity = mock(type, CALLS_REAL_METHODS);
            try {
                java.lang.reflect.Field passengers = Entity.class.getDeclaredField("passengers");
                passengers.setAccessible(true);
                passengers.set(entity, new java.util.ArrayList<Entity>());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            entity.level = origin;
            entity.x = 0.5;
            entity.y = 64;
            entity.z = 0.5;
            entity.server = mock(Server.class);
            entity.temporalVector = new Vector3();
            entity.boundingBox = new cn.nukkit.math.SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);
            entity.scale = 1;
            entity.justCreated = true;
            entity.age = 1;
            doNothing().when(entity).scheduleUpdate();
            doNothing().when(entity).updateMovement();
            doReturn(false).when(entity).isNoClip();
            doReturn(true).when(entity).isInTickingRange();
            doReturn(false).when(entity).isImmobile();
            doReturn(false).when(entity).isKnockback();
            doReturn(false).when(entity).move(anyDouble(), anyDouble(), anyDouble());
            PluginManager plugins = mock(PluginManager.class);
            when(entity.server.getPluginManager()).thenReturn(plugins);
            doAnswer(call -> {
                Object event = call.getArgument(0);
                if (event instanceof EntityLevelChangeEvent worldEvent) worldEvent.setCancelled(cancel.get());
                if (event instanceof cn.nukkit.event.entity.EntityTeleportEvent teleportEvent) {
                    teleportEvent.setCancelled(cancel.get());
                    if (redirect.get() != null) teleportEvent.setTo(redirect.get());
                    if (eventRoute.get() != null) entity.route = eventRoute.get();
                }
                return null;
            }).when(plugins).callEvent(any(cn.nukkit.event.Event.class));
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
            Block currentBlock = mock(Block.class);
            when(currentBlock.getId()).thenReturn(Block.STONE);
            doReturn(currentBlock).when(entity).getLevelBlock();
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
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenReturn(chunk);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(stone.getFrictionFactor()).thenReturn(0.6);
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
