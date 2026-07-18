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

class CraftingManagerItemTagRecipeMetadataTest {

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
    void tagExpandedFurnaceRecipeKeepsStableClientRecipeMetadata() {
        List<Item> cobblestoneRing = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            cobblestoneRing.add(Item.get(BlockID.COBBLESTONE));
        }

        CraftingRecipe recipe = manager.matchRecipe(cobblestoneRing, Item.get(BlockID.FURNACE), Collections.emptyList());

        assertNotNull(recipe);
        assertEquals("minecraft:furnace_from_cobblestone", recipe.getRecipeId(),
                "expanded furnace recipes need stable recipe ids for client recipe book visibility");
        assertEquals(-1, recipe.getPriority());
    }
}
