package cn.nukkit.entity.mob;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityEndermanTest {

    @BeforeEach
    void initialize() {
        MockServer.init();
        Effect.init();
    }

    @Test
    void projectileDoesNotHurtTheEndermanAndMakesItTryToDodge() {
        EntityEnderman enderman = spy(enderman());
        doNothing().when(enderman).teleport();
        PluginManager pluginManager = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            EntityDamageEvent event = invocation.getArgument(0);
            assertFalse(event.isCancelled());
            return null;
        }).when(pluginManager).callEvent(any(EntityDamageEvent.class));
        float health = enderman.getHealth();
        EntityDamageEvent hit = projectileHit(enderman, 10f);

        assertFalse(enderman.attack(hit));
        assertTrue(hit.isCancelled());
        assertEquals(health, enderman.getHealth(), 0.0001f);
        assertTrue(enderman.isAngry());
        verify(enderman).teleport();
        verify(pluginManager).callEvent(hit);
    }

    @Test
    void pluginCancellationPreventsTheProjectileDodge() {
        EntityEnderman enderman = spy(enderman());
        doNothing().when(enderman).teleport();
        PluginManager pluginManager = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            EntityDamageEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(EntityDamageEvent.class));
        float health = enderman.getHealth();
        EntityDamageEvent hit = projectileHit(enderman, 10f);

        assertTrue(enderman.attack(hit));
        assertTrue(hit.isCancelled());
        assertEquals(health, enderman.getHealth(), 0.0001f);
        assertFalse(enderman.isAngry());
        verify(enderman, never()).teleport();
        verify(pluginManager).callEvent(hit);
    }

    @Test
    void repeatedProjectilesNeverKillTheEnderman() {
        EntityEnderman enderman = enderman();
        for (int i = 0; i < 20; i++) {
            enderman.attack(projectileHit(enderman, 9f));
        }

        assertTrue(enderman.isAlive());
        assertEquals(enderman.getMaxHealth(), enderman.getHealth(), 0.0001f);
    }

    @Test
    void meleeHitStillHurtsTheEnderman() {
        EntityEnderman enderman = enderman();
        float health = enderman.getHealth();
        EntityDamageEvent hit = new EntityDamageByEntityEvent(mock(Entity.class), enderman,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6f);

        assertTrue(enderman.attack(hit));
        assertFalse(hit.isCancelled());
        assertEquals(health - 6f, enderman.getHealth(), 0.0001f);
        assertTrue(enderman.isAngry());
    }

    private static EntityDamageEvent projectileHit(EntityEnderman enderman, float damage) {
        return new EntityDamageByEntityEvent(mock(Entity.class), enderman,
                EntityDamageEvent.DamageCause.PROJECTILE, damage);
    }

    private static EntityEnderman enderman() {
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(MockServer.get());
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(0, 0)).thenReturn(Collections.emptyMap());
        when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
        level.isBeingConverted = true;
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        return new EntityEnderman(chunk, Entity.getDefaultNBT(new Vector3(0.5, 64, 0.5)));
    }
}
