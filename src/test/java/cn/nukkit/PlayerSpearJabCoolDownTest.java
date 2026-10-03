package cn.nukkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server accepts a spear jab once per swing cool down, with a few ticks of jitter: a legit
 * client waits for its own cool down, a spamming one is refused.
 */
public class PlayerSpearJabCoolDownTest {

    @Test
    void firstJabIsAlwaysAccepted() {
        assertTrue(Player.isSpearJabReady(Integer.MIN_VALUE, 0, 19));
        assertTrue(Player.isSpearJabReady(Integer.MIN_VALUE, Integer.MAX_VALUE, 19));
    }

    @Test
    void jabInsideCoolDownIsRefused() {
        assertFalse(Player.isSpearJabReady(100, 100, 19));
        assertFalse(Player.isSpearJabReady(100, 115, 19));
    }

    @Test
    void jabAfterCoolDownWithJitterIsAccepted() {
        assertTrue(Player.isSpearJabReady(100, 116, 19));
        assertTrue(Player.isSpearJabReady(100, 119, 19));
    }
}
