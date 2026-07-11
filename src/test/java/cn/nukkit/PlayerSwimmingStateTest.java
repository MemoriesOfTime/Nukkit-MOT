package cn.nukkit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class PlayerSwimmingStateTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void swimmingResetsWhenPlayerNotInWater() {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        doNothing().when(player).setDataFlag(anyInt(), anyInt(), anyBoolean());
        doNothing().when(player).recalculateBoundingBox(anyBoolean());
        doReturn(false).when(player).isInsideOfWater();

        player.setSwimming(true);
        assertTrue(player.isSwimming(), "Precondition: player should be swimming");

        player.checkSwimmingState();

        assertFalse(player.isSwimming(), "Swimming should be reset when not in water");
    }

    @Test
    void swimmingPreservedWhenPlayerInWater() {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        doNothing().when(player).setDataFlag(anyInt(), anyInt(), anyBoolean());
        doNothing().when(player).recalculateBoundingBox(anyBoolean());
        doReturn(true).when(player).isInsideOfWater();

        player.setSwimming(true);
        assertTrue(player.isSwimming(), "Precondition: player should be swimming");

        player.checkSwimmingState();

        assertTrue(player.isSwimming(), "Swimming should be preserved when in water");
    }
}
