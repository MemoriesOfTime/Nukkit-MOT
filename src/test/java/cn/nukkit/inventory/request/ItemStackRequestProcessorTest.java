package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.event.inventory.InventoryEvent;
import cn.nukkit.event.inventory.ItemStackRequestActionEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.special.FireworkRecipe;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.descriptor.ComplexAliasDescriptor;
import cn.nukkit.network.protocol.types.inventory.descriptor.DefaultDescriptor;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemDescriptorWithCount;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemTagDescriptor;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.utils.TradeRecipeBuildUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemStackRequestProcessorTest {

    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        TradeRecipeBuildUtils.RECIPE_MAP.clear();
    }

    @Test
    void creativeCraftWithZeroRequestedCountCreatesFullStack() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);

        List<Item> creativeItems = Item.getCreativeItems(GameVersion.V1_21_130);
        int creativeIndex = -1;
        for (int i = 0; i < creativeItems.size(); i++) {
            if (creativeItems.get(i).getMaxStackSize() > 1) {
                creativeIndex = i;
                break;
            }
        }
        assertTrue(creativeIndex >= 0, "creative catalog should contain stackable items");

        Item expected = creativeItems.get(creativeIndex);
        ItemStackRequestContext context = context();
        ActionResponse response = new CraftCreativeActionProcessor()
                .handle(new CraftCreativeAction(creativeIndex + 1, 0), player, context);

        assertNull(response);
        assertEquals(Boolean.TRUE, context.get(CraftCreativeActionProcessor.CRAFT_CREATIVE_KEY));
        Item created = ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT);
        assertEquals(expected.getId(), created.getId());
        assertEquals(expected.getMaxStackSize(), created.getCount());
        assertTrue(created.getStackNetId() > 0);
    }

    @Test
    void creativeCreatedOutputTakeCanOverwriteDifferentDestinationItem() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        Item source = Item.get(Item.DIAMOND, 0, 64);
        source.autoAssignStackNetworkId();
        assertTrue(ui.setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, source, false));
        source = ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT);

        Item existing = Item.get(Item.DIRT, 0, 5);
        existing.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, existing, false));
        existing = inventory.getItem(0);

        ItemStackRequestContext context = context();
        context.put(CraftCreativeActionProcessor.CRAFT_CREATIVE_KEY, true);
        TakeAction action = new TakeAction(
                64,
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, source.getStackNetId(), null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, existing.getStackNetId(), null)
        );

        ActionResponse response = new TakeActionProcessor().handle(action, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(1, response.containers().size());
        assertEquals(ContainerSlotType.HOTBAR, response.containers().get(0).getContainer());
        Item dest = inventory.getItem(0);
        assertEquals(Item.DIAMOND, dest.getId());
        assertEquals(64, dest.getCount());
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }

    @Test
    void suppressedDestroyStillMutatesInventory() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item item = Item.get(Item.STONE, 0, 5);
        item.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, item, false));
        item = inventory.getItem(0);

        ItemStackRequestContext context = context();
        context.put(DestroyActionProcessor.NO_RESPONSE_DESTROY_KEY, true);
        DestroyAction action = new DestroyAction(
                2,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, item.getStackNetId(), null)
        );

        ActionResponse response = new DestroyActionProcessor().handle(action, player, context);

        assertNull(response);
        assertEquals(3, inventory.getItem(0).getCount());
    }

    @Test
    void dropActionOnlyDropsItemOnCommit() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item item = Item.get(Item.STONE, 0, 5);
        item.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, item, false));
        item = inventory.getItem(0);

        ItemStackRequestContext context = context();
        DropAction action = new DropAction(
                2,
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, item.getStackNetId(), null),
                false
        );

        ActionResponse response = new DropActionProcessor().handle(action, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(3, inventory.getItem(0).getCount());
        verify(player, never()).dropItem(any(Item.class));

        assertTrue(context.commit());
        ArgumentCaptor<Item> dropped = ArgumentCaptor.forClass(Item.class);
        verify(player).dropItem(dropped.capture());
        assertEquals(Item.STONE, dropped.getValue().getId());
        assertEquals(2, dropped.getValue().getCount());
    }

    @Test
    void mineBlockResponseCarriesInventoryDynamicId() {
        Player player = mockPlayer();
        PlayerInventory inventory = new PlayerInventory(player);
        Mockito.when(player.getInventory()).thenReturn(inventory);

        Item item = Item.get(Item.STONE, 0, 1);
        item.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, item, false));
        item = inventory.getItem(0);

        ActionResponse response = new MineBlockActionProcessor()
                .handle(new MineBlockAction(0, 0, item.getStackNetId()), player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(1, response.containers().size());
        assertEquals(ContainerSlotType.HOTBAR_AND_INVENTORY, response.containers().get(0).getContainerName().getContainer());
        assertEquals(0, response.containers().get(0).getContainerName().getDynamicId());
    }

    @Test
    void multiRecipeRequiresMatchingConsumePlan() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());

        Item paper = Item.get(Item.PAPER, 0, 4);
        paper.autoAssignStackNetworkId();
        Item powder = Item.get(Item.GUNPOWDER, 0, 4);
        powder.autoAssignStackNetworkId();
        assertTrue(ui.getCraftingGrid().setItem(0, paper, false));
        assertTrue(ui.getCraftingGrid().setItem(1, powder, false));
        paper = ui.getCraftingGrid().getItem(0);
        powder = ui.getCraftingGrid().getItem(1);

        MultiRecipe recipe = new FireworkRecipe();
        Item output = Item.get(Item.FIREWORKS, 0, 3);

        ItemStackRequestContext missingConsumes = context(
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                new CraftResultsDeprecatedAction(new Item[]{output}, 1)
        );
        missingConsumes.setCurrentActionIndex(0);
        assertFalse(CraftRecipeActionProcessor.validateMultiRecipeConsumePlan(player, recipe, output, missingConsumes));

        ItemStackRequestContext withConsumes = context(
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 28, paper.getStackNetId(), null)),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT, 29, powder.getStackNetId(), null)),
                new CraftResultsDeprecatedAction(new Item[]{output}, 1)
        );
        withConsumes.setCurrentActionIndex(0);
        assertTrue(CraftRecipeActionProcessor.validateMultiRecipeConsumePlan(player, recipe, output, withConsumes));
    }

    @Test
    void autoCraftUsesConsumedItemsWhenCraftingGridIsEmpty() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory inventory = new PlayerInventory(player);
        CraftingManager craftingManager = Mockito.mock(CraftingManager.class);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getServer().getCraftingManager()).thenReturn(craftingManager);

        ShapelessRecipe recipe = new ShapelessRecipe("test:auto_stone_to_diamond", 10,
                Item.get(Item.DIAMOND, 0, 1),
                List.of(Item.get(Item.STONE, 0, 1)));
        Mockito.when(craftingManager.getRecipeByNetworkId(recipe.getNetworkId())).thenReturn(recipe);
        Mockito.when(craftingManager.matchRecipe(anyList(), any(Item.class), anyList())).thenAnswer(invocation -> {
            List<Item> inputs = cloneItems(invocation.getArgument(0));
            Item output = invocation.getArgument(1);
            List<Item> expected = new ArrayList<>(List.of(Item.get(Item.STONE, 0, 1)));
            return output.getId() == Item.DIAMOND
                    && output.getCount() == 1
                    && Recipe.matchItemList(inputs, expected) ? recipe : null;
        });

        Item stone = Item.get(Item.STONE, 0, 1);
        stone.autoAssignStackNetworkId();
        assertTrue(inventory.setItem(0, stone, false));
        stone = inventory.getItem(0);

        AutoCraftRecipeAction action = new AutoCraftRecipeAction(
                recipe.getNetworkId(),
                1,
                1,
                List.of(new ItemDescriptorWithCount(new DefaultDescriptor(Item.STONE, 0), 1))
        );
        ItemStackRequestContext context = context(
                action,
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, stone.getStackNetId(), null))
        );
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeAutoProcessor().handle(action, player, context);

        assertNotNull(response);
        assertTrue(response.success());
        Item output = ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT);
        assertEquals(Item.DIAMOND, output.getId());
        assertEquals(1, output.getCount());
        assertTrue(ui.getCraftingGrid().getItem(0).isNull(), "auto craft must not require prefilled crafting grid");
    }

    @Test
    void beaconPaymentRequiresDestroyFromPaymentSlot() {
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        BeaconInventory beacon = new BeaconInventory(ui, new Position());
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(beacon));
        Mockito.when(player.getWindowById(Player.BEACON_WINDOW_ID)).thenReturn(beacon);

        Item payment = Item.get(Item.EMERALD, 0, 1);
        payment.autoAssignStackNetworkId();
        assertTrue(beacon.setItem(0, payment, false));
        payment = beacon.getItem(0);

        ItemStackRequestContext missingDestroy = context(new BeaconPaymentAction(1, 0));
        missingDestroy.setCurrentActionIndex(0);
        assertFalse(BeaconPaymentActionProcessor.hasValidPaymentDestroyAction(player, beacon, missingDestroy));

        ItemStackRequestContext withDestroy = context(
                new BeaconPaymentAction(1, 0),
                new DestroyAction(1, new ItemStackRequestSlotData(ContainerSlotType.BEACON_PAYMENT, 27, payment.getStackNetId(), null))
        );
        withDestroy.setCurrentActionIndex(0);
        assertTrue(BeaconPaymentActionProcessor.hasValidPaymentDestroyAction(player, beacon, withDestroy));
    }

    @Test
    void rollbackClearsNewDoubleChestSlots() throws Exception {
        BlockEntityChest leftHolder = Mockito.mock(BlockEntityChest.class);
        BlockEntityChest rightHolder = Mockito.mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        Mockito.when(leftHolder.getRealInventory()).thenReturn(left);
        Mockito.when(rightHolder.getRealInventory()).thenReturn(right);
        DoubleChestInventory doubleChest = new DoubleChestInventory(leftHolder, rightHolder);

        assertTrue(doubleChest.setItem(left.getSize(), Item.get(Item.DIAMOND, 0, 1), false));
        Method restore = ItemStackRequestHandler.class.getDeclaredMethod("restoreInventory", cn.nukkit.inventory.Inventory.class, Map.class);
        restore.setAccessible(true);
        restore.invoke(null, doubleChest, Map.of());

        assertTrue(right.getItem(0).isNull(), "rollback should clear slots backed by the real chest side");
    }

    @Test
    void eventOnlyTransactionRejectsBindingCurseArmorRemoval() {
        Player player = mockPlayer();
        Mockito.when(player.isCreative()).thenReturn(false);
        PlayerInventory inventory = new PlayerInventory(player);

        Item helmet = Item.get(Item.DIAMOND_HELMET, 0, 1);
        helmet.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_BINDING_CURSE));
        assertTrue(inventory.setItem(36, helmet, false));
        helmet = inventory.getItem(36);

        List<InventoryAction> actions = List.of(
                new SlotChangeAction(inventory, 36, helmet, Item.get(Item.AIR))
        );
        var transaction = new TransferItemActionProcessor.EventOnlyInventoryTransaction(player, actions, context());

        assertFalse(transaction.execute(), "SAI compatibility transaction must keep the legacy binding curse guard");
    }

    @Test
    void dynamicContainerTransferRespondsForDifferentDynamicIdsWithSameSlot() {
        Player player = mockPlayer();
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
        ItemBundle sourceBundle = new ItemBundle();
        ItemBundle destinationBundle = new ItemBundle();

        Item sourceItem = Item.get(Item.STONE, 0, 2);
        sourceItem.autoAssignStackNetworkId();
        assertTrue(sourceBundle.getInventory().setItem(0, sourceItem, false));
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(inventory.getContents()).thenReturn(Map.of(0, sourceBundle, 1, destinationBundle));

        sourceItem = sourceBundle.getInventory().getItem(0);
        PlaceAction action = new PlaceAction(
                1,
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, sourceItem.getStackNetId(), sourceBundle.getBundleId()),
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0, 0, destinationBundle.getBundleId())
        );

        ActionResponse response = new PlaceActionProcessor().handle(action, player, context());

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(2, response.containers().size(), "source and destination bundles need separate responses");
        assertEquals(sourceBundle.getBundleId(), response.containers().get(0).getContainerName().getDynamicId());
        assertEquals(destinationBundle.getBundleId(), response.containers().get(1).getContainerName().getDynamicId());
    }

    @Test
    void itemStackRequestActionEventIsNotInventoryEvent() {
        assertFalse(InventoryEvent.class.isAssignableFrom(ItemStackRequestActionEvent.class));
    }

    @Test
    void tagDescriptorsMatchRegisteredItemTags() {
        Item planks = Mockito.mock(Item.class);
        Mockito.when(planks.isNull()).thenReturn(false);
        Mockito.when(planks.getNamespaceId()).thenReturn("minecraft:planks");

        assertTrue(new ItemTagDescriptor("minecraft:planks").match(planks));
        assertTrue(new ComplexAliasDescriptor("minecraft:planks").match(planks));
    }

    @Test
    void enchantRecipeRequiresCurrentWindowRecipeId() throws Exception {
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        EnchantInventory current = new EnchantInventory(ui, new Position());
        EnchantInventory other = new EnchantInventory(ui, new Position());
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(current));

        Item sword = Item.get(Item.DIAMOND_SWORD, 0, 1);
        sword.autoAssignStackNetworkId();
        assertTrue(current.setItem(0, sword, false));

        int recipeId = PlayerEnchantOptionsPacket.assignRecipeId(enchantOption(1, 0));
        markPublishedOption(other, recipeId);

        ItemStackRequestContext context = context(new CraftRecipeAction(recipeId, 1));
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor()
                .handle(new CraftRecipeAction(recipeId, 1), player, context);

        assertNotNull(response);
        assertFalse(response.success());
    }

    @Test
    void enchantRecipeRequiresDisplayedMinimumLevel() throws Exception {
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        Player player = mockPlayer();
        Mockito.when(player.isCreative()).thenReturn(false);
        Mockito.when(player.getExperienceLevel()).thenReturn(5);
        Mockito.when(player.getExperience()).thenReturn(0);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        EnchantInventory enchant = new EnchantInventory(ui, new Position());
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(enchant));
        Mockito.when(player.getWindowById(Player.ENCHANT_WINDOW_ID)).thenReturn(enchant);

        Item sword = Item.get(Item.DIAMOND_SWORD, 0, 1);
        sword.autoAssignStackNetworkId();
        assertTrue(enchant.setItem(0, sword, false));
        sword = enchant.getItem(0);
        Item lapis = Item.get(Item.DYE, 4, 3);
        lapis.autoAssignStackNetworkId();
        assertTrue(enchant.setItem(1, lapis, false));
        lapis = enchant.getItem(1);

        int recipeId = PlayerEnchantOptionsPacket.assignRecipeId(enchantOption(30, 0));
        markPublishedOption(enchant, recipeId);
        ItemStackRequestContext context = context(
                new CraftRecipeAction(recipeId, 1),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.ENCHANTING_INPUT, 14, sword.getStackNetId(), null)),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.ENCHANTING_MATERIAL, 15, lapis.getStackNetId(), null))
        );
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor()
                .handle(new CraftRecipeAction(recipeId, 1), player, context);

        assertNotNull(response);
        assertFalse(response.success());
    }

    @Test
    void tradeRecipeRequiresCurrentVillagerRecipeId() throws Exception {
        TradeRecipeBuildUtils.RECIPE_MAP.clear();
        Player player = mockPlayer();
        PlayerUIInventory ui = new PlayerUIInventory(player);
        EntityVillager villager = Mockito.mock(EntityVillager.class);
        TradeInventory tradeInventory = new TradeInventory(villager);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(tradeInventory));

        CompoundTag currentRecipe = tradeRecipe(Item.get(Item.COAL, 0, 1), Item.get(Item.APPLE, 0, 1));
        int currentRecipeId = TradeRecipeBuildUtils.assignRecipeId(currentRecipe);
        currentRecipe.putInt("netId", currentRecipeId);
        ListTag<Tag> recipes = new ListTag<>("Recipes");
        recipes.add(currentRecipe);
        Mockito.when(villager.getRecipes()).thenReturn(recipes);
        markAssignedTradeRecipe(tradeInventory, currentRecipeId);

        CompoundTag foreignRecipe = tradeRecipe(Item.get(Item.EMERALD, 0, 1), Item.get(Item.DIAMOND, 0, 1));
        int foreignRecipeId = TradeRecipeBuildUtils.assignRecipeId(foreignRecipe);
        foreignRecipe.putInt("netId", foreignRecipeId);

        Item emerald = Item.get(Item.EMERALD, 0, 1);
        emerald.autoAssignStackNetworkId();
        assertTrue(tradeInventory.setItem(0, emerald, false));
        emerald = tradeInventory.getItem(0);
        ItemStackRequestContext context = context(
                new CraftRecipeAction(foreignRecipeId, 1),
                new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.TRADE2_INGREDIENT_1, 0, emerald.getStackNetId(), null))
        );
        context.setCurrentActionIndex(0);

        ActionResponse response = new CraftRecipeActionProcessor()
                .handle(new CraftRecipeAction(foreignRecipeId, 1), player, context);

        assertNotNull(response);
        assertFalse(response.success());
    }

    private static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        Mockito.when(player.isCreative()).thenReturn(true);
        return player;
    }

    private static ItemStackRequestContext context() {
        return context(new ItemStackRequestAction[0]);
    }

    private static ItemStackRequestContext context(ItemStackRequestAction... actions) {
        return new ItemStackRequestContext(new ItemStackRequest(
                1,
                actions,
                new String[0]
        ));
    }

    private static List<Item> cloneItems(List<Item> items) {
        List<Item> cloned = new ArrayList<>(items.size());
        for (Item item : items) {
            cloned.add(item.clone());
        }
        return cloned;
    }

    private static PlayerEnchantOptionsPacket.EnchantOptionData enchantOption(int minLevel, int primarySlot) {
        return new PlayerEnchantOptionsPacket.EnchantOptionData(
                minLevel,
                primarySlot,
                List.of(new PlayerEnchantOptionsPacket.EnchantData(Enchantment.ID_DAMAGE_ALL, 1)),
                List.of(),
                List.of(),
                "test",
                0
        );
    }

    @SuppressWarnings("unchecked")
    private static void markPublishedOption(EnchantInventory inventory, int recipeId) throws Exception {
        Field field = EnchantInventory.class.getDeclaredField("publishedOptionIds");
        field.setAccessible(true);
        ((Set<Integer>) field.get(inventory)).add(recipeId);
    }

    private static CompoundTag tradeRecipe(Item buy, Item sell) {
        return new TradeInventoryRecipe(sell, buy).toNBT();
    }

    @SuppressWarnings("unchecked")
    private static void markAssignedTradeRecipe(TradeInventory inventory, int recipeId) throws Exception {
        Field field = TradeInventory.class.getDeclaredField("assignedRecipeIds");
        field.setAccessible(true);
        ((Set<Integer>) field.get(inventory)).add(recipeId);
    }
}
