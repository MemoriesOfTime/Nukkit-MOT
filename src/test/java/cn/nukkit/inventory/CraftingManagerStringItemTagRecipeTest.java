package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CraftingManagerStringItemTagRecipeTest {

    private CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        manager = new CraftingManager();
    }

    @Test
    void matchesCopperTorchRecipeWithStringItemDefaultIngredientAndItemTag() {
        Item copperNugget = Item.fromString("minecraft:copper_nugget");
        assertEquals(Item.STRING_IDENTIFIED_ITEM, copperNugget.getId());

        CraftingRecipe recipe = manager.matchRecipe(
                new ArrayList<>(List.of(copperNugget, Item.get(Item.COAL), Item.get(Item.STICK))),
                Item.get(255 - BlockID.COPPER_TORCH, 0, 4),
                Collections.emptyList()
        );

        assertNotNull(recipe);
    }
}
