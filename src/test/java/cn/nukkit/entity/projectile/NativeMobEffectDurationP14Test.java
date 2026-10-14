package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityCaveSpider;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native attack/collision dispatch against Mojang bedrock-samples 46ba6ea9, not a duration model. */
class NativeMobEffectDurationP14Test {
    @BeforeAll static void effects() { Effect.init(); }

    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "2,140", "3,300"})
    void caveSpiderPoisonMatchesDifficulty(int difficulty, int ticks) throws Exception {
        List<Effect> effects = new ArrayList<>();
        Player target = player(effects, true, false);
        EntityCaveSpider spider = spider(difficulty);
        spider.attackEntity(target);
        assertEffect(effects, ticks, Effect.POISON, 0);
        verify(target).attack(any(EntityDamageEvent.class));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true"})
    void caveSpiderDeniedDamageNeverPoisons(boolean accepted, boolean cancelled) throws Exception {
        List<Effect> effects = new ArrayList<>();
        spider(3).attackEntity(player(effects, accepted, cancelled));
        assertTrue(effects.isEmpty());
    }

    @Test
    void caveSpiderKeepsPoisonImmunity() throws Exception {
        EntityCaveSpider spider = spider(3);
        doCallRealMethod().when(spider).canBeAffected(Effect.POISON);
        assertFalse(spider.canBeAffected(Effect.POISON));
    }

    @ParameterizedTest
    @CsvSource({"false,0,0", "false,1,0", "false,2,200", "false,3,800",
            "true,0,0", "true,1,0", "true,2,200", "true,3,800"})
    void bothSkullsApplyWitherTwoAtVanillaDuration(boolean blue, int difficulty, int ticks)
            throws Exception {
        List<Effect> effects = new ArrayList<>();
        Entity target = target(effects, true);
        EntityWitherSkull skull = skull(blue, difficulty, false);
        skull.onCollideWithEntity(target);
        assertEffect(effects, ticks, Effect.WITHER, 1);
        verify(target).attack(any(EntityDamageEvent.class));
        verify(skull).onHit();
        assertTrue(skull.hadCollision);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusedSkullDamageNeverAddsWither(boolean blue) throws Exception {
        List<Effect> effects = new ArrayList<>();
        EntityWitherSkull skull = skull(blue, 3, false);
        skull.onCollideWithEntity(target(effects, false));
        assertTrue(effects.isEmpty());
        assertFalse(skull.hadCollision);
        verify(skull, never()).onHit();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void cancelledSkullImpactDoesNotInheritAnEarlierAcceptedCollision(boolean blue, boolean previous)
            throws Exception {
        List<Effect> effects = new ArrayList<>();
        Entity target = target(effects, true);
        EntityWitherSkull skull = skull(blue, 3, true);
        skull.hadCollision = previous;
        skull.onCollideWithEntity(target);
        assertTrue(effects.isEmpty());
        assertEquals(previous, skull.hadCollision);
        verify(target, never()).attack(any(EntityDamageEvent.class));
        verify(skull, never()).onHit();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusedSkullDamageDoesNotInheritAnEarlierAcceptedCollision(boolean blue) throws Exception {
        List<Effect> effects = new ArrayList<>();
        EntityWitherSkull skull = skull(blue, 3, false);
        skull.hadCollision = true;
        skull.onCollideWithEntity(target(effects, false));
        assertTrue(effects.isEmpty());
        assertTrue(skull.hadCollision);
        verify(skull, never()).onHit();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nestedAcceptedHitDoesNotPoisonAnOuterCancelledImpact(boolean blue) throws Exception {
        List<Effect> outerEffects = new ArrayList<>();
        List<Effect> innerEffects = new ArrayList<>();
        Entity outer = target(outerEffects, true);
        Entity inner = target(innerEffects, true);
        EntityWitherSkull skull = skull(blue, 3, false);
        PluginManager manager = skull.getServer().getPluginManager();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof ProjectileHitEvent hit
                    && hit.getMovingObjectPosition().entityHit == outer) {
                skull.onCollideWithEntity(inner);
                hit.setCancelled(true);
            }
            return null;
        }).when(manager).callEvent(any());
        skull.onCollideWithEntity(outer);
        assertTrue(outerEffects.isEmpty());
        assertEffect(innerEffects, 800, Effect.WITHER, 1);
        verify(outer, never()).attack(any(EntityDamageEvent.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nestedAcceptedHitDoesNotPoisonAnOuterRefusedDamage(boolean blue) throws Exception {
        List<Effect> outerEffects = new ArrayList<>();
        List<Effect> innerEffects = new ArrayList<>();
        Entity outer = target(outerEffects, false);
        Entity inner = target(innerEffects, true);
        EntityWitherSkull skull = skull(blue, 3, false);
        when(outer.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
            skull.onCollideWithEntity(inner);
            return false;
        });
        skull.onCollideWithEntity(outer);
        assertTrue(outerEffects.isEmpty());
        assertEffect(innerEffects, 800, Effect.WITHER, 1);
    }

    private static EntityCaveSpider spider(int difficulty) throws Exception {
        EntityCaveSpider spider = mock(EntityCaveSpider.class);
        field(spider, "server", server(difficulty, false));
        field(spider, "attackDelay", 24);
        when(spider.getDamage()).thenReturn(3);
        when(spider.distanceSquared(any(Entity.class))).thenReturn(0.0);
        doCallRealMethod().when(spider).attackEntity(any(Entity.class));
        return spider;
    }

    private static EntityWitherSkull skull(boolean blue, int difficulty, boolean cancel) throws Exception {
        // The helper is absent on the baseline; this name also exercises its real body after patching.
        var settings = withSettings().defaultAnswer(call ->
                call.getMethod().getName().equals("onCollideWithEntityAccepted")
                        ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
        EntityWitherSkull skull = blue
                ? mock(EntityBlueWitherSkull.class, settings) : mock(EntityWitherSkull.class, settings);
        Server server = server(difficulty, cancel);
        field(skull, "server", server);
        when(skull.getServer()).thenReturn(server);
        when(skull.getResultDamage()).thenReturn(8);
        doCallRealMethod().when(skull).onCollideWithEntity(any(Entity.class));
        return skull;
    }

    private static Server server(int difficulty, boolean cancel) {
        Server server = mock(Server.class);
        when(server.getDifficulty()).thenReturn(difficulty);
        PluginManager manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        doAnswer(call -> {
            if (cancel && call.getArgument(0) instanceof ProjectileHitEvent hit) hit.setCancelled(true);
            return null;
        }).when(manager).callEvent(any());
        return server;
    }

    private static Player player(List<Effect> effects, boolean accepted, boolean cancel) {
        Player target = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(target.getInventory()).thenReturn(inventory);
        when(inventory.getArmorContents()).thenReturn(Item.EMPTY_ARRAY);
        when(target.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
            ((EntityDamageEvent) call.getArgument(0)).setCancelled(cancel);
            return accepted;
        });
        capture(target, effects);
        return target;
    }

    private static Entity target(List<Effect> effects, boolean accepted) {
        Entity target = mock(Entity.class);
        when(target.attack(any(EntityDamageEvent.class))).thenReturn(accepted);
        capture(target, effects);
        return target;
    }

    private static void capture(Entity target, List<Effect> effects) {
        doAnswer(call -> { effects.add(call.getArgument(0)); return null; })
                .when(target).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ATTACK));
    }

    private static void assertEffect(List<Effect> effects, int ticks, int id, int amplifier) {
        if (ticks == 0) {
            assertTrue(effects.isEmpty());
        } else {
            assertEquals(1, effects.size());
            assertEquals(id, effects.get(0).getId());
            assertEquals(ticks, effects.get(0).getDuration());
            assertEquals(amplifier, effects.get(0).getAmplifier());
        }
    }

    private static void field(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
