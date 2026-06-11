package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingManagerCakeRecipeTest {

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
