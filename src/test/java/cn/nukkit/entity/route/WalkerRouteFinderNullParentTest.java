package cn.nukkit.entity.route;

import cn.nukkit.entity.EntityWalking;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalkerRouteFinderNullParentTest {
    @Test
    void exactDestinationReturnsSingleRootNode() {
        Vector3 start = new Vector3(0.5, 64, 0.5);
        EntityWalking entity = mock(EntityWalking.class);
        when(entity.getLevel()).thenReturn(mock(Level.class));
        WalkerRouteFinder routeFinder = new WalkerRouteFinder(entity, start, start.clone());

        routeFinder.search();

        assertTrue(routeFinder.isFinished());
        assertFalse(routeFinder.isSearching());
        assertEquals(start, routeFinder.getCurrentNode().getVector3());
        assertNull(routeFinder.getCurrentNode().getParent());
        assertFalse(routeFinder.hasNext());
    }
}
