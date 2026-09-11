package cn.nukkit.inventory.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.ShapelessRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.plugin.PluginManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ItemStackResponseDurabilityTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void craftedPlankResponseDoesNotExposeWoodVariantAsDurability() {
        Player player = Mockito.mock(Player.class);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        CraftingManager craftingManager = Mockito.mock(CraftingManager.class);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        when(player.getServer()).thenReturn(MockServer.get());
        when(player.getName()).thenReturn("test");
        when(player.getUIInventory()).thenReturn(ui);
        when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        when(player.getTopWindow()).thenReturn(Optional.empty());
        when(MockServer.get().getCraftingManager()).thenReturn(craftingManager);
        when(MockServer.get().getPluginManager()).thenReturn(pluginManager);

        ShapelessRecipe recipe = new ShapelessRecipe(
                "test:acacia_planks",
                10,
                Item.get(Item.PLANKS, 4, 4),
                List.of(Item.get(Item.STONE, 0, 1)));
        when(craftingManager.getRecipeByNetworkId(recipe.getNetworkId())).thenReturn(recipe);
        when(craftingManager.matchRecipe(anyList(), any(Item.class), anyList())).thenReturn(recipe);

        Item stone = Item.get(Item.STONE, 0, 1);
        stone.autoAssignStackNetworkId();
        assertTrue(ui.getCraftingGrid().setItem(0, stone, false));
        stone = ui.getCraftingGrid().getItem(0);

        CraftRecipeAction craft = new CraftRecipeAction(recipe.getNetworkId(), 1);
        ConsumeAction consume = new ConsumeAction(
                1,
                new ItemStackRequestSlotData(
                        ContainerSlotType.CRAFTING_INPUT, 28, stone.getStackNetId(), null));
        ItemStackRequestContext context = new ItemStackRequestContext(new ItemStackRequest(
                1, new ItemStackRequestAction[]{craft, consume}, new String[0]));
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor().handle(craft, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(
                0,
                response.containers().get(0).getItems().get(0).getDurabilityCorrection());
        assertEquals(
                4,
                ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).getDamage(),
                "the server still keeps the acacia variant");
    }

    @Test
    void legacyBlockMetadataIsNotReportedAsDurability() {
        Item acaciaPlanks = Item.get(Item.PLANKS, 4, 4);

        assertEquals(4, acaciaPlanks.getDamage(), "legacy damage stores the wood variant");
        assertEquals(0, ItemStackRequestActionProcessor.durabilityCorrection(acaciaPlanks));
    }

    @Test
    void nonDurableItemMetadataIsNotReportedAsDurability() {
        Item potion = Item.get(Item.POTION, 4, 1);

        assertEquals(4, potion.getDamage(), "legacy damage stores the potion variant");
        assertEquals(0, ItemStackRequestActionProcessor.durabilityCorrection(potion));
    }

    @Test
    void actualDurabilityIsStillReported() {
        Item pickaxe = Item.get(Item.DIAMOND_PICKAXE, 37, 1);

        assertEquals(37, ItemStackRequestActionProcessor.durabilityCorrection(pickaxe));
    }
}
