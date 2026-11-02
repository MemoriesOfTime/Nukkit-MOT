package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerMeleeReachTest {
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
        when(player.getDirectionVector()).thenReturn(new Vector3(1, 0, 0));
        player.level = mock(Level.class);
        doCallRealMethod().when(player).distanceSquared(any(Vector3.class));
        Method method = Player.class.getDeclaredMethod("canInteractEntity", Vector3.class, double.class);
        method.setAccessible(true);
        return (boolean) method.invoke(player, target, reach);
    }

    private static final double REACH = 5;

    /** Full stone cubes at the listed positions, air everywhere else. */
    private static MeleeLineOfSight.Obstacles stone(int[]... cells) {
        Set<Long> solid = new HashSet<>();
        for (int[] c : cells) {
            solid.add(key(c[0], c[1], c[2]));
        }
        return (x, y, z) -> solid.contains(key(x, y, z))
                ? new SimpleAxisAlignedBB(x, y, z, x + 1, y + 1, z + 1)
                : null;
    }

    private static long key(int x, int y, int z) {
        return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
    }

    // A 6x6x6 body (a dragon at scale 2) whose near face is at x = 3.
    private static final AxisAlignedBB BODY = new SimpleAxisAlignedBB(3, 64, -3, 9, 70, 3);

    @Test
    void openAirHitReachesTheBody() {
        assertTrue(MeleeLineOfSight.clear(0.5, 65.62, 0.5, 1, 0, 0, BODY, REACH, stone()));
    }

    @Test
    void wallBetweenTheEyesAndTheBodyRefusesTheHit() {
        assertFalse(MeleeLineOfSight.clear(0.5, 65.62, 0.5, 1, 0, 0, BODY, REACH, stone(new int[]{2, 65, 0})));
    }

    @Test
    void rayMissingTheBodyChecksTheNearestPointAndStillSeesTheWall() {
        // Rotation a tick behind: the look ray goes up past the body, the nearest point is behind the wall.
        assertFalse(MeleeLineOfSight.clear(0.5, 65.62, 0.5, 0, 1, 0, BODY, REACH, stone(new int[]{2, 65, 0})));
        assertTrue(MeleeLineOfSight.clear(0.5, 65.62, 0.5, 0, 1, 0, BODY, REACH, stone()));
    }

    @Test
    void blockBesideTheLineDoesNotRefuse() {
        assertTrue(MeleeLineOfSight.clear(0.5, 65.62, 0.5, 1, 0, 0, BODY, REACH,
                stone(new int[]{2, 66, 0}, new int[]{2, 64, 0}, new int[]{2, 65, 1}, new int[]{0, 64, 0})));
    }

    @Test
    void edgeOfTheBodyStillCounts() {
        // Aiming at the top corner of the near face (3, 69, 3), four and a half blocks away.
        assertTrue(MeleeLineOfSight.clear(0.5, 65.62, 0.5, 2.5, 3.37, 2.5, BODY, REACH, stone(new int[]{3, 63, 0})));
    }

    @Test
    void insideTheBodyLookingIntoOpenAirIsAHit() {
        AxisAlignedBB around = new SimpleAxisAlignedBB(-3, 63, -3, 3, 69, 3);
        assertTrue(MeleeLineOfSight.clear(2.5, 65.62, 0.5, 0, 1, 0, around, REACH, stone(new int[]{0, 65, 0})));
    }

    @Test
    void insideTheBodyLookingAtAWallIsNotAHit() {
        // The player stands in a pit next to the body and aims at the stone hump in front of him.
        AxisAlignedBB around = new SimpleAxisAlignedBB(-3, 63, -3, 3, 69, 3);
        assertFalse(MeleeLineOfSight.clear(2.5, 65.62, 0.5, -1, 0, 0, around, REACH, stone(new int[]{1, 65, 0})));
    }

    @Test
    void eyesAgainstABlockFaceAreNotBlockedByIt() {
        // The eyes rest exactly on the face of the block behind the player.
        assertTrue(MeleeLineOfSight.clear(1.0, 65.62, 0.5, 1, 0, 0, BODY, REACH, stone(new int[]{0, 65, 0})));
    }

    @Test
    void diagonalWallRefuses() {
        AxisAlignedBB body = new SimpleAxisAlignedBB(3, 64, 3, 5, 66, 5);
        assertFalse(MeleeLineOfSight.clear(0.5, 65.5, 0.5, 1, 0, 1, body, REACH, stone(new int[]{2, 65, 2})));
        assertTrue(MeleeLineOfSight.clear(0.5, 65.5, 0.5, 1, 0, 1, body, REACH, stone(new int[]{2, 65, 0})));
    }
}
