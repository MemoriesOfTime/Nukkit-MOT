package cn.nukkit.inventory.special;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDurable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepairItemRecipeTest {

    private static final int ELYTRA_MAX_DURABILITY = 432;

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
        Item output = Item.get(Item.ELYTRA, 148);

        assertTrue(recipe.canExecute(null, output, List.of(elytra1, elytra2)));
    }

    @Test
    void testElytraWrongOutputDamage() {
        Item elytra1 = Item.get(Item.ELYTRA, 300);
        Item elytra2 = Item.get(Item.ELYTRA, 300);
        Item wrongOutput = Item.get(Item.ELYTRA, 149);

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
        assertEquals(248, recipe.calculateDamage(350, 350, ELYTRA_MAX_DURABILITY));
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
}
