package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityHorseBase;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.ContainerClosePacket;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.InteractPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class HorseInventoryTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void armorSlotAcceptsStringBackedHorseArmor() {
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class);
        Mockito.when(horse.canWearHorseArmor()).thenReturn(true);
        Mockito.when(horse.getViewers()).thenReturn(Map.of());
        HorseInventory inventory = new HorseInventory(horse, 0);

        Item copper = Item.fromString(ItemNamespaceId.COPPER_HORSE_ARMOR);
        Item netherite = Item.fromString(ItemNamespaceId.NETHERITE_HORSE_ARMOR);

        assertTrue(copper.isHorseArmor());
        assertTrue(netherite.isHorseArmor());
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, copper, false));
        assertEquals(ItemNamespaceId.COPPER_HORSE_ARMOR, inventory.getItem(HorseInventory.SLOT_ARMOR).getNamespaceId());
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, netherite, false));
        assertEquals(ItemNamespaceId.NETHERITE_HORSE_ARMOR, inventory.getItem(HorseInventory.SLOT_ARMOR).getNamespaceId());
    }

    @Test
    void horseArmorAccessorsReadInventorySlot() throws Exception {
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class, Mockito.CALLS_REAL_METHODS);
        Mockito.doReturn(true).when(horse).canWearHorseArmor();
        Mockito.doReturn(Map.of()).when(horse).getViewers();
        HorseInventory inventory = new HorseInventory(horse, 0);
        setHorseInventory(horse, inventory);

        Item diamond = Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1);
        horse.setHorseArmor(diamond);

        assertTrue(horse.hasHorseArmor());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, inventory.getItem(HorseInventory.SLOT_ARMOR).getId());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, horse.getHorseArmor().getId());

        Item copper = Item.fromString(ItemNamespaceId.COPPER_HORSE_ARMOR);
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, copper, false));

        assertTrue(horse.hasHorseArmor());
        assertEquals(ItemNamespaceId.COPPER_HORSE_ARMOR, horse.getHorseArmor().getNamespaceId());
    }

    @Test
    void armorSlotBroadcastIncludesBodyField() {
        Player viewer = Mockito.mock(Player.class);
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class);
        Mockito.when(horse.canWearHorseArmor()).thenReturn(true);
        Mockito.when(horse.getId()).thenReturn(77L);
        Mockito.when(horse.getViewers()).thenReturn(Map.of(1, viewer));
        HorseInventory inventory = new HorseInventory(horse, 0);

        Item armor = Item.get(Item.DIAMOND_HORSE_ARMOR, 0, 1);
        assertTrue(inventory.setItem(HorseInventory.SLOT_ARMOR, armor, false));

        MobArmorEquipmentPacket packet = capturePacket(viewer, MobArmorEquipmentPacket.class);
        assertEquals(77L, packet.eid);
        assertEquals(Item.DIAMOND_HORSE_ARMOR, packet.slots[1].getId());
        assertEquals(Item.DIAMOND_HORSE_ARMOR, packet.body.getId());
    }

    @Test
    void crouchingWithChestOpensScreenWhenHorseCannotTakeChest() throws Exception {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.isSneaking()).thenReturn(true);
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class, Mockito.CALLS_REAL_METHODS);
        Mockito.doReturn(false).when(horse).isBaby();
        HorseInventory inventory = new HorseInventory(horse, 0);
        setHorseInventory(horse, inventory);

        assertFalse(horse.onInteract(player, Item.get(Item.CHEST, 0, 1), new Vector3()));

        Mockito.verify(player).addWindow(inventory);
    }

    @Test
    void riderInventoryActionOpensAndRegistersHorseContainer() {
        TestPlayer player = createTestPlayer();
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class);
        Mockito.when(horse.getId()).thenReturn(91L);
        HorseInventory inventory = new HorseInventory(horse, 0);
        Mockito.when(horse.getInventory()).thenReturn(inventory);

        player.openInventoryActionWhileRiding(horse);

        int windowId = player.getWindowId(inventory);
        assertTrue(windowId >= Player.MINIMUM_OTHER_WINDOW_ID);
        assertSame(inventory, player.getTopWindow().orElseThrow());
        assertTrue(inventory.getViewers().contains(player));
        ContainerOpenPacket packet = findPacket(player, ContainerOpenPacket.class);
        assertNotNull(packet);
        assertEquals(windowId, packet.windowId);
        assertEquals(InventoryType.HORSE.getNetworkType(), packet.type);
        assertEquals(91L, packet.entityId);
    }

    @Test
    void closingHorseRemovesOpenWindowFromViewer() throws Exception {
        TestPlayer viewer = createTestPlayer();
        EntityHorseBase horse = Mockito.mock(EntityHorseBase.class, Mockito.CALLS_REAL_METHODS);
        horse.closed = true;
        HorseInventory inventory = new HorseInventory(horse, 0);
        setHorseInventory(horse, inventory);
        assertTrue(viewer.addWindow(inventory) >= Player.MINIMUM_OTHER_WINDOW_ID);

        horse.close();

        assertFalse(inventory.getViewers().contains(viewer));
        assertTrue(viewer.getTopWindow().isEmpty());
        assertNotNull(findPacket(viewer, ContainerClosePacket.class));
    }

    private static void setHorseInventory(EntityHorseBase horse, HorseInventory inventory) throws Exception {
        Field field = EntityHorseBase.class.getDeclaredField("horseInventory");
        field.setAccessible(true);
        field.set(horse, inventory);
    }

    private static <T extends DataPacket> T capturePacket(Player player, Class<T> type) {
        ArgumentCaptor<DataPacket> captor = ArgumentCaptor.forClass(DataPacket.class);
        verify(player, atLeastOnce()).dataPacket(captor.capture());
        for (DataPacket packet : captor.getAllValues()) {
            if (type.isInstance(packet)) {
                return type.cast(packet);
            }
        }
        fail("Expected packet " + type.getSimpleName());
        return null;
    }

    private static TestPlayer createTestPlayer() {
        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));
        TestPlayer player = new TestPlayer(sourceInterface);
        player.protocol = ProtocolInfo.v1_21_30;
        player.spawned = true;
        return player;
    }

    private static <T extends DataPacket> T findPacket(TestPlayer player, Class<T> type) {
        return player.sentPackets.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static final class TestPlayer extends Player {

        private final List<DataPacket> sentPackets = new ArrayList<>();

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void openInventoryActionWhileRiding(EntityHorseBase horse) {
            this.spawned = true;
            this.loggedIn = true;
            this.loginVerified = true;
            this.riding = horse;
            InteractPacket packet = new InteractPacket();
            packet.action = InteractPacket.ACTION_OPEN_INVENTORY;
            packet.target = Long.MAX_VALUE;
            this.handleDataPacket(packet);
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            this.sentPackets.add(packet);
            return true;
        }
    }
}
