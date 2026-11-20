package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.*;
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
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationEventTest {

    @Nested
    @Timeout(60)
    @Execution(ExecutionMode.SAME_THREAD)
    @Isolated("Uses the mock server singleton and enchantment recipe registry")
    class EventCost {
        private static Server previousServer;
        private static final Set<Integer> recipeIds = new HashSet<>();
        private PluginManager previousPluginManager;

        @BeforeAll
        static void init() {
            previousServer = Server.getInstance();
            MockServer.init();
        }

        @AfterAll
        static void restoreServer() throws Exception {
            Field instance = Server.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, previousServer);
        }

        @BeforeEach
        void savePluginManager() {
            previousPluginManager = MockServer.get().getPluginManager();
        }

        @AfterEach
        void restoreSharedState() {
            when(MockServer.get().getPluginManager()).thenReturn(previousPluginManager);
            recipeIds.forEach(id -> PlayerEnchantOptionsPacket.RECIPE_MAP.remove(id.intValue()));
            recipeIds.clear();
        }

        @Test void negativeAnvilEventCostRejectedOnOldClient() { check(GameVersion.V1_20_0, -1, false); }
        @Test void negativeAnvilEventCostRejectedOnNewClient() { check(GameVersion.V1_26_50, -1, false); }
        @Test void unaffordableAnvilEventCostRejected() { check(GameVersion.V1_26_50, 11, false); }
        @Test void zeroAnvilEventCostPreservesAnAuthorizedDiscount() { check(GameVersion.V1_26_50, 0, true); }
        private static void check(GameVersion version, int fee, boolean accepted) {
            AnvilFixture f = new AnvilFixture(version);
            doAnswer(call -> { ((RepairItemEvent) call.getArgument(0)).setCost(fee); return null; })
                    .when(f.plugins).callEvent(any(RepairItemEvent.class));
            assertEquals(accepted ? ItemStackResponseStatus.OK : ItemStackResponseStatus.ERROR, f.rename(1, 1));
            assertEquals(10, f.levels.get());
            assertEquals(1, f.total());
            assertEquals(accepted, f.anvil.getInputSlot().isNull());
            assertEquals(accepted, !f.main.getItem(0).isNull());
        }
        @Test void duplicatedAnvilGeneratorRejectedOnOldClient() { duplicate(GameVersion.V1_20_0); }
        @Test void duplicatedAnvilGeneratorRejectedOnNewClient() { duplicate(GameVersion.V1_26_50); }
        private static void duplicate(GameVersion version) {
            AnvilFixture f = new AnvilFixture(version);
            assertEquals(ItemStackResponseStatus.ERROR, f.rename(1, 1, true));
            assertEquals(10, f.levels.get());
            assertEquals(1, f.anvil.getInputSlot().getCount());
            assertTrue(f.main.getItem(0).isNull());
        }
        private static final class AnvilFixture {
            final Player actor = mock(Player.class);
            final PlayerUIInventory ui = new PlayerUIInventory(actor);
            final PlayerInventory main = new PlayerInventory(actor);
            final PluginManager plugins = mock(PluginManager.class);
            final Level level = mock(Level.class);
            final AnvilInventory anvil = new AnvilInventory(ui, new Position(0, 64, 0, level));
            final AtomicInteger levels;
            final Item original;

            AnvilFixture(GameVersion version) {
                actor.protocol = version.getProtocol();
                levels = new AtomicInteger(10);
                when(actor.getServer()).thenReturn(MockServer.get());
                when(actor.getGameVersion()).thenReturn(version);
                when(actor.getName()).thenReturn("workstation-test");
                when(actor.getUIInventory()).thenReturn(ui);
                when(actor.getInventory()).thenReturn(main);
                when(actor.getCursorInventory()).thenReturn(ui.getCursorInventory());
                when(actor.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
                when(actor.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(actor));
                when(actor.getLevel()).thenReturn(level);
                when(actor.getExperienceLevel()).thenAnswer(ignored -> levels.get());
                doAnswer(call -> { levels.set(call.getArgument(1)); return null; })
                        .when(actor).setExperience(anyInt(), anyInt());
                when(MockServer.get().getPluginManager()).thenReturn(plugins);
                when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(Block.get(Block.AIR));
                when(actor.getTopWindow()).thenReturn(Optional.of(anvil));
                when(actor.getWindowById(Player.ANVIL_WINDOW_ID)).thenReturn(anvil);
                when(actor.getWindowId(anvil)).thenReturn(Player.ANVIL_WINDOW_ID);
                when(actor.getWindowId(ui)).thenReturn(0);
                when(actor.getWindowId(main)).thenReturn(0);
                Item input = Item.get(Item.PLANKS, 0, 1);
                input.autoAssignStackNetworkId();
                assertTrue(anvil.setItem(0, input, false));
                original = anvil.getInputSlot();
            }

            ItemStackResponseStatus rename(int consumeCount, int outputCount) {
                return rename(consumeCount, outputCount, false);
            }

            ItemStackResponseStatus rename(int consumeCount, int outputCount, boolean duplicate) {
                List<ItemStackRequestAction> actions = new ArrayList<>();
                actions.add(new CraftRecipeOptionalAction(0, 0));
                if (duplicate) actions.add(new CraftRecipeOptionalAction(0, 0));
                actions.add(new ConsumeAction(consumeCount, new ItemStackRequestSlotData(
                        ContainerSlotType.ANVIL_INPUT, 1, original.getStackNetId(), null)));
                Item material = anvil.getMaterialSlot();
                if (!material.isNull()) actions.add(new ConsumeAction(1, new ItemStackRequestSlotData(
                        ContainerSlotType.ANVIL_MATERIAL, 2, material.getStackNetId(), null)));
                actions.add(new PlaceAction(outputCount,
                            new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                                    PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                            new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null)));
                ItemStackRequestHandler.handleRequests(actor,
                        List.of(new ItemStackRequest(1, actions.toArray(ItemStackRequestAction[]::new), new String[]{"Named planks"})));
                ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
                verify(actor, atLeastOnce()).dataPacket(packets.capture());
                return packets.getAllValues().stream().filter(ItemStackResponsePacket.class::isInstance)
                        .map(ItemStackResponsePacket.class::cast).reduce((first, second) -> second)
                        .orElseThrow().entries.get(0).getResult();
            }

            int total() { return count(anvil.getInputSlot()) + count(main.getItem(0))
                    + count(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT)); }
            private static int count(Item item) { return item.isNull() ? 0 : item.getCount(); }
        }

        @ParameterizedTest
        @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
        void duplicateRecipeActionsInOneRequestCannotChargeTheSameEnchantTwice(GameVersion version)
                throws Exception {
            EnchantFixture f = new EnchantFixture(version, 2, 30, 30, 3);
            ItemStackResponseStatus status = f.request(3, true);
            assertAll("one request cannot reserve two payments for one enchantment",
                    () -> assertEquals(ItemStackResponseStatus.ERROR, status),
                    f::assertUnchanged);
        }

        static Stream<Arguments> invalidEventCosts() {
            return Stream.of(GameVersion.V1_20_0, GameVersion.V1_26_50)
                    .flatMap(version -> Stream.of(-1, 31).map(cost -> Arguments.of(version, cost)));
        }

        @ParameterizedTest
        @MethodSource("invalidEventCosts")
        void invalidEventPriceCannotGrantExperienceOrSpendUnaffordableResources(GameVersion version, int cost)
                throws Exception {
            EnchantFixture f = new EnchantFixture(version, 2, 30, 30, 8);
            doAnswer(call -> {
                if (call.getArgument(0) instanceof EnchantItemEvent event) event.setXpCost(cost);
                return null;
            }).when(f.plugins).callEvent(any());
            ItemStackResponseStatus status = f.request(3);
            assertAll("invalid event cost=" + cost,
                    () -> assertEquals(ItemStackResponseStatus.ERROR, status),
                    f::assertUnchanged);
            verify(f.plugins, times(1)).callEvent(any(EnchantItemEvent.class));
        }

        static Stream<Arguments> discountedEventCosts() {
            return Stream.of(GameVersion.V1_20_0, GameVersion.V1_26_50)
                    .flatMap(version -> Stream.of(0, 1).map(cost -> Arguments.of(version, cost)));
        }

        @ParameterizedTest
        @MethodSource("discountedEventCosts")
        void lawfulEventDiscountChangesXpPaymentWithoutChangingTheRequiredLapis(GameVersion version, int cost)
                throws Exception {
            EnchantFixture f = new EnchantFixture(version, 2, 30, 30, 8);
            doAnswer(call -> {
                if (call.getArgument(0) instanceof EnchantItemEvent event) event.setXpCost(cost);
                return null;
            }).when(f.plugins).callEvent(any());
            assertEquals(ItemStackResponseStatus.OK, f.request(3));
            assertAll("lawful event cost=" + cost,
                    () -> assertEquals(30 - cost, f.levels.get()),
                    () -> assertEquals(5, f.enchant.getReagentSlot().getCount()),
                    () -> assertTrue(f.enchant.getInputSlot().isNull()),
                    () -> assertEquals(Item.DIAMOND_SWORD, f.main.getItem(0).getId()),
                    () -> assertEquals(1, f.main.getItem(0).getCount()),
                    () -> assertEquals(1, f.main.getItem(0).getEnchantment(Enchantment.ID_DAMAGE_ALL).getLevel()),
                    () -> assertTrue(f.ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull()));
            verify(f.actor, times(1)).setExperience(17, 30 - cost);
            verify(f.plugins, times(1)).callEvent(any(EnchantItemEvent.class));
        }

        private static final class EnchantFixture {
            final Player actor = mock(Player.class);
            final PluginManager plugins = mock(PluginManager.class);
            final Level level = mock(Level.class);
            final PlayerUIInventory ui = new PlayerUIInventory(actor);
            final PlayerInventory main = new PlayerInventory(actor);
            final EnchantInventory enchant = new EnchantInventory(ui, new Position(0, 0, 0, level));
            final AtomicInteger levels;
            final int originalLevels;
            final Item originalInput;
            final Item originalLapis;
            final int recipeId;
            final PlayerEnchantOptionsPacket.EnchantOptionData option;
            int requestId;

            EnchantFixture(GameVersion version, int row, int minimumLevel, int xp, int lapisCount) throws Exception {
                actor.protocol = version.getProtocol();
                levels = new AtomicInteger(xp);
                originalLevels = xp;
                when(actor.getServer()).thenReturn(MockServer.get());
                when(actor.getName()).thenReturn("workstation-test");
                when(actor.getGameVersion()).thenReturn(version);
                when(actor.getUIInventory()).thenReturn(ui);
                when(actor.getInventory()).thenReturn(main);
                when(actor.getCursorInventory()).thenReturn(ui.getCursorInventory());
                when(actor.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
                when(actor.getOffhandInventory()).thenReturn(new PlayerOffhandInventory(actor));
                when(actor.getLevel()).thenReturn(level);
                when(actor.getExperience()).thenReturn(17);
                when(actor.getExperienceLevel()).thenAnswer(ignored -> levels.get());
                doAnswer(call -> { levels.set(call.getArgument(1)); return null; })
                        .when(actor).setExperience(anyInt(), anyInt());
                when(MockServer.get().getPluginManager()).thenReturn(plugins);
                when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(Block.get(Block.AIR));
                when(actor.getTopWindow()).thenReturn(Optional.of(enchant));
                when(actor.getWindowById(Player.ENCHANT_WINDOW_ID)).thenReturn(enchant);
                when(actor.getWindowId(enchant)).thenReturn(Player.ENCHANT_WINDOW_ID);
                when(actor.getWindowId(ui)).thenReturn(0);
                when(actor.getWindowId(main)).thenReturn(0);
                assertTrue(enchant.setItem(0, Item.get(Item.DIAMOND_SWORD, 0, 1)
                        .autoAssignStackNetworkId(), false));
                assertTrue(enchant.setItem(1, Item.get(Item.DYE, 4, lapisCount)
                        .autoAssignStackNetworkId(), false));
                originalInput = enchant.getInputSlot().clone();
                originalLapis = enchant.getReagentSlot().clone();
                option = new PlayerEnchantOptionsPacket.EnchantOptionData(minimumLevel, row,
                        List.of(new PlayerEnchantOptionsPacket.EnchantData(Enchantment.ID_DAMAGE_ALL, 1)),
                        List.of(), List.of(), "Enchantment", 0);
                recipeId = PlayerEnchantOptionsPacket.assignRecipeId(option);
                recipeIds.add(recipeId);
                publishedIds(enchant).add(recipeId);
            }

            ItemStackResponseStatus request(int lapisConsumed) {
                return request(lapisConsumed, false);
            }

            ItemStackResponseStatus request(int lapisConsumed, boolean duplicateRecipe) {
                ItemStackRequestAction[] actions = {
                        new CraftRecipeAction(recipeId, 1),
                        consume(1, ContainerSlotType.ENCHANTING_INPUT, 14, originalInput),
                        consume(lapisConsumed, ContainerSlotType.ENCHANTING_MATERIAL, 15, originalLapis),
                        new PlaceAction(1,
                                new ItemStackRequestSlotData(ContainerSlotType.CREATED_OUTPUT,
                                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, 0, null),
                                new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, 0, 0, null))
                };
                if (duplicateRecipe) {
                    actions = new ItemStackRequestAction[]{actions[0], new CraftRecipeAction(recipeId, 1),
                            actions[1], actions[2], actions[3]};
                }
                ItemStackRequestHandler.handleRequests(actor,
                        List.of(new ItemStackRequest(++requestId, actions, new String[0])));
                ArgumentCaptor<DataPacket> packets = ArgumentCaptor.forClass(DataPacket.class);
                verify(actor, atLeastOnce()).dataPacket(packets.capture());
                return packets.getAllValues().stream().filter(ItemStackResponsePacket.class::isInstance)
                        .map(ItemStackResponsePacket.class::cast).reduce((first, second) -> second)
                        .orElseThrow().entries.get(0).getResult();
            }

            void assertUnchanged() {
                assertAll("rejected request retains all resources",
                        () -> assertTrue(originalInput.equalsExact(enchant.getInputSlot())),
                        () -> assertTrue(originalLapis.equalsExact(enchant.getReagentSlot())),
                        () -> assertTrue(main.getItem(0).isNull()),
                        () -> assertTrue(ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull()),
                        () -> assertEquals(originalLevels, levels.get()));
                verify(actor, never()).setExperience(anyInt(), anyInt());
            }

            private static ConsumeAction consume(int count, ContainerSlotType type, int slot, Item item) {
                return new ConsumeAction(count, new ItemStackRequestSlotData(type, slot, item.getStackNetId(), null));
            }

            @SuppressWarnings("unchecked")
            private static Set<Integer> publishedIds(EnchantInventory inventory) throws Exception {
                Field field = EnchantInventory.class.getDeclaredField("publishedOptionIds");
                field.setAccessible(true);
                return (Set<Integer>) field.get(inventory);
            }
        }
    }

    @Nested
    class EventOutputDelivery {

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
}
