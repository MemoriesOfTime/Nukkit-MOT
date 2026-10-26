package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;
import org.mockito.MockedStatic;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static cn.nukkit.event.entity.EntityDamageEvent.DamageModifier.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the native drowned attack and real event constructors; no server startup. */
class NativeDrownedDamageMapTest {
    @BeforeAll
    static void initializeEffects() {
        Effect.init();
    }

    @ParameterizedTest
    @CsvSource({"23,0.5,false", "24,1.0,true", "24,1.000001,false", "24,NaN,false"})
    void delayAndRangeKeepTheirBoundaryAndShortCircuit(int delay, double distance, boolean attacks) {
        Fixture fixture = new Fixture(false);
        fixture.drowned.arm(delay);
        fixture.distance = distance;

        fixture.drowned.attackEntity(fixture.target);

        assertEquals(attacks ? 1 : 0, fixture.events.size());
        assertEquals(attacks ? 0 : delay, fixture.drowned.delay());
        if (delay <= 23) {
            assertTrue(fixture.trace.isEmpty(), "Cooldown must skip even the target distance getter");
        } else if (!attacks) {
            assertEquals(List.of("distance"), fixture.trace);
        } else {
            assertEquals(List.of("distance", "damage", "target.hasResistance", "drowned.hasStrength",
                    "drowned.hasWeakness", "attack", "animation"), fixture.trace);
        }
    }

    @Test
    void originalArmorEffectsAndMapConstructorDefaultsRemainExact() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            Fixture fixture = new Fixture(variant != 0);
            int[] armor = variant == 2
                    ? new int[]{Item.IRON_HELMET, Item.DIAMOND_CHESTPLATE, Item.AIR, Item.LEATHER_BOOTS}
                    : new int[0];
            fixture.armor(armor);
            fixture.resistance = Effect.getEffect(Effect.DAMAGE_RESISTANCE).setAmplifier(1);
            fixture.drowned.strength = Effect.getEffect(Effect.STRENGTH).setAmplifier(0);
            fixture.drowned.weakness = Effect.getEffect(Effect.WEAKNESS).setAmplifier(0);

            fixture.drowned.attackEntity(fixture.target);

            EntityDamageByEntityEvent event = fixture.events.get(0);
            float armorDamage = variant == 2 ? -3f : 0f;
            assertSame(fixture.drowned, event.getDamager());
            assertSame(fixture.target, event.getEntity());
            assertEquals(EntityDamageEvent.DamageCause.ENTITY_ATTACK, event.getCause());
            assertSame(Enchantment.EMPTY_ARRAY, event.getWeaponEnchantments());
            sameBits(0.3f, event.getKnockBack());
            sameBits(7f, event.getDamage(BASE));
            sameBits(armorDamage, event.getDamage(ARMOR));
            sameBits(-2.8f, event.getDamage(RESISTANCE));
            sameBits(2.1f, event.getDamage(STRENGTH));
            sameBits(-1.4f, event.getDamage(WEAKNESS));
            assertEquals(variant != 0, event.isApplicable(ARMOR));
            assertEquals(variant == 0 ? Map.of(BASE, 7f) : Map.of(BASE, 7f, ARMOR, armorDamage),
                    originals(event));
            sameBits(0f, event.getOriginalDamage(RESISTANCE));
            sameBits(0f, event.getOriginalDamage(STRENGTH));
            sameBits(0f, event.getOriginalDamage(WEAKNESS));
            float finalDamage = 7f + armorDamage;
            finalDamage += 2.1f;
            finalDamage += -1.4f;
            finalDamage += -2.8f;
            sameBits(finalDamage, event.getFinalDamage());

            List<String> expected = new ArrayList<>(List.of("distance", "damage"));
            if (variant != 0) {
                expected.add("inventory");
                expected.add("armor");
                for (int id : armor) {
                    expected.add("item:" + id);
                    expected.add("points:" + id);
                }
            }
            expected.addAll(List.of("target.hasResistance", "target.getResistance",
                    "drowned.hasStrength", "drowned.getStrength", "drowned.hasWeakness",
                    "drowned.getWeakness", "attack", "animation"));
            assertEquals(expected, fixture.trace, "variant=" + variant);
        }
    }

    @Test
    void rejectedDamageStillPlaysTheAttackAnimation() {
        Fixture fixture = new Fixture(true);
        fixture.accepts = false;

        fixture.drowned.attackEntity(fixture.target);

        assertEquals(1, fixture.events.size());
        assertEquals(0, fixture.drowned.delay());
        assertEquals(List.of("attack", "animation"), fixture.trace.subList(fixture.trace.size() - 2, fixture.trace.size()));
    }

    @Test
    void thrownAttackKeepsTheResetDelayAndSkipsAnimation() {
        Fixture fixture = new Fixture(true);
        IllegalStateException failure = new IllegalStateException("target attack failure");
        fixture.onAttack = event -> { throw failure; };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.drowned.attackEntity(fixture.target)));

        assertEquals(1, fixture.events.size());
        assertEquals(0, fixture.drowned.delay());
        assertEquals("attack", fixture.trace.get(fixture.trace.size() - 1));
        assertFalse(fixture.trace.contains("animation"));
    }

    @Test
    void retainedAndReentrantAttacksOwnIndependentCurrentAndOriginalModifiers() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.armor(Item.IRON_HELMET, Item.DIAMOND_CHESTPLATE, Item.LEATHER_BOOTS);
        fixture.onAttack = event -> {
            if (fixture.events.size() == 1) {
                event.setDamage(31f, BASE);
                event.setDamage(-99f, ARMOR);
                fixture.drowned.damage = 11;
                fixture.drowned.arm(24);
                fixture.drowned.attackEntity(fixture.target);
            }
        };

        fixture.drowned.attackEntity(fixture.target);

        EntityDamageByEntityEvent first = fixture.events.get(0);
        EntityDamageByEntityEvent nested = fixture.events.get(1);
        assertNotSame(first, nested);
        sameBits(31f, first.getDamage(BASE));
        sameBits(-99f, first.getDamage(ARMOR));
        assertEquals(Map.of(BASE, 7f, ARMOR, -3f), originals(first));
        sameBits(11f, nested.getDamage(BASE));
        sameBits(-4f, nested.getDamage(ARMOR));
        assertEquals(Map.of(BASE, 11f, ARMOR, -4f), originals(nested));

        nested.setDamage(100f, BASE);
        first.setDamage(50f, RESISTANCE);
        fixture.onAttack = null;
        fixture.drowned.damage = 3;
        fixture.drowned.arm(24);
        fixture.drowned.attackEntity(fixture.target);
        EntityDamageByEntityEvent next = fixture.events.get(2);
        sameBits(3f, next.getDamage(BASE));
        sameBits(-1f, next.getDamage(ARMOR));
        assertFalse(next.isApplicable(RESISTANCE));
        assertFalse(nested.isApplicable(RESISTANCE));
        assertEquals(Map.of(BASE, 3f, ARMOR, -1f), originals(next));
        assertEquals(3, fixture.trace.stream().filter("animation"::equals).count());
    }

    @Test
    void meleeWithHeldTridentDoesNotReadTheRangedTool() {
        Fixture fixture = new Fixture(false);
        Item trident = mock(Item.class);
        fixture.drowned.tool = trident;
        fixture.drowned.arm(121);

        fixture.drowned.attackEntity(fixture.target);

        verifyNoInteractions(trident);
        assertEquals(1, fixture.events.size());
        assertEquals(0, fixture.drowned.delay());
        assertEquals("animation", fixture.trace.get(fixture.trace.size() - 1));
    }

    @ParameterizedTest
    @CsvSource({"23,1.0", "24,2.0", "120,2.0"})
    void heldTridentKeepsItsSeparateCooldownAndDoesNotConstructMeleeEvents(int delay, double distance) {
        Fixture fixture = new Fixture(false);
        Item trident = mock(Item.class);
        when(trident.getId()).thenAnswer(call -> {
            fixture.trace.add("tool");
            return Item.TRIDENT;
        });
        fixture.drowned.tool = trident;
        fixture.drowned.arm(delay);
        fixture.distance = distance;

        fixture.drowned.attackEntity(fixture.target);

        assertEquals(delay, fixture.drowned.delay());
        assertTrue(fixture.events.isEmpty());
        assertEquals(delay <= 23 ? List.of("tool") : List.of("distance", "tool"), fixture.trace);
        verify(trident).getId();
    }

    @ParameterizedTest
    @CsvSource({"4,55,121,false", "3,55.000001,121,false", "3,55,0,true"})
    void rangedTridentKeepsRandomRangeAndBlockedLaunchBoundaries(
            int roll, double rangedDistance, int expectedDelay, boolean readsWorld) {
        Fixture fixture = new Fixture(false);
        fixture.distance = 2;
        fixture.drowned.arm(121);
        fixture.drowned.rangedDistance = rangedDistance;
        Item trident = mock(Item.class);
        when(trident.getId()).thenAnswer(call -> {
            fixture.trace.add("tool");
            return Item.TRIDENT;
        });
        fixture.drowned.tool = trident;
        Level level = mock(Level.class);
        fixture.drowned.fixtureLevel(level);
        when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            fixture.trace.add("world");
            assertEquals(0, fixture.drowned.delay(), "Ranged cooldown resets before the world lookup");
            return Block.STONE;
        });
        try (MockedStatic<Utils> random = mockStatic(Utils.class)) {
            random.when(() -> Utils.rand(1, 32)).thenAnswer(call -> {
                fixture.trace.add("rng");
                return roll;
            });
            fixture.drowned.attackEntity(fixture.target);
            random.verify(() -> Utils.rand(1, 32));
        }
        List<String> expected = new ArrayList<>(List.of("distance", "tool", "rng"));
        if (roll < 4) expected.add("ranged.distance");
        if (readsWorld) expected.add("world");
        assertEquals(expected, fixture.trace);
        assertEquals(expectedDelay, fixture.drowned.delay());
        assertTrue(fixture.events.isEmpty());
        verify(level, times(readsWorld ? 1 : 0)).getBlockIdAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void cancelledEventKeepsAnimationAndOriginalModifiers() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.armor(Item.IRON_CHESTPLATE);
        fixture.onAttack = event -> {
            event.setCancelled();
            event.setDamage(99f);
        };
        fixture.accepts = false;

        fixture.drowned.attackEntity(fixture.target);

        EntityDamageByEntityEvent event = fixture.events.get(0);
        assertTrue(event.isCancelled());
        sameBits(99f, event.getDamage());
        assertEquals(Map.of(BASE, 7f, ARMOR, -1f), originals(event));
        assertEquals("animation", fixture.trace.get(fixture.trace.size() - 1));
        assertEquals(0, fixture.drowned.delay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"distance", "damage", "inventory", "armor", "item:306", "points:306",
            "target.hasResistance", "target.getResistance", "drowned.hasStrength", "drowned.getStrength",
            "drowned.hasWeakness", "drowned.getWeakness", "attack", "animation"})
    void callbackExceptionsKeepTheirExactPrefixAndDelayState(String failingStep) {
        Fixture reference = new Fixture(true);
        reference.armor(Item.IRON_HELMET);
        reference.resistance = Effect.getEffect(Effect.DAMAGE_RESISTANCE);
        reference.drowned.strength = Effect.getEffect(Effect.STRENGTH);
        reference.drowned.weakness = Effect.getEffect(Effect.WEAKNESS);
        reference.drowned.attackEntity(reference.target);
        int end = reference.trace.indexOf(failingStep) + 1;
        assertTrue(end > 0, failingStep);
        List<String> expectedPrefix = new ArrayList<>(reference.trace.subList(0, end));

        Fixture fixture = new Fixture(true);
        fixture.armor(Item.IRON_HELMET);
        fixture.resistance = Effect.getEffect(Effect.DAMAGE_RESISTANCE);
        fixture.drowned.strength = Effect.getEffect(Effect.STRENGTH);
        fixture.drowned.weakness = Effect.getEffect(Effect.WEAKNESS);
        fixture.trace.failingStep = failingStep;

        assertSame(fixture.trace.failure, assertThrows(IllegalStateException.class,
                () -> fixture.drowned.attackEntity(fixture.target)));
        assertEquals(expectedPrefix, fixture.trace);
        assertEquals(failingStep.equals("distance") ? 24 : 0, fixture.drowned.delay());
        assertEquals(failingStep.equals("animation") ? 1 : 0, fixture.events.size());
    }

    private static final class Trace extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
        final IllegalStateException failure = new IllegalStateException("injected callback failure");
        String failingStep;

        @Override public boolean add(String step) {
            boolean changed = super.add(step);
            if (step.equals(failingStep)) throw failure;
            return changed;
        }
    }

    private static void sameBits(float expected, float actual) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual));
    }

    private static Map<EntityDamageEvent.DamageModifier, Float> originals(EntityDamageEvent event) {
        Map<EntityDamageEvent.DamageModifier, Float> result = new java.util.EnumMap<>(EntityDamageEvent.DamageModifier.class);
        for (EntityDamageEvent.DamageModifier type : EntityDamageEvent.DamageModifier.values()) {
            float value = event.getOriginalDamage(type);
            // ARMOR is an original modifier for player targets even when the armor value is zero.
            if (value != 0f || type == EntityDamageEvent.DamageModifier.ARMOR && event.isApplicable(type)) {
                result.put(type, value);
            }
        }
        return result;
    }

    private static final class Fixture {
        final Trace trace = new Trace();
        final List<EntityDamageByEntityEvent> events = new ArrayList<>();
        final TestDrowned drowned = new ObjenesisStd().newInstance(TestDrowned.class);
        final Entity target;
        final PlayerInventory inventory;
        double distance = 1;
        boolean accepts = true;
        Effect resistance;
        Consumer<EntityDamageByEntityEvent> onAttack;

        Fixture(boolean player) {
            drowned.trace = trace;
            drowned.damage = 7;
            drowned.arm(24);
            target = player ? mock(Player.class) : mock(EntityDrowned.class);
            inventory = player ? mock(PlayerInventory.class) : null;
            when(target.distanceSquared(any(Entity.class))).thenAnswer(call -> {
                trace.add("distance");
                assertTrue(drowned.delay() > 23);
                return distance;
            });
            if (player) {
                when(((Player) target).getInventory()).thenAnswer(call -> {
                    trace.add("inventory");
                    return inventory;
                });
                armor();
            }
            when(target.hasEffect(Effect.DAMAGE_RESISTANCE)).thenAnswer(call -> {
                trace.add("target.hasResistance");
                return resistance != null;
            });
            when(target.getEffect(Effect.DAMAGE_RESISTANCE)).thenAnswer(call -> {
                trace.add("target.getResistance");
                return resistance;
            });
            when(target.attack(any(EntityDamageEvent.class))).thenAnswer(call -> {
                trace.add("attack");
                assertEquals(0, drowned.delay());
                EntityDamageByEntityEvent event = call.getArgument(0);
                events.add(event);
                if (onAttack != null) onAttack.accept(event);
                return accepts;
            });
        }

        void armor(int... ids) {
            if (inventory == null) return;
            Item[] items = new Item[ids.length];
            for (int i = 0; i < ids.length; i++) {
                int id = ids[i];
                Item item = mock(Item.class);
                when(item.getId()).thenAnswer(call -> {
                    trace.add("item:" + id);
                    return id;
                });
                items[i] = item;
            }
            doAnswer(call -> {
                trace.add("armor");
                return items;
            }).when(inventory).getArmorContents();
        }
    }

    private static final class TestDrowned extends EntityDrowned {
        List<String> trace;
        int damage;
        double rangedDistance;
        Effect strength, weakness;

        private TestDrowned() { super(null, null); }

        void arm(int delay) { attackDelay = delay; }
        int delay() { return attackDelay; }
        void fixtureLevel(Level value) { level = value; }
        @Override public double distanceSquared(Vector3 value) {
            trace.add("ranged.distance");
            return rangedDistance;
        }

        @Override public int getDamage() {
            trace.add("damage");
            assertEquals(0, attackDelay, "Reset must precede damage calculation");
            return damage;
        }

        @Override protected float getArmorPoints(int item) {
            trace.add("points:" + item);
            return super.getArmorPoints(item);
        }

        @Override public boolean hasEffect(int id) {
            trace.add(id == Effect.STRENGTH ? "drowned.hasStrength" : "drowned.hasWeakness");
            return id == Effect.STRENGTH ? strength != null : id == Effect.WEAKNESS && weakness != null;
        }

        @Override public Effect getEffect(int id) {
            trace.add(id == Effect.STRENGTH ? "drowned.getStrength" : "drowned.getWeakness");
            return id == Effect.STRENGTH ? strength : weakness;
        }

        @Override protected void playAttack() { trace.add("animation"); }
    }
}
