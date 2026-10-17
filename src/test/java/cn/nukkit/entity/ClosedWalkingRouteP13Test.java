package cn.nukkit.entity;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.mob.EntityWalkingMob;
import cn.nukkit.entity.route.Node;
import cn.nukkit.entity.route.RouteFinder;
import cn.nukkit.entity.route.RouteFinderSearchTask;
import cn.nukkit.entity.route.WalkerRouteFinder;
import cn.nukkit.event.entity.EntityLevelChangeEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClosedWalkingRouteP13Test {
    @Test
    void nativeCloseCancelsTheQueuedOwnerWithoutTerrainWork() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        f.entity.close();
        boolean pendingImmediatelyAfterClose = route.hasPendingSearch();
        assertTrue(f.entity.closed);
        f.originQueries.set(0);
        task.run();
        System.out.println("p13 closed_queued_search_queries=" + f.originQueries.get());
        assertAll(() -> assertFalse(pendingImmediatelyAfterClose),
                () -> assertEquals(0, f.originQueries.get()),
                () -> assertFalse(route.hasPendingSearch()));
    }

    @Test
    void nativeCloseClearsReadyNodesAndRemainsIdempotentThroughRealLevelRemoval() throws Exception {
        Fixture f = new Fixture();
        f.searchCurrent();
        RouteFinder route = f.entity.route;
        assertTrue(route.hasCurrentNode());
        f.entity.close();
        f.entity.close();
        assertTrue(f.entity.closed);
        assertFalse(route.hasCurrentNode());
        verify(f.origin, atLeastOnce()).removeEntity(f.entity);
        verify(f.entity.chunk, atLeastOnce()).removeEntity(f.entity);
    }

    @Test
    void healthyClosedMobStillReportsAliveSoHealthIsNotACancellationFence() {
        Fixture f = new Fixture();
        assertTrue(f.entity.isAlive());
        f.entity.close();
        assertTrue(f.entity.closed);
        assertTrue(f.entity.isAlive());
    }

    @Test
    void directNativeTaskCannotRestartTheClosedFinder() {
        Fixture f = new Fixture();
        f.entity.close();
        f.originQueries.set(0);
        new RouteFinderSearchTask(f.entity.route).run();
        System.out.println("p13 closed_direct_search_queries=" + f.originQueries.get());
        assertEquals(0, f.originQueries.get());
        assertFalse(f.entity.route.hasCurrentNode());
        assertFalse(f.entity.route.isSearching());
    }

    @Test
    void freshReservedTaskForAClosedFinderReturnsWithoutLeakingOwnership() throws Exception {
        Fixture f = new Fixture();
        f.entity.close();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        assertTrue(route.hasPendingSearch());
        f.originQueries.set(0);
        task.run();
        assertEquals(0, f.originQueries.get());
        assertFalse(route.hasPendingSearch(), "the early worker return must release the claimed lease");
        assertFalse(route.hasCurrentNode());
    }

    @Test
    void runningNativeSearchStopsAfterTheCurrentExpansionAndReleasesItsLease() throws Exception {
        Fixture f = new Fixture();
        f.blockOriginRead.set(true);
        runCloseInterleave(f, "expansion");
    }

    @Test
    void closeDuringNativeSmoothingCannotPublishALatePath() throws Exception {
        Fixture f = new Fixture();
        when(f.origin.hasCollision(eq(f.entity), any(), eq(false))).thenAnswer(call -> {
            AxisAlignedBB box = call.getArgument(1);
            return box.getMinX() < 3 && box.getMaxX() > 2 && box.getMinZ() < 2 && box.getMaxZ() > -2;
        });
        f.blockSmoothingRead.set(true);
        runCloseInterleave(f, "smoothing");
    }

    @Test
    void cleanupFailureDoesNotLeaveTheQueuedSearchAlive() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        // Public close enters its cleanup finally after the despawn event succeeds.
        doThrow(new IllegalStateException("p13 close listener failure")).when(f.entity).despawnFromAll();
        IllegalStateException error = assertThrows(IllegalStateException.class, f.entity::close);
        assertEquals("p13 close listener failure", error.getMessage());
        assertTrue(f.entity.closed);
        f.originQueries.set(0);
        task.run();
        assertEquals(0, f.originQueries.get());
        assertFalse(route.hasPendingSearch());
        verify(f.origin, atLeastOnce()).removeEntity(f.entity);
    }

    @Test
    void listenerFailureStillClosesTheRouteWithoutClaimingBaseEntityCleanup() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        f.failDespawnEvent.set(true);
        assertThrows(IllegalStateException.class, f.entity::close);
        assertTrue(f.entity.closed);
        f.originQueries.set(0);
        task.run();
        assertEquals(0, f.originQueries.get());
        assertFalse(route.hasPendingSearch());
    }

    @Test
    void lateSingleAndBatchNodePublicationIsRejectedAfterClose() throws Exception {
        Fixture f = new Fixture();
        RouteFinder route = f.entity.route;
        f.entity.close();
        route.addNode(node(new Vector3(1.5, 64, 0.5)));
        ArrayList<Node> batch = new ArrayList<>();
        batch.add(node(new Vector3(2.5, 64, 0.5)));
        route.addNode(batch);
        assertFalse(route.hasCurrentNode());
    }

    @Test
    void resetNodesCannotReopenTheClosedSearchLifecycle() {
        Fixture f = new Fixture();
        f.entity.close();
        f.entity.route.resetNodes();
        f.originQueries.set(0);
        f.entity.route.research();
        assertEquals(0, f.originQueries.get());
        assertFalse(f.entity.route.hasCurrentNode());
    }

    @Test
    void finderConstructedForAnAlreadyClosedNativeEntityDoesNotSearch() {
        Fixture f = new Fixture();
        f.entity.close();
        f.entity.route = new WalkerRouteFinder(f.entity);
        f.originQueries.set(0);
        new RouteFinderSearchTask(f.entity.route).run();
        assertEquals(0, f.originQueries.get());
        assertFalse(f.entity.route.hasCurrentNode());
    }

    @Test
    void customWalkerSubclassIsLeftToItsExistingOwner() throws Exception {
        Fixture f = new Fixture();
        RouteFinder custom = new CustomWalker(f.entity);
        f.entity.route = custom;
        new RouteFinderSearchTask(custom).run();
        assertTrue(custom.hasCurrentNode());
        f.entity.close();
        assertSame(custom, f.entity.route);
        assertTrue(custom.hasCurrentNode());
    }

    @Test
    void realNativeWorldTransferClosesTheMobAndDoesNotProveLivePortalAI() {
        Fixture f = new Fixture();
        assertTrue(f.entity.switchLevel(f.destination));
        assertTrue(f.entity.closed, "known adjacent core defect: removeEntity closes non-players during transfer");
        assertTrue(f.entity.isAlive());
        assertSame(f.destination, f.entity.getLevel());
        verify(f.destination).addEntity(f.entity);
        System.out.println("p13 real_world_transfer_closed=" + f.entity.closed);
    }

    private static void runCloseInterleave(Fixture f, String phase) throws Exception {
        RouteFinder route = f.entity.route;
        RouteFinderSearchTask task = queued(route);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { task.run(); }
            catch (Throwable error) { failure.set(error); }
        }, "p13-close-" + phase + "-worker");
        worker.start();
        try {
            assertTrue(f.originReadEntered.await(5, TimeUnit.SECONDS), "real search reached " + phase);
            assertTrue(route.isSearching());
            f.entity.close();
            assertTrue(f.entity.closed);
            assertTrue(route.hasPendingSearch(), "a running task retains its lease until finally");
            int queriesBeforeRelease = f.originQueries.get();
            f.releaseOriginRead.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertNull(failure.get());
            System.out.println("p13 closed_" + phase + "_remaining_queries=" + (f.originQueries.get() - queriesBeforeRelease));
            assertFalse(route.hasPendingSearch());
            assertFalse(route.isSearching());
            assertFalse(route.hasCurrentNode(), "closed route must reject late path publication");
        } finally {
            f.releaseOriginRead.countDown();
            worker.join(5000);
            if (worker.isAlive()) { worker.interrupt(); worker.join(5000); }
            assertFalse(worker.isAlive());
        }
    }

    private static RouteFinderSearchTask queued(RouteFinder route) throws Exception {
        RouteFinderSearchTask task = new RouteFinderSearchTask(route);
        Method method = RouteFinderSearchTask.class.getDeclaredMethod("tryQueue");
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(task));
        return task;
    }

    private static Node node(Vector3 point) throws Exception {
        Constructor<Node> constructor = Node.class.getDeclaredConstructor(Vector3.class);
        constructor.setAccessible(true);
        return constructor.newInstance(point);
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
        final AtomicBoolean failDespawnEvent = new AtomicBoolean();
        final AtomicBoolean blockSmoothingRead = new AtomicBoolean();
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
            entity.health = 20;
            goal.health = 20;
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
                if (event instanceof cn.nukkit.event.entity.EntityDespawnEvent && failDespawnEvent.get()) {
                    throw new IllegalStateException("p13 close listener failure");
                }
                if (event instanceof EntityLevelChangeEvent worldEvent) worldEvent.setCancelled(cancel.get());
                if (event instanceof cn.nukkit.event.entity.EntityTeleportEvent teleportEvent) {
                    teleportEvent.setCancelled(cancel.get());
                    if (redirect.get() != null) teleportEvent.setTo(redirect.get());
                    if (eventRoute.get() != null) entity.route = eventRoute.get();
                }
                return null;
            }).when(plugins).callEvent(any(cn.nukkit.event.Event.class));
            doNothing().when(entity).despawnFromAll();
            doNothing().when(entity).removeAllEffects(any(cn.nukkit.event.entity.EntityPotionEffectEvent.Cause.class));
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
            nativeRemoval(origin);
            nativeRemoval(destination);
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
                boolean smoothing = oldWorld && blockSmoothingRead.get()
                        && java.lang.StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getMethodName().equals("FloydSmooth")))
                        && blockSmoothingRead.compareAndSet(true, false);
                if (oldWorld && (blockOriginRead.compareAndSet(true, false) || smoothing)) {
                    originReadEntered.countDown();
                    assertTrue(releaseOriginRead.await(5, TimeUnit.SECONDS));
                }
                return call.<Integer>getArgument(2) <= 63 ? stone : air;
            });
            when(level.hasCollision(eq(entity), any(), eq(false))).thenReturn(false);
            return chunk;
        }

        void nativeRemoval(Level level) {
            try {
                for (String name : new String[] {"entities", "updateEntities"}) {
                    java.lang.reflect.Field field = Level.class.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(level, new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<Entity>());
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            doCallRealMethod().when(level).removeEntity(any(Entity.class));
        }

        void searchCurrent() throws Exception { queued(entity.route).run(); }
    }
}
