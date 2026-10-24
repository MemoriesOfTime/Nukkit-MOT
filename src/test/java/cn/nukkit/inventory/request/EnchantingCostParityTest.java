package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.inventory.EnchantInventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.PlayerUIInventory;
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
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native request contracts for the XP requirement, row payment, and rejection paths. */
@Timeout(60)
@Execution(ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.parallel.Isolated
class EnchantingCostParityTest {
    private static Server previousServer;

    @org.junit.jupiter.api.AfterAll
    static void restoreServerInstance() throws ReflectiveOperationException {
        java.lang.reflect.Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }

    private PluginManager previousPluginManager;
    private java.util.Map<Integer, PlayerEnchantOptionsPacket.EnchantOptionData> previousOptions;

    @org.junit.jupiter.api.BeforeEach
    void saveServerBindings() {
        previousPluginManager = MockServer.get().getPluginManager();
        previousOptions = new java.util.HashMap<>(PlayerEnchantOptionsPacket.RECIPE_MAP);
    }

    @org.junit.jupiter.api.AfterEach
    void restoreServerBindings() {
        when(MockServer.get().getPluginManager()).thenReturn(previousPluginManager);
        PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
        PlayerEnchantOptionsPacket.RECIPE_MAP.putAll(previousOptions);
    }

    @BeforeAll
    static void init() {
        previousServer = Server.getInstance();
        MockServer.init();
    }

    static Stream<Arguments> rowsAndProtocols() {
        return Stream.of(GameVersion.V1_20_0, GameVersion.V1_26_50)
                .flatMap(version -> Stream.of(0, 1, 2).map(row -> Arguments.of(version, row)));
    }

    @ParameterizedTest
    @MethodSource("rowsAndProtocols")
    void eachRowPaysItsOwnLevelsAndLapisInsteadOfTheRequiredLevel(GameVersion version, int row)
            throws Exception {
        Fixture f = new Fixture(version, row, 30, 30, 8);
        assertEquals(ItemStackResponseStatus.OK, f.request(row + 1));
        assertAll("row payment and one authoritative result",
                () -> assertEquals(30 - row - 1, f.levels.get()),
                () -> assertEquals(8 - row - 1, f.enchant.getReagentSlot().getCount()),
                () -> assertTrue(f.enchant.getInputSlot().isNull()),
                () -> assertEquals(Item.DIAMOND_SWORD, f.main.getItem(0).getId()),
                () -> assertEquals(1, f.main.getItem(0).getCount()),
                () -> assertEquals(1, f.main.getItem(0).getEnchantment(Enchantment.ID_DAMAGE_ALL).getLevel()),
                () -> assertTrue(f.ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull()),
                () -> assertFalse(f.enchant.hasPublishedOption(f.recipeId)));
        verify(f.actor, times(1)).setExperience(17, 30 - row - 1);
        ArgumentCaptor<EnchantItemEvent> event = ArgumentCaptor.forClass(EnchantItemEvent.class);
        verify(f.plugins, times(1)).callEvent(event.capture());
        assertEquals(row + 1, event.getValue().getXpCost());
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void levelTwentyNineCannotBuyARequiredThirtyOfferEvenWithThreeLevelsToPay(GameVersion version)
            throws Exception {
        Fixture f = new Fixture(version, 2, 30, 29, 8);
        assertEquals(ItemStackResponseStatus.ERROR, f.request(3));
        f.assertUnchanged();
        verify(f.plugins, never()).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void twoLapisCannotBuyTheThirdRow(GameVersion version) throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 2);
        assertEquals(ItemStackResponseStatus.ERROR, f.request(3));
        f.assertUnchanged();
        verify(f.plugins, never()).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void forgedOneLapisConsumeForTheThirdRowLeavesAllResourcesUntouched(GameVersion version)
            throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 8);
        assertEquals(ItemStackResponseStatus.ERROR, f.request(1));
        f.assertUnchanged();
        verify(f.plugins, never()).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void cancelledEnchantEventKeepsTheInputLapisAndExperience(GameVersion version) throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 8);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof EnchantItemEvent event) event.setCancelled();
            return null;
        }).when(f.plugins).callEvent(any());
        assertEquals(ItemStackResponseStatus.ERROR, f.request(3));
        f.assertUnchanged();
        verify(f.plugins, times(1)).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void optionFromAnotherWindowCannotSpendResources(GameVersion version) throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 8);
        f.enchant.releasePublishedOption(f.recipeId);
        // Keep the global option known: failure must come from this window's publication fence.
        PlayerEnchantOptionsPacket.RECIPE_MAP.put(f.recipeId, f.option);
        assertEquals(ItemStackResponseStatus.ERROR, f.request(3));
        f.assertUnchanged();
        verify(f.plugins, never()).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void stalePublishedIdWithoutARecipeCannotSpendResources(GameVersion version) throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 8);
        PlayerEnchantOptionsPacket.RECIPE_MAP.remove(f.recipeId);
        assertEquals(ItemStackResponseStatus.ERROR, f.request(3));
        f.assertUnchanged();
        verify(f.plugins, never()).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void aPaidOptionCannotBeReplayedForASecondResultOrCharge(GameVersion version) throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 8);
        assertEquals(ItemStackResponseStatus.OK, f.request(3));
        Item delivered = f.main.getItem(0).clone();
        Item remainder = f.enchant.getReagentSlot().clone();
        assertEquals(ItemStackResponseStatus.ERROR, f.request(3));
        assertAll("replay changes neither output nor payment",
                () -> assertTrue(delivered.equalsExact(f.main.getItem(0))),
                () -> assertTrue(remainder.equalsExact(f.enchant.getReagentSlot())),
                () -> assertTrue(f.enchant.getInputSlot().isNull()),
                () -> assertTrue(f.ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull()),
                () -> assertEquals(27, f.levels.get()));
        verify(f.actor, times(1)).setExperience(17, 27);
        verify(f.plugins, times(1)).callEvent(any(EnchantItemEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameVersion.class, names = {"V1_20_0", "V1_26_50"})
    void duplicateRecipeActionsInOneRequestCannotChargeTheSameEnchantTwice(GameVersion version)
            throws Exception {
        Fixture f = new Fixture(version, 2, 30, 30, 3);
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
        Fixture f = new Fixture(version, 2, 30, 30, 8);
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
        Fixture f = new Fixture(version, 2, 30, 30, 8);
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

    private static final class Fixture {
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

        Fixture(GameVersion version, int row, int minimumLevel, int xp, int lapisCount) throws Exception {
            PlayerEnchantOptionsPacket.RECIPE_MAP.clear();
            actor.protocol = version.getProtocol();
            levels = new AtomicInteger(xp);
            originalLevels = xp;
            when(actor.getServer()).thenReturn(MockServer.get());
            when(actor.getName()).thenReturn("enchant-test");
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
