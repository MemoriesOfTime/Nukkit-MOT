package cn.nukkit;

import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The spear charge measures how fast the player moves. {@code Player.speed} is only refreshed when a
 * new position arrives, so a player who stopped would keep the speed of his last step; the recent
 * velocity drops to zero after a couple of ticks without movement.
 */
public class PlayerRecentVelocityTest {

    @Test
    void freshStepIsTheVelocityPerTick() {
        // speed holds from - to: a step of +0.56 on z over two ticks.
        Vector3 velocity = Player.recentVelocity(new Vector3(0, 0, -0.56), 100, 2, 101);
        assertEquals(0.28, velocity.z, 1e-9);
        assertEquals(0.0, velocity.x, 1e-9);
    }

    @Test
    void staleStepIsStandingStill() {
        Vector3 velocity = Player.recentVelocity(new Vector3(0, 0, -0.28), 100, 1, 103);
        assertEquals(0.0, velocity.lengthSquared(), 1e-12);
    }

    @Test
    void noMovementYetIsStandingStill() {
        assertEquals(0.0, Player.recentVelocity(null, 100, 1, 100).lengthSquared(), 1e-12);
        assertEquals(0.0, Player.recentVelocity(new Vector3(0, 0, -0.28), -1, 1, 5).lengthSquared(), 1e-12);
    }
}
