package cn.nukkit.entity;

import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityWalkingOrientationTest {

    @Test
    void routeFacingBodySurvivesTrackingAPlayerOffTheRoute() {
        EntityWalking entity = Mockito.mock(EntityWalking.class, Mockito.CALLS_REAL_METHODS);
        entity.x = 0;
        entity.y = 0;
        entity.z = 0;
        entity.setRotation(0, 0, 0);

        entity.turnBodyTowardsAndLookAt(90, new Vector3(10, 0, 0));

        assertEquals(30, entity.getYaw(), "body follows the route with the normal turn limit");
        assertEquals(-90, entity.getHeadYaw(), "head keeps tracking the player");
        assertEquals(0, entity.getPitch());
    }

    @Test
    void routeFacingWithoutALiveTargetTurnsBodyAndHeadTogether() {
        EntityWalking entity = Mockito.mock(EntityWalking.class, Mockito.CALLS_REAL_METHODS);
        entity.setRotation(0, 0, 0);

        entity.turnBodyTowardsAndLookAt(90, null);

        assertEquals(30, entity.getYaw());
        assertEquals(30, entity.getHeadYaw());
    }
}
