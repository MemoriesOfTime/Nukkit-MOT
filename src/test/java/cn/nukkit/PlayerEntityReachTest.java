package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerEntityReachTest {
    @Test
    void nearbyFaceOfLargeBodyCanBeHitDespiteDistantCentre() throws Exception {
        assertTrue(canReach(entity(20, 2, 30, 0, 8), 3));
    }

    @Test
    void attackerInsideLargeBodyCanHitIt() throws Exception {
        assertTrue(canReach(entity(20, -2, 30, 0, 8), 3));
    }

    @Test
    void nearestFaceBeyondReachStillRefusesTheHit() throws Exception {
        assertFalse(canReach(entity(20, 3.01, 30, 0, 8), 3));
    }

    @Test
    void nearestFaceBehindAttackerStillRefusesTheHit() throws Exception {
        assertFalse(canReach(entity(-8, -12, -2, 0, 8), 3));
    }

    @Test
    void verticalDistanceIsMeasuredFromTheAttackersEyes() throws Exception {
        assertTrue(canReach(entity(1, 0, 2, 4.5, 8), 3));
        assertFalse(canReach(entity(1, 0, 2, 4.7, 8), 3));
    }

    @Test
    void playerTargetsKeepCentreBasedReachEvenWithALargeBox() throws Exception {
        Player target = mock(Player.class);
        target.x = 4;
        target.boundingBox = new SimpleAxisAlignedBB(0, 0, -1, 10, 8, 1);
        assertFalse(canReach(target, 3));
        target.x = 3;
        assertTrue(canReach(target, 3));
    }

    @Test
    void plainPositionsAndEntitiesWithoutABoxKeepTheOldChecks() throws Exception {
        assertTrue(canReach(new Vector3(3, 0, 0), 3));
        assertFalse(canReach(new Vector3(3.01, 0, 0), 3));
        Entity target = mock(Entity.class);
        target.x = 3;
        assertTrue(canReach(target, 3));
        target.x = -3;
        assertFalse(canReach(target, 3));
    }

    private static Entity entity(double centreX, double minX, double maxX, double minY, double maxY) {
        Entity target = mock(Entity.class);
        target.x = centreX;
        target.boundingBox = new SimpleAxisAlignedBB(minX, minY, -1, maxX, maxY, 1);
        return target;
    }

    private static boolean canReach(Vector3 target, double reach) throws Exception {
        Player player = mock(Player.class);
        when(player.getEyeHeight()).thenReturn(1.62f);
        when(player.getDirectionPlane()).thenReturn(new Vector2(1, 0));
        doCallRealMethod().when(player).distanceSquared(any(Vector3.class));
        Method method = Player.class.getDeclaredMethod("canInteractEntity", Vector3.class, double.class);
        method.setAccessible(true);
        return (boolean) method.invoke(player, target, reach);
    }
}
