package cn.nukkit.entity;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CriticalHitConditionsTest {

    @Test
    void fallingAttackerLandsACritical() throws Exception {
        assertTrue(canCriticalHit(attacker(false, false)));
    }

    @Test
    void sprintingAttackerIsDeniedTheCritical() throws Exception {
        assertFalse(canCriticalHit(attacker(true, false)));
    }

    @Test
    void flyingAttackerIsDeniedTheCritical() throws Exception {
        assertFalse(canCriticalHit(attacker(false, true)));
    }

    private static Player attacker(boolean sprinting, boolean flying) {
        Player player = mock(Player.class);
        player.speed = new Vector3(0, 1, 0);
        AdventureSettings settings = mock(AdventureSettings.class);
        when(settings.get(AdventureSettings.Type.FLYING)).thenReturn(flying);
        lenient().when(player.getAdventureSettings()).thenReturn(settings);
        lenient().when(player.isSprinting()).thenReturn(sprinting);
        lenient().when(player.isOnGround()).thenReturn(false);
        lenient().when(player.getLevel()).thenReturn(mock(Level.class));
        return player;
    }

    private static boolean canCriticalHit(Player player) throws Exception {
        Method method = Entity.class.getDeclaredMethod("canCriticalHit", Player.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, player);
    }
}
