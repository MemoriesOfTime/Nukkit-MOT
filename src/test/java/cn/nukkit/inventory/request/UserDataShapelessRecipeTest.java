package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.CraftingDataPacket;
import cn.nukkit.network.protocol.v113.ProtocolInfo_v113;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for "shulker_box" (type 5) recipes: loading, NBT-tolerant matching, and NBT preservation.
 */
class UserDataShapelessRecipeTest {

    private static CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
        // protocol < 137 的 DataPacket#reset() 需要 v113 包池；只放行要编码的包类，其余回退 id 可暴露映射错配。
        // DataPacket#reset() needs a v113 pool below protocol 137; only the encoded class gets a real id.
        MockServer.installLegacyPacketPool(CraftingDataPacket.class, ProtocolInfo_v113.CRAFTING_DATA_PACKET);
        manager = new CraftingManager();
    }

    @AfterAll
    static void cleanup() {
        // 包池桩挂在共享 mock Server 上，清掉避免泄漏给同一 fork 的后续测试类。
        // The stub lives on the shared mock Server; reset it for later test classes in the same fork.
        MockServer.reset();
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
    void craftingDataPacketBuildsOnEveryProtocolBranch() {
        // 只取 CraftingDataPacket 的分支边界（含 legacy < 354）；中间版本不会新增编码分支。
        // Only the CraftingDataPacket branch boundaries are kept (including legacy < 354); mid versions add no branch.
        for (GameVersion version : List.of(GameVersion.V1_1_0, GameVersion.V1_11_0, GameVersion.V1_12_0,
                GameVersion.V1_16_0, GameVersion.V1_16_100, GameVersion.V1_19_80, GameVersion.V1_20_0_23,
                GameVersion.V1_20_80, GameVersion.V1_21_0, GameVersion.V1_21_40, GameVersion.V1_26_20_26,
                GameVersion.V1_26_40, GameVersion.V1_26_50)) {
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
