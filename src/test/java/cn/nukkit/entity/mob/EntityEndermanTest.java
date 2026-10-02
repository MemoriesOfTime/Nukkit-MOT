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
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class EntityEndermanTest {

    private Level level;

    @BeforeEach
    void initialize() {
        MockServer.init();
        Effect.init();
    }

    @Test
    void projectileDoesNotHurtTheEndermanAndMakesItTryToDodge() {
        EntityEnderman enderman = enderman();
        float health = enderman.getHealth();
        clearInvocations(level);

        EntityDamageEvent hit = new EntityDamageByEntityEvent(mock(Entity.class), enderman,
                EntityDamageEvent.DamageCause.PROJECTILE, 10f);

        assertFalse(enderman.attack(hit));
        assertTrue(hit.isCancelled());
        assertEquals(health, enderman.getHealth(), 0.0001f);
        assertTrue(enderman.isAngry());
        // No loaded chunk around: every attempt looks for a spot and gives up, none are skipped.
        verify(level, times(EntityEnderman.TELEPORT_ATTEMPTS)).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void repeatedArrowsNeverKillTheEnderman() {
        EntityEnderman enderman = enderman();
        for (int i = 0; i < 20; i++) {
            enderman.attack(new EntityDamageByEntityEvent(mock(Entity.class), enderman,
                    EntityDamageEvent.DamageCause.PROJECTILE, 9f));
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

    private EntityEnderman enderman() {
        level = mock(Level.class);
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
