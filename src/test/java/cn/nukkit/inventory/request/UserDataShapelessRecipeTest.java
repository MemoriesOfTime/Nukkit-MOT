package cn.nukkit.inventory.request;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for "shulker_box" (type 5) recipes: loading, NBT-tolerant matching, and NBT preservation.
 */
class UserDataShapelessRecipeTest {

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
    void shulkerBoxRecipesAreLoadedAsUserDataShapeless() {
        UserDataShapelessRecipe recipe = findShulkerBoxDyeRecipe();
        assertNotNull(recipe, "Expected at least one shulker_box (type 5) recipe to be loaded");
        assertEquals(RecipeType.SHULKER_BOX, recipe.getType());
    }

    @Test
    void matchRecipeAcceptsInputShulkerBoxCarryingContainerNbt() {
        UserDataShapelessRecipe recipe = findShulkerBoxDyeRecipe();
        assertNotNull(recipe);

        // Input shulker box carries container NBT (Items list) that must survive recoloring.
        Item shulkerInput = Item.get(BlockID.UNDYED_SHULKER_BOX, 0, 1);
        CompoundTag containerTag = new CompoundTag()
                .putList(new ListTag<CompoundTag>("Items"));
        shulkerInput.setCompoundTag(containerTag);

        Item blackDye = Item.get(Item.DYE, ItemDye.BLACK, 1);
        List<Item> inputs = new ArrayList<>(List.of(shulkerInput, blackDye));

        CraftingRecipe matched = manager.matchRecipe(
                inputs,
                recipe.getResult(),
                List.of()
        );
        assertSame(recipe, matched, "Recipe matching must tolerate container NBT on the input shulker box");
    }

    @Test
    void applyInputNbtCarriesContainerNbtToOutput() {
        Item output = Item.get(BlockID.SHULKER_BOX, 0, 1);
        assertFalse(output.hasCompoundTag());

        Item input = Item.get(BlockID.UNDYED_SHULKER_BOX, 0, 1);
        input.setCompoundTag(new CompoundTag().putList(new ListTag<CompoundTag>("Items")));

        CraftRecipeActionProcessor.applyInputNbt(output, List.of(input));

        assertTrue(output.hasCompoundTag(), "Container NBT must be carried onto the crafted output");
    }

    @Test
    void applyInputNbtLeavesOutputUntouchedWhenNoNbtInput() {
        Item output = Item.get(BlockID.SHULKER_BOX, 0, 1);
        Item input = Item.get(BlockID.UNDYED_SHULKER_BOX, 0, 1);

        CraftRecipeActionProcessor.applyInputNbt(output, List.of(input));

        assertFalse(output.hasCompoundTag(), "Output must stay NBT-free when no input carries NBT");
    }

    @Test
    void craftingDataPacketBuildsForAllProtocols() {
        // 构造函数只构建最新版本；此处强制遍历所有协议 (含 legacy < 354 路径) 走 encode()，验证 SHULKER_BOX 分支。
        for (cn.nukkit.GameVersion version : cn.nukkit.GameVersion.getValues()) {
            assertDoesNotThrow(() -> manager.getCachedPacket(version),
                    "CraftingDataPacket must encode for " + version);
        }
    }

    private UserDataShapelessRecipe findShulkerBoxDyeRecipe() {
        for (Recipe recipe : manager.getRecipes()) {
            if (recipe instanceof UserDataShapelessRecipe userData) {
                Item result = userData.getResult();
                if (result.getId() == BlockID.SHULKER_BOX
                        && userData.getIngredientList().stream()
                                .anyMatch(i -> i.getId() == BlockID.UNDYED_SHULKER_BOX)) {
                    return userData;
                }
            }
        }
        return null;
    }
}
