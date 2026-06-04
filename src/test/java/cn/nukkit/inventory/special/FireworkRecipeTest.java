package cn.nukkit.inventory.special;

import cn.nukkit.MockServer;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.item.ItemID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FireworkRecipeTest {

    private FireworkRecipe recipe;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        recipe = new FireworkRecipe();
    }

    @Test
    void rejectsBatchOutputWithoutBatchMaterials() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER),
                Item.get(ItemID.GUNPOWDER)
        )));
    }

    @Test
    void rejectsBatchOutputWhenGunpowderIsNotDivisibleByBatchCount() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        assertFalse(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 3)
        )));
    }

    @Test
    void acceptsBatchOutputWhenPaperAndGunpowderMatchBatchCount() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        assertTrue(recipe.canExecute(null, output, List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 2)
        )));
    }

    @Test
    void toRecipeUsesGunpowderPerBatchAsFlightDuration() {
        Item output = Item.get(ItemID.FIREWORKS, 0, 6);

        Recipe craftedRecipe = recipe.toRecipe(output, List.of(
                Item.get(ItemID.PAPER, 0, 2),
                Item.get(ItemID.GUNPOWDER, 0, 6)
        ));

        assertInstanceOf(ItemFirework.class, craftedRecipe.getResult());
        ItemFirework firework = (ItemFirework) craftedRecipe.getResult();
        assertEquals(6, firework.getCount());
        assertEquals(3, firework.getFlight());
    }
}
