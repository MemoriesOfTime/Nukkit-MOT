package cn.nukkit.potion;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.potion.PotionApplyEvent;
import cn.nukkit.plugin.PluginManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Slowness IV identity/level proof with the selected native 400-tick duration preserved.
 * Other values are a preservation snapshot of JAR3d568082, not a claim of complete vanilla parity.
 */
class NativeSlownessIvFactoryP14Test {
    @BeforeAll static void bootstrap() { MockServer.init(); Effect.init(); }
    @BeforeEach void reset() { MockServer.reset(); Potion.init(); }

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
        Fixture f = new Fixture(true);
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
        Fixture f = new Fixture(true);
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
        Fixture f = new Fixture(false);
        Potion.getPotion(Potion.SLOWNESS_IV).setSplash(splash).applyPotion(f.target, 1);
        assertTrue(f.effects.isEmpty());
        assertTrue(f.events.stream().anyMatch(event -> event instanceof EntityPotionEffectEvent potion
                && potion.isCancelled()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pluginReplacementEffectKeepsItsOwnLevelAndExistingDurationScaling(boolean splash) throws Exception {
        Fixture f = new Fixture(true);
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
            22,22,2,6,0,0
            23,23,1,7,0,0
            24,24,2,7,0,0
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

    private static final class Fixture {
        final Server server = MockServer.get();
        final PluginManager manager = server.getPluginManager();
        final Map<Integer, Effect> effects = new HashMap<>();
        final List<Event> events = new ArrayList<>();
        final EntityLiving target;
        Fixture(boolean affected) throws Exception {
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
