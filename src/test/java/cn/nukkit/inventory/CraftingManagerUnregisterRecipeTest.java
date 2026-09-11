package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingManagerUnregisterRecipeTest {

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
    void unregisterBundleRecipe() {
        List<Recipe> recipesToRemove = new ArrayList<>();
        for (Recipe recipe : manager.getRecipes()) {
            if (recipe instanceof CraftingRecipe craftingRecipe && "minecraft:bundle".equals(craftingRecipe.getResult().getNamespaceId())) {
                recipesToRemove.add(recipe);
            }
        }
        assertFalse(recipesToRemove.isEmpty(), "expected bundle's recipe to exist");
        recipesToRemove.forEach(recipe -> {
            assertTrue(manager.unregisterRecipe(recipe));
        });
        boolean bundleRecipeExists = manager.getRecipes().stream().filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast)
                                            .anyMatch(recipe -> "minecraft:bundle".equals(recipe.getResult().getNamespaceId()));
        assertFalse(bundleRecipeExists, "bundle's recipe should have been removed");
        assertFalse(manager.getShapelessRecipes().values().stream().flatMap(map -> map.values().stream()).anyMatch(recipe -> "minecraft:bundle".equals(recipe.getResult().getNamespaceId())));
    }
}
