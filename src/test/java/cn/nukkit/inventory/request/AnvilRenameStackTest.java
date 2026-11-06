package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Timeout(15)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.parallel.Isolated
class AnvilRenameStackTest {
    private static Server previousServer;

    @org.junit.jupiter.api.AfterAll
    static void restoreServerInstance() throws ReflectiveOperationException {
        java.lang.reflect.Field instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }

    @BeforeAll static void init() { previousServer = Server.getInstance(); MockServer.init(); }
    private PluginManager previousPluginManager;

    @org.junit.jupiter.api.BeforeEach
    void savePluginManager() {
        previousPluginManager = MockServer.get().getPluginManager();
    }

    @org.junit.jupiter.api.AfterEach
    void restorePluginManager() {
        when(MockServer.get().getPluginManager()).thenReturn(previousPluginManager);
    }

    static Stream<Arguments> countsAndProtocols() {
        return Stream.of(GameVersion.V1_20_0, GameVersion.V1_26_50).flatMap(version ->
                Stream.of(1, 2, 64).map(count -> Arguments.of(version, count)));
    }

    @ParameterizedTest @MethodSource("countsAndProtocols")
    void fullStackRenameConsumesExactlyTheStackAndOneLevel(GameVersion version, int count) {
        Fixture f = new Fixture(version, count, 0, 10);
        assertEquals(ItemStackResponseStatus.OK, f.rename(count, count));
        assertTrue(f.anvil.getInputSlot().isNull());
        assertEquals(count, f.main.getItem(0).getCount());
        assertEquals("Planks", f.main.getItem(0).getCustomName());
        assertEquals(0, f.main.getItem(0).getRepairCost());
        assertEquals(9, f.levels.get());
        assertEquals(count, f.total());
        assertTrue(f.ui.getItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT).isNull());
    }

    @ParameterizedTest @MethodSource("countsAndProtocols")
    void underconsumingRequestCannotMultiplyTheRenamedStack(GameVersion version, int count) {
        Fixture f = new Fixture(version, count, 0, 10);
        ItemStackResponseStatus status = f.rename(1, count);
        assertEquals(count, f.total(), "renamed output plus returned input must equal the original stack");
        assertEquals(count == 1 ? ItemStackResponseStatus.OK : ItemStackResponseStatus.ERROR, status);
        assertEquals(count == 1 ? 9 : 10, f.levels.get());
    }

    @Test void renameCapsThePriceAt39WithoutIncreasingPriorWork() {
        Fixture f = new Fixture(GameVersion.V1_26_50, 64, 63, 40);
        assertEquals(ItemStackResponseStatus.OK, f.rename(64, 64));
        assertEquals(1, f.levels.get());
        assertEquals(63, f.main.getItem(0).getRepairCost());
        assertEquals(64, f.total());
    }

    @Test void insufficientLevelsLeaveBothTheStackAndExperienceUntouched() {
        Fixture f = new Fixture(GameVersion.V1_20_0, 64, 63, 38);
        assertEquals(ItemStackResponseStatus.ERROR, f.rename(64, 64));
        assertEquals(64, f.anvil.getInputSlot().getCount());
        assertTrue(f.main.getItem(0).isNull());
        assertEquals(38, f.levels.get());
    }

    @Test void cancelledRenamePreservesTheWholeInputStack() {
        Fixture f = new Fixture(GameVersion.V1_26_50, 64, 0, 10);
        doAnswer(invocation -> {
            ((RepairItemEvent) invocation.getArgument(0)).setCancelled();
            return null;
        }).when(f.plugins).callEvent(any(RepairItemEvent.class));
        assertEquals(ItemStackResponseStatus.ERROR, f.rename(64, 64));
        assertEquals(64, f.anvil.getInputSlot().getCount());
        assertEquals(10, f.levels.get());
        assertEquals(64, f.total());
    }

    @Test void replayCannotPublishAnotherRenamedStackOrChargeAgain() {
        Fixture f = new Fixture(GameVersion.V1_26_50, 64, 0, 10);
        assertEquals(ItemStackResponseStatus.OK, f.rename(64, 64));
        assertEquals(ItemStackResponseStatus.ERROR, f.rename(64, 64));
        assertEquals(64, f.total());
        assertEquals(9, f.levels.get());
    }

    @Test void identicalNameIsNotASecondPaidOperationOnOldClient() {
        assertIdenticalNameRefused(GameVersion.V1_20_0);
    }

    @Test void identicalNameIsNotASecondPaidOperationOnNewClient() {
        assertIdenticalNameRefused(GameVersion.V1_26_50);
    }

    private static void assertIdenticalNameRefused(GameVersion version) {
        Fixture f = new Fixture(version, 1, 7, 10, "Planks");
        assertEquals(ItemStackResponseStatus.ERROR, f.rename(1, 1));
        assertEquals(10, f.levels.get());
        assertEquals(7, f.anvil.getInputSlot().getRepairCost());
        assertTrue(f.main.getItem(0).isNull());
        assertEquals(1, f.total());
    }

    @Test void materialRepairWithTheSameNamePaysOnlyForTheRepairOnOldClient() {
        assertSameNameMaterialRepair(GameVersion.V1_20_0);
    }

    @Test void materialRepairWithTheSameNamePaysOnlyForTheRepairOnNewClient() {
        assertSameNameMaterialRepair(GameVersion.V1_26_50);
    }

    private static void assertSameNameMaterialRepair(GameVersion version) {
        Fixture f = new Fixture(version, 1, 0, 10, "Planks", Item.DIAMOND_SWORD, 100);
        assertTrue(f.anvil.setItem(1, Item.get(Item.DIAMOND, 0, 1).autoAssignStackNetworkId(), false));
        assertEquals(ItemStackResponseStatus.OK, f.rename(1, 1));
        assertEquals(9, f.levels.get(), "the unchanged name must not add a second level");
        assertEquals(0, f.main.getItem(0).getDamage());
        assertEquals(1, f.main.getItem(0).getRepairCost());
        assertEquals("Planks", f.main.getItem(0).getCustomName());
        assertTrue(f.anvil.getMaterialSlot().isNull());
    }

    private static final class Fixture {
        final Player actor = mock(Player.class);
        final PlayerUIInventory ui = new PlayerUIInventory(actor);
        final PlayerInventory main = new PlayerInventory(actor);
        final PluginManager plugins = mock(PluginManager.class);
        final Level level = mock(Level.class);
        final AnvilInventory anvil = new AnvilInventory(ui, new Position(0, 64, 0, level));
        final AtomicInteger levels;
        final Item original;

        Fixture(GameVersion version, int count, int priorWork, int xp) {
            this(version, count, priorWork, xp, null);
        }

        Fixture(GameVersion version, int count, int priorWork, int xp, String name) {
            this(version, count, priorWork, xp, name, Item.PLANKS, 0);
        }

        Fixture(GameVersion version, int count, int priorWork, int xp, String name, int itemId, int damage) {
            actor.protocol = version.getProtocol();
            levels = new AtomicInteger(xp);
            when(actor.getServer()).thenReturn(MockServer.get());
            when(actor.getGameVersion()).thenReturn(version);
            when(actor.getName()).thenReturn("anvil-test");
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
            Item input = Item.get(itemId, damage, count);
            if (priorWork > 0) input.setRepairCost(priorWork);
            if (name != null) input.setCustomName(name);
            input.autoAssignStackNetworkId();
            assertTrue(anvil.setItem(0, input, false));
            original = anvil.getInputSlot();
        }

        ItemStackResponseStatus rename(int consumeCount, int outputCount) {
            List<ItemStackRequestAction> actions = new ArrayList<>();
            actions.add(new CraftRecipeOptionalAction(0, 0));
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
                    List.of(new ItemStackRequest(1, actions.toArray(ItemStackRequestAction[]::new), new String[]{"Planks"})));
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
}
