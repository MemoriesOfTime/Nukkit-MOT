package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShapelessEmptyExtraResultTest {
    @BeforeAll static void init() { MockServer.init(); }

    private ShapelessRecipe ordinary() {
        return new ShapelessRecipe(Item.get(Item.DIAMOND),
                List.of(Item.get(Item.STICK, 0, 2), Item.get(Item.IRON_INGOT)));
    }

    @ParameterizedTest
    @CsvSource({"2,1,1,true", "4,2,2,true", "1,1,1,false", "3,1,1,false", "2,2,1,false",
            "4,1,2,false", "0,0,1,false", "2,1,0,false"})
    void aggregateCountsAndMultiplierKeepTheirMeaning(int sticks, int iron, int multiplier, boolean matches) {
        ShapelessRecipe recipe = ordinary();
        List<Item> inputs = List.of(Item.get(Item.STICK, 0, sticks), Item.get(Item.IRON_INGOT, 0, iron));
        assertEquals(matches, recipe.matchItems(inputs, List.of(), multiplier));
        assertEquals(sticks, inputs.get(0).getCount());
        assertEquals(iron, inputs.get(1).getCount());
        assertEquals(3, recipe.getIngredientsAggregate().get(0).getCount()
                + recipe.getIngredientsAggregate().get(1).getCount());
    }

    @Test void splitCountsAndAirAreNotReplacedByOneBitPerIngredient() {
        List<Item> inputs = List.of(Item.get(Item.STICK), Item.get(Item.AIR),
                Item.get(Item.IRON_INGOT), Item.get(Item.STICK));
        assertTrue(ordinary().matchItems(inputs, List.of()));
        assertEquals(1, inputs.get(0).getCount());
        assertEquals(1, inputs.get(3).getCount());
    }

    @Test void unexpectedSecondaryOutputStillRefuses() {
        List<Item> inputs = List.of(Item.get(Item.STICK, 0, 2), Item.get(Item.IRON_INGOT));
        assertFalse(ordinary().matchItems(inputs, List.of(Item.get(Item.BUCKET))));
    }

    private static final class SecondaryRecipe extends ShapelessRecipe {
        int extraReads;
        private final List<Item> secondary;
        SecondaryRecipe(List<Item> secondary) {
            super(Item.get(Item.DIAMOND), List.of(Item.get(Item.STICK)));
            this.secondary = secondary;
        }
        @Override public List<Item> getExtraResults() { extraReads++; return secondary; }
    }

    @Test void subclassCannotOmitItsRequiredSecondaryOutput() {
        SecondaryRecipe recipe = new SecondaryRecipe(List.of(Item.get(Item.BUCKET)));
        assertFalse(recipe.matchItems(List.of(Item.get(Item.STICK)), List.of()));
        assertEquals(1, recipe.extraReads);
    }

    @Test void subclassSecondaryOutputRetainsMultiplierAndDetachedCounts() {
        Item need = Item.get(Item.BUCKET);
        SecondaryRecipe recipe = new SecondaryRecipe(List.of(need));
        Item actual = Item.get(Item.BUCKET, 0, 2);
        assertTrue(recipe.matchItems(List.of(Item.get(Item.STICK, 0, 2)), List.of(actual), 2));
        assertEquals(1, recipe.extraReads);
        assertEquals(1, need.getCount());
        assertEquals(2, actual.getCount());
    }

    @Test void subclassWithEmptyResultsStillReceivesItsVirtualHook() {
        SecondaryRecipe recipe = new SecondaryRecipe(List.of());
        assertTrue(recipe.matchItems(List.of(Item.get(Item.STICK)), List.of()));
        assertEquals(1, recipe.extraReads);
    }

    @Test void exactNbtKeepsItsInputPolicy() {
        Item named = Item.get(Item.STICK).setCustomName("owned");
        ShapelessRecipe recipe = new ShapelessRecipe(Item.get(Item.DIAMOND), List.of(named));
        assertFalse(recipe.matchItems(List.of(Item.get(Item.STICK)), List.of()));
        assertTrue(recipe.matchItems(List.of(named.clone()), List.of()));
        assertEquals("owned", named.getCustomName());
    }
}
