package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.event.entity.EntityDamageBlockedEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native arrow dispatch and native shield decision; event delivery and target storage are mocked. */
class NativeArrowEffectAcceptanceP14Test {
    @BeforeAll static void effects() { Effect.init(); }

    @Test void ordinaryArrowKeepsAcceptedCollisionLifecycle() throws Exception {
        Fixture f = new Fixture(Effect.NO_EFFECT);
        f.arrow.onCollideWithEntity(f.target(true, false));
        assertEquals(1, f.attacks.size());
        assertEquals(EntityDamageEvent.DamageCause.PROJECTILE, f.attacks.get(0).getCause());
        assertTrue(f.effects.isEmpty());
        assertTrue(f.heals.isEmpty());
        assertTrue(f.arrow.hadCollision);
        verify(f.arrow).onHit();
        verify(f.arrow).close();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptedTimedArrowRetainsDurationAndPayload(boolean full) throws Exception {
        Fixture f = new Fixture(Effect.POISON);
        field(f.arrow, "isFullEffect", full);
        f.arrow.onCollideWithEntity(f.target(true, false));
        assertEquals(1, f.effects.size());
        assertEquals(Effect.POISON, f.effects.get(0).getId());
        assertEquals(full ? 160 : 20, f.effects.get(0).getDuration());
        assertEquals(1, f.effects.get(0).getAmplifier());
        assertEquals(160, f.payload.get(Effect.POISON).getDuration());
        verify(f.arrow).onHit();
        verify(f.arrow).close();
    }

    @ParameterizedTest @CsvSource({"6,false,4", "6,true,6", "7,false,6", "7,true,4"})
    void acceptedInstantArrowsKeepLivingAndUndeadSemantics(int id, boolean undead, int amount)
            throws Exception {
        Fixture f = new Fixture(id);
        f.arrow.onCollideWithEntity(f.target(true, undead));
        boolean harms = (id == Effect.INSTANT_DAMAGE) != undead;
        if (harms) {
            assertEquals(2, f.attacks.size());
            assertEquals(EntityDamageEvent.DamageCause.MAGIC, f.attacks.get(1).getCause());
            assertEquals(amount, f.attacks.get(1).getDamage());
            assertTrue(f.heals.isEmpty());
        } else {
            assertEquals(1, f.attacks.size());
            assertEquals(1, f.heals.size());
            assertEquals(amount, f.heals.get(0).getAmount());
        }
        assertTrue(f.effects.isEmpty());
        verify(f.arrow).onHit();
        verify(f.arrow).close();
    }

    @Test void legacyHostWhitelistKeepsAcceptedTimedEffect() throws Exception {
        Fixture f = new Fixture(Effect.POISON, false);
        f.arrow.onCollideWithEntity(f.target(true, false));
        assertEquals(1, f.attacks.size());
        assertEquals(1, f.effects.size());
        assertEquals(20, f.effects.get(0).getDuration());
        assertTrue(f.arrow.hadCollision);
        verify(f.arrow).onHit();
        verify(f.arrow).close();
    }

    @ParameterizedTest @ValueSource(ints = {6, 7, 19})
    void refusedPhysicalDamageNeverAppliesArrowEffects(int id) throws Exception {
        Fixture f = new Fixture(id);
        f.arrow.onCollideWithEntity(f.target(false, false));
        assertNoEffects(f);
        assertEquals(1, f.attacks.size());
        assertFalse(f.arrow.hadCollision);
        assertEquals(1, f.payload.size());
        verify(f.arrow, never()).onHit();
        verify(f.arrow).close();
    }

    @ParameterizedTest @CsvSource({"6,false", "6,true", "7,false", "7,true", "19,false", "19,true"})
    void cancelledHitNeverAppliesEffectsOrInheritsCollision(int id, boolean previous) throws Exception {
        Fixture f = new Fixture(id);
        f.arrow.hadCollision = previous;
        doAnswer(call -> {
            if (call.getArgument(0) instanceof ProjectileHitEvent hit) hit.setCancelled(true);
            return null;
        }).when(f.manager).callEvent(any());
        f.arrow.onCollideWithEntity(f.target(true, false));
        assertNoEffects(f);
        assertTrue(f.attacks.isEmpty());
        assertEquals(previous, f.arrow.hadCollision);
        verify(f.arrow, never()).onHit();
        verify(f.arrow, never()).close();
    }

    @ParameterizedTest @ValueSource(ints = {6, 7, 19})
    void refusedHitCannotInheritEarlierCollision(int id) throws Exception {
        Fixture f = new Fixture(id);
        f.arrow.hadCollision = true;
        f.arrow.onCollideWithEntity(f.target(false, false));
        assertNoEffects(f);
        assertEquals(1, f.attacks.size());
        assertTrue(f.arrow.hadCollision);
        verify(f.arrow, never()).onHit();
    }

    @ParameterizedTest @ValueSource(ints = {6, 7, 19})
    void nativeShieldDecisionSuppressesAllArrowEffects(int id) throws Exception {
        Fixture f = new Fixture(id);
        List<EntityDamageBlockedEvent> blocks = new ArrayList<>();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EntityDamageBlockedEvent blocked) blocks.add(blocked);
            return null;
        }).when(f.manager).callEvent(any());
        Player player = mock(Player.class, call -> {
            String name = call.getMethod().getName();
            if (name.equals("attack")) {
                EntityDamageEvent damage = call.getArgument(0);
                f.attacks.add(damage);
                // Only physical attack must enter the native shield path. Magic leakage is counted.
                return damage.getCause() == EntityDamageEvent.DamageCause.PROJECTILE
                        ? call.callRealMethod() : false;
            }
            return Set.of("blockedByShield").contains(name) ? call.callRealMethod()
                    : RETURNS_DEFAULTS.answer(call);
        });
        when(player.isAlive()).thenReturn(true);
        when(player.isBlocking()).thenReturn(true);
        when(player.getServer()).thenReturn(f.server);
        when(player.getDirectionVector()).thenReturn(new Vector3(0, 0, 1));
        when(player.getPosition()).thenReturn(new Position(0, 0, 0));
        when(f.arrow.getPosition()).thenReturn(new Position(0, 0, 1));
        f.capture(player);
        try (var singleton = mockStatic(Server.class)) {
            singleton.when(Server::getInstance).thenReturn(f.server);
            f.arrow.onCollideWithEntity(player);
        }
        assertEquals(1, blocks.size());
        assertFalse(blocks.get(0).isCancelled());
        assertNoEffects(f);
        assertEquals(1, f.attacks.size());
        assertFalse(f.arrow.hadCollision);
        verify(f.arrow, never()).onHit();
        verify(f.arrow).close();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nestedAcceptedHitCannotAuthorizeOuterRejectedHit(boolean cancelHit) throws Exception {
        Fixture f = new Fixture(Effect.POISON);
        Entity outer = f.target(false, false);
        List<Effect> innerEffects = new ArrayList<>();
        Entity inner = mock(Entity.class);
        when(inner.attack(any(EntityDamageEvent.class))).thenReturn(true);
        doAnswer(call -> { innerEffects.add(call.getArgument(0)); return null; })
                .when(inner).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ARROW));
        if (cancelHit) {
            doAnswer(call -> {
                if (call.getArgument(0) instanceof ProjectileHitEvent hit
                        && hit.getMovingObjectPosition().entityHit == outer) {
                    f.arrow.onCollideWithEntity(inner);
                    hit.setCancelled(true);
                }
                return null;
            }).when(f.manager).callEvent(any());
        } else {
            when(outer.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
                f.arrow.onCollideWithEntity(inner);
                return false;
            });
        }
        f.arrow.onCollideWithEntity(outer);
        assertNoEffects(f);
        assertEquals(1, innerEffects.size());
        assertEquals(20, innerEffects.get(0).getDuration());
        assertTrue(f.arrow.hadCollision);
        verify(f.arrow).onHit();
        verify(f.arrow, times(cancelHit ? 1 : 2)).close();
    }

    private static void assertNoEffects(Fixture f) {
        assertTrue(f.effects.isEmpty(), "rejected hit must not add timed effects");
        assertTrue(f.heals.isEmpty(), "rejected hit must not apply instant healing");
        assertTrue(f.attacks.stream().noneMatch(e -> e.getCause() == EntityDamageEvent.DamageCause.MAGIC),
                "rejected hit must not apply instant magic damage");
    }

    private static final class Fixture {
        final Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        final PluginManager manager = mock(PluginManager.class);
        final List<Effect> effects = new ArrayList<>();
        final List<EntityDamageEvent> attacks = new ArrayList<>();
        final List<EntityRegainHealthEvent> heals = new ArrayList<>();
        final Int2ObjectOpenHashMap<Effect> payload = new Int2ObjectOpenHashMap<>();
        final EntityArrow arrow;
        Fixture(int id) throws Exception {
            this(id, true);
        }
        Fixture(int id, boolean includeAcceptedHelper) throws Exception {
            when(server.getPluginManager()).thenReturn(manager);
            Set<String> realMethods = includeAcceptedHelper
                    ? Set.of("onCollideWithEntity", "onCollideWithEntityAccepted")
                    : Set.of("onCollideWithEntity", "getResultDamage", "getImpactDamage", "getDamage",
                            "getBaseDamage", "isCritical", "getMobEffects");
            arrow = mock(EntityArrow.class, call ->
                    realMethods.contains(call.getMethod().getName())
                            ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
            field(arrow, "server", server);
            if (id != Effect.NO_EFFECT) payload.put(id, Effect.getEffect(id).setDuration(160).setAmplifier(1));
            field(arrow, "mobEffects", payload);
            doReturn(5).when(arrow).getResultDamage();
        }
        Entity target(boolean accepts, boolean undead) {
            Entity target = undead ? mock(Entity.class, withSettings().extraInterfaces(EntitySmite.class))
                    : mock(Entity.class);
            when(target.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
                EntityDamageEvent damage = call.getArgument(0);
                attacks.add(damage);
                return damage.getCause() != EntityDamageEvent.DamageCause.PROJECTILE || accepts;
            });
            capture(target);
            return target;
        }
        void capture(Entity target) {
            when(target.canBeAffected(anyInt())).thenReturn(true);
            doAnswer(call -> { effects.add(call.getArgument(0)); return null; })
                    .when(target).addEffect(any(Effect.class), eq(EntityPotionEffectEvent.Cause.ARROW));
            doAnswer(call -> { heals.add(call.getArgument(0)); return null; })
                    .when(target).heal(any(EntityRegainHealthEvent.class));
        }
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
