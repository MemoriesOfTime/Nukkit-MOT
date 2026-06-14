package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.entity.item.EntityMinecartHopper;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServerAuthoritativeSyncTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void baseInventorySyncUsesConcreteContainerName() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        BlockEntityChest holder = Mockito.mock(BlockEntityChest.class);
        ChestInventory chest = new ChestInventory(holder);
        Mockito.when(player.getWindowId(chest)).thenReturn(7);

        chest.sendSlot(3, player);
        InventorySlotPacket slotPacket = capturePacket(player, InventorySlotPacket.class);
        assertEquals(ContainerSlotType.LEVEL_ENTITY, slotPacket.containerNameData.getContainer());
        // Dynamic containers are never registered server-side, so a non-null dynamicId
        // makes the client reject the packet (e.g. hopper/chest GUI closes immediately).
        assertNull(slotPacket.containerNameData.getDynamicId());

        Mockito.reset(player);
        player.protocol = ProtocolInfo.v1_21_30;
        Mockito.when(player.getWindowId(chest)).thenReturn(7);
        chest.sendContents(player);
        InventoryContentPacket contentPacket = capturePacket(player, InventoryContentPacket.class);
        assertEquals(ContainerSlotType.LEVEL_ENTITY, contentPacket.containerNameData.getContainer());
        assertNull(contentPacket.containerNameData.getDynamicId());
    }

    @Test
    void furnaceSlotSyncUsesSlotSpecificContainerName() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        BlockEntityFurnace holder = Mockito.mock(BlockEntityFurnace.class);
        FurnaceInventory furnace = new FurnaceInventory(holder);
        Mockito.when(player.getWindowId(furnace)).thenReturn(8);

        furnace.sendSlot(0, player);
        InventorySlotPacket ingredientPacket = capturePacket(player, InventorySlotPacket.class);
        assertEquals(ContainerSlotType.FURNACE_INGREDIENT, ingredientPacket.containerNameData.getContainer());
        assertNull(ingredientPacket.containerNameData.getDynamicId());

        Mockito.reset(player);
        player.protocol = ProtocolInfo.v1_21_30;
        Mockito.when(player.getWindowId(furnace)).thenReturn(8);
        furnace.sendSlot(2, player);
        InventorySlotPacket resultPacket = capturePacket(player, InventorySlotPacket.class);
        assertEquals(ContainerSlotType.FURNACE_RESULT, resultPacket.containerNameData.getContainer());
        assertNull(resultPacket.containerNameData.getDynamicId());
    }

    @Test
    void doubleChestUnclonedAccessAndClearProxyToRealSides() {
        BlockEntityChest leftHolder = Mockito.mock(BlockEntityChest.class);
        BlockEntityChest rightHolder = Mockito.mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        Mockito.when(leftHolder.getRealInventory()).thenReturn(left);
        Mockito.when(rightHolder.getRealInventory()).thenReturn(right);

        assertTrue(left.setItem(0, Item.get(Item.STONE, 0, 4), false));
        assertTrue(right.setItem(0, Item.get(Item.DIRT, 0, 6), false));

        DoubleChestInventory doubleChest = new DoubleChestInventory(leftHolder, rightHolder);

        assertSame(left.getUnclonedItem(0), doubleChest.getUnclonedItem(0));
        assertSame(right.getUnclonedItem(0), doubleChest.getUnclonedItem(left.getSize()));
        assertEquals(Item.DIRT, doubleChest.getItem(left.getSize()).getId());

        assertTrue(doubleChest.clear(left.getSize(), false));
        assertTrue(right.getItem(0).isNull());
    }

    @Test
    void doubleChestSlotSyncCarriesWindowDynamicId() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        BlockEntityChest leftHolder = Mockito.mock(BlockEntityChest.class);
        BlockEntityChest rightHolder = Mockito.mock(BlockEntityChest.class);
        ChestInventory left = new ChestInventory(leftHolder);
        ChestInventory right = new ChestInventory(rightHolder);
        Mockito.when(leftHolder.getRealInventory()).thenReturn(left);
        Mockito.when(rightHolder.getRealInventory()).thenReturn(right);
        DoubleChestInventory doubleChest = new DoubleChestInventory(leftHolder, rightHolder);
        Mockito.when(player.getWindowId(doubleChest)).thenReturn(9);

        doubleChest.sendSlot(right, 0, player);

        InventorySlotPacket packet = capturePacket(player, InventorySlotPacket.class);
        assertEquals(27, packet.slot);
        assertEquals(ContainerSlotType.LEVEL_ENTITY, packet.containerNameData.getContainer());
        assertNull(packet.containerNameData.getDynamicId());
    }

    @Test
    void playerArmorSlotSyncCarriesSpecialArmorDynamicId() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        PlayerInventory inventory = new PlayerInventory(player);

        inventory.sendArmorSlot(inventory.getSize(), player);

        InventorySlotPacket packet = capturePacket(player, InventorySlotPacket.class);
        assertEquals(0, packet.slot);
        assertEquals(ContainerSlotType.ARMOR, packet.containerNameData.getContainer());
        assertEquals(InventoryContentPacket.SPECIAL_ARMOR, packet.containerNameData.getDynamicId());
    }

    @Test
    void playerInventorySlotSyncCarriesViewerWindowDynamicId() {
        Player holder = Mockito.mock(Player.class);
        Player viewer = Mockito.mock(Player.class);
        viewer.protocol = ProtocolInfo.v1_21_30;
        PlayerInventory inventory = new PlayerInventory(holder);
        Mockito.when(viewer.getWindowId(inventory)).thenReturn(11);

        inventory.sendSlot(10, viewer);

        InventorySlotPacket packet = capturePacket(viewer, InventorySlotPacket.class);
        assertEquals(ContainerSlotType.INVENTORY, packet.containerNameData.getContainer());
        assertEquals(11, packet.containerNameData.getDynamicId());
    }

    @Test
    void offhandSyncCarriesStaticDynamicId() {
        Player player = Mockito.mock(Player.class);
        player.protocol = ProtocolInfo.v1_21_30;
        PlayerOffhandInventory inventory = new PlayerOffhandInventory(player);

        inventory.sendContents(player);

        InventoryContentPacket packet = capturePacket(player, InventoryContentPacket.class);
        assertEquals(ContainerSlotType.OFFHAND, packet.containerNameData.getContainer());
        assertEquals(0, packet.containerNameData.getDynamicId());
    }

    @Test
    void failedDynamicWindowOpenDoesNotSendClosePacket() {
        TestPlayer player = createWindowTestPlayer();
        FailingContainerInventory inventory = new FailingContainerInventory(Mockito.mock(BlockEntityChest.class));

        int result = player.addWindow(inventory);

        assertEquals(-1, result);
        assertEquals(-1, player.getWindowId(inventory));
        assertFalse(player.sentPackets.stream().anyMatch(ContainerClosePacket.class::isInstance));
    }

    @Test
    void failedPermanentWindowOpenKeepsWindowMappingWithoutClosePacket() {
        TestPlayer player = createWindowTestPlayer();
        player.spawned = false;
        FailingContainerInventory inventory = new FailingContainerInventory(Mockito.mock(BlockEntityChest.class));

        int result = player.addWindow(inventory, 42, true);

        assertEquals(-1, result);
        assertEquals(42, player.getWindowId(inventory));
        assertFalse(player.sentPackets.stream().anyMatch(ContainerClosePacket.class::isInstance));
    }

    @Test
    void minecartHopperUsesHopperUiTypeWhileCartographyUsesProtocolContainerId() {
        assertEquals(InventoryType.HOPPER.getNetworkType(), InventoryType.MINECART_HOPPER.getNetworkType());
        assertEquals(ContainerType.CARTOGRAPHY.getId(), InventoryType.CARTOGRAPHY.getNetworkType());
    }

    @Test
    void minecartHopperWindowOpensWithHopperUiTypeAndEntityId() {
        TestPlayer player = createWindowTestPlayer();
        EntityMinecartHopper minecart = Mockito.mock(EntityMinecartHopper.class);
        Mockito.when(minecart.getId()).thenReturn(1234L);
        MinecartHopperInventory inventory = new MinecartHopperInventory(minecart);

        int windowId = player.addWindow(inventory);

        assertTrue(windowId >= Player.MINIMUM_OTHER_WINDOW_ID);
        ContainerOpenPacket openPacket = findPacket(player, ContainerOpenPacket.class);
        assertNotNull(openPacket);
        assertEquals(windowId, openPacket.windowId);
        assertEquals(InventoryType.HOPPER.getNetworkType(), openPacket.type);
        assertEquals(1234L, openPacket.entityId);

        InventoryContentPacket contentPacket = findPacket(player, InventoryContentPacket.class);
        assertNotNull(contentPacket);
        assertTrue(player.sentPackets.indexOf(openPacket) < player.sentPackets.indexOf(contentPacket));
        assertEquals(windowId, contentPacket.inventoryId);
        assertEquals(ContainerSlotType.LEVEL_ENTITY, contentPacket.containerNameData.getContainer());
        assertNull(contentPacket.containerNameData.getDynamicId());
    }

    @Test
    void playerUIComponentForceWriteUsesBackingUIInventory() {
        Player player = Mockito.mock(Player.class);
        PlayerUIInventory ui = new PlayerUIInventory(player);

        ui.getCursorInventory().setItemForce(0, Item.get(Item.DIAMOND, 0, 1));
        assertEquals(Item.DIAMOND, ui.getItem(0).getId());

        ui.getCraftingGrid().setItemForce(1, Item.get(Item.STONE, 0, 2));
        assertEquals(Item.STONE, ui.getItem(29).getId());
        assertEquals(2, ui.getItem(29).getCount());

        AnvilInventory anvil = new AnvilInventory(ui, new Position());
        anvil.setItemForce(0, Item.get(Item.IRON_INGOT, 0, 3));
        assertEquals(Item.IRON_INGOT, ui.getItem(1).getId());
        assertEquals(3, ui.getItem(1).getCount());

        anvil.setItemForce(0, Item.get(Item.AIR));
        assertTrue(ui.getItem(1).isNull());
    }

    private static <T extends DataPacket> T capturePacket(Player player, Class<T> type) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        Mockito.verify(player).dataPacket(captor.capture());
        return assertInstanceOf(type, captor.getValue());
    }

    private static <T extends DataPacket> T findPacket(TestPlayer player, Class<T> type) {
        return player.sentPackets.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static TestPlayer createWindowTestPlayer() {
        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        TestPlayer player = new TestPlayer(sourceInterface);
        player.protocol = ProtocolInfo.v1_21_30;
        player.spawned = true;
        return player;
    }

    private static final class TestPlayer extends Player {

        private final List<DataPacket> sentPackets = new ArrayList<>();

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            this.sentPackets.add(packet);
            return true;
        }
    }

    private static final class FailingContainerInventory extends ContainerInventory {

        private FailingContainerInventory(InventoryHolder holder) {
            super(holder, InventoryType.HOPPER);
        }

        @Override
        public boolean open(Player who) {
            return false;
        }
    }
}
