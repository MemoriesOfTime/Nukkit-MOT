package cn.nukkit.potion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.entity.mob.EntityWither;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.event.potion.PotionApplyEvent;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real Potion factories/application and real callback events. Amount checks are relative native
 * formula checks; no fixed Bedrock HP amount is asserted. Action-kind immunity keyed by Effect ID.
 * <p>
 * Slowness IV identity/level proof with the selected native 400-tick duration preserved.
 * Other values are a preservation snapshot of JAR3d568082, not a claim of complete vanilla parity.
 */
class PotionTest {
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

    @Test void registeredSlownessFourKeepsItsCanonicalPotionIdentityAndLevel() {
        Potion potion = Potion.getPotion(Potion.SLOWNESS_IV);
        assertNotNull(potion);
        assertAll("canonical Slowness IV registry entry",
                () -> assertEquals(Potion.SLOWNESS_IV, potion.getId()),
                () -> assertEquals(4, potion.getLevel()),
                () -> assertFalse(potion.isSplash()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void staticSlownessFourEffectUsesLevelFourWithoutChangingCurrentDuration(boolean splash) {
        Effect effect = Potion.getEffect(Potion.SLOWNESS_IV, splash);
        assertEquals(Effect.SLOWNESS, effect.getId());
        assertEquals(3, effect.getAmplifier());
        assertEquals(400, effect.getDuration(), "preserve current native duration, not a Mojang timing claim");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void registryFactoryAndStaticFactoryReturnTheSameCanonicalSlownessEffect(boolean splash) {
        Effect fromRegistry = Potion.getPotion(Potion.SLOWNESS_IV).setSplash(splash).getEffect();
        Effect fromStatic = Potion.getEffect(Potion.SLOWNESS_IV, splash);
        assertAll("factory routes agree",
                () -> assertEquals(Effect.SLOWNESS, fromRegistry.getId()),
                () -> assertEquals(3, fromRegistry.getAmplifier()),
                () -> assertEquals(fromStatic.getAmplifier(), fromRegistry.getAmplifier()),
                () -> assertEquals(fromStatic.getDuration(), fromRegistry.getDuration()));
    }

    @ParameterizedTest @CsvSource({"false,0.25,400", "false,1,400", "true,0,0",
            "true,0.00125,1", "true,0.25,100", "true,0.5,200", "true,1,400"})
    void nativeApplicationStoresCanonicalLevelWithExistingDistanceAndRounding(boolean splash,
            double factor, int ticks) throws Exception {
        SlownessFixture f = new SlownessFixture(true);
        Potion.getPotion(Potion.SLOWNESS_IV).setSplash(splash).applyPotion(f.target, factor);
        assertEquals(1, f.effects.size());
        Effect effect = f.effects.get(Effect.SLOWNESS);
        assertAll("native application result",
                () -> assertEquals(3, effect.getAmplifier()),
                () -> assertEquals(ticks, effect.getDuration()));
        assertEquals(1, f.events.stream().filter(PotionApplyEvent.class::isInstance).count());
        List<EntityPotionEffectEvent> effects = f.events.stream()
                .filter(EntityPotionEffectEvent.class::isInstance).map(EntityPotionEffectEvent.class::cast).toList();
        assertEquals(1, effects.size());
        assertEquals(splash ? EntityPotionEffectEvent.Cause.POTION_SPLASH
                : EntityPotionEffectEvent.Cause.POTION_DRINK, effects.get(0).getCause());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledPotionApplicationNeverReachesNativeEffectStorage(boolean splash) throws Exception {
        SlownessFixture f = new SlownessFixture(true);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            f.events.add(event);
            if (event instanceof PotionApplyEvent apply) apply.setCancelled(true);
            return null;
        }).when(f.manager).callEvent(any(Event.class));
        Potion.getPotion(Potion.SLOWNESS_IV).setSplash(splash).applyPotion(f.target, 1);
        assertTrue(f.effects.isEmpty());
        assertEquals(1, f.events.size());
        verify(f.target, never()).addEffect(any(Effect.class), any(EntityPotionEffectEvent.Cause.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void existingNativeImmunityStillRejectsSlowness(boolean splash) throws Exception {
        SlownessFixture f = new SlownessFixture(false);
        Potion.getPotion(Potion.SLOWNESS_IV).setSplash(splash).applyPotion(f.target, 1);
        assertTrue(f.effects.isEmpty());
        assertTrue(f.events.stream().anyMatch(event -> event instanceof EntityPotionEffectEvent potion
                && potion.isCancelled()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pluginReplacementEffectKeepsItsOwnLevelAndExistingDurationScaling(boolean splash) throws Exception {
        SlownessFixture f = new SlownessFixture(true);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            f.events.add(event);
            if (event instanceof PotionApplyEvent apply)
                apply.setApplyEffect(Effect.getEffect(Effect.SLOWNESS).setAmplifier(6).setDuration(123));
            return null;
        }).when(f.manager).callEvent(any(Event.class));
        Potion.getPotion(Potion.SLOWNESS_IV).setSplash(splash).applyPotion(f.target, .5);
        assertEquals(6, f.effects.get(Effect.SLOWNESS).getAmplifier());
        assertEquals(splash ? 62 : 123, f.effects.get(Effect.SLOWNESS).getDuration());
    }

    @Test void registryClonesDoNotShareSplashFlags() {
        Potion first = Potion.getPotion(Potion.SLOWNESS_IV);
        Potion second = Potion.getPotion(Potion.SLOWNESS_IV);
        assertNotSame(first, second);
        first.setSplash(true);
        assertTrue(first.isSplash());
        assertFalse(second.isSplash());
        assertFalse(Potion.getPotion(Potion.SLOWNESS_IV).isSplash());
    }

    @Test void staticEffectClonesDoNotMutateDefaultDurationOrRegistry() {
        Effect first = Potion.getEffect(Potion.SLOWNESS_IV, false);
        first.setDuration(1);
        first.setAmplifier(9);
        Effect second = Potion.getEffect(Potion.SLOWNESS_IV, false);
        assertNotSame(first, second);
        assertEquals(400, second.getDuration());
    }

    @Test void nonLivingEntityStillReceivesNoPotionDispatch() {
        Entity target = mock(Entity.class);
        Potion.getPotion(Potion.SLOWNESS_IV).applyPotion(target, 1);
        verifyNoInteractions(target);
    }

    @ParameterizedTest @CsvSource(textBlock = """
            0,0,1,-1,-1,-1
            1,1,1,-1,-1,-1
            2,2,2,-1,-1,-1
            3,3,1,-1,-1,-1
            4,4,1,-1,-1,-1
            5,5,1,16,0,3600
            6,6,1,16,0,9600
            7,7,1,14,0,3600
            8,8,1,14,0,9600
            9,9,1,8,0,3600
            10,10,1,8,0,9600
            11,11,2,8,1,1800
            12,12,1,12,0,3600
            13,13,1,12,0,9600
            14,14,1,1,0,3600
            15,15,1,1,0,9600
            16,16,2,1,1,1800
            17,17,1,2,0,1800
            18,18,1,2,0,4800
            19,19,1,13,0,3600
            20,20,1,13,0,9600
            21,21,1,6,0,0
            22,22,2,6,1,0
            23,23,1,7,0,0
            24,24,2,7,1,0
            25,25,1,19,0,900
            26,26,1,19,0,2400
            27,27,2,19,1,450
            28,28,1,10,0,900
            29,29,1,10,0,2400
            30,30,2,10,1,450
            31,31,1,5,0,3600
            32,32,1,5,0,9600
            33,33,2,5,1,1800
            34,34,1,18,0,1800
            35,35,1,18,0,4800
            36,36,2,20,1,800
            37,37,1,2,3,400
            38,38,1,2,3,800
            39,39,2,2,5,400
            40,40,1,27,0,1800
            41,41,1,27,0,4800
            43,43,1,32,0,3600
            44,44,1,33,0,3600
            45,45,1,34,0,3600
            46,46,1,35,0,3600
            47,-1,-1,-1,-1,-1
            """)
    void otherRegistryEntriesAndEffectsRetainSelectedNativeSnapshot(int id, int registeredId, int level,
            int effectId, int amplifier, int duration) {
        Potion potion = Potion.getPotion(id);
        if (registeredId < 0) {
            assertNull(potion);
            assertNull(Potion.getEffect(id, false));
            return;
        }
        assertEquals(registeredId, potion.getId());
        assertEquals(level, potion.getLevel());
        assertFalse(potion.isSplash());
        for (boolean splash : new boolean[] {false, true}) {
            Effect fromStatic = Potion.getEffect(id, splash);
            Effect fromRegistry = potion.setSplash(splash).getEffect();
            if (effectId < 0) {
                assertNull(fromStatic);
                assertNull(fromRegistry);
            } else {
                assertNotNull(fromStatic);
                assertNotNull(fromRegistry);
                assertEquals(effectId, fromStatic.getId());
                assertEquals(amplifier, fromStatic.getAmplifier());
                assertEquals(duration, fromStatic.getDuration());
                assertEquals(effectId, fromRegistry.getId());
                assertEquals(amplifier, fromRegistry.getAmplifier());
                assertEquals(duration, fromRegistry.getDuration());
            }
        }
    }

    private static final class SlownessFixture {
        final Server server = MockServer.get();
        final PluginManager manager = server.getPluginManager();
        final Map<Integer, Effect> effects = new HashMap<>();
        final List<Event> events = new ArrayList<>();
        final EntityLiving target;
        SlownessFixture(boolean affected) throws Exception {
            Set<String> real = Set.of("addEffect", "getEffect", "getEffects", "hasEffect");
            target = mock(EntityLiving.class, call -> real.contains(call.getMethod().getName())
                    ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
            field(target, "server", server);
            field(target, "effects", effects);
            when(target.getServer()).thenReturn(server);
            when(target.canBeAffected(anyInt())).thenReturn(affected);
            doAnswer(call -> { events.add(call.getArgument(0)); return null; })
                    .when(manager).callEvent(any(Event.class));
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
