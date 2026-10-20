package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.special.RepairItemRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static cn.nukkit.inventory.request.CakeCraftingReproTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepairItemMetadataRequestTest {
    @BeforeAll
    static void init() {
        CakeCraftingReproTest.init();
    }
    @BeforeEach
    void setUp() {
        new CakeCraftingReproTest().setUp();
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void namedRepairConsumesBothInputsAndPublishesOneResult(GameVersion version) {
        bindPlayer(version);
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1).setCustomName("First shovel");
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1);
        execute(first, second, Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.OK, lastResponse().entries.get(0).getResult());
        assertTrue(ui.getBigCraftingGrid().getItem(0).isNull());
        assertTrue(ui.getBigCraftingGrid().getItem(1).isNull());
        Item result = inventory.getItem(2);
        assertEquals(Item.IRON_SHOVEL, result.getId());
        assertEquals(163, result.getDamage());
        assertEquals(1, result.getCount());
        assertFalse(result.hasCustomName());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void namedRepairCannotDiscardUnknownMetadataFromSecondInput(GameVersion version) {
        bindPlayer(version);
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1).setCustomName("First shovel");
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1).setNamedTag(new CompoundTag().putString("example:owner", "owned"));
        execute(first, second, Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.ERROR, lastResponse().entries.get(0).getResult());
        assertEquals(first, ui.getBigCraftingGrid().getItem(0));
        assertEquals(second, ui.getBigCraftingGrid().getItem(1));
        assertTrue(inventory.getItem(2).isNull());
    }

    @ParameterizedTest
    @CsvSource({"V1_21_70,example:owner", "V1_26_50,example:owner", "V1_21_70,example:seal",
            "V1_26_50,example:seal", "V1_21_70,example:origin", "V1_26_50,example:origin"})
    void plainFirstCannotDiscardSecondInputCustodyMetadata(GameVersion version, String key) {
        bindPlayer(version);
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1);
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1)
                .setNamedTag(new CompoundTag().putString(key, "owned"));
        execute(first, second, Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.ERROR, lastResponse().entries.get(0).getResult());
        assertEquals(first, ui.getBigCraftingGrid().getItem(0));
        assertEquals(second, ui.getBigCraftingGrid().getItem(1));
        assertTrue(inventory.getItem(2).isNull());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void eventMayRestoreCustodyAndAddMetadata(GameVersion version) {
        bindPlayer(version);
        authorOutput("example:original");
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1);
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1)
                .setNamedTag(new CompoundTag().putString("example:origin", "example:original"));
        execute(first, second, Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.OK, lastResponse().entries.get(0).getResult());
        assertTrue(ui.getBigCraftingGrid().getItem(0).isNull());
        assertTrue(ui.getBigCraftingGrid().getItem(1).isNull());
        assertEquals("example:original", inventory.getItem(2).getNamedTag().getString("example:origin"));
        assertEquals("event", inventory.getItem(2).getNamedTag().getString("example:audit"));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void eventCannotReplaceProtectedInputValue(GameVersion version) {
        bindPlayer(version);
        authorOutput("example:replacement");
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1);
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1)
                .setNamedTag(new CompoundTag().putString("example:origin", "example:original"));
        execute(first, second, Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.ERROR, lastResponse().entries.get(0).getResult());
        assertEquals(first, ui.getBigCraftingGrid().getItem(0));
        assertEquals(second, ui.getBigCraftingGrid().getItem(1));
        assertTrue(inventory.getItem(2).isNull());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void conflictingProtectedInputValuesCannotBeMerged(GameVersion version) {
        bindPlayer(version);
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1)
                .setNamedTag(new CompoundTag().putString("example:owner", "first"));
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1)
                .setNamedTag(new CompoundTag().putString("example:owner", "second"));
        Item output = Item.get(Item.IRON_SHOVEL, 163, 1).setNamedTag(first.getNamedTag().copy());
        execute(first, second, output);
        assertEquals(ItemStackResponseStatus.ERROR, lastResponse().entries.get(0).getResult());
        assertEquals(first, ui.getBigCraftingGrid().getItem(0));
        assertEquals(second, ui.getBigCraftingGrid().getItem(1));
        assertTrue(inventory.getItem(2).isNull());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void matchingProtectedInputValuesAreRetained(GameVersion version) {
        bindPlayer(version);
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1)
                .setNamedTag(new CompoundTag().putString("example:origin", "example:original"));
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1).setNamedTag(first.getNamedTag().copy());
        Item output = Item.get(Item.IRON_SHOVEL, 163, 1).setNamedTag(first.getNamedTag().copy());
        execute(first, second, output);
        assertEquals(ItemStackResponseStatus.OK, lastResponse().entries.get(0).getResult());
        assertTrue(ui.getBigCraftingGrid().getItem(0).isNull());
        assertTrue(ui.getBigCraftingGrid().getItem(1).isNull());
        assertEquals("example:original", inventory.getItem(2).getNamedTag().getString("example:origin"));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void ordinaryEnchantmentsAndPriorWorkAreRemoved(GameVersion version) {
        bindPlayer(version);
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1);
        first.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(2));
        first.setNamedTag(first.getNamedTag().putInt("RepairCost", 3));
        execute(first, Item.get(Item.IRON_SHOVEL, 225, 1), Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.OK, lastResponse().entries.get(0).getResult());
        assertTrue(ui.getBigCraftingGrid().getItem(0).isNull());
        assertTrue(ui.getBigCraftingGrid().getItem(1).isNull());
        assertEquals(1, inventory.getItem(2).getCount());
        assertFalse(inventory.getItem(2).hasCompoundTag());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_21_70", "V1_26_50"})
    void mutatingEventInputSnapshotCannotHideOriginalMetadata(GameVersion version) {
        bindPlayer(version);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof CraftItemEvent event) {
                event.getTransaction().getInputList().forEach(Item::clearNamedTag);
            }
            return null;
        }).when(pluginManager).callEvent(any());
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1);
        Item second = Item.get(Item.IRON_SHOVEL, 225, 1)
                .setNamedTag(new CompoundTag().putString("example:owner", "original"));
        execute(first, second, Item.get(Item.IRON_SHOVEL, 163, 1));
        assertEquals(ItemStackResponseStatus.ERROR, lastResponse().entries.get(0).getResult());
        assertEquals(first, ui.getBigCraftingGrid().getItem(0));
        assertEquals(second, ui.getBigCraftingGrid().getItem(1));
        assertTrue(inventory.getItem(2).isNull());
    }

    private static void authorOutput(String origin) {
        doAnswer(invocation -> {
            Object called = invocation.getArgument(0);
            if (called instanceof CraftItemEvent event) {
                Item output = event.getTransaction().getPrimaryOutput().clone();
                output.setNamedTag(output.getOrCreateNamedTag()
                        .putString("example:origin", origin).putString("example:audit", "event"));
                event.getTransaction().setPrimaryOutput(output);
            }
            return null;
        }).when(pluginManager).callEvent(any());
    }

    private static void execute(Item first, Item second, Item output) {
        RepairItemRecipe recipe = manager.getMultiRecipes().values().stream()
                .filter(RepairItemRecipe.class::isInstance)
                .map(RepairItemRecipe.class::cast).findFirst().orElseThrow();
        ui.getBigCraftingGrid().setItem(0, first, false);
        ui.getBigCraftingGrid().setItem(1, second, false);
        ItemStackRequest request = new ItemStackRequest(3, join(
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                new CraftResultsDeprecatedAction(new Item[]{output}, 1),
                new ConsumeAction(1, gridSlot(0)), new ConsumeAction(1, gridSlot(1)),
                createAction(0), placeCreatedToHotbar(2, 1)), new String[0]);
        ItemStackRequestHandler.handleRequests(player, List.of(request));
    }
}
