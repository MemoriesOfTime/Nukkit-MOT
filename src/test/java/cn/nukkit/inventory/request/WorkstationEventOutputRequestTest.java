package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.event.inventory.GrindItemEvent;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.event.inventory.SmithingTableEvent;
import cn.nukkit.event.inventory.StonecutterItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftGrindstoneAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeOptionalAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;

class WorkstationEventOutputRequestTest {

    private Player player;
    private PlayerUIInventory ui;
    private PlayerInventory inventory;
    private PluginManager pluginManager;
    private CraftingManager manager;
    private Level level;

    @BeforeAll
    static void init() {
        MockServer.init();
        Item.initCreativeItems();
    }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        player = Mockito.mock(Player.class);
        player.protocol = GameVersion.V1_26_20.getProtocol();
        ui = new PlayerUIInventory(player);
        inventory = new PlayerInventory(player);
        pluginManager = Mockito.mock(PluginManager.class);
        manager = new CraftingManager();
        level = Mockito.mock(Level.class);

        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_26_20);
        Mockito.when(player.isCreative()).thenReturn(true);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player));
        Mockito.when(player.getLevel()).thenReturn(level);
        Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(Block.get(Block.AIR));
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);
        Mockito.when(MockServer.get().getCraftingManager()).thenReturn(manager);

        Mockito.doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            Item output = null;
            if (event instanceof RepairItemEvent e) {
                output = e.getNewItem();
            } else if (event instanceof GrindItemEvent e) {
                output = e.getNewItem();
            } else if (event instanceof EnchantItemEvent e) {
                output = e.getNewItem();
            } else if (event instanceof SmithingTableEvent e) {
                output = e.getResultItem();
            } else if (event instanceof StonecutterItemEvent e) {
                output = e.getOutputItem();
            }
            if (output != null && !output.isNull()) {
                output.setNamedTag(output.getOrCreateNamedTag().putString("km_origin", "kit:workstation"));
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any());
    }

    @Test
    void anvilEventOutputIsActuallyDelivered() {
        AnvilInventory anvil = new AnvilInventory(ui, position());
        bindWindow(anvil, Player.ANVIL_WINDOW_ID);
        Item input = Item.get(Item.DIAMOND_SWORD, 10, 1).autoAssignStackNetworkId();
        assertTrue(anvil.setItem(0, input, false));
        input = anvil.getInputSlot();

        request(new String[]{"renamed"},
                new CraftRecipeOptionalAction(0, 0),
                consume(1, ContainerSlotType.ANVIL_INPUT, 1, input),
                place(0, 1));

        assertDeliveredTag(0);
    }

    @Test
    void grindstoneEventOutputIsActuallyDelivered() {
        GrindstoneInventory grindstone = new GrindstoneInventory(ui, position());
        bindWindow(grindstone, Player.GRINDSTONE_WINDOW_ID);
        Item input = Item.get(Item.DIAMOND_SWORD, 200, 1);
        input.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_DAMAGE_ALL).setLevel(1));
        input.autoAssignStackNetworkId();
        assertTrue(grindstone.setItem(0, input, false));
        input = grindstone.getEquipment();

        request(new String[0],
                new CraftGrindstoneAction(0, 1, 0),
                consume(1, ContainerSlotType.GRINDSTONE_INPUT, 16, input),
                place(1, 1));

        assertDeliveredTag(1);
    }

    @Test
    void enchantEventOutputIsActuallyDelivered() throws Exception {
        EnchantInventory enchant = new EnchantInventory(ui, position());
        bindWindow(enchant, Player.ENCHANT_WINDOW_ID);
        Item input = Item.get(Item.DIAMOND_SWORD, 0, 1).autoAssignStackNetworkId();
        assertTrue(enchant.setItem(0, input, false));
        input = enchant.getInputSlot();
        int recipeId = PlayerEnchantOptionsPacket.assignRecipeId(new PlayerEnchantOptionsPacket.EnchantOptionData(
                1, 0,
                List.of(new PlayerEnchantOptionsPacket.EnchantData(Enchantment.ID_DAMAGE_ALL, 1)),
                List.of(), List.of(), "test", 0));
        markPublishedOption(enchant, recipeId);

        request(new String[0],
                new CraftRecipeAction(recipeId, 1),
                consume(1, ContainerSlotType.ENCHANTING_INPUT, 14, input),
                place(2, 1));

        assertDeliveredTag(2);
    }

    @Test
    void smithingEventOutputIsActuallyDelivered() {
        SmithingTransformRecipe recipe = manager.getSmithingRecipes().values().stream()
                .filter(r -> r instanceof SmithingTransformRecipe)
                .map(r -> (SmithingTransformRecipe) r)
                .filter(r -> r.getResult().getId() == Item.NETHERITE_SWORD)
                .findFirst()
                .orElseThrow();
        SmithingInventory smithing = new SmithingInventory(ui, position());
        bindWindow(smithing, Player.SMITHING_WINDOW_ID);
        Item equipment = recipe.getEquipment().clone().autoAssignStackNetworkId();
        Item ingredient = recipe.getIngredient().clone().autoAssignStackNetworkId();
        Item template = recipe.getTemplate().clone().autoAssignStackNetworkId();
        smithing.setEquipment(equipment);
        smithing.setIngredient(ingredient);
        smithing.setTemplate(template);
        equipment = smithing.getEquipment();
        ingredient = smithing.getIngredient();
        template = smithing.getTemplate();

        request(new String[0],
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                consume(1, ContainerSlotType.SMITHING_TABLE_INPUT, 51, equipment),
                consume(1, ContainerSlotType.SMITHING_TABLE_MATERIAL, 52, ingredient),
                consume(1, ContainerSlotType.SMITHING_TABLE_TEMPLATE, 53, template),
                place(3, 1));

        assertDeliveredTag(3);
    }

    @Test
    void stonecutterEventOutputIsActuallyDelivered() {
        StonecutterRecipe recipe = manager.getStonecutterRecipes().stream()
                .filter(r -> !r.getIngredient().isNull() && !r.getResult().isNull())
                .findFirst()
                .orElseThrow();
        StonecutterInventory stonecutter = new StonecutterInventory(ui, position());
        bindWindow(stonecutter, Player.STONECUTTER_WINDOW_ID);
        Item input = recipe.getIngredient().clone().autoAssignStackNetworkId();
        assertTrue(stonecutter.setItem(0, input, false));
        input = stonecutter.getInput();

        request(new String[0],
                new CraftRecipeAction(recipe.getNetworkId(), 1),
                consume(recipe.getIngredient().getCount(), ContainerSlotType.STONECUTTER_INPUT, 3, input),
                place(4, recipe.getResult().getCount()));

        assertDeliveredTag(4);
    }

    private Position position() {
        return new Position(0, 0, 0, level);
    }

    private void bindWindow(Inventory window, int windowId) {
        Mockito.when(player.getTopWindow()).thenReturn(Optional.of(window));
        Mockito.when(player.getWindowById(windowId)).thenReturn(window);
        Mockito.when(player.getWindowId(window)).thenReturn(windowId);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(inventory)).thenReturn(0);
    }

    private static ConsumeAction consume(int count, ContainerSlotType type, int slot, Item item) {
        return new ConsumeAction(count, new ItemStackRequestSlotData(type, slot, item.getStackNetId(), null));
    }

    private static PlaceAction place(int hotbarSlot, int count) {
        return new PlaceAction(count,
                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, hotbarSlot, 0, null));
    }

    private void request(String[] filters, ItemStackRequestAction... actions) {
        ItemStackRequestHandler.handleRequests(player,
                List.of(new ItemStackRequest(1, actions, filters)));
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player, atLeastOnce()).dataPacket(captor.capture());
        ItemStackResponsePacket response = captor.getAllValues().stream()
                .filter(ItemStackResponsePacket.class::isInstance)
                .map(ItemStackResponsePacket.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals(ItemStackResponseStatus.OK, response.entries.get(0).getResult());
    }

    private void assertDeliveredTag(int slot) {
        assertEquals("kit:workstation", inventory.getItem(slot).getNamedTag().getString("km_origin"));
        assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }

    @SuppressWarnings("unchecked")
    private static void markPublishedOption(EnchantInventory inventory, int recipeId) throws Exception {
        Field field = EnchantInventory.class.getDeclaredField("publishedOptionIds");
        field.setAccessible(true);
        ((Set<Integer>) field.get(inventory)).add(recipeId);
    }
}
