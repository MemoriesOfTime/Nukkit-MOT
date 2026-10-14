package cn.nukkit.entity.route;

import cn.nukkit.block.Block;
import cn.nukkit.entity.EntityWalking;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkerClosedLookupP13Test {
    @Test
    void fullSearchDepthDoesNotRescanAllClosedNodesForEveryCandidate() throws Exception {
        Fixture f = new Fixture(0);
        WalkerRouteFinder route = f.route(new Vector3(0.5, 64, 0.5), new Vector3(500.5, 64, 500.5));
        route.search();
        assertFalse(route.isReachable());
        assertEquals(101, closed(route).size(), "preserve the existing 100-pop budget");
        CountingVector.comparisons = 0;
        for (int i = 0; i < 800; i++) {
            assertTrue(unvisited(route, new CountingVector(1000.5 + i, 64, 1000.5)));
        }
        System.out.println("p13 closed_lookup_comparisons=" + CountingVector.comparisons);
        assertTrue(CountingVector.comparisons <= 1600, "800 lookups must not rescan 101 closed nodes");
    }

    @Test
    void visitedPositionsAndMovingOriginKeepExactMembership() throws Exception {
        Fixture f = new Fixture(0);
        Vector3 start = new Vector3(0.5, 64, 0.5);
        WalkerRouteFinder route = f.route(start, new Vector3(500.5, 64, 500.5));
        route.search();
        for (Node node : closed(route)) {
            assertFalse(unvisited(route, node.getVector3().clone()));
        }
        start.x = 2000.5;
        start.z = 2000.5;
        assertFalse(unvisited(route, start.clone()), "origin can be a live mutable entity/vector");
        assertTrue(unvisited(route, new Vector3(0.5, 64, 0.5)), "old origin must not remain indexed");
    }

    @Test
    void reusedFinderDoesNotRetainClosedPositionsFromThePreviousSearch() throws Exception {
        Fixture f = new Fixture(0);
        WalkerRouteFinder route = f.route(new Vector3(0.5, 64, 0.5), new Vector3(500.5, 64, 500.5));
        route.search();
        Vector3 oldPosition = closed(route).get(20).getVector3().clone();
        route.setStart(new Vector3(3000.5, 64, 3000.5));
        route.setDestination(new Vector3(3500.5, 64, 3500.5));
        route.search();
        assertTrue(unvisited(route, oldPosition));
    }

    @Test
    void loadedFlatRoutesStillReachPositiveAndNegativeTargets() {
        for (int offset : new int[] {-32, 0, 32}) {
            Fixture f = new Fixture(0);
            Vector3 target = new Vector3(offset + 6.5, 64, offset + 4.5);
            WalkerRouteFinder route = f.route(new Vector3(offset + 0.5, 64, offset + 0.5), target);
            route.search();
            assertTrue(route.isFinished());
            assertFalse(route.isSearching());
            assertTrue(route.isReachable());
            assertEquals(target, route.nodes.get(route.nodes.size() - 1).getVector3());
            verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }
    }

    @Test
    void unloadedTerrainNeverLoadsAChunk() {
        Fixture f = new Fixture(0);
        when(f.level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(null);
        WalkerRouteFinder route = f.route(new Vector3(15.5, 64, 15.5), new Vector3(20.5, 64, 20.5));
        route.search();
        assertFalse(route.isReachable());
        verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(f.level, never()).getHighestBlockAt(anyInt(), anyInt());
    }

    @SuppressWarnings("unchecked")
    private static List<Node> closed(WalkerRouteFinder route) throws Exception {
        Field field = WalkerRouteFinder.class.getDeclaredField("closeList");
        field.setAccessible(true);
        return (List<Node>) field.get(route);
    }

    private static boolean unvisited(WalkerRouteFinder route, Vector3 candidate) throws Exception {
        Method method = WalkerRouteFinder.class.getDeclaredMethod("noContainsInClose", Vector3.class);
        method.setAccessible(true);
        return (boolean) method.invoke(route, candidate);
    }

    private static final class CountingVector extends Vector3 {
        static long comparisons;
        CountingVector(double x, double y, double z) { super(x, y, z); }
        @Override public boolean equals(Object other) {
            comparisons++;
            return super.equals(other);
        }
        @Override public int hashCode() { return super.hashCode(); }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final EntityWalking entity = mock(EntityWalking.class);
        final BaseFullChunk chunk = mock(BaseFullChunk.class);
        Fixture(int wall) {
            when(entity.getLevel()).thenReturn(level);
            when(entity.getWidth()).thenReturn(0.6f);
            when(entity.getHeight()).thenReturn(1.8f);
            when(entity.getScale()).thenReturn(1f);
            entity.chunk = chunk;
            when(level.getProvider()).thenReturn(mock(LevelProvider.class));
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(chunk);
            Block stone = mock(Block.class);
            when(stone.getId()).thenReturn(Block.STONE);
            when(stone.getY()).thenReturn(63.0);
            Block air = mock(Block.class);
            when(air.getId()).thenReturn(Block.AIR);
            when(air.canPassThrough()).thenReturn(true);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(false)))
                    .thenAnswer(call -> call.<Integer>getArgument(2) <= 63 ? stone : air);
            when(level.hasCollision(eq(entity), any(), eq(false))).thenAnswer(call -> {
                AxisAlignedBB box = call.getArgument(1);
                return wall > 0 && box.getMinX() < 3 && box.getMaxX() > 2
                        && box.getMinZ() < wall && box.getMaxZ() > -wall;
            });
        }
        WalkerRouteFinder route(Vector3 start, Vector3 target) {
            return new WalkerRouteFinder(entity, start, target);
        }
    }

    public static void main(String[] args) {
        for (int wall : new int[] {0, 1, 2, 3}) {
            for (int offset : new int[] {-16, -8, 0, 8, 16}) {
                for (int distance : new int[] {4, 8, 14}) {
                    Fixture f = new Fixture(wall);
                    WalkerRouteFinder route = f.route(new Vector3(offset + 0.5, 64, 0.5),
                            new Vector3(offset + distance + 0.5, 64, 3.5));
                    route.search();
                    StringBuilder path = new StringBuilder();
                    for (Node node : route.nodes) {
                        Vector3 pos = node.getVector3();
                        path.append(pos.x).append(',').append(pos.y).append(',').append(pos.z).append(';');
                    }
                    System.out.println(wall + ":" + offset + ":" + distance + ":"
                            + route.isReachable() + ":" + path);
                }
            }
        }
    }
}
