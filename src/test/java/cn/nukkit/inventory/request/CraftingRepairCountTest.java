package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.special.RepairItemRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Timeout(30)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.parallel.Isolated
class CraftingRepairCountTest {
    private static Server previousServer;

    @org.junit.jupiter.api.AfterAll
    static void restoreServerInstance() throws ReflectiveOperationException {
        java.lang.reflect.Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }

    private static CraftingManager manager;
    private PluginManager previousPluginManager;
    private CraftingManager previousCraftingManager;
    private boolean previousPrimaryThread;

    @org.junit.jupiter.api.BeforeEach
    void saveServerBindings() {
        previousPluginManager = MockServer.get().getPluginManager();
        previousCraftingManager = MockServer.get().getCraftingManager();
        previousPrimaryThread = MockServer.get().isPrimaryThread();
    }

    @org.junit.jupiter.api.AfterEach
    void restoreServerBindings() {
        when(MockServer.get().getPluginManager()).thenReturn(previousPluginManager);
        when(MockServer.get().getCraftingManager()).thenReturn(previousCraftingManager);
        when(MockServer.get().isPrimaryThread()).thenReturn(previousPrimaryThread);
    }
    @BeforeAll static void init() { previousServer = Server.getInstance(); MockServer.init(); manager = new CraftingManager(); }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void twoToolsProduceOneRepairedToolWithInputAndEventMetadata(GameVersion version) {
        Fixture f = new Fixture(version, true);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof CraftItemEvent event) {
                f.events.incrementAndGet();
                Item result = event.getTransaction().getPrimaryOutput().clone();
                result.setNamedTag(result.getOrCreateNamedTag().putString("example:origin", "example:repair-event"));
                event.getTransaction().setPrimaryOutput(result);
            }
            return null;
        }).when(f.plugins).callEvent(any());
        assertEquals(ItemStackResponseStatus.OK, f.repair(1, 1, false));
        assertEquals(1, f.total());
        assertTrue(f.grid.getItem(0).isNull());
        assertTrue(f.grid.getItem(1).isNull());
        Item delivered = f.main.getItem(0);
        assertEquals(1, delivered.getCount());
        assertEquals(f.output(1).getDamage(), delivered.getDamage());
        assertEquals("protected-item", delivered.getNamedTag().getString("example:seal"));
        assertEquals(7, delivered.getRepairCost());
        assertEquals("example:repair-event", delivered.getNamedTag().getString("example:origin"));
        assertEquals(1, f.events.get());
        assertTrue(f.ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void forgedOutputCountCannotTurnTwoToolsIntoSixtyFour(GameVersion version) {
        Fixture f = new Fixture(version, false);
        ItemStackResponseStatus status = f.repair(64, 1, false);
        assertEquals(ItemStackResponseStatus.ERROR, status);
        f.assertInputsRetained();
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void repeatedRepairCannotPublishOverflowFromOnePair(GameVersion version) {
        Fixture f = new Fixture(version, false);
        ItemStackResponseStatus status = f.repair(1, 64, false);
        assertEquals(ItemStackResponseStatus.ERROR, status);
        f.assertInputsRetained();
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void rejectedFinalTransferRestoresBothToolsAndMetadata(GameVersion version) {
        Fixture f = new Fixture(version, true);
        assertEquals(ItemStackResponseStatus.ERROR, f.repair(1, 1, true));
        f.assertInputsRetained();
    }

    @Test void repairRecipeRejectsForgedOutputCountForBothInputShapes() {
        RepairItemRecipe recipe = new RepairItemRecipe();
        Item left = Item.get(Item.DIAMOND_PICKAXE, 1000, 1);
        Item right = Item.get(Item.DIAMOND_PICKAXE, 1000, 1);
        Item output = Item.get(Item.DIAMOND_PICKAXE, recipe.calculateDamage(1000, 1000, left.getMaxDurability()), 64);
        assertFalse(recipe.canExecute(null, output, List.of(left, right)));
        left.setCount(2);
        assertFalse(recipe.canExecute(null, output, List.of(left)));
    }

    @Test void repairRecipeConsumesExactlyTwoItemsAndRetainsPackedInputCompatibility() {
        RepairItemRecipe recipe = new RepairItemRecipe();
        Item left = Item.get(Item.ELYTRA, 300, 1);
        Item right = Item.get(Item.ELYTRA, 300, 1);
        Item output = Item.get(Item.ELYTRA, recipe.calculateDamage(300, 300, left.getMaxDurability()), 1);
        assertTrue(recipe.canExecute(null, output, List.of(left, right)));
        left.setCount(2);
        assertFalse(recipe.canExecute(null, output, List.of(left, right)));
        assertTrue(recipe.canExecute(null, output, List.of(left)));
    }

    private static final class Fixture {
        final Player player;
        final PlayerUIInventory ui;
        final PlayerInventory main;
        final CraftingGrid grid;
        final PluginManager plugins;
        final RepairItemRecipe recipe;
        final Item left;
        final Item right;
        final AtomicInteger events = new AtomicInteger();
        final List<Item> publishedDrops = new ArrayList<>();

        Fixture(GameVersion version, boolean marked) {
            recipe = manager.getMultiRecipes().values().stream()
                    .filter(RepairItemRecipe.class::isInstance).map(RepairItemRecipe.class::cast)
                    .findFirst().orElseThrow();
            player = mock(Player.class);
            player.protocol = version.getProtocol();
            player.spawned = true;
            player.craftingType = Player.CRAFTING_BIG;
            ui = new PlayerUIInventory(player);
            main = new PlayerInventory(player);
            grid = ui.getBigCraftingGrid();
            PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
            plugins = mock(PluginManager.class);
            Level world = mock(Level.class);
            when(player.getLevel()).thenReturn(world);
            doAnswer(call -> {
                publishedDrops.add(((Item) call.getArgument(1)).clone());
                return null;
            }).when(world).dropItem(any(Vector3.class), any(Item.class));
            when(player.getServer()).thenReturn(MockServer.get());
            when(player.getName()).thenReturn("repair-test");
            when(player.getGameVersion()).thenReturn(version);
            when(player.isAlive()).thenReturn(true);
            when(player.isOnline()).thenReturn(true);
            when(player.getUIInventory()).thenReturn(ui);
            when(player.getInventory()).thenReturn(main);
            when(player.getCraftingGrid()).thenReturn(grid);
            when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
            when(player.getOffhandInventory()).thenReturn(offhand);
            when(player.getTopWindow()).thenReturn(Optional.empty());
            when(player.getWindowId(main)).thenReturn(0);
            when(player.getWindowId(ui)).thenReturn(0);
            when(player.getWindowId(offhand)).thenReturn(0);
            when(MockServer.get().getPluginManager()).thenReturn(plugins);
            when(MockServer.get().getCraftingManager()).thenReturn(manager);
            when(MockServer.get().isPrimaryThread()).thenReturn(true);
            left = Item.get(Item.DIAMOND_PICKAXE, 1000, 1);
            right = Item.get(Item.DIAMOND_PICKAXE, 1000, 1);
            if (marked) {
                left.setNamedTag(left.getOrCreateNamedTag().putString("example:seal", "protected-item").putInt("RepairCost", 7));
            }
            assertTrue(grid.setItem(0, left.clone().autoAssignStackNetworkId(), false));
            assertTrue(grid.setItem(1, right.clone().autoAssignStackNetworkId(), false));
        }

        Item output(int count) {
            Item result = left.clone();
            result.setDamage(recipe.calculateDamage(left.getDamage(), right.getDamage(), left.getMaxDurability()));
            result.setCount(count);
            return result;
        }

        ItemStackResponseStatus repair(int outputCount, int repetitions, boolean invalidFinalTransfer) {
            List<ItemStackRequestAction> actions = new ArrayList<>();
            actions.add(new CraftRecipeAction(recipe.getNetworkId(), repetitions));
            actions.add(new CraftResultsDeprecatedAction(new Item[]{output(outputCount)}, 1));
            for (int slot = 0; slot < 2; slot++) {
                Item input = grid.getItem(slot);
                actions.add(new ConsumeAction(1, new ItemStackRequestSlotData(ContainerSlotType.CRAFTING_INPUT,
                        32 + slot, input.getStackNetId(), null)));
            }
            actions.add(new CreateAction(0));
            for (int slot = 0; slot < Math.min(3, outputCount * repetitions); slot++) {
                actions.add(new PlaceAction(1,
                        new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT, 50, 0, null),
                        new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, slot, 0, null)));
            }
            if (invalidFinalTransfer) {
                actions.add(new PlaceAction(1,
                        new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT, 50, 0, null),
                        new ItemStackRequestSlotData(ContainerSlotType.INVENTORY, 4, 0, null)));
            }
            ItemStackRequestHandler.handleRequests(player,
                    List.of(new ItemStackRequest(1, actions.toArray(ItemStackRequestAction[]::new), new String[0])));
            ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
            verify(player, atLeastOnce()).dataPacket(packets.capture());
            return packets.getAllValues().stream().filter(ItemStackResponsePacket.class::isInstance)
                    .map(ItemStackResponsePacket.class::cast).reduce((a, b) -> b).orElseThrow()
                    .entries.get(0).getResult();
        }

        int total() {
            return main.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + grid.getContents().values().stream().mapToInt(Item::getCount).sum()
                    + ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).getCount()
                    + publishedDrops.stream().mapToInt(Item::getCount).sum();
        }

        void assertInputsRetained() {
            assertEquals(2, total());
            assertTrue(publishedDrops.isEmpty(), "failed requests must not publish repaired overflow into the world");
            assertTrue(main.getContents().isEmpty());
            assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
            assertTrue(grid.getItem(0).equalsExact(left));
            assertTrue(grid.getItem(1).equalsExact(right));
        }
    }
}
