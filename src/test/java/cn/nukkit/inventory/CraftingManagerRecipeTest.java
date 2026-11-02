package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingManagerRecipeTest {

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

    @Test
    void cakeRecipeDoesNotMatchWhenEmptyBucketsAreMissing() {
        CraftingRecipe recipe = manager.matchRecipe(
                new ArrayList<>(cakeInputs()),
                Item.get(Item.CAKE),
                Collections.emptyList()
        );

        assertNull(recipe);
    }

    @Test
    void bucketNamespacedIdResolvesToEmptyBucket() {
        Item bucket = Item.fromString("minecraft:bucket");

        assertEquals(Item.BUCKET, bucket.getId());
        assertEquals(ItemBucket.EMPTY_BUCKET, bucket.getDamage());
    }

    @Test
    void loadedCakeRecipeContainsThreeEmptyBuckets() {
        CraftingRecipe recipe = findCakeRecipe();

        assertNotNull(recipe);
        assertEquals(1, recipe.getExtraResults().size());
        Item bucket = recipe.getExtraResults().get(0);
        assertEquals(Item.BUCKET, bucket.getId());
        assertEquals(ItemBucket.EMPTY_BUCKET, bucket.getDamage());
        assertEquals(3, bucket.getCount());
    }

    @Test
    void cakeRecipeReturnsThreeEmptyBuckets() {
        CraftingRecipe recipe = manager.matchRecipe(
                new ArrayList<>(cakeInputs()),
                Item.get(Item.CAKE),
                List.of(Item.get(Item.BUCKET, ItemBucket.EMPTY_BUCKET, 3))
        );

        assertNotNull(recipe);
        assertEquals(1, recipe.getExtraResults().size());
        Item bucket = recipe.getExtraResults().get(0);
        assertEquals(Item.BUCKET, bucket.getId());
        assertEquals(ItemBucket.EMPTY_BUCKET, bucket.getDamage());
        assertEquals(3, bucket.getCount());
    }

    private static List<Item> cakeInputs() {
        return List.of(
                Item.get(Item.BUCKET, ItemBucket.MILK_BUCKET, 3),
                Item.get(Item.SUGAR, 0, 2),
                Item.get(Item.EGG),
                Item.get(Item.WHEAT, 0, 3)
        );
    }

    private CraftingRecipe findCakeRecipe() {
        for (Recipe recipe : manager.getRecipes()) {
            if (recipe instanceof CraftingRecipe craftingRecipe
                    && Item.CAKE == craftingRecipe.getResult().getId()) {
                return craftingRecipe;
            }
        }
        return null;
    }
}
