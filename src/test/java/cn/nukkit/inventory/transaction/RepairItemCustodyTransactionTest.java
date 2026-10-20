package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.CraftingGrid;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.action.CraftingTakeResultAction;
import cn.nukkit.inventory.transaction.action.CraftingTransferMaterialAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepairItemCustodyTransactionTest {
    private static CraftingManager manager;

    @BeforeAll
    static void init() {
        MockServer.init();
        manager = new CraftingManager();
    }

    @Test
    void missingCustodyAfterEventRefusesWithoutSpendingSources() throws Exception {
        checkSecondInputMetadata(null, false);
    }

    @Test
    void eventMayPreserveCustodyAndAddMetadata() throws Exception {
        checkSecondInputMetadata("example:original", true);
    }

    @Test
    void eventCannotReplaceProtectedValue() throws Exception {
        checkSecondInputMetadata("example:replacement", false);
    }

    @Test
    void mutatingEventInputsCannotHideOriginalMetadata() throws Exception {
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1);
        Item second = markedInput(225, "example:original");
        check(first, second, Item.get(Item.IRON_SHOVEL, 163, 1), false,
                event -> event.getTransaction().getInputList().forEach(Item::clearNamedTag));
    }

    @Test
    void conflictingInputMetadataRefusesWithoutSpendingEitherInput() throws Exception {
        Item first = markedInput(200, "example:first");
        Item second = markedInput(225, "example:second");
        Item output = Item.get(Item.IRON_SHOVEL, 163, 1).setNamedTag(first.getNamedTag().copy());
        check(first, second, output, false, event -> { });
    }

    @Test
    void namedEnchantedRepairConsumesBothInputsAndProducesOneCleanResult() throws Exception {
        Item first = Item.get(Item.IRON_SHOVEL, 200, 1).setCustomName("First shovel");
        first.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_EFFICIENCY).setLevel(2));
        first.setNamedTag(first.getNamedTag().putInt("RepairCost", 3));
        check(first, Item.get(Item.IRON_SHOVEL, 225, 1), Item.get(Item.IRON_SHOVEL, 163, 1),
                true, event -> { });
    }

    private static Item markedInput(int damage, String origin) {
        return Item.get(Item.IRON_SHOVEL, damage, 1)
                .setNamedTag(new CompoundTag().putString("example:origin", origin));
    }

    private static void checkSecondInputMetadata(String eventOrigin, boolean accepted) throws Exception {
        Item output = Item.get(Item.IRON_SHOVEL, 163, 1);
        if (eventOrigin != null) {
            output.setNamedTag(new CompoundTag()
                    .putString("example:origin", eventOrigin).putString("example:audit", "event"));
        }
        check(Item.get(Item.IRON_SHOVEL, 200, 1), markedInput(225, "example:original"), output,
                accepted, event -> event.getTransaction().setPrimaryOutput(output));
    }

    private static void check(Item first, Item second, Item expectedOutput, boolean accepted,
                              Consumer<CraftItemEvent> callback) throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.grid.setItem(0, first, false));
            assertTrue(f.grid.setItem(1, second, false));
            Item requestedOutput = Item.get(Item.IRON_SHOVEL, 163, 1);
            if (first.hasCompoundTag() && !first.hasCustomName()) {
                requestedOutput.setNamedTag(first.getNamedTag().copy());
            }
            doAnswer(invocation -> {
                if (invocation.getArgument(0) instanceof CraftItemEvent event) {
                    callback.accept(event);
                }
                return null;
            }).when(f.plugins).callEvent(any());
            CraftingTransaction tx = new CraftingTransaction(f.player, List.of(
                    new CraftingTransferMaterialAction(Item.AIR_ITEM.clone(), first.clone(), 0),
                    new CraftingTransferMaterialAction(Item.AIR_ITEM.clone(), second.clone(), 1),
                    new CraftingTakeResultAction(requestedOutput.clone(), Item.AIR_ITEM.clone()),
                    new SlotChangeAction(f.grid, 0, first.clone(), Item.AIR_ITEM.clone()),
                    new SlotChangeAction(f.grid, 1, second.clone(), Item.AIR_ITEM.clone()),
                    new SlotChangeAction(f.main, 0, Item.AIR_ITEM.clone(), requestedOutput.clone())));
            assertTrue(tx.canExecute(), "provisional recipe validates before the event");
            // The native transaction reports success only after the inventory commit.
            assertEquals(accepted, tx.execute());
            assertEquals(accepted, tx.hasExecuted());
            if (accepted) {
                assertTrue(f.grid.getItem(0).isNull());
                assertTrue(f.grid.getItem(1).isNull());
                assertEquals(1, f.main.getItem(0).getCount());
                assertEquals(expectedOutput, f.main.getItem(0));
            } else {
                assertEquals(first, f.grid.getItem(0));
                assertEquals(second, f.grid.getItem(1));
                assertTrue(f.main.getItem(0).isNull());
            }
        }
    }

    /** Installs only this fixture's server and restores the previous singleton on every exit. */
    private static final class Fixture implements AutoCloseable {
        private final Field instanceField;
        private final Server previousServer;
        final PluginManager plugins = mock(PluginManager.class);
        final Player player = mock(Player.class);
        final PlayerInventory main;
        final CraftingGrid grid;

        Fixture() throws Exception {
            instanceField = Server.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            previousServer = (Server) instanceField.get(null);
            Server server = mock(Server.class);
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getCraftingManager()).thenReturn(manager);
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            when(server.isPrimaryThread()).thenReturn(true);

            player.craftingType = Player.CRAFTING_SMALL;
            player.spawned = true;
            when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
            when(player.getServer()).thenReturn(server);
            when(player.getName()).thenReturn("repair-test");
            when(player.isAlive()).thenReturn(true);
            when(player.isOnline()).thenReturn(true);
            PlayerUIInventory ui = new PlayerUIInventory(player);
            main = new PlayerInventory(player);
            grid = ui.getCraftingGrid();
            when(player.getInventory()).thenReturn(main);
            when(player.getCraftingGrid()).thenReturn(grid);
            when(player.getUIInventory()).thenReturn(ui);
            when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
            when(player.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(player));
            when(player.getWindowId(any(Inventory.class))).thenReturn(0);
            instanceField.set(null, server);
        }

        @Override
        public void close() throws Exception {
            instanceField.set(null, previousServer);
        }
    }
}
