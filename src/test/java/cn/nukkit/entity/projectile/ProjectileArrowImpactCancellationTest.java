package cn.nukkit.entity.projectile;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native impact callbacks; only the server/world, victim and close/networking are mocked. */
class ProjectileArrowImpactCancellationTest {
    private EntityArrow arrow;
    private EntityLiving victim;
    private PluginManager plugins;

    @BeforeAll static void boot() { MockServer.init(); Effect.init(); }
    @BeforeEach void setup() throws Exception {
        MockServer.init();
        plugins = Server.getInstance().getPluginManager();
        reset(plugins);
        arrow = mock(EntityArrow.class, CALLS_REAL_METHODS);
        Field server = Entity.class.getDeclaredField("server");
        server.setAccessible(true); server.set(arrow, Server.getInstance());
        arrow.level = mock(Level.class);
        arrow.namedTag = new CompoundTag();
        Field metadata = Entity.class.getDeclaredField("dataProperties");
        metadata.setAccessible(true);
        metadata.set(arrow, new cn.nukkit.entity.data.EntityMetadata().putLong(Entity.DATA_FLAGS, 0));
        arrow.motionX = 2.5;
        arrow.mobEffects = new Int2ObjectOpenHashMap<>();
        arrow.mobEffects.put(Effect.POISON, Effect.getEffect(Effect.POISON).setDuration(80));
        victim = mock(EntityLiving.class);
        when(victim.attack(any(EntityDamageEvent.class))).thenReturn(true);
        doNothing().when(arrow).close();
    }

    @Test void cancelledHitMustNotApplyTippedArrowEffect() {
        doAnswer(call -> { ((ProjectileHitEvent)call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(ProjectileHitEvent.class));
        arrow.onCollideWithEntity(victim);
        verify(victim, never()).attack(any(EntityDamageEvent.class));
        verify(victim, never()).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ARROW));
        assertFalse(arrow.hadCollision);
    }

    @Test void cancelledHitWithOldBlockCollisionCannotApplyEffect() {
        arrow.hadCollision = true;
        doAnswer(call -> { ((ProjectileHitEvent) call.getArgument(0)).setCancelled(); return null; })
                .when(plugins).callEvent(any(ProjectileHitEvent.class));
        arrow.onCollideWithEntity(victim);
        verify(victim, never()).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ARROW));
    }

    @Test void rejectedPhysicalHitCannotApplyEffect() {
        when(victim.attack(any(EntityDamageEvent.class))).thenReturn(false);
        arrow.onCollideWithEntity(victim);
        verify(victim, never()).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ARROW));
    }

    @Test void cleanupCloseInsideAcceptedHitRetainsPhysicalDamageAndEffect() {
        doAnswer(call -> { arrow.closed = true; return null; })
                .when(plugins).callEvent(any(ProjectileHitEvent.class));
        arrow.onCollideWithEntity(victim);
        verify(victim, times(1)).attack(any(EntityDamageEvent.class));
        verify(victim, times(1)).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ARROW));
    }

    @Test void acceptedHitRetainsTippedArrowEffectAndDuration() {
        arrow.onCollideWithEntity(victim);
        verify(victim, times(1)).attack(any(EntityDamageEvent.class));
        var effect = org.mockito.ArgumentCaptor.forClass(Effect.class);
        verify(victim, times(1)).addEffect(effect.capture(), eq(EntityPotionEffectEvent.Cause.ARROW));
        assertEquals(Effect.POISON, effect.getValue().getId());
        assertEquals(10, effect.getValue().getDuration());
        assertTrue(arrow.hadCollision);
        verify(arrow, times(1)).close();
    }

    @Test void acceptedFullEffectArrowKeepsWholeDuration() {
        arrow.setFullEffect(true);
        arrow.onCollideWithEntity(victim);
        var effect = org.mockito.ArgumentCaptor.forClass(Effect.class);
        verify(victim).addEffect(effect.capture(), eq(EntityPotionEffectEvent.Cause.ARROW));
        assertEquals(80, effect.getValue().getDuration());
    }
}
