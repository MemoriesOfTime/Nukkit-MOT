package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CraftingManager#unregisterRecipe(Recipe)} 的注册-注销往返测试，
 * 覆盖各配方分支与 networkId / XP 映射的清理。
 * <p>
 * Round-trip tests for {@link CraftingManager#unregisterRecipe(Recipe)}, covering every
 * recipe branch plus the network id and XP map cleanup.
 */
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

    @Test
    void concurrentIterationWhileRegisteringAndUnregisteringIsSafe() throws Exception {
        // getRecipes() 交出活引用：无锁迭代须在并发 register/unregister 下不 CME；latch 保证迭代始于首次变更之后（零重叠假绿）
        // Live reference out: lock-free iteration must not CME under concurrent mutation; the latch
        // starts iteration after the first mutation (zero overlap would fake-green)
        java.util.concurrent.CountDownLatch firstAdd = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread mutator = new Thread(() -> {
            for (int i = 0; i < 500; i++) {
                ShapedRecipe recipe = new ShapedRecipe(
                        Item.get(Item.GOLD_INGOT),
                        new String[]{"A"},
                        Map.of('A', Item.get(Item.IRON_INGOT)),
                        new ArrayList<>()
                );
                manager.registerRecipe(recipe);
                if (i == 0) {
                    firstAdd.countDown();
                }
                manager.unregisterRecipe(recipe);
            }
            done.set(true);
        }, "CraftingManagerConcurrentMutationTest");
        mutator.start();
        assertTrue(firstAdd.await(5, java.util.concurrent.TimeUnit.SECONDS), "mutator should make its first add");
        try {
            while (!done.get()) {
                for (Recipe ignored : manager.getRecipes()) {
                    // 空循环体即断言：底层抛并发异常则失败 / iterating is the assertion
                }
            }
        } finally {
            mutator.join(30_000);
        }
        assertFalse(mutator.isAlive(), "mutator thread should finish");
    }
}
