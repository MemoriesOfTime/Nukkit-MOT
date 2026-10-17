package cn.nukkit.item.food;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.event.player.PlayerEatFoodEvent;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Food numbers: immutable Mojang bedrock-samples 46ba6ea985fb5a92d79a9419198f10dda14c199d
 * golden_apple.json, appleEnchanted.json and pufferfish.json.
 * Reapplication tests preserve the existing native contract; they do not assert hidden BDS stacking.
 */
class NativeFoodEffectDefinitionsP14Test {
    @BeforeAll static void bootstrap() { MockServer.init(); Effect.init(); }
    @BeforeEach void reset() { MockServer.reset(); }

    @ParameterizedTest @CsvSource({"19,1,1200", "9,0,300", "17,2,300"})
    void pufferfishStoresPrimaryBedrockLevelAndDuration(int id, int amplifier, int duration) throws Exception {
        Fixture f = new Fixture();
        assertTrue(Food.pufferfish.eatenBy(f.player));
        assertEquals(Set.of(Effect.POISON, Effect.NAUSEA, Effect.HUNGER), f.effects.keySet());
        assertEffect(f.effects.get(id), amplifier, duration);
        assertEquals(1, f.count(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @CsvSource({"10,1,100", "22,0,2400"})
    void normalGoldenAppleRetainsPinnedPrimaryEffects(int id, int amplifier, int duration) throws Exception {
        Fixture f = new Fixture();
        assertTrue(Food.apple_golden.eatenBy(f.player));
        assertEquals(Set.of(Effect.REGENERATION, Effect.ABSORPTION), f.effects.keySet());
        assertEffect(f.effects.get(id), amplifier, duration);
        assertEquals(4, f.player.getAbsorption());
    }

    @ParameterizedTest @CsvSource({"10,1,600", "22,3,2400", "11,0,6000", "12,0,6000"})
    void enchantedGoldenAppleRetainsExactPinnedPrimaryEffects(int id, int amplifier, int duration)
            throws Exception {
        Fixture f = new Fixture();
        assertTrue(Food.apple_golden_enchanted.eatenBy(f.player));
        assertEquals(Set.of(Effect.REGENERATION, Effect.ABSORPTION,
                Effect.DAMAGE_RESISTANCE, Effect.FIRE_RESISTANCE), f.effects.keySet());
        assertEffect(f.effects.get(id), amplifier, duration);
        assertEquals(16, f.player.getAbsorption());
    }

    @Test void correctedPufferfishLevelUsesItsNativePoisonCadence() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(Food.pufferfish.eatenBy(f.player));
        f.tick(61);
        // Native POISON II period is 25 >> 1 = 12; tick61 includes pulses at1,13,...,61.
        assertEquals(6, f.damage.size());
        assertTrue(f.damage.stream().allMatch(e -> e.getCause() == EntityDamageEvent.DamageCause.MAGIC));
        assertEquals(1139, f.effects.get(Effect.POISON).getDuration());
        assertEquals(239, f.effects.get(Effect.NAUSEA).getDuration());
    }

    @ParameterizedTest @CsvSource({"1,1300", "2,100", "3,1500", "4,50"})
    void weakerFoodPoisonPreservesExistingNativeEffect(int amplifier, int duration) throws Exception {
        Fixture f = new Fixture();
        Effect old = Effect.getEffect(Effect.POISON).setAmplifier(amplifier).setDuration(duration);
        f.player.addEffect(old, EntityPotionEffectEvent.Cause.UNKNOWN);
        assertTrue(Food.pufferfish.eatenBy(f.player));
        assertSame(old, f.effects.get(Effect.POISON));
        assertEffect(old, amplifier, duration);
    }

    @ParameterizedTest @CsvSource({"0,600", "1,100"})
    void weakerFoodNauseaPreservesExistingNativeEffect(int amplifier, int duration) throws Exception {
        Fixture f = new Fixture();
        Effect old = Effect.getEffect(Effect.NAUSEA).setAmplifier(amplifier).setDuration(duration);
        f.player.addEffect(old, EntityPotionEffectEvent.Cause.UNKNOWN);
        assertTrue(Food.pufferfish.eatenBy(f.player));
        assertSame(old, f.effects.get(Effect.NAUSEA));
        assertEffect(old, amplifier, duration);
    }

    @Test void repeatedPufferfishRefreshesDurationWithoutAddingDurationsOrMutatingTemplate() throws Exception {
        Map<Integer, Snapshot> template = snapshot(Food.pufferfish);
        Fixture f = new Fixture();
        assertTrue(Food.pufferfish.eatenBy(f.player));
        Effect first = f.effects.get(Effect.POISON);
        first.setDuration(100);
        assertTrue(Food.pufferfish.eatenBy(f.player));
        assertNotSame(first, f.effects.get(Effect.POISON));
        assertEquals(1200, f.effects.get(Effect.POISON).getDuration());
        assertEquals(template, snapshot(Food.pufferfish));
        assertEquals(2, f.count(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @ValueSource(strings = {"golden", "enchanted", "puffer"})
    void defaultFoodTemplatesAndPlayersNeverShareLiveEffectObjects(String name) throws Exception {
        Food food = food(name);
        Map<Integer, Snapshot> template = snapshot(food);
        Fixture first = new Fixture();
        assertTrue(food.eatenBy(first.player));
        first.effects.values().forEach(effect -> effect.setDuration(1));
        assertEquals(template, snapshot(food));
        Fixture second = new Fixture();
        assertTrue(food.eatenBy(second.player));
        for (int id : template.keySet()) {
            assertNotSame(first.effects.get(id), second.effects.get(id));
            assertEffect(second.effects.get(id), template.get(id).amplifier(), template.get(id).duration());
        }
        assertEquals(template, snapshot(food));
    }

    @ParameterizedTest @ValueSource(strings = {"golden", "enchanted", "puffer"})
    void cancelledFoodDoesNotRestoreFoodOrApplyEffects(String name) throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            f.events.add(event);
            if (event instanceof PlayerEatFoodEvent eat) eat.setCancelled(true);
            return null;
        }).when(f.manager).callEvent(any(Event.class));
        assertFalse(food(name).eatenBy(f.player));
        assertTrue(f.effects.isEmpty());
        assertEquals(0, f.foodData.getLevel());
        assertEquals(1, f.count(PlayerEatFoodEvent.class));
    }

    @Test void cancelledPoisonApplicationKeepsOtherFoodEffectsAndFoodSettlement() throws Exception {
        Fixture f = new Fixture();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            f.events.add(event);
            if (event instanceof EntityPotionEffectEvent potion && potion.getNewEffect() != null
                    && potion.getNewEffect().getId() == Effect.POISON) potion.setCancelled(true);
            return null;
        }).when(f.manager).callEvent(any(Event.class));
        assertTrue(Food.pufferfish.eatenBy(f.player));
        assertEquals(Set.of(Effect.NAUSEA, Effect.HUNGER), f.effects.keySet());
        assertEquals(1, f.foodData.getLevel());
    }

    @ParameterizedTest @ValueSource(floats = {0, 2, 4, 8, 16})
    void normalAppleRefillsOnlyItsFoodAbsorptionGrantWithoutReplacingStrongerIcon(float absorption)
            throws Exception {
        Fixture f = new Fixture();
        Effect old = Effect.getEffect(Effect.ABSORPTION).setAmplifier(3).setDuration(2000);
        f.player.addEffect(old, EntityPotionEffectEvent.Cause.UNKNOWN);
        f.player.setAbsorption(absorption);
        assertTrue(Food.apple_golden.eatenBy(f.player));
        assertSame(old, f.effects.get(Effect.ABSORPTION));
        assertEffect(old, 3, 2000);
        assertEquals(Math.max(absorption, 4), f.player.getAbsorption());
    }

    @Test void nativeExpirationRemovesLiveCopyWithoutChangingDefaultFoodTemplate() throws Throwable {
        Map<Integer, Snapshot> template = snapshot(Food.apple_golden);
        Fixture f = new Fixture();
        assertTrue(Food.apple_golden.eatenBy(f.player));
        f.effects.get(Effect.REGENERATION).setDuration(1);
        f.tick(1);
        assertFalse(f.effects.containsKey(Effect.REGENERATION));
        assertTrue(f.effects.containsKey(Effect.ABSORPTION));
        assertEquals(template, snapshot(Food.apple_golden));
        assertTrue(f.events.stream().anyMatch(e -> e instanceof EntityPotionEffectEvent potion
                && potion.getCause() == EntityPotionEffectEvent.Cause.EXPIRATION));
    }

    private record Snapshot(int amplifier, int duration, float chance) { }
    private static Map<Integer, Snapshot> snapshot(Food food) throws Exception {
        Field field = FoodEffective.class.getDeclaredField("effects");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Effect, Float> effects = (Map<Effect, Float>) field.get(food);
        Map<Integer, Snapshot> result = new LinkedHashMap<>();
        effects.forEach((effect, chance) -> result.put(effect.getId(),
                new Snapshot(effect.getAmplifier(), effect.getDuration(), chance)));
        return result;
    }
    private static Food food(String name) { return switch (name) {
        case "golden" -> Food.apple_golden;
        case "enchanted" -> Food.apple_golden_enchanted;
        case "puffer" -> Food.pufferfish;
        default -> throw new IllegalArgumentException(name);
    }; }
    private static void assertEffect(Effect effect, int amplifier, int duration) {
        assertNotNull(effect);
        assertEquals(amplifier, effect.getAmplifier());
        assertEquals(duration, effect.getDuration());
    }

    private static final class Fixture {
        final Server server = MockServer.get();
        final PluginManager manager = server.getPluginManager();
        final List<Event> events = new ArrayList<>();
        final List<EntityDamageEvent> damage = new ArrayList<>();
        final Map<Integer, Effect> effects = new HashMap<>();
        final Player player;
        final PlayerFood foodData;
        Fixture() throws Exception {
            Set<String> real = Set.of("addEffect", "hasEffect", "getEffect", "getEffects", "removeEffect",
                    "getAbsorption", "setAbsorption", "entityBaseTick");
            player = mock(Player.class, call -> real.contains(call.getMethod().getName())
                    ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
            field(player, "server", server);
            field(player, "effects", effects);
            Level level = mock(Level.class);
            field(player, "level", level);
            player.protocol = 589;
            player.y = 64;
            when(player.getLevel()).thenReturn(level);
            when(player.getServer()).thenReturn(server);
            when(player.canBeAffected(anyInt())).thenReturn(true);
            when(player.isAlive()).thenReturn(true);
            when(player.getHealth()).thenReturn(10f);
            when(player.getMaxHealth()).thenReturn(20);
            foodData = new PlayerFood(player, 0, 0);
            when(player.getFoodData()).thenReturn(foodData);
            when(player.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
                damage.add(call.getArgument(0)); return true;
            });
            doAnswer(call -> { events.add(call.getArgument(0)); return null; })
                    .when(manager).callEvent(any(Event.class));
        }
        void tick(int ticks) throws Throwable {
            var method = MethodHandles.privateLookupIn(EntityLiving.class, MethodHandles.lookup())
                    .findSpecial(Entity.class, "entityBaseTick", MethodType.methodType(boolean.class, int.class),
                            EntityLiving.class);
            for (int i = 0; i < ticks; i++) method.invoke((EntityLiving) player, 1);
        }
        long count(Class<? extends Event> type) { return events.stream().filter(type::isInstance).count(); }
    }
    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
