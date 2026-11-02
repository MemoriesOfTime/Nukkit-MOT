package cn.nukkit.item;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerFood;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.player.PlayerEatFoodEvent;
import cn.nukkit.event.player.PlayerItemConsumeEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.food.Food;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.potion.Effect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real honey/edible/Food dispatch, native inventory, PlayerFood and native effect-removal ledger. */
class NativeHoneyEffectAcceptanceP14Test {
    private static final Set<Integer> OTHER_EFFECTS = Set.of(Effect.SPEED, Effect.WITHER, Effect.FATAL_POISON);
    private static final Set<Integer> ALL_EFFECTS = Set.of(Effect.POISON, Effect.SPEED, Effect.WITHER, Effect.FATAL_POISON);

    @BeforeAll static void bootstrap() { MockServer.init(); Effect.init(); }
    @BeforeEach void reset() { MockServer.reset(); }

    static IntStream earlyTicks() { return IntStream.rangeClosed(10, 30); }

    @ParameterizedTest @MethodSource("earlyTicks")
    void releasingBeforeCurrentNativeUseBoundaryCannotCleanseOrCreateBottle(int ticks) throws Exception {
        Fixture f = new Fixture(false, 2, "none");
        boolean accepted = f.honey.onUse(f.player, ticks);
        f.assertRejected(accepted);
        assertEquals(0, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(0, f.countEvents(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @ValueSource(ints = {0, 9})
    void existingVeryEarlyRejectionRemainsUntouched(int ticks) throws Exception {
        Fixture f = new Fixture(false, 2, "none");
        f.assertRejected(f.honey.onUse(f.player, ticks));
        assertEquals(0, f.countEvents(PlayerItemConsumeEvent.class));
    }

    @ParameterizedTest @ValueSource(ints = {31, 40})
    void consumeCancellationCannotCleanseOrCreateBottle(int ticks) throws Exception {
        Fixture f = new Fixture(false, 2, "consume");
        f.assertRejected(f.honey.onUse(f.player, ticks));
        assertEquals(1, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(0, f.countEvents(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @ValueSource(ints = {31, 40})
    void foodCancellationCannotCleanseOrCreateBottle(int ticks) throws Exception {
        Fixture f = new Fixture(false, 2, "food");
        f.assertRejected(f.honey.onUse(f.player, ticks));
        assertEquals(1, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(1, f.countEvents(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @CsvSource({"31,1", "31,2", "31,16", "40,1", "40,2", "40,16"})
    void acceptedHoneyRemovesOnlyPoisonConsumesOneAndReturnsOneBottle(int ticks, int count)
            throws Exception {
        Fixture f = new Fixture(false, count, "none");
        assertTrue(f.honey.onUse(f.player, ticks));
        f.assertOnlyPoisonRemoved();
        assertEquals(count - 1, f.honey.getCount());
        assertEquals(count - 1, f.total(Item.HONEY_BOTTLE));
        assertEquals(1, f.total(Item.BOTTLE));
        assertEquals(12, f.food.getLevel());
        assertEquals(1.2f, f.food.getFoodSaturationLevel(), 0.0001f);
        assertEquals(1, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(1, f.countEvents(PlayerEatFoodEvent.class));
        verify(f.player.getLevel().getVibrationManager()).callVibrationEvent(any());
    }

    @ParameterizedTest @ValueSource(ints = {31, 40})
    void creativeHoneyRemovesOnlyPoisonWithoutInventoryLossOrBottle(int ticks) throws Exception {
        Fixture f = new Fixture(true, 2, "none");
        assertTrue(f.honey.onUse(f.player, ticks));
        f.assertOnlyPoisonRemoved();
        assertEquals(2, f.honey.getCount());
        assertEquals(2, f.total(Item.HONEY_BOTTLE));
        assertEquals(0, f.total(Item.BOTTLE));
    }

    @ParameterizedTest @ValueSource(strings = {"none", "consume", "food"})
    void ordinaryBreadReportsOnlyAcceptedFoodSettlement(String cancel) throws Exception {
        Fixture f = new Fixture(false, 2, cancel);
        assertTrue(f.inventory.setItemInHand(new ItemBread(0, 2)));
        ItemBread bread = (ItemBread) f.inventory.getItemInHand();
        f.events.clear();
        boolean accepted = bread.onUse(f.player, 31);
        boolean expected = cancel.equals("none");
        assertAll("ordinary edible acceptance",
                () -> assertEquals(expected, accepted),
                () -> assertEquals(expected ? 1 : 2, bread.getCount()),
                () -> assertEquals(expected ? 1 : 2, f.total(Item.BREAD)),
                () -> assertEquals(expected ? 11 : 6, f.food.getLevel()),
                () -> assertEquals(ALL_EFFECTS, f.effects.keySet()),
                () -> assertEquals(0, f.total(Item.BOTTLE)));
        assertEquals(1, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(cancel.equals("consume") ? 0 : 1, f.countEvents(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @ValueSource(ints = {10, 31})
    void missingFoodDefinitionRetainsItsExistingTrueResult(int ticks) throws Exception {
        Fixture f = new Fixture(false, 2, "none");
        ItemEdible unknownFood = new ItemEdible(Item.STICK, 0, 2) { };
        assertNull(Food.getByRelative(unknownFood));
        assertTrue(f.inventory.setItemInHand(unknownFood));
        f.events.clear();
        assertTrue(unknownFood.onUse(f.player, ticks));
        assertEquals(2, unknownFood.getCount());
        assertEquals(2, f.total(Item.STICK));
        assertEquals(6, f.food.getLevel());
        assertEquals(ALL_EFFECTS, f.effects.keySet());
        assertEquals(1, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(0, f.countEvents(PlayerEatFoodEvent.class));
    }

    @ParameterizedTest @ValueSource(strings = {"consume", "food"})
    void rejectedChorusFoodNeverStartsTeleportCooldown(String cancel) throws Exception {
        Fixture f = new Fixture(false, 2, cancel);
        assertTrue(f.inventory.setItemInHand(new ItemChorusFruit(0, 2)));
        ItemChorusFruit chorus = (ItemChorusFruit) f.inventory.getItemInHand();
        f.events.clear();
        assertAll("rejected chorus acceptance",
                () -> assertFalse(chorus.onUse(f.player, 31)),
                () -> assertEquals(2, chorus.getCount()),
                () -> assertEquals(2, f.total(Item.CHORUS_FRUIT)),
                () -> assertEquals(6, f.food.getLevel()),
                () -> verify(f.player, never()).onChorusFruitTeleport());
        assertEquals(1, f.countEvents(PlayerItemConsumeEvent.class));
        assertEquals(cancel.equals("consume") ? 0 : 1, f.countEvents(PlayerEatFoodEvent.class));
    }

    private static final class Fixture {
        final Server server = MockServer.get();
        final PluginManager manager = server.getPluginManager();
        final List<Event> events = new ArrayList<>();
        final Map<Integer, Effect> effects = new HashMap<>();
        final Player player;
        final PlayerInventory inventory;
        final PlayerFood food;
        final ItemHoneyBottle honey;
        final int initialCount;

        Fixture(boolean creative, int count, String cancel) throws Exception {
            initialCount = count;
            player = mock(Player.class, call ->
                    Set.of("hasEffect", "getEffect", "getEffects", "removeEffect")
                            .contains(call.getMethod().getName()) ? call.callRealMethod()
                            : RETURNS_DEFAULTS.answer(call));
            field(player, "server", server);
            field(player, "effects", effects);
            for (int id : ALL_EFFECTS) effects.put(id, Effect.getEffect(id).setDuration(600).setAmplifier(1));
            when(player.getServer()).thenReturn(server);
            when(player.getViewers()).thenReturn(Map.of());
            when(player.isCreative()).thenReturn(creative);
            when(player.isAlive()).thenReturn(true);
            Level level = mock(Level.class);
            when(player.getLevel()).thenReturn(level);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(player.add(anyDouble(), anyDouble(), anyDouble())).thenReturn(new Location(0, 1.6, 0));
            food = new PlayerFood(player, 6, 0);
            when(player.getFoodData()).thenReturn(food);
            inventory = new PlayerInventory(player);
            when(player.getInventory()).thenReturn(inventory);
            assertTrue(inventory.setItemInHand(new ItemHoneyBottle(0, count)));
            honey = (ItemHoneyBottle) inventory.getItemInHand();
            assertEquals(31, honey.getUseDuration(), "preserve current native accepted boundary");
            doAnswer(call -> {
                Event event = call.getArgument(0);
                events.add(event);
                if (cancel.equals("consume") && event instanceof PlayerItemConsumeEvent consume)
                    consume.setCancelled(true);
                if (cancel.equals("food") && event instanceof PlayerEatFoodEvent eat)
                    eat.setCancelled(true);
                return null;
            }).when(manager).callEvent(any(Event.class));
        }

        void assertRejected(boolean accepted) {
            assertAll("rejected honey has no effect, food or inventory settlement",
                    () -> assertFalse(accepted, "use must report rejection"),
                    () -> assertEquals(ALL_EFFECTS, effects.keySet(), "effect ledger remains untouched"),
                    () -> assertEquals(initialCount, honey.getCount(), "held source count remains untouched"),
                    () -> assertEquals(initialCount, total(Item.HONEY_BOTTLE)),
                    () -> assertEquals(0, total(Item.BOTTLE), "rejected use cannot mint an empty bottle"),
                    () -> assertEquals(6, food.getLevel()),
                    () -> assertEquals(0, food.getFoodSaturationLevel()));
            for (Effect effect : effects.values()) {
                assertEquals(600, effect.getDuration());
                assertEquals(1, effect.getAmplifier());
            }
        }

        void assertOnlyPoisonRemoved() {
            assertEquals(OTHER_EFFECTS, effects.keySet());
            for (Effect effect : effects.values()) {
                assertEquals(600, effect.getDuration());
                assertEquals(1, effect.getAmplifier());
            }
            List<EntityPotionEffectEvent> removals = events.stream()
                    .filter(EntityPotionEffectEvent.class::isInstance)
                    .map(EntityPotionEffectEvent.class::cast).toList();
            assertEquals(1, removals.size());
            assertEquals(EntityPotionEffectEvent.Cause.FOOD, removals.get(0).getCause());
            assertEquals(Effect.POISON, removals.get(0).getOldEffect().getId());
        }

        long countEvents(Class<? extends Event> type) { return events.stream().filter(type::isInstance).count(); }
        int total(int id) { return inventory.getContents().values().stream()
                .filter(item -> item.getId() == id).mapToInt(Item::getCount).sum(); }
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
