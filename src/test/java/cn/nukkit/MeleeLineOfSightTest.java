package cn.nukkit;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeLineOfSightTest {

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
