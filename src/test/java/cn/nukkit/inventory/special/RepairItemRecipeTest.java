package cn.nukkit.inventory.special;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDurable;
import cn.nukkit.item.ItemShovelIron;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepairItemRecipeTest {

    private static final int ELYTRA_MAX_DURABILITY = 433;

    private RepairItemRecipe recipe;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        recipe = new RepairItemRecipe();
    }

    @Test
    void testElytraCombineTwoDifferent() {
        Item elytra1 = Item.get(Item.ELYTRA, 200);
        Item elytra2 = Item.get(Item.ELYTRA, 150);

        assertDurableButNeitherToolNorArmor(elytra1);

        Item output = Item.get(Item.ELYTRA, 0);
        assertTrue(recipe.canExecute(null, output, List.of(elytra1, elytra2)));
    }

    @Test
    void testElytraCombineFullyRepaired() {
        Item elytra1 = Item.get(Item.ELYTRA, 200);
        Item elytra2 = Item.get(Item.ELYTRA, 200);
        Item output = Item.get(Item.ELYTRA, 0);

        assertTrue(recipe.canExecute(null, output, List.of(elytra1, elytra2)));
    }

    @Test
    void testElytraCombineWithRemainingDamage() {
        Item elytra1 = Item.get(Item.ELYTRA, 300);
        Item elytra2 = Item.get(Item.ELYTRA, 300);
        Item output = Item.get(Item.ELYTRA, 146);

        assertTrue(recipe.canExecute(null, output, List.of(elytra1, elytra2)));
    }

    @Test
    void testElytraWrongOutputDamage() {
        Item elytra1 = Item.get(Item.ELYTRA, 300);
        Item elytra2 = Item.get(Item.ELYTRA, 300);
        Item wrongOutput = Item.get(Item.ELYTRA, 147);

        assertFalse(recipe.canExecute(null, wrongOutput, List.of(elytra1, elytra2)));
    }

    @Test
    void testElytraDifferentItemsRejected() {
        Item elytra = Item.get(Item.ELYTRA, 100);
        Item sword = Item.get(Item.DIAMOND_SWORD, 100);
        Item output = Item.get(Item.ELYTRA, 50);

        assertFalse(recipe.canExecute(null, output, List.of(elytra, sword)));
    }

    @Test
    void testDiamondSwordCombine() {
        Item sword1 = Item.get(Item.DIAMOND_SWORD, 500);
        Item sword2 = Item.get(Item.DIAMOND_SWORD, 500);
        Item output = Item.get(Item.DIAMOND_SWORD, 0);

        assertTrue(recipe.canExecute(null, output, List.of(sword1, sword2)));
    }

    @Test
    void testLeatherBootsCombine() {
        Item boots1 = Item.get(Item.LEATHER_BOOTS, 30);
        Item boots2 = Item.get(Item.LEATHER_BOOTS, 20);
        Item output = Item.get(Item.LEATHER_BOOTS, 0);

        assertTrue(recipe.canExecute(null, output, List.of(boots1, boots2)));
    }

    @Test
    void testNonDurableItemRejected() {
        Item dirt1 = Item.get(Item.DIRT);
        Item dirt2 = Item.get(Item.DIRT);
        Item output = Item.get(Item.DIRT);

        assertFalse(recipe.canExecute(null, output, List.of(dirt1, dirt2)));
    }

    @Test
    void testSingleInputStackOf2() {
        Item twoElytra = Item.get(Item.ELYTRA, 200);
        twoElytra.setCount(2);
        Item output = Item.get(Item.ELYTRA, 0);

        assertTrue(recipe.canExecute(null, output, List.of(twoElytra)));
    }

    @Test
    void testSingleInputStackOf1Rejected() {
        Item oneElytra = Item.get(Item.ELYTRA, 200);
        oneElytra.setCount(1);
        Item output = Item.get(Item.ELYTRA, 0);

        assertFalse(recipe.canExecute(null, output, List.of(oneElytra)));
    }

    @Test
    void testEmptyInputsRejected() {
        Item output = Item.get(Item.ELYTRA, 0);

        assertFalse(recipe.canExecute(null, output, List.of()));
    }

    @Test
    void testThreeInputsRejected() {
        Item e1 = Item.get(Item.ELYTRA, 100);
        Item e2 = Item.get(Item.ELYTRA, 100);
        Item e3 = Item.get(Item.ELYTRA, 100);
        Item output = Item.get(Item.ELYTRA, 0);

        assertFalse(recipe.canExecute(null, output, List.of(e1, e2, e3)));
    }

    @Test
    void testCalculateDamageClampsToZero() {
        assertEquals(0, recipe.calculateDamage(50, 50, ELYTRA_MAX_DURABILITY));
    }

    @Test
    void testCalculateDamageHeavyDamage() {
        assertEquals(246, recipe.calculateDamage(350, 350, ELYTRA_MAX_DURABILITY));
    }

    @Test
    void testCalculateDamageSymmetric() {
        int d1 = recipe.calculateDamage(100, 200, ELYTRA_MAX_DURABILITY);
        int d2 = recipe.calculateDamage(200, 100, ELYTRA_MAX_DURABILITY);
        assertEquals(d1, d2);
    }

    private static void assertDurableButNeitherToolNorArmor(Item item) {
        assertAll(
                () -> assertInstanceOf(ItemDurable.class, item),
                () -> assertFalse(item.isTool()),
                () -> assertFalse(item.isArmor())
        );
    }

    @Test void namedVanillaInputsAcceptCleanResultInEitherOrder() {
        Item named = first().setCustomName("First shovel");
        assertTrue(matches(named, second(), output()));
        assertTrue(matches(second(), named, output()));
    }
    @Test void ordinaryEnchantmentAndPriorWorkCanBeRemoved() {
        Item enchanted = first().setCustomName("First shovel");
        enchanted.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(2));
        enchanted.setNamedTag(enchanted.getNamedTag().putInt("RepairCost", 3));
        assertTrue(matches(enchanted, second(), output()));
    }
    @Test void originalPlainAndExactMetadataPathsArePreserved() {
        assertTrue(matches(first(), second(), output()));
        Item marked = first().setNamedTag(new CompoundTag().putString("example:origin", "example:original"));
        Item markedOutput = output().setNamedTag(marked.getNamedTag().copy());
        assertTrue(matches(marked, second(), markedOutput));
        Item secondMarked = second().setNamedTag(marked.getNamedTag().copy());
        assertTrue(matches(marked, secondMarked, markedOutput));
        assertFalse(matches(marked, second(), output()));
    }
    @Test void newAcceptanceNeverDiscardsServerMetadataFromEitherInput() {
        for (String key : List.of("example:origin", "example:owner", "example:seal", "custom")) {
            Item marked = second().setNamedTag(new CompoundTag().putString(key, "value"));
            Item named = first().setCustomName("First shovel");
            assertFalse(matches(named, marked, output()), key);
            assertFalse(matches(marked, named, output()), key);
        }
    }
    @Test void cursesAboveVanillaEnchantmentsAndCustomLoreStayOnExactPath() {
        Item cursed = first(); cursed.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_VANISHING_CURSE));
        assertFalse(matches(cursed, second(), output()));
        Item high = first(); high.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(10, false));
        assertFalse(matches(high, second(), output()));
        Item lore = first().setCustomName("First shovel"); lore.setLore("server-owned");
        assertFalse(matches(lore, second(), output()));
    }
    @Test void forgedOutputAndWrongDurabilityStayRejected() {
        Item named = first().setCustomName("First shovel");
        assertFalse(matches(named, second(), new ItemShovelIron(163, 1)));
        assertFalse(matches(named, second(), output().setCustomName("Forged")));
        assertFalse(matches(named, second(), output().setNamedTag(new CompoundTag().putString("example:origin", "example:forged"))));
    }

    private Item first() { return new ItemShovelIron(200, 1); }
    private Item second() { return new ItemShovelIron(225, 1); }
    private Item output() { return new ItemShovelIron(162, 1); }
    private boolean matches(Item first, Item second, Item output) {
        Item beforeFirst = first.clone(), beforeSecond = second.clone(), beforeOutput = output.clone();
        boolean result = recipe.canExecute(null, output, List.of(first, second));
        assertEquals(beforeFirst, first); assertEquals(beforeSecond, second); assertEquals(beforeOutput, output);
        return result;
    }
}
