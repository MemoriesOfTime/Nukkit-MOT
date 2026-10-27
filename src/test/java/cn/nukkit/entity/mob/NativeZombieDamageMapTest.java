package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
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
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static cn.nukkit.event.entity.EntityDamageEvent.DamageModifier.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the native zombie attack and real event constructors; no server startup. */
class NativeZombieDamageMapTest {
    @BeforeAll
    static void initializeEffects() {
        Effect.init();
    }

    @ParameterizedTest
    @CsvSource({"23,0.5,false", "24,1.0,true", "24,1.000001,false", "24,NaN,false"})
    void delayAndRangeKeepTheirBoundaryAndShortCircuit(int delay, double distance, boolean attacks) {
        Fixture fixture = new Fixture(false);
        fixture.zombie.arm(delay);
        fixture.distance = distance;

        fixture.zombie.attackEntity(fixture.target);

        assertEquals(attacks ? 1 : 0, fixture.events.size());
        assertEquals(attacks ? 0 : delay, fixture.zombie.delay());
        if (delay <= 23) {
            assertTrue(fixture.trace.isEmpty(), "Cooldown must skip even the target distance getter");
        } else if (!attacks) {
            assertEquals(List.of("distance"), fixture.trace);
        } else {
            assertEquals(List.of("distance", "damage", "target.hasResistance", "zombie.hasStrength",
                    "zombie.hasWeakness", "attack", "animation"), fixture.trace);
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
            fixture.zombie.strength = Effect.getEffect(Effect.STRENGTH).setAmplifier(0);
            fixture.zombie.weakness = Effect.getEffect(Effect.WEAKNESS).setAmplifier(1);

            fixture.zombie.attackEntity(fixture.target);

            EntityDamageByEntityEvent event = fixture.events.get(0);
            float armorDamage = variant == 2 ? -3f : 0f;
            assertSame(fixture.zombie, event.getDamager());
            assertSame(fixture.target, event.getEntity());
            assertEquals(EntityDamageEvent.DamageCause.ENTITY_ATTACK, event.getCause());
            assertSame(Enchantment.EMPTY_ARRAY, event.getWeaponEnchantments());
            sameBits(0.3f, event.getKnockBack());
            sameBits(7f, event.getDamage(BASE));
            sameBits(armorDamage, event.getDamage(ARMOR));
            sameBits(-2.8f, event.getDamage(RESISTANCE));
            sameBits(2.1f, event.getDamage(STRENGTH));
            sameBits(-2.8f, event.getDamage(WEAKNESS));
            assertEquals(variant != 0, event.isApplicable(ARMOR));
            assertEquals(variant == 0 ? Map.of(BASE, 7f) : Map.of(BASE, 7f, ARMOR, armorDamage),
                    originals(event));
            sameBits(0f, event.getOriginalDamage(RESISTANCE));
            sameBits(0f, event.getOriginalDamage(STRENGTH));
            sameBits(0f, event.getOriginalDamage(WEAKNESS));
            float finalDamage = 7f + armorDamage;
            finalDamage += 2.1f;
            finalDamage += -2.8f;
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
                    "zombie.hasStrength", "zombie.getStrength", "zombie.hasWeakness",
                    "zombie.getWeakness", "attack", "animation"));
            assertEquals(expected, fixture.trace, "variant=" + variant);
        }
    }

    @Test
    void rejectedDamageStillPlaysTheAttackAnimation() {
        Fixture fixture = new Fixture(true);
        fixture.accepts = false;

        fixture.zombie.attackEntity(fixture.target);

        assertEquals(1, fixture.events.size());
        assertEquals(0, fixture.zombie.delay());
        assertEquals(List.of("attack", "animation"), fixture.trace.subList(fixture.trace.size() - 2, fixture.trace.size()));
    }

    @Test
    void thrownAttackKeepsTheResetDelayAndSkipsAnimation() {
        Fixture fixture = new Fixture(true);
        IllegalStateException failure = new IllegalStateException("target attack failure");
        fixture.onAttack = event -> { throw failure; };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.zombie.attackEntity(fixture.target)));

        assertEquals(1, fixture.events.size());
        assertEquals(0, fixture.zombie.delay());
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
                fixture.zombie.damage = 11;
                fixture.zombie.arm(24);
                fixture.zombie.attackEntity(fixture.target);
            }
        };

        fixture.zombie.attackEntity(fixture.target);

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
        fixture.zombie.damage = 3;
        fixture.zombie.arm(24);
        fixture.zombie.attackEntity(fixture.target);
        EntityDamageByEntityEvent next = fixture.events.get(2);
        sameBits(3f, next.getDamage(BASE));
        sameBits(-1f, next.getDamage(ARMOR));
        assertFalse(next.isApplicable(RESISTANCE));
        assertFalse(nested.isApplicable(RESISTANCE));
        assertEquals(Map.of(BASE, 3f, ARMOR, -1f), originals(next));
        assertEquals(3, fixture.trace.stream().filter("animation"::equals).count());
    }

    private static void sameBits(float expected, float actual) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual));
    }

    @SuppressWarnings("unchecked")
    private static Map<EntityDamageEvent.DamageModifier, Float> originals(EntityDamageEvent event) throws Exception {
        Field field = EntityDamageEvent.class.getDeclaredField("originals");
        field.setAccessible(true);
        return (Map<EntityDamageEvent.DamageModifier, Float>) field.get(event);
    }

    private static final class Fixture {
        final List<String> trace = new ArrayList<>();
        final List<EntityDamageByEntityEvent> events = new ArrayList<>();
        final TestZombie zombie = new ObjenesisStd().newInstance(TestZombie.class);
        final Entity target;
        final PlayerInventory inventory;
        double distance = 1;
        boolean accepts = true;
        Effect resistance;
        Consumer<EntityDamageByEntityEvent> onAttack;

        Fixture(boolean player) {
            zombie.trace = trace;
            zombie.damage = 7;
            zombie.arm(24);
            target = player ? mock(Player.class) : mock(EntityZombie.class);
            inventory = player ? mock(PlayerInventory.class) : null;
            when(target.distanceSquared(any(Entity.class))).thenAnswer(call -> {
                trace.add("distance");
                assertTrue(zombie.delay() > 23);
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
                assertEquals(0, zombie.delay());
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

    private static final class TestZombie extends EntityZombie {
        List<String> trace;
        int damage;
        Effect strength, weakness;

        private TestZombie() { super(null, null); }

        void arm(int delay) { attackDelay = delay; }
        int delay() { return attackDelay; }

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
            trace.add(id == Effect.STRENGTH ? "zombie.hasStrength" : "zombie.hasWeakness");
            return id == Effect.STRENGTH ? strength != null : id == Effect.WEAKNESS && weakness != null;
        }

        @Override public Effect getEffect(int id) {
            trace.add(id == Effect.STRENGTH ? "zombie.getStrength" : "zombie.getWeakness");
            return id == Effect.STRENGTH ? strength : weakness;
        }

        @Override protected void playAttack() { trace.add("animation"); }
    }
}
