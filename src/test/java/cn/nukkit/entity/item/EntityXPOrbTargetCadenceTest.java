package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityXPOrbTargetCadenceTest {
    private static EntityXPOrb orb(Level level, Map<Integer, Player> viewers, long id) {
        EntityXPOrb orb = mock(EntityXPOrb.class);
        when(orb.onUpdate(anyInt())).thenCallRealMethod();
        when(orb.isAlive()).thenReturn(true);
        when(orb.getViewers()).thenReturn(viewers);
        when(orb.getId()).thenReturn(id);
        doCallRealMethod().when(orb).updateClosestPlayer(anyInt());
        orb.level = level;
        orb.y = 64;
        return orb;
    }

    private static Player player(Level level, Entity orb) {
        Player player = mock(Player.class);
        player.level = level;
        player.x = 4;
        player.y = 64;
        when(player.isAlive()).thenReturn(true);
        when(player.canPickupXP()).thenReturn(true);
        when(player.distanceSquared(orb)).thenReturn(16d);
        return player;
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void firstSearchIsImmediateAndANewViewerWaitsAtMostOneTick(long id) {
        Level level = mock(Level.class);
        Map<Integer, Player> viewers = new LinkedHashMap<>();
        EntityXPOrb orb = orb(level, viewers, id);
        orb.onUpdate(1);
        verify(orb).getViewers();
        Player player = player(level, orb);
        viewers.put(1, player);
        orb.onUpdate(2);
        if (orb.closestPlayer == null) orb.onUpdate(3);
        assertSame(player, orb.closestPlayer);
    }

    @Test
    void skippedTicksNeverStarveTheSearch() {
        Level level = mock(Level.class);
        Map<Integer, Player> viewers = new LinkedHashMap<>();
        EntityXPOrb orb = orb(level, viewers, 1);
        orb.onUpdate(1);
        Player player = player(level, orb);
        viewers.put(1, player);
        orb.onUpdate(4);
        assertSame(player, orb.closestPlayer);
    }

    @Test
    void invalidTargetClearsBeforeAttractionEvenBetweenSearchTicks() {
        Level level = mock(Level.class);
        Map<Integer, Player> viewers = new LinkedHashMap<>();
        EntityXPOrb orb = orb(level, viewers, 1);
        Player player = player(level, orb);
        viewers.put(1, player);
        orb.onUpdate(1);
        assertSame(player, orb.closestPlayer);
        player.closed = true;
        orb.motionX = orb.motionY = orb.motionZ = 0;
        orb.onUpdate(2);
        assertNull(orb.closestPlayer);
        assertEquals(0, orb.motionX);
        verify(orb, times(2)).updateMovement();
        verify(orb, times(2)).entityBaseTick(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"closed", "dead", "other-level", "spectator", "pickup-disabled", "far"})
    void scanSkipsInvalidFirstViewerAndFindsTheEligibleSecond(String reason) {
        Level level = mock(Level.class);
        Map<Integer, Player> viewers = new LinkedHashMap<>();
        EntityXPOrb orb = orb(level, viewers, 1);
        Player invalid = player(level, orb);
        switch (reason) {
            case "closed" -> invalid.closed = true;
            case "dead" -> when(invalid.isAlive()).thenReturn(false);
            case "other-level" -> invalid.level = mock(Level.class);
            case "spectator" -> when(invalid.isSpectator()).thenReturn(true);
            case "pickup-disabled" -> when(invalid.canPickupXP()).thenReturn(false);
            case "far" -> when(invalid.distanceSquared(orb)).thenReturn(65d);
        }
        Player eligible = player(level, orb);
        viewers.put(1, invalid);
        viewers.put(2, eligible);
        orb.onUpdate(1);
        assertSame(eligible, orb.closestPlayer);
        assertTrue(orb.motionX > 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 500})
    void denseUnclaimedOrbsHalveViewerDistanceChecks(int count) {
        Level level = mock(Level.class);
        int visits = 0;
        Map<Integer, Player> viewers = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            Player player = mock(Player.class);
            player.level = level;
            when(player.isAlive()).thenReturn(true);
            when(player.canPickupXP()).thenReturn(true);
            when(player.distanceSquared(any(Entity.class))).thenReturn(100d);
            viewers.put(i, player);
        }
        for (int id = 0; id < count; id++) {
            EntityXPOrb orb = orb(level, viewers, id);
            // First acquisition remains immediate; begin measuring after that one-time warm-up.
            orb.onUpdate(1);
            for (Player player : viewers.values()) clearInvocations(player);
            for (int tick = 2; tick <= 21; tick++) orb.onUpdate(tick);
            for (Player player : viewers.values()) {
                verify(player, times(10)).distanceSquared(orb);
                visits += 10;
            }
        }
        assertEquals(count * 20 * 10, visits);
    }
}
