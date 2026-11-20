package cn.nukkit.entity.route;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityWalking;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkerZeroDistanceP13Test {
    @Test
    void anotherEntityAtTheExactStartProducesOneRootNodeWithoutTerrainWork() {
        for (Vector3 start : new Vector3[] {
                new Vector3(0.5, 64, 0.5), new Vector3(-8.5, -63, -16.5), new Vector3(8.5, 128, 16.5)}) {
            Fixture f = new Fixture(start, start);
            assertNotSame(f.walker, f.target, "native target is another entity, not self");
            assertDoesNotThrow(() -> f.runSearch());
            assertTrue(f.route.isFinished());
            assertFalse(f.route.isSearching());
            assertFalse(f.route.hasPendingSearch());
            assertTrue(f.route.isReachable());
            assertEquals(1, f.route.nodes.size(), "do not duplicate the root for an empty walk");
            assertEquals(start, f.route.getCurrentNode().getVector3());
            assertFalse(f.route.hasNext());
            verifyNoInteractions(f.level);
        }
    }

    @Test
    void differentPointsInTheSameGridCellKeepBothEndpoints() {
        Vector3 start = new Vector3(0.2, 64, 0.3);
        Vector3 target = new Vector3(0.8, 64, 0.9);
        Fixture f = new Fixture(start, target);
        assertDoesNotThrow(() -> f.runSearch());
        assertEquals(2, f.route.nodes.size());
        assertEquals(start, f.route.getCurrentNode().getVector3());
        assertTrue(f.route.hasNext());
        assertEquals(target, f.route.next());
        assertFalse(f.route.hasNext());
        verifyNoInteractions(f.level);
    }

    @Test
    void repeatedNativeSearchesAtTheSamePointDoNotGenerateExceptionWork() {
        Vector3 position = new Vector3(0.5, 64, 0.5);
        Fixture f = new Fixture(position, position);
        int failures = 0;
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                f.runSearch();
            } catch (NullPointerException expectedOldFailure) {
                failures++;
            }
        }
        System.out.println("p13 repeated_zero_route_errors=" + failures + " searches=100");
        assertEquals(0, failures);
        assertEquals(1, f.route.nodes.size());
        assertFalse(f.route.hasPendingSearch());
        assertFalse(f.route.isSearching());
        verifyNoInteractions(f.level);
    }

    private static final class Fixture {
        final EntityWalking walker = mock(EntityWalking.class);
        final Entity target = mock(Entity.class, CALLS_REAL_METHODS);
        final Level level = mock(Level.class);
        final WalkerRouteFinder route;

        Fixture(Vector3 start, Vector3 destination) {
            target.x = destination.x;
            target.y = destination.y;
            target.z = destination.z;
            when(walker.getLevel()).thenReturn(level);
            when(walker.getTarget()).thenReturn(target);
            route = new WalkerRouteFinder(walker, start);
        }

        void runSearch() {
            RouteFinderSearchTask task = new RouteFinderSearchTask(route);
            assertTrue(task.tryQueue());
            task.run();
        }
    }
}
