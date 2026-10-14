package cn.nukkit.entity.route;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityWalking;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkerReachabilityP13Test {
    @Test
    void successfulNativeRetryReportsThePublishedPathReachableAgain() {
        Fixture f = new Fixture();
        f.failOnUnloadedTerrain();
        f.loaded.set(true);
        f.search();
        f.assertSuccessfulPath();
        System.out.println("p13 reachable_after_successful_retry=" + f.route.isReachable());
        assertTrue(f.route.isReachable(), "successful path must replace the previous failed result");
    }

    @Test
    void laterFailureStillReportsUnreachableAndKeepsNativeFallback() {
        Fixture f = new Fixture();
        f.search();
        assertTrue(f.route.isReachable());
        f.failOnUnloadedTerrain();
        assertEquals(1, f.route.nodes.size());
        assertEquals(f.position(), f.route.getCurrentNode().getVector3());
    }

    @Test
    void failedResultIsNotClearedBeforeSuccessfulPublication() {
        Fixture f = new Fixture();
        f.failOnUnloadedTerrain();
        f.loaded.set(true);
        AtomicInteger checks = new AtomicInteger();
        f.readProbe.set(() -> {
            checks.incrementAndGet();
            assertFalse(f.route.isReachable(), "in-progress terrain reads are not a successful result");
        });
        f.search();
        assertTrue(checks.get() > 0);
        f.assertSuccessfulPath();
        assertTrue(f.route.isReachable());
    }

    @Test
    void missingTargetAndInterruptRetainThePreviousFailedResult() {
        Fixture f = new Fixture();
        f.failOnUnloadedTerrain();
        when(f.entity.getTarget()).thenReturn(null);
        f.search();
        assertFalse(f.route.isReachable());
        assertFalse(f.route.hasCurrentNode());
        f.route.setDestination(f.target);
        f.route.interrupt();
        f.route.search();
        assertFalse(f.route.isReachable());
        assertFalse(f.route.hasCurrentNode());
        assertTrue(f.route.isFinished());
        assertFalse(f.route.isSearching());
    }

    @Test
    void terrainExceptionCannotMarkAnUnpublishedRetryReachable() {
        Fixture f = new Fixture();
        f.failOnUnloadedTerrain();
        f.loaded.set(true);
        IllegalStateException failure = new IllegalStateException("planned terrain read failure");
        f.readProbe.set(() -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, f::search));
        assertFalse(f.route.isReachable());
        assertFalse(f.route.hasCurrentNode());
        assertFalse(f.route.hasPendingSearch());
        assertFalse(f.route.isSearching());
        assertTrue(f.route.isFinished());
    }

    private static final class Fixture {
        final EntityWalking entity = mock(EntityWalking.class);
        final Entity target = mock(Entity.class, CALLS_REAL_METHODS);
        final Level level = mock(Level.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        final AtomicBoolean loaded = new AtomicBoolean(true);
        final AtomicReference<Runnable> readProbe = new AtomicReference<>();
        final WalkerRouteFinder route;

        Fixture() {
            target.x = 6.5;
            target.y = 64;
            target.z = 4.5;
            when(entity.getLevel()).thenReturn(level);
            when(entity.getTarget()).thenReturn(target);
            when(entity.getWidth()).thenReturn(0.6f);
            when(entity.getHeight()).thenReturn(1.8f);
            when(entity.getScale()).thenReturn(1f);
            entity.chunk = chunk;
            when(level.getProvider()).thenReturn(mock(LevelProvider.class));
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(call -> loaded.get() ? chunk : null);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(stone.getY()).thenReturn(63.0);
            Block air = mock(Block.class);
            when(air.getId()).thenReturn(Block.AIR);
            when(air.canPassThrough()).thenReturn(true);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(false))).thenAnswer(call -> {
                Runnable probe = readProbe.get();
                if (probe != null) probe.run();
                return call.<Integer>getArgument(2) <= 63 ? stone : air;
            });
            when(level.hasCollision(eq(entity), any(), eq(false))).thenReturn(false);
            route = new WalkerRouteFinder(entity, new Vector3(0.5, 64, 0.5));
        }

        void search() {
            RouteFinderSearchTask task = new RouteFinderSearchTask(route);
            assertTrue(task.tryQueue());
            task.run();
        }

        void failOnUnloadedTerrain() {
            loaded.set(false);
            search();
            assertFalse(route.isReachable());
            assertTrue(route.isFinished());
            assertFalse(route.isSearching());
            assertFalse(route.hasPendingSearch());
            verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }

        Vector3 position() { return new Vector3(target.x, target.y, target.z); }

        void assertSuccessfulPath() {
            assertTrue(route.isFinished());
            assertFalse(route.isSearching());
            assertFalse(route.hasPendingSearch());
            assertTrue(route.nodes.size() > 1);
            assertEquals(position(), route.nodes.get(route.nodes.size() - 1).getVector3());
            verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }
    }
}
