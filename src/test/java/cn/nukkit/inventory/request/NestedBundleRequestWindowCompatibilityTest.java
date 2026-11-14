package cn.nukkit.inventory.request;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.passive.EntityHorse;
import cn.nukkit.inventory.BundleInventory;
import cn.nukkit.inventory.ChestInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.HorseInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TakeFromItemContainerAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/** Healthy requests use actual native inventories and verify the serialized canonical carrier. */
@org.junit.jupiter.api.parallel.Isolated
class NestedBundleRequestWindowCompatibilityTest {
    private static cn.nukkit.Server previousServer;
    private PluginManager previousPlugins;
    private cn.nukkit.inventory.CraftingManager previousCraftingManager;

    @org.junit.jupiter.api.AfterAll static void restoreServer() throws Exception {
        var instance = cn.nukkit.Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServer);
    }

    @org.junit.jupiter.api.AfterEach void restoreGlobalStubs() {
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(previousPlugins);
        Mockito.when(MockServer.get().getCraftingManager()).thenReturn(previousCraftingManager);
    }
    @BeforeAll static void init() { previousServer = cn.nukkit.Server.getInstance(); MockServer.init(); Item.initCreativeItems(); }
    @BeforeEach void captureGlobalStubs() { previousPlugins = MockServer.get().getPluginManager(); previousCraftingManager = MockServer.get().getCraftingManager(); }

    @Test
    void dynamicMainBundleCanEquipLawfulRiddenHorseWithoutTopWindow() throws Exception {
        Fixture f = fixture();
        EntityHorse horse = Mockito.mock(EntityHorse.class);
        HorseInventory equipment = new HorseInventory(horse, 0);
        Mockito.when(horse.getInventory()).thenReturn(equipment);
        Mockito.when(f.player.getRiding()).thenReturn(horse);
        Mockito.when(f.player.getWindowId(equipment)).thenReturn(2);
        assertTrue(f.player.getTopWindow().isEmpty());
        assertSame(equipment, NetworkMapping.getInventory(f.player, ContainerSlotType.HORSE_EQUIP, null));

        Item payload = payload(Item.SADDLE, 1);
        ItemBundle root = bundle("main-saddle");
        assertTrue(root.getInventory().setItem(0, payload, false));
        root.saveNBT();
        assertTrue(f.main.setItem(0, root, false));
        ItemBundle live = (ItemBundle) f.main.getUnclonedItem(0);
        transfer(f, live, 1, ContainerSlotType.HORSE_EQUIP, HorseInventory.SLOT_SADDLE);

        assertEquals(ItemStackResponseStatus.OK, response(f), "a lawful native saddle setter must stay usable");
        assertEquals(1, serializedCount(f.main, Item.SADDLE) + serializedCount(equipment, Item.SADDLE));
        assertEquals(0, serializedCount(f.main, Item.SADDLE));
        assertEquals(1, equipment.getItem(HorseInventory.SLOT_SADDLE).getCount());
        assertOpaque(equipment.getItem(HorseInventory.SLOT_SADDLE), payload);
        assertEquals("main-saddle", f.main.getItem(0).getNamedTag().getString("opaqueCarrier"));
    }

    @Test
    void nestedBundleInRightHalfOfDoubleTopWindowUsesCanonicalCarrierAndLogicalOuterSlot() throws Exception {
        Fixture f = fixture();
        ChestInventory left = chest();
        ChestInventory right = chest();
        DoubleChestInventory window = new DoubleChestInventory(left.getHolder(), right.getHolder());
        int rootSlot = left.getSize() + 3;
        assertHealthyWindow(f, window, rootSlot);
        assertEquals(4, serializedCount(right, Item.DIAMOND));
        assertEquals(0, serializedCount(left, Item.DIAMOND));
        ItemBundle live = (ItemBundle) right.getUnclonedItem(3);
        ItemBundle leaf = (ItemBundle) live.getInventory().getUnclonedItem(0);
        var ref = NetworkMapping.findBundleHolder(f.player, leaf.getBundleId());
        assertNotNull(ref);
        assertSame(window, ref.outer(), "notification must retain the logical double window");
        assertEquals(rootSlot, ref.outerSlot(), "right-half notification keeps the double-window slot offset");
        assertSame(live, ref.chain().get(0));
        assertSame(leaf, ref.chain().get(1));
        assertSame(leaf.getInventory(), NetworkMapping.getInventory(f.player,
                ContainerSlotType.DYNAMIC_CONTAINER, leaf.getBundleId()));
    }

    @Test
    void nestedBundleInSingleChestTopWindowPreservesOrdinaryNativeTransfer() throws Exception {
        Fixture f = fixture();
        ChestInventory window = chest();
        assertHealthyWindow(f, window, 3);
        ItemBundle root = (ItemBundle) window.getUnclonedItem(3);
        ItemBundle leaf = (ItemBundle) root.getInventory().getUnclonedItem(0);
        var ref = NetworkMapping.findBundleHolder(f.player, leaf.getBundleId());
        assertNotNull(ref);
        assertSame(window, ref.outer());
        assertEquals(3, ref.outerSlot());
        assertSame(root, ref.chain().get(0));
        assertSame(leaf, ref.chain().get(1));
    }

    private static void assertHealthyWindow(Fixture f, Inventory window, int rootSlot) throws Exception {
        Mockito.when(f.player.getTopWindow()).thenReturn(Optional.of(window));
        Mockito.when(f.player.getWindowId(window)).thenReturn(2);
        Item payload = payload(Item.DIAMOND, 7);
        ItemBundle inner = bundle("leaf");
        assertTrue(inner.getInventory().setItem(0, payload, false));
        ItemBundle outer = bundle("root");
        assertTrue(outer.getInventory().setItem(0, inner, false));
        outer.saveNBT();
        assertTrue(window.setItem(rootSlot, outer, false));
        ItemBundle canonicalRoot = (ItemBundle) window.getUnclonedItem(rootSlot);
        ItemBundle canonicalLeaf = (ItemBundle) canonicalRoot.getInventory().getUnclonedItem(0);
        assertEquals(7, serializedCount(window, Item.DIAMOND) + serializedCount(f.main, Item.DIAMOND));

        transfer(f, canonicalLeaf, 3, ContainerSlotType.HOTBAR, 1);

        assertEquals(ItemStackResponseStatus.OK, response(f), "healthy actual handler transfer must be accepted");
        assertEquals(7, serializedCount(window, Item.DIAMOND) + serializedCount(f.main, Item.DIAMOND));
        assertEquals(4, serializedCount(window, Item.DIAMOND));
        assertEquals(3, f.main.getItem(1).getCount());
        ItemBundle persistedRoot = (ItemBundle) window.getItem(rootSlot);
        ItemBundle persistedLeaf = (ItemBundle) persistedRoot.getInventory().getItem(0);
        assertOpaque(persistedLeaf.getInventory().getItem(0), payload);
        assertOpaque(f.main.getItem(1), payload);
        assertEquals("root", persistedRoot.getNamedTag().getString("opaqueCarrier"));
        assertEquals("leaf", persistedLeaf.getNamedTag().getString("opaqueCarrier"));
    }

    private static ChestInventory chest() {
        BlockEntityChest holder = Mockito.mock(BlockEntityChest.class);
        holder.chunk = Mockito.mock(FullChunk.class);
        ChestInventory inventory = new ChestInventory(holder);
        Mockito.when(holder.getRealInventory()).thenReturn(inventory);
        Mockito.when(holder.getInventory()).thenReturn(inventory);
        return inventory;
    }

    private static Fixture fixture() throws Exception {
        Method factory = ItemStackRequestProcessorTest.class.getDeclaredMethod("mockPlayer");
        factory.setAccessible(true);
        Player player = (Player) factory.invoke(null);
        Mockito.when(player.isCreative()).thenReturn(false);
        Mockito.when(player.getGameVersion()).thenReturn(GameVersion.V1_21_130);
        player.spawned = true;
        PlayerInventory main = new PlayerInventory(player);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerOffhandInventory offhand = new PlayerOffhandInventory(player);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        Mockito.when(player.getInventory()).thenReturn(main);
        Mockito.when(player.getUIInventory()).thenReturn(ui);
        Mockito.when(player.getCursorInventory()).thenReturn(ui.getCursorInventory());
        Mockito.when(player.getCraftingGrid()).thenReturn(ui.getCraftingGrid());
        Mockito.when(player.getOffhandInventory()).thenReturn(offhand);
        Mockito.when(player.getTopWindow()).thenReturn(Optional.empty());
        Mockito.when(player.getWindowId(main)).thenReturn(0);
        Mockito.when(player.getWindowId(ui)).thenReturn(0);
        Mockito.when(player.getWindowId(offhand)).thenReturn(0);
        List<DataPacket> packets = new ArrayList<>();
        Mockito.doAnswer(call -> { packets.add(call.getArgument(0)); return false; })
                .when(player).dataPacket(any(DataPacket.class));
        return new Fixture(player, main, packets);
    }

    private static Item payload(int id, int count) {
        Item item = Item.get(id, 0, count).setNamedTag(new CompoundTag()
                .putString("opaqueWindow", "original")
                .putCompound("futureComponent", new CompoundTag().putLong("nonce", 0x102030405060L)
                        .putByteArray("raw", new byte[]{0, 1, -1, 33, 0})));
        item.autoAssignStackNetworkId();
        return item;
    }

    private static ItemBundle bundle(String marker) {
        ItemBundle bundle = (ItemBundle) Item.fromString("minecraft:bundle");
        bundle.setNamedTag(new CompoundTag().putString("opaqueCarrier", marker));
        return bundle;
    }

    private static void transfer(Fixture f, ItemBundle source, int count, ContainerSlotType destination, int slot) {
        Item item = source.getInventory().getItem(0);
        var action = new TakeFromItemContainerAction(count,
                new ItemStackRequestSlotData(ContainerSlotType.DYNAMIC_CONTAINER, 0,
                        item.getStackNetId(), source.getBundleId()),
                new ItemStackRequestSlotData(destination, slot, 0, null));
        ItemStackRequestHandler.handleRequests(f.player, List.of(new ItemStackRequest(77,
                new TakeFromItemContainerAction[]{action}, new String[0])));
    }

    private static ItemStackResponseStatus response(Fixture f) {
        return f.packets.stream().filter(ItemStackResponsePacket.class::isInstance)
                .map(ItemStackResponsePacket.class::cast).reduce((first, last) -> last)
                .orElseThrow().entries.get(0).getResult();
    }

    private static int serializedCount(Inventory inventory, int id) {
        return inventory.getContents().keySet().stream().mapToInt(slot -> {
            Item item = inventory.getItem(slot);
            return item instanceof ItemBundle bundle ? serializedCount(bundle.getInventory(), id)
                    : item.getId() == id ? item.getCount() : 0;
        }).sum();
    }

    private static void assertOpaque(Item actual, Item initial) throws Exception {
        assertEquals(initial.getId(), actual.getId());
        assertArrayEquals(NBTIO.write(initial.getNamedTag()), NBTIO.write(actual.getNamedTag()));
    }

    private record Fixture(Player player, PlayerInventory main, List<DataPacket> packets) { }
}
