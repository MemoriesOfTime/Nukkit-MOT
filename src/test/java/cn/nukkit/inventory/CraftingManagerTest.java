package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CraftingManagerTest {

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

    @Test
    void matchesRawIronBlockUnpackingRecipe() {
        assertMatches(
                List.of(Item.get(255 - BlockID.RAW_IRON_BLOCK)),
                rawIron(9)
        );
    }

    @Test
    void matchesRawIronPackingRecipe() {
        assertMatches(
                List.of(rawIron(9)),
                Item.get(255 - BlockID.RAW_IRON_BLOCK)
        );
    }

    @Test
    void matchesRawGoldBlockUnpackingRecipe() {
        assertMatches(
                List.of(Item.get(255 - BlockID.RAW_GOLD_BLOCK)),
                rawGold(9)
        );
    }

    @Test
    void matchesRawGoldPackingRecipe() {
        assertMatches(
                List.of(rawGold(9)),
                Item.get(255 - BlockID.RAW_GOLD_BLOCK)
        );
    }

    @Test
    void keepsRawCopperUnpackingRecipeAsControl() {
        assertMatches(
                List.of(Item.get(255 - BlockID.RAW_COPPER_BLOCK)),
                rawCopper(9)
        );
    }

    @Test
    void keepsRawCopperPackingRecipeAsControl() {
        assertMatches(
                List.of(rawCopper(9)),
                Item.get(255 - BlockID.RAW_COPPER_BLOCK)
        );
    }

    @Test
    void matchesRawCopperPackingRecipeWithWildcardStringItemDamage() {
        Item damagedRawCopper = rawCopper(9);
        damagedRawCopper.setDamage(7);

        assertMatches(
                List.of(damagedRawCopper),
                Item.get(255 - BlockID.RAW_COPPER_BLOCK)
        );
    }

    @Test
    void rawMaterialsUseStringIdentifiedItems() {
        assertEquals(Item.STRING_IDENTIFIED_ITEM, rawIron(1).getId());
        assertEquals(Item.STRING_IDENTIFIED_ITEM, rawGold(1).getId());
        assertEquals(Item.STRING_IDENTIFIED_ITEM, rawCopper(1).getId());
    }

    /**
     * {@link CraftingManager#unregisterRecipe(Recipe)} 的注册-注销往返测试，
     * 覆盖各配方分支与 networkId / XP 映射的清理。
     * <p>
     * Round-trip tests for {@link CraftingManager#unregisterRecipe(Recipe)}, covering every
     * recipe branch plus the network id and XP map cleanup.
     */
    @Test
    void unregisterVanillaShapelessRecipe() {
        List<Recipe> recipesToRemove = new ArrayList<>();
        for (Recipe recipe : manager.getRecipes()) {
            if (recipe instanceof CraftingRecipe craftingRecipe && "minecraft:bundle".equals(craftingRecipe.getResult().getNamespaceId())) {
                recipesToRemove.add(recipe);
            }
        }
        assertFalse(recipesToRemove.isEmpty(), "expected bundle's recipe to exist");
        for (Recipe recipe : recipesToRemove) {
            assertTrue(manager.unregisterRecipe(recipe), "bundle recipe should have been unregistered");
        }
        boolean bundleRecipeExists = manager.getRecipes().stream().filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast)
                                            .anyMatch(recipe -> "minecraft:bundle".equals(recipe.getResult().getNamespaceId()));
        assertFalse(bundleRecipeExists, "bundle's recipe should have been removed");
        assertFalse(manager.getShapelessRecipes().values().stream().flatMap(map -> map.values().stream()).anyMatch(recipe -> "minecraft:bundle".equals(recipe.getResult().getNamespaceId())));
    }

    @Test
    void unregisterShapedRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(
                Item.get(Item.GOLD_INGOT),
                new String[]{"A"},
                Map.of('A', Item.get(Item.IRON_INGOT)),
                new ArrayList<>()
        );
        manager.registerRecipe(recipe);

        assertTrue(manager.unregisterRecipe(recipe), "shaped recipe should have been unregistered");
        assertFalse(manager.getRecipes().contains(recipe), "shaped recipe should have been removed from recipes");
        assertTrue(manager.getShapedRecipes().values().stream().noneMatch(map -> map.containsValue(recipe)), "shaped recipe should have been removed from shapedRecipes");
        assertNull(manager.getRecipeByNetworkId(recipe.getNetworkId()), "network id mapping should have been removed");
    }

    /**
     * SmithingRecipe extends ShapelessRecipe, so the instanceof dispatch in
     * unregisterRecipe must check SmithingRecipe first; this test guards that order.
     */
    @Test
    void unregisterSmithingRecipe() {
        SmithingRecipe recipe = new SmithingRecipe(
                "test_smithing",
                1,
                Arrays.asList(Item.get(Item.IRON_INGOT), Item.get(Item.DIAMOND_SWORD)),
                Item.get(Item.DIAMOND_SWORD)
        );
        manager.registerSmithingRecipe(recipe);
        assertTrue(manager.getSmithingRecipes().containsValue(recipe), "smithing recipe should be registered");

        assertTrue(manager.unregisterRecipe(recipe), "smithing recipe should have been unregistered");
        assertFalse(manager.getSmithingRecipes().containsValue(recipe), "smithing recipe should have been removed from smithingRecipes");
        assertNull(manager.getRecipeByNetworkId(recipe.getNetworkId()), "network id mapping should have been removed");

        assertFalse(manager.unregisterRecipe(recipe), "unregistering twice should return false");
    }

    @Test
    void unregisterFurnaceRecipe() {
        FurnaceRecipe recipe = new FurnaceRecipe(Item.get(Item.DIAMOND), Item.get(Item.BEDROCK));
        manager.registerFurnaceRecipe(recipe);
        assertTrue(manager.matchFurnaceRecipe(Item.get(Item.BEDROCK)) == recipe, "furnace recipe should be registered");

        assertTrue(manager.unregisterRecipe(recipe), "furnace recipe should have been unregistered");
        assertNull(manager.matchFurnaceRecipe(Item.get(Item.BEDROCK)), "furnace recipe should no longer match");
    }

    @Test
    void unregisterMultiBrewingAndContainerRecipes() {
        MultiRecipe multi = new MultiRecipe(UUID.nameUUIDFromBytes("test_multi".getBytes()));
        manager.registerMultiRecipe(multi);
        assertTrue(manager.unregisterRecipe(multi), "multi recipe should have been unregistered");
        assertFalse(manager.multiRecipes.containsValue(multi));
        assertNull(manager.getRecipeByNetworkId(multi.getNetworkId()));

        BrewingRecipe brewing = new BrewingRecipe(Item.get(Item.POTION), Item.get(Item.DIAMOND), Item.get(Item.POTION));
        manager.registerBrewingRecipe(brewing);
        assertTrue(manager.unregisterRecipe(brewing), "brewing recipe should have been unregistered");
        assertFalse(manager.brewingRecipes.containsValue(brewing));

        ContainerRecipe container = new ContainerRecipe(Item.get(Item.GLASS_BOTTLE), Item.get(Item.SPIDER_EYE), Item.get(Item.POTION));
        manager.registerContainerRecipe(container);
        assertTrue(manager.unregisterRecipe(container), "container recipe should have been unregistered");
        assertFalse(manager.containerRecipes.containsValue(container));
    }

    @Test
    void unregisterRemovesRecipeXp() {
        ShapedRecipe recipe = new ShapedRecipe(
                Item.get(Item.GOLD_INGOT),
                new String[]{"A"},
                Map.of('A', Item.get(Item.IRON_INGOT)),
                new ArrayList<>()
        );
        manager.registerRecipe(recipe);
        manager.setRecipeXp(recipe, 1.5);

        assertTrue(manager.unregisterRecipe(recipe));
        assertTrue(manager.getRecipeXp(recipe) == 0.0, "recipe xp entry should have been removed");
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

    private static Item rawIron(int count) {
        Item item = Item.fromString("minecraft:raw_iron");
        item.setCount(count);
        return item;
    }

    private static Item rawGold(int count) {
        Item item = Item.fromString("minecraft:raw_gold");
        item.setCount(count);
        return item;
    }

    private static Item rawCopper(int count) {
        Item item = Item.fromString("minecraft:raw_copper");
        item.setCount(count);
        return item;
    }

    private void assertMatches(List<Item> inputs, Item output) {
        CraftingRecipe recipe = manager.matchRecipe(new ArrayList<>(inputs), output, Collections.emptyList());
        assertNotNull(recipe);
    }
}
