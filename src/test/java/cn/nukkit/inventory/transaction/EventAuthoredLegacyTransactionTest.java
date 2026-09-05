package cn.nukkit.inventory.transaction;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.event.inventory.GrindItemEvent;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.event.inventory.SmithingTableEvent;
import cn.nukkit.event.inventory.StonecutterItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventAuthoredLegacyTransactionTest {

    private Player player;
    private PlayerUIInventory ui;
    private PlayerInventory inventory;
    private PluginManager pluginManager;
    private Level level;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        MockServer.reset();
        player = Mockito.mock(Player.class);
        player.craftingType = Player.CRAFTING_BIG;
        ui = new PlayerUIInventory(player);
        inventory = new PlayerInventory(player);
        pluginManager = Mockito.mock(PluginManager.class);
        level = Mockito.mock(Level.class);
        player.level = level;

        Mockito.when(player.getServer()).thenReturn(MockServer.get());
        Mockito.when(player.getName()).thenReturn("test");
        Mockito.when(player.isCreative()).thenReturn(true);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getInventory()).thenReturn(inventory);
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getBigCraftingGrid());
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(pluginManager);
        Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(Block.get(Block.AIR));

        Mockito.doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            Item output = null;
            if (event instanceof CraftItemEvent e) {
                output = e.getTransaction().getPrimaryOutput().clone();
                output.setNamedTag(output.getOrCreateNamedTag().putString("km_origin", "kit:legacy"));
                e.getTransaction().setPrimaryOutput(output);
                return null;
            } else if (event instanceof RepairItemEvent e) {
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
                output.setNamedTag(output.getOrCreateNamedTag().putString("km_origin", "kit:legacy"));
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any());
    }

    @Test
    void craftingEventOutputRewritesAndExecutesRealSlotAction() {
        Item output = Item.get(Item.BREAD, 0, 1);
        List<InventoryAction> actions = new ArrayList<>();
        actions.add(new CraftingTakeResultAction(output.clone(), Item.get(Item.AIR)));
        actions.add(delivery(0, output));
        CraftingTransaction transaction = new CraftingTransaction(player, actions) {
            @Override public boolean canExecute() { return true; }
        };

        assertTrue(transaction.execute());
        assertDelivered(0);
    }

    @Test
    void anvilEventOutputRewritesAndExecutesRealSlotAction() {
        AnvilInventory anvil = new AnvilInventory(ui, position());
        bindWindow(Player.ANVIL_WINDOW_ID, anvil);
        Item output = Item.get(Item.DIAMOND_SWORD, 10, 1);
        RepairItemTransaction transaction = new RepairItemTransaction(player, List.of(
                new RepairItemAction(output.clone(), Item.get(Item.AIR), NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                delivery(1, output))) {
            @Override public boolean canExecute() { return true; }
        };

        assertTrue(transaction.execute());
        assertDelivered(1);
    }

    @Test
    void grindstoneEventOutputRewritesAndExecutesRealSlotAction() {
        GrindstoneInventory grindstone = new GrindstoneInventory(ui, position());
        bindWindow(Player.GRINDSTONE_WINDOW_ID, grindstone);
        Item output = Item.get(Item.DIAMOND_SWORD, 10, 1);
        GrindstoneTransaction transaction = new GrindstoneTransaction(player, List.of(
                new GrindstoneItemAction(output.clone(), Item.get(Item.AIR), NetworkInventoryAction.SOURCE_TYPE_ANVIL_RESULT),
                delivery(2, output))) {
            @Override public boolean canExecute() { return true; }
        };

        assertTrue(transaction.execute());
        assertDelivered(2);
    }

    @Test
    void enchantEventOutputRewritesAndExecutesRealSlotAction() {
        EnchantInventory enchant = new EnchantInventory(ui, position());
        bindWindow(Player.ENCHANT_WINDOW_ID, enchant);
        Item output = Item.get(Item.DIAMOND_SWORD, 0, 1);
        EnchantTransaction transaction = new EnchantTransaction(player, List.of(
                new EnchantingAction(output.clone(), Item.get(Item.AIR), NetworkInventoryAction.SOURCE_TYPE_ENCHANT_OUTPUT),
                delivery(3, output))) {
            @Override public boolean canExecute() { return true; }
        };

        assertTrue(transaction.execute());
        assertDelivered(3);
    }

    @Test
    void smithingEventOutputRewritesAndExecutesRealSlotAction() {
        SmithingInventory smithing = new SmithingInventory(ui, position());
        bindWindow(Player.SMITHING_WINDOW_ID, smithing);
        Item output = Item.get(Item.NETHERITE_SWORD, 0, 1);
        SmithingTransaction transaction = new SmithingTransaction(player, List.of(
                new SmithingItemAction(output.clone(), Item.get(Item.AIR), 2),
                delivery(4, output))) {
            @Override public boolean canExecute() { return true; }
        };

        assertTrue(transaction.execute());
        assertDelivered(4);
    }

    @Test
    void stonecutterEventOutputIsUsedByItsRealManualDeliveryPath() {
        StonecutterInventory stonecutter = new StonecutterInventory(ui, position());
        bindWindow(Player.STONECUTTER_WINDOW_ID, stonecutter);
        Item input = Item.get(Item.STONE, 0, 1);
        Item output = Item.get(Item.STONE_BRICKS, 0, 1);
        assertTrue(stonecutter.setItem(0, input, false));
        CraftingManager manager = new CraftingManager();
        manager.registerStonecutterRecipe(new StonecutterRecipe("test:stone", 0, output, input));
        Mockito.when(MockServer.get().getCraftingManager()).thenReturn(manager);
        StonecutterTransaction transaction = new StonecutterTransaction(player, List.of(
                new StonecutterItemAction(Item.get(Item.AIR), input,
                        NetworkInventoryAction.SOURCE_TYPE_CRAFTING_USE_INGREDIENT),
                new StonecutterItemAction(output, Item.get(Item.AIR),
                        NetworkInventoryAction.SOURCE_TYPE_CRAFTING_RESULT)));

        assertTrue(transaction.execute());
        assertDelivered(0);
    }

    private Position position() {
        return new Position(0, 0, 0, level);
    }

    private void bindWindow(int id, Inventory window) {
        Mockito.when(player.getWindowById(id)).thenReturn(window);
    }

    private SlotChangeAction delivery(int slot, Item output) {
        return new SlotChangeAction(inventory, slot, Item.get(Item.AIR), output.clone());
    }

    private void assertDelivered(int slot) {
        assertEquals("kit:legacy", inventory.getItem(slot).getNamedTag().getString("km_origin"));
    }
}
