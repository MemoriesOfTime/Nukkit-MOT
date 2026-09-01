package cn.nukkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerFrostWalkerRadiusTest {

    @Test
    void keepsVanillaRadii() {
        assertEquals(3, Player.frostWalkerRadius(1));
        assertEquals(4, Player.frostWalkerRadius(2));
    }

    @Test
    void boundsIllegalLevelsBeforeWalkingBlocks() {
        assertEquals(4, Player.frostWalkerRadius(10));
        assertEquals(4, Player.frostWalkerRadius(32_000));
        assertEquals(4, Player.frostWalkerRadius(Integer.MAX_VALUE));
    }

    @Test
    void neverReturnsLessThanTheFirstVanillaRadius() {
        assertEquals(3, Player.frostWalkerRadius(0));
        assertEquals(3, Player.frostWalkerRadius(Integer.MIN_VALUE));
    }
}
