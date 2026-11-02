package cn.nukkit.potion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.event.potion.PotionApplyEvent;
import cn.nukkit.plugin.PluginManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real Potion factories/application and real callback events. Amount checks are relative native
 * formula checks; no fixed Bedrock HP amount is asserted. Action-kind immunity keyed by Effect ID.
 */
class NativeInstantPotionLevelsP14Test {
    private static final List<Integer> IDS = List.of(Potion.INSTANT_HEALTH, Potion.INSTANT_HEALTH_II,
            Potion.HARMING, Potion.HARMING_II);
    @BeforeAll static void bootstrap() { MockServer.init(); Effect.init(); }
    @BeforeEach void reset() { MockServer.reset(); Potion.init(); }

    static Stream<Arguments> typedModes() { return IDS.stream().flatMap(id ->
            Stream.of(Arguments.of(id, false), Arguments.of(id, true))); }
    static Stream<Arguments> intensityCases() { return Stream.of(Potion.INSTANT_HEALTH, Potion.HARMING)
            .flatMap(id -> Stream.of(false, true).flatMap(undead -> Stream.of(false, true)
                    .flatMap(splash -> Stream.of(.25, .5, 1.0).map(factor ->
                            Arguments.of(id, undead, splash, factor))))); }

    @ParameterizedTest @MethodSource("typedModes")
    void normalAndStrongFactoriesUseTheirDeclaredCanonicalAmplifier(int id, boolean splash) {
        Potion potion = Potion.getPotion(id).setSplash(splash);
        Effect fromStatic = Potion.getEffect(id, splash);
        Effect fromInstance = potion.getEffect();
        int amplifier = Potion.getLevel(id) - 1;
        assertAll("canonical instant potion factory",
                () -> assertEquals(id, potion.getId()),
                () -> assertEquals(action(id), fromStatic.getId()),
                () -> assertEquals(amplifier, fromStatic.getAmplifier()),
                () -> assertEquals(action(id), fromInstance.getId()),
                () -> assertEquals(amplifier, fromInstance.getAmplifier()),
                () -> assertEquals(0, fromStatic.getDuration()),
                () -> assertEquals(0, fromInstance.getDuration()));
    }

    @ParameterizedTest @MethodSource("intensityCases")
    void strongerLevelDoublesTheExistingNativeCallbackWithUndeadAndIntensity(int normalId,
            boolean undead, boolean splash, double factor) {
        Fixture normal = new Fixture(undead, Set.of());
        apply(normalId, splash, factor, normal);
        Fixture strong = new Fixture(undead, Set.of());
        apply(normalId + 1, splash, factor, strong);
        boolean heals = (normalId == Potion.INSTANT_HEALTH) != undead;
        assertEmission(normal, heals);
        assertEmission(strong, heals);
        assertEquals(normal.amount() * 2, strong.amount(), 0.0001f,
                "strong level must double the same existing native action formula");
        assertTrue(strong.amount() > normal.amount());
    }

    @ParameterizedTest @MethodSource("typedModes")
    void effectSpecificImmunityRejectsTheActualInstantAction(int id, boolean splash) {
        Fixture f = new Fixture(false, Set.of(action(id)));
        apply(id, splash, .5, f);
        assertEquals(0, f.callbacks(), "Effect6/7 immunity must not be bypassed with Potion21..24");
    }

    @ParameterizedTest @MethodSource("typedModes")
    void unrelatedEffectImmunityMustNotBlockHealingOrHarming(int id, boolean splash) {
        Fixture f = new Fixture(false, Set.of(Effect.HEALTH_BOOST, Effect.ABSORPTION,
                Effect.SATURATION, Effect.LEVITATION));
        apply(id, splash, .5, f);
        assertEmission(f, action(id) == Effect.HEALING);
    }

    @ParameterizedTest @MethodSource("typedModes")
    void eventReplacementCannotBypassImmunityOfTheOriginalInstantAction(int id, boolean splash) {
        Fixture f = new Fixture(false, Set.of(action(id)));
        f.replace(Effect.SPEED, 2);
        apply(id, splash, .5, f);
        assertEquals(0, f.callbacks(), "replacing payload with Speed still dispatches the original instant action");
    }

    @ParameterizedTest @MethodSource("typedModes")
    void payloadEffectImmunityDoesNotInventADifferentInstantDispatchAction(int id, boolean splash) {
        Fixture f = new Fixture(false, Set.of(Effect.SPEED));
        f.replace(Effect.SPEED, 2);
        apply(id, splash, .5, f);
        assertEmission(f, action(id) == Effect.HEALING);
    }

    @ParameterizedTest @MethodSource("typedModes")
    void cancelledPotionApplyEventEmitsNoNativeCallback(int id, boolean splash) {
        Fixture f = new Fixture(false, Set.of());
        doAnswer(call -> {
            Event event = call.getArgument(0);
            f.events.add(event);
            if (event instanceof PotionApplyEvent apply) apply.setCancelled(true);
            return null;
        }).when(f.manager).callEvent(any(Event.class));
        apply(id, splash, .5, f);
        assertEquals(0, f.callbacks());
        assertEquals(1, f.events.size());
    }

    @ParameterizedTest @MethodSource("typedModes")
    void eventAmplifierReplacementKeepsExistingNativeFormulaInsteadOfResettingCanonicalLevel(int id,
            boolean splash) {
        Fixture baseline = new Fixture(false, Set.of());
        baseline.replace(action(id), 0);
        apply(id, splash, .25, baseline);
        Fixture changed = new Fixture(false, Set.of());
        changed.replace(action(id), 2);
        apply(id, splash, .25, changed);
        assertEmission(baseline, action(id) == Effect.HEALING);
        assertEmission(changed, action(id) == Effect.HEALING);
        assertEquals(baseline.amount() * 4, changed.amount(), 0.0001f);
    }

    @ParameterizedTest @ValueSource(ints = {21, 22, 23, 24})
    void zeroIntensityKeepsExistingZeroAmountCallback(int id) {
        Fixture f = new Fixture(false, Set.of());
        apply(id, true, 0, f);
        assertEquals(1, f.callbacks());
        assertEquals(0, f.amount());
    }

    @ParameterizedTest @ValueSource(ints = {21, 22, 23, 24})
    void strongFactoryClonesDoNotMutateEffectPrototypesOrPotionSplashFlags(int id) {
        Effect first = Potion.getEffect(id, false);
        first.setAmplifier(9).setDuration(100);
        Effect second = Potion.getEffect(id, false);
        assertNotSame(first, second);
        assertEquals(Potion.getLevel(id) - 1, second.getAmplifier());
        assertEquals(0, second.getDuration());
        assertEquals(0, Effect.getEffect(action(id)).getAmplifier());
        Potion a = Potion.getPotion(id).setSplash(true);
        Potion b = Potion.getPotion(id);
        assertNotSame(a, b);
        assertFalse(b.isSplash());
    }

    @ParameterizedTest @MethodSource("typedModes")
    void actualWitherEffectHookAllowsInstantActionsAndKeepsUndeadReversal(int id, boolean splash) {
        Server server = MockServer.get();
        EntityWither wither = mock(EntityWither.class, call ->
                call.getMethod().getName().equals("canBeAffected") ? call.callRealMethod()
                        : RETURNS_DEFAULTS.answer(call));
        assertTrue(wither instanceof EntitySmite);
        when(wither.getServer()).thenReturn(server);
        List<EntityDamageEvent> damage = new ArrayList<>();
        List<EntityRegainHealthEvent> healing = new ArrayList<>();
        when(wither.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
            damage.add(call.getArgument(0)); return true;
        });
        doAnswer(call -> { healing.add(call.getArgument(0)); return null; })
                .when(wither).heal(any(EntityRegainHealthEvent.class));
        Potion.getPotion(id).setSplash(splash).applyPotion(wither, .5);
        boolean heals = action(id) == Effect.HARMING;
        assertEquals(heals ? 1 : 0, healing.size());
        assertEquals(heals ? 0 : 1, damage.size());
        verify(wither).canBeAffected(action(id));
    }

    @Test void nonLivingTargetStillReceivesNoInstantDispatch() {
        Entity target = mock(Entity.class);
        Potion.getPotion(Potion.INSTANT_HEALTH_II).applyPotion(target);
        verifyNoInteractions(target);
    }

    private static int action(int id) { return id == Potion.INSTANT_HEALTH || id == Potion.INSTANT_HEALTH_II
            ? Effect.HEALING : Effect.HARMING; }
    private static void apply(int id, boolean splash, double factor, Fixture fixture) {
        Potion.getPotion(id).setSplash(splash).applyPotion(fixture.target, factor);
    }
    private static void assertEmission(Fixture f, boolean heals) {
        assertEquals(1, f.callbacks());
        assertEquals(heals ? 1 : 0, f.healing.size());
        assertEquals(heals ? 0 : 1, f.damage.size());
        assertEquals(1, f.events.stream().filter(PotionApplyEvent.class::isInstance).count());
        if (heals) {
            assertEquals(EntityRegainHealthEvent.CAUSE_MAGIC, f.healing.get(0).getRegainReason());
            assertSame(f.target, f.healing.get(0).getEntity());
        } else {
            assertEquals(EntityDamageEvent.DamageCause.MAGIC, f.damage.get(0).getCause());
            assertSame(f.target, f.damage.get(0).getEntity());
        }
    }

    private static final class Fixture {
        final Server server = MockServer.get();
        final PluginManager manager = server.getPluginManager();
        final List<Event> events = new ArrayList<>();
        final List<EntityDamageEvent> damage = new ArrayList<>();
        final List<EntityRegainHealthEvent> healing = new ArrayList<>();
        final EntityLiving target;
        Fixture(boolean undead, Set<Integer> deniedEffectIds) {
            target = undead ? mock(EntityLiving.class, withSettings().extraInterfaces(EntitySmite.class))
                    : mock(EntityLiving.class);
            when(target.getServer()).thenReturn(server);
            when(target.canBeAffected(anyInt())).thenAnswer(call -> !deniedEffectIds.contains(call.getArgument(0)));
            when(target.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
                damage.add(call.getArgument(0)); return true;
            });
            doAnswer(call -> { healing.add(call.getArgument(0)); return null; })
                    .when(target).heal(any(EntityRegainHealthEvent.class));
            doAnswer(call -> { events.add(call.getArgument(0)); return null; })
                    .when(manager).callEvent(any(Event.class));
        }
        void replace(int effectId, int amplifier) {
            doAnswer(call -> {
                Event event = call.getArgument(0);
                events.add(event);
                if (event instanceof PotionApplyEvent apply)
                    apply.setApplyEffect(Effect.getEffect(effectId).setAmplifier(amplifier));
                return null;
            }).when(manager).callEvent(any(Event.class));
        }
        int callbacks() { return damage.size() + healing.size(); }
        float amount() { return healing.isEmpty() ? damage.get(0).getDamage() : healing.get(0).getAmount(); }
    }
}
