package cn.nukkit;

import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.event.player.PlayerGameModeChangeEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerCreativeUiResetTest {

    private Level level;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        level = mock(Level.class);
        when(MockServer.get().getDefaultLevel()).thenReturn(level);
        when(MockServer.get().getPluginManager()).thenReturn(mock(PluginManager.class));
        when(MockServer.get().getCraftingManager()).thenReturn(mock(CraftingManager.class));
    }

    static Stream<Arguments> creativeCases() {
        return Arrays.stream(new int[]{589, 786, 2168, 2169}).boxed()
                .flatMap(protocol -> Stream.of(false, true)
                        .flatMap(big -> Stream.of(false, true)
                                .map(full -> Arguments.of(protocol, big, full))));
    }

    @ParameterizedTest
    @MethodSource("creativeCases")
    void creativeResetDiscardsCraftCursorAndStationWithoutReturningOrDropping(
            int protocol, boolean big, boolean full) {
        TestPlayer player = player(Player.CREATIVE, protocol, big);
        if (full) fill(player);
        int original = count(player);
        fillTransient(player);

        player.resetCraftingGridType();

        assertEquals(original, count(player), "creative UI must not enter the permanent inventory");
        assertTrue(player.getUIInventory().getContents().isEmpty());
        assertSame(player.getUIInventory().getCraftingGrid(), player.getCraftingGrid());
        assertEquals(Player.CRAFTING_SMALL, player.craftingType);
        verify(level, never()).dropItem(any(), any());

        // A late close after the mode switch cannot resurrect any of these free items.
        player.gamemode = Player.SURVIVAL;
        player.resetCraftingGridType();
        assertEquals(original, count(player));
        verify(level, never()).dropItem(any(), any());
    }

    @Test
    void chatCannotFlushAFullCreativeCraftingGridIntoTheWorld() {
        TestPlayer player = player(Player.CREATIVE, 2169, false);
        fill(player);
        fillTransient(player);

        player.chat("");

        assertEquals(36 * 64, count(player));
        assertTrue(player.getUIInventory().getContents().isEmpty());
        verify(level, never()).dropItem(any(), any());
    }

    static Stream<Arguments> modeChangeCases() {
        return Arrays.stream(new int[]{589, 786, 2168, 2169}).boxed()
                .flatMap(protocol -> Arrays.stream(new int[]{Player.CREATIVE, Player.SURVIVAL, Player.ADVENTURE}).boxed()
                        .flatMap(mode -> Stream.of(false, true)
                                .map(cancelled -> Arguments.of(protocol, mode, cancelled))));
    }

    @ParameterizedTest
    @MethodSource("modeChangeCases")
    void realModeChangeResolvesUiAfterAcceptanceAndBeforePublishingMode(
            int protocol, int mode, boolean cancelled) {
        TestPlayer player = player(mode, protocol, false);
        if (mode == Player.CREATIVE || cancelled) {
            fill(player);
        } else {
            assertTrue(player.getInventory().setItem(0, Item.get(Item.IRON_INGOT, 0, 64), false));
        }
        int original = count(player);
        fillTransient(player);
        int transientCount = 4 * 64 + 17 + 11;
        PluginManager plugins = MockServer.get().getPluginManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof PlayerGameModeChangeEvent change) {
                assertEquals(mode, player.getGamemode(), "the event still observes the old mode");
                assertEquals(original, count(player));
                assertEquals(transientCount, player.getUIInventory().getContents().values().stream()
                        .mapToInt(Item::getCount).sum(), "listeners must be able to capture or refuse the intact UI");
                change.setCancelled(cancelled);
            }
            return null;
        }).when(plugins).callEvent(any());
        int target = mode == Player.CREATIVE ? Player.SURVIVAL : Player.CREATIVE;

        assertEquals(!cancelled, player.setGamemode(target));

        verify(plugins).callEvent(isA(PlayerGameModeChangeEvent.class));
        assertEquals(cancelled ? mode : target, player.getGamemode());
        if (cancelled) {
            assertEquals(original, count(player));
            assertEquals(transientCount, player.getUIInventory().getContents().values().stream()
                    .mapToInt(Item::getCount).sum(), "cancelled or MOVING transitions must preserve honest UI");
            assertEquals("kept cursor", player.getCursorInventory().getItem(0).getCustomName());
        } else {
            int expected = mode == Player.CREATIVE ? original : original + transientCount;
            assertEquals(expected, count(player));
            assertTrue(player.getUIInventory().getContents().isEmpty());
            // A close under the new mode cannot leak creative items or erase finite ones.
            player.resetCraftingGridType();
            assertEquals(expected, count(player));
            if (mode != Player.CREATIVE) {
                assertEquals(17, player.getInventory().getContents().values().stream()
                        .filter(item -> item.getCustomName().equals("kept cursor"))
                        .mapToInt(Item::getCount).sum());
            }
        }
        verify(level, never()).dropItem(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {Player.SURVIVAL, Player.ADVENTURE})
    void finiteResetReturnsEveryItemWithNbtWhenThereIsSpace(int mode) {
        TestPlayer player = player(mode, 2169, false);
        fillTransient(player);

        player.resetCraftingGridType();

        assertEquals(4 * 64 + 17 + 11, count(player));
        assertEquals(17, player.getInventory().getContents().values().stream()
                .filter(item -> item.hasCustomName() && item.getCustomName().equals("kept cursor"))
                .mapToInt(Item::getCount).sum());
        assertTrue(player.getUIInventory().getContents().isEmpty());
        verify(level, never()).dropItem(any(), any());
        player.resetCraftingGridType();
        assertEquals(4 * 64 + 17 + 11, count(player), "repeated reset must be idempotent");
    }

    @ParameterizedTest
    @ValueSource(ints = {Player.SURVIVAL, Player.ADVENTURE})
    void finiteResetDropsOnlyOverflowAndPreservesItsNbt(int mode) {
        TestPlayer player = player(mode, 589, false);
        fill(player);
        fillTransient(player);

        player.resetCraftingGridType();

        ArgumentCaptor<Item> drops = ArgumentCaptor.forClass(Item.class);
        verify(level, times(6)).dropItem(same(player), drops.capture());
        assertEquals(4 * 64 + 17 + 11, drops.getAllValues().stream().mapToInt(Item::getCount).sum());
        assertTrue(drops.getAllValues().stream()
                .anyMatch(item -> item.getCount() == 17 && item.getCustomName().equals("kept cursor")));
        assertEquals(36 * 64, count(player));
        assertTrue(player.getUIInventory().getContents().isEmpty());
        player.resetCraftingGridType();
        verify(level, times(6)).dropItem(any(), any());
    }

    /**
     * A workstation UI closed through the real path: ContainerClosePacket -> removeWindow
     * -> inventory.close -> onClose, the flow a client close actually takes.
     */
    private record Station(String name, int windowId,
                           BiFunction<PlayerUIInventory, Position, FakeBlockUIComponent> factory,
                           int inputSlots) {
    }

    private static List<Station> stations() {
        return List.of(
                new Station("enchant", Player.ENCHANT_WINDOW_ID, EnchantInventory::new, 2),
                new Station("grindstone", Player.GRINDSTONE_WINDOW_ID, GrindstoneInventory::new, 2),
                new Station("smithing", Player.SMITHING_WINDOW_ID, SmithingInventory::new, 3),
                new Station("stonecutter", Player.STONECUTTER_WINDOW_ID, StonecutterInventory::new, 1),
                new Station("cartography", Player.CARTOGRAPHY_WINDOW_ID, CartographyTableInventory::new, 2));
    }

    private static Station station(String name) {
        return stations().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private FakeBlockUIComponent openStation(TestPlayer player, Station station) {
        FakeBlockUIComponent inv = station.factory().apply(player.getUIInventory(), new Position(0, 64, 0, level));
        player.windowIndex.put(station.windowId(), inv);
        return inv;
    }

    private static int placeStationItems(FakeBlockUIComponent inv, Station station) {
        int total = 0;
        for (int slot = 0; slot < station.inputSlots(); slot++) {
            int amount = 64 >> slot; // 64, 32, 16: distinct counts per slot
            assertTrue(inv.setItem(slot, Item.get(Item.DIAMOND, 0, amount), false));
            total += amount;
        }
        return total;
    }

    @Test
    void creativeRealCloseDiscardsStationInputs() {
        for (boolean full : new boolean[]{false, true}) {
            for (Station station : stations()) {
                clearInvocations(level);
                TestPlayer player = player(Player.CREATIVE, 2169, false);
                if (full) {
                    fill(player);
                }
                int original = count(player);
                FakeBlockUIComponent inv = openStation(player, station);
                placeStationItems(inv, station);

                player.removeWindow(inv);

                assertEquals(original, count(player),
                        station.name() + ": creative close must not return free items to the backpack");
                assertTrue(inv.getContents().isEmpty(), station.name());
                assertTrue(player.getUIInventory().getContents().isEmpty(), station.name());
                verify(level, never()).dropItem(any(), any());
            }
        }
    }

    @Test
    void creativeRealCloseDiscardsTradeInputs() {
        TestPlayer player = player(Player.CREATIVE, 2169, false);
        TradeInventory trade = new TradeInventory(mock(EntityVillager.class));
        assertTrue(trade.setItem(0, Item.get(Item.EMERALD, 0, 64), false));
        assertTrue(trade.setItem(1, Item.get(Item.DIAMOND, 0, 32), false));

        player.removeWindow(trade);

        assertEquals(0, count(player), "creative trade close must not return free items");
        assertTrue(trade.getContents().isEmpty());
        verify(level, never()).dropItem(any(), any());
    }

    @Test
    void finiteRealCloseReturnsStationInputsOrDropsOverflow() {
        for (Station station : stations()) {
            clearInvocations(level);
            TestPlayer player = player(Player.SURVIVAL, 2169, false);
            FakeBlockUIComponent inv = openStation(player, station);
            int placed = placeStationItems(inv, station);

            player.removeWindow(inv);

            assertEquals(placed, count(player), station.name() + ": finite close must recover the inputs");
            assertTrue(inv.getContents().isEmpty(), station.name());
            verify(level, never()).dropItem(any(), any());

            player.removeWindow(inv);
            assertEquals(placed, count(player), station.name() + ": repeated close must be idempotent");

            clearInvocations(level);
            TestPlayer fullPlayer = player(Player.SURVIVAL, 2169, false);
            fill(fullPlayer);
            FakeBlockUIComponent fullInv = openStation(fullPlayer, station);
            placeStationItems(fullInv, station);

            fullPlayer.removeWindow(fullInv);

            ArgumentCaptor<Item> drops = ArgumentCaptor.forClass(Item.class);
            verify(level, times(station.inputSlots())).dropItem(any(), drops.capture());
            assertEquals(placed, drops.getAllValues().stream().mapToInt(Item::getCount).sum(),
                    station.name() + ": overflow must keep its full counts");
            assertEquals(36 * 64, count(fullPlayer), station.name());
            assertTrue(fullInv.getContents().isEmpty(), station.name());
        }
    }

    @Test
    void stationClosedInsideModeChangeEventResolvesUnderTheOldMode() {
        PluginManager plugins = MockServer.get().getPluginManager();

        TestPlayer creative = player(Player.CREATIVE, 2169, false);
        FakeBlockUIComponent enchant = openStation(creative, station("enchant"));
        assertTrue(enchant.setItem(0, Item.get(Item.DIAMOND, 0, 64), false));
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof PlayerGameModeChangeEvent) {
                creative.removeWindow(enchant);
            }
            return null;
        }).when(plugins).callEvent(any());

        assertTrue(creative.setGamemode(Player.SURVIVAL));

        assertEquals(0, count(creative), "a close during the event must discard under the old creative mode");
        verify(level, never()).dropItem(any(), any());

        clearInvocations(level);
        TestPlayer finite = player(Player.SURVIVAL, 2169, false);
        FakeBlockUIComponent grindstone = openStation(finite, station("grindstone"));
        assertTrue(grindstone.setItem(0, Item.get(Item.DIAMOND, 0, 64), false));
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof PlayerGameModeChangeEvent) {
                finite.removeWindow(grindstone);
            }
            return null;
        }).when(plugins).callEvent(any());

        assertTrue(finite.setGamemode(Player.CREATIVE));

        assertEquals(64, count(finite), "a close during the event must recover under the old survival mode");
        verify(level, never()).dropItem(any(), any());
    }

    @Test
    void disconnectedBeaconCloseDropsForFiniteModesButNotCreative() {
        TestPlayer creative = player(Player.CREATIVE, 2169, false);
        creative.connected = false;
        BeaconInventory creativeBeacon = new BeaconInventory(creative.getUIInventory(), new Position(0, 64, 0, level));
        assertTrue(creativeBeacon.setItem(0, Item.get(Item.IRON_INGOT, 0, 3), false));

        creative.removeWindow(creativeBeacon);

        assertTrue(creativeBeacon.getContents().isEmpty());
        verify(level, never()).dropItem(any(), any());

        TestPlayer finite = player(Player.SURVIVAL, 2169, false);
        finite.connected = false;
        BeaconInventory finiteBeacon = new BeaconInventory(finite.getUIInventory(), new Position(0, 64, 0, level));
        assertTrue(finiteBeacon.setItem(0, Item.get(Item.IRON_INGOT, 0, 3), false));

        finite.removeWindow(finiteBeacon);

        ArgumentCaptor<Item> drops = ArgumentCaptor.forClass(Item.class);
        verify(level, times(1)).dropItem(any(), drops.capture());
        assertEquals(3, drops.getValue().getCount());
    }

    @Test
    void modeSwitchResolvesOpenTradeInputsUnderTheOldMode() {
        PluginManager plugins = MockServer.get().getPluginManager();

        for (boolean full : new boolean[]{false, true}) {
            clearInvocations(level);
            TestPlayer player = player(Player.CREATIVE, 2169, false);
            if (full) {
                fill(player);
            }
            int original = count(player);
            TradeInventory trade = openTrade(player);
            assertTrue(trade.setItem(0, Item.get(Item.DIAMOND, 0, 64), false));
            assertTrue(trade.setItem(1, Item.get(Item.EMERALD, 0, 32), false));

            assertTrue(player.setGamemode(Player.SURVIVAL));

            assertTrue(trade.getContents().isEmpty(), "the switch must discard the free trade inputs");
            // A delayed close under the new mode cannot resurrect them.
            player.removeWindow(trade);
            assertEquals(original, count(player));
            verify(level, never()).dropItem(any(), any());
        }

        clearInvocations(level);
        TestPlayer finite = player(Player.SURVIVAL, 2169, false);
        TradeInventory finiteTrade = openTrade(finite);
        assertTrue(finiteTrade.setItem(0, Item.get(Item.DIAMOND, 0, 64), false));
        assertTrue(finiteTrade.setItem(1, Item.get(Item.EMERALD, 0, 32), false));

        assertTrue(finite.setGamemode(Player.CREATIVE));

        assertEquals(96, count(finite), "finite trade inputs must be recovered under the old survival mode");
        assertTrue(finiteTrade.getContents().isEmpty());
        verify(level, never()).dropItem(any(), any());

        clearInvocations(level);
        TestPlayer cancelled = player(Player.CREATIVE, 2169, false);
        TradeInventory keptTrade = openTrade(cancelled);
        assertTrue(keptTrade.setItem(0, Item.get(Item.DIAMOND, 0, 64), false));
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof PlayerGameModeChangeEvent change) {
                change.setCancelled(true);
            }
            return null;
        }).when(plugins).callEvent(any());

        assertFalse(cancelled.setGamemode(Player.SURVIVAL));

        assertEquals(64, keptTrade.getItem(0).getCount(), "a cancelled transition must preserve the trade inputs");
        assertEquals(0, count(cancelled));
        verify(level, never()).dropItem(any(), any());
    }

    private TradeInventory openTrade(TestPlayer player) {
        TradeInventory trade = new TradeInventory(mock(EntityVillager.class));
        player.windowIndex.put(42, trade); // trade windows register under a dynamic id
        return trade;
    }

    private void fillTransient(TestPlayer player) {
        for (int slot = 0; slot < 4; slot++) {
            assertTrue(player.getCraftingGrid().setItem(slot, Item.get(Item.DIAMOND_BLOCK, 0, 64), false));
        }
        Item cursor = Item.get(Item.GOLD_INGOT, 0, 17);
        cursor.setCustomName("kept cursor");
        assertTrue(player.getCursorInventory().setItem(0, cursor, false));
        AnvilInventory anvil = new AnvilInventory(player.getUIInventory(), new Position(0, 64, 0, level));
        player.windowIndex.put(Player.ANVIL_WINDOW_ID, anvil);
        assertTrue(anvil.setItem(0, Item.get(Item.EMERALD, 0, 11), false));
    }

    private static void fill(TestPlayer player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            assertTrue(player.getInventory().setItem(slot, Item.get(Item.IRON_INGOT, 0, 64), false));
        }
    }

    private static int count(TestPlayer player) {
        return player.getInventory().getContents().values().stream().mapToInt(Item::getCount).sum();
    }

    private TestPlayer player(int mode, int protocol, boolean big) {
        SourceInterface source = mock(SourceInterface.class);
        when(source.getSession(any(InetSocketAddress.class))).thenReturn(mock(NetworkPlayerSession.class));
        return new TestPlayer(source, mode, protocol, big);
    }

    private final class TestPlayer extends Player {
        TestPlayer(SourceInterface source, int mode, int protocol, boolean big) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
            this.level = PlayerCreativeUiResetTest.this.level;
            this.protocol = protocol;
            this.gameVersion = GameVersion.byProtocol(protocol, false);
            this.gamemode = mode;
            this.inventory = new PlayerInventory(this);
            this.offhandInventory = new PlayerOffhandInventory(this);
            this.namedTag = new CompoundTag();
            this.adventureSettings = new AdventureSettings(this);
            this.playerUIInventory = new PlayerUIInventory(this);
            this.craftingGrid = big ? playerUIInventory.getBigCraftingGrid() : playerUIInventory.getCraftingGrid();
            this.craftingType = big ? CRAFTING_BIG : CRAFTING_SMALL;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }
    }
}
