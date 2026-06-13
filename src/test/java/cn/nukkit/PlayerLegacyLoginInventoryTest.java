package cn.nukkit;

import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacketV113;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerLegacyLoginInventoryTest {

    @Test
    void firstSpawnSendsSavedInventoryStateToLegacyClient() {
        TestPlayer player = createLegacyPlayer();
        PlayerInventory inventory = player.getInventory();
        Item heldItem = Item.get(ItemID.DIAMOND, 0, 3);
        Item helmet = Item.get(ItemID.IRON_HELMET);
        inventory.setItem(0, heldItem);
        inventory.setArmorItem(0, helmet, true);

        player.runFirstSpawn();

        int inventoryWindowId = player.getWindowId(inventory);
        ContainerSetContentPacketV113 inventoryPacket = player.findContentPacket(inventoryWindowId);
        assertNotNull(inventoryPacket);
        assertEquals(ItemID.DIAMOND, inventoryPacket.slots[0].getId());
        assertEquals(3, inventoryPacket.slots[0].getCount());

        ContainerSetContentPacketV113 armorPacket = player.findContentPacket(ContainerSetContentPacketV113.SPECIAL_ARMOR);
        assertNotNull(armorPacket);
        assertEquals(ItemID.IRON_HELMET, armorPacket.slots[0].getId());

        assertTrue(player.sentPackets.stream()
                .filter(MobEquipmentPacket.class::isInstance)
                .map(MobEquipmentPacket.class::cast)
                .anyMatch(packet -> packet.item.getId() == ItemID.DIAMOND && packet.item.getCount() == 3));
    }

    private static TestPlayer createLegacyPlayer() {
        MockServer.reset();
        Server server = MockServer.get();
        Mockito.when(server.getViewDistance()).thenReturn(2);

        Level level = server.getDefaultLevel();
        Position spawn = new Position(0, 64, 0, level);
        Mockito.when(level.getSafeSpawn()).thenReturn(spawn);
        Mockito.when(level.getSafeSpawn(Mockito.any(Position.class))).thenReturn(spawn);
        Mockito.when(level.getChunkEntities(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.emptyMap());

        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        TestPlayer player = new TestPlayer(sourceInterface);
        player.initializeLegacyInventoryState();
        return player;
    }

    private static final class TestPlayer extends Player {

        private final List<DataPacket> sentPackets = new ArrayList<>();

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void initializeLegacyInventoryState() {
            this.protocol = ProtocolInfo.v1_1_0;
            this.gameVersion = GameVersion.V1_1_0;
            this.adventureSettings = new AdventureSettings(this);
            this.inventory = new PlayerInventory(this);
            this.offhandInventory = new PlayerOffhandInventory(this);
            this.addDefaultWindows();
        }

        private void runFirstSpawn() {
            this.doFirstSpawn();
        }

        private ContainerSetContentPacketV113 findContentPacket(int windowId) {
            return this.sentPackets.stream()
                    .filter(ContainerSetContentPacketV113.class::isInstance)
                    .map(ContainerSetContentPacketV113.class::cast)
                    .filter(packet -> packet.windowid == windowId)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            this.sentPackets.add(packet);
            return true;
        }

        @Override
        public boolean setPosition(Vector3 pos) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            return true;
        }

        @Override
        public void sendPosition(Vector3 pos, double yaw, double pitch, double headYaw, int mode, Player[] targets) {
        }

        @Override
        public void checkSpawnBlockPosition() {
        }

        @Override
        public void spawnToAll() {
        }
    }
}
