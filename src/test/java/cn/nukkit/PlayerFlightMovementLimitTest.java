package cn.nukkit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class PlayerFlightMovementLimitTest {

    private Player player;
    private AdventureSettings settings;

    @BeforeEach
    void setUp() {
        player = mock(Player.class, CALLS_REAL_METHODS);
        settings = new AdventureSettings(player);
        player.adventureSettings = settings;
    }

    @Test
    void scalesSquaredLimitsForAuthorizedFastFlight() {
        settings.set(AdventureSettings.Type.ALLOW_FLIGHT, true);
        settings.set(AdventureSettings.Type.FLYING, true);
        player.setFlySpeed(1.0f);

        assertEquals(40_000d, player.movementSanityLimitSquared(100d), 0.01d);
        assertEquals(90_000d, player.movementSanityLimitSquared(225d), 0.01d);
    }

    @Test
    void permissionWithoutActiveFlightKeepsVanillaLimit() {
        settings.set(AdventureSettings.Type.ALLOW_FLIGHT, true);
        player.setFlySpeed(1.0f);

        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);
    }

    @Test
    void activeFlightWithoutPermissionKeepsVanillaLimit() {
        settings.set(AdventureSettings.Type.FLYING, true);
        player.setFlySpeed(1.0f);

        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);
    }

    @Test
    void vanillaAndInvalidSpeedsKeepVanillaLimit() {
        settings.set(AdventureSettings.Type.ALLOW_FLIGHT, true);
        settings.set(AdventureSettings.Type.FLYING, true);

        player.setFlySpeed(Player.DEFAULT_FLY_SPEED);
        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);

        player.setFlySpeed(Float.POSITIVE_INFINITY);
        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);

        player.setFlySpeed(Float.MAX_VALUE);
        assertTrue(Double.isFinite(player.movementSanityLimitSquared(100d)));
    }
}
