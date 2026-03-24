package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryPacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
    }

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(407);
    }

    static Stream<Arguments> versionsPre407() {
        return filteredVersionsRange(291, 407);
    }

    static Stream<Arguments> versionsFrom712() {
        return filteredVersions(712);
    }

    @ParameterizedTest(name = "MobEquipmentPacket v{0}")
    @MethodSource("versionsFrom313")
    void testMobEquipmentPacket(int protocolVersion) {
        var nukkitPacket = new MobEquipmentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.item = Item.AIR_ITEM;
        nukkitPacket.inventorySlot = 0;
        nukkitPacket.hotbarSlot = 0;
        nukkitPacket.windowId = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(0, cbPacket.getInventorySlot());
        assertEquals(0, cbPacket.getHotbarSlot());
        assertEquals(0, cbPacket.getContainerId());
    }

    @ParameterizedTest(name = "MobArmorEquipmentPacket v{0}")
    @MethodSource("versionsFrom313")
    void testMobArmorEquipmentPacket(int protocolVersion) {
        var nukkitPacket = new MobArmorEquipmentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.slots = new Item[]{Item.AIR_ITEM, Item.AIR_ITEM, Item.AIR_ITEM, Item.AIR_ITEM};
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            nukkitPacket.body = Item.AIR_ITEM;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    @ParameterizedTest(name = "InventoryContentPacket v{0}")
    @MethodSource("versionsFrom313")
    void testInventoryContentPacket(int protocolVersion) {
        var nukkitPacket = new InventoryContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.inventoryId = 1;
        nukkitPacket.slots = new Item[]{Item.AIR_ITEM, Item.AIR_ITEM};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket.class);

        assertEquals(1, cbPacket.getContainerId());
        assertEquals(2, cbPacket.getContents().size());
    }

    @ParameterizedTest(name = "InventorySlotPacket v{0}")
    @MethodSource("versionsFrom313")
    void testInventorySlotPacket(int protocolVersion) {
        var nukkitPacket = new InventorySlotPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.inventoryId = 1;
        nukkitPacket.slot = 5;
        nukkitPacket.item = Item.AIR_ITEM;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket.class);

        assertEquals(1, cbPacket.getContainerId());
        assertEquals(5, cbPacket.getSlot());
    }

    @ParameterizedTest(name = "ContainerClosePacket v{0}")
    @MethodSource("versionsFrom313")
    void testContainerClosePacket(int protocolVersion) {
        var nukkitPacket = new ContainerClosePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 5;
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.wasServerInitiated = true;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket.class);

        assertEquals(5, cbPacket.getId());
    }

    @ParameterizedTest(name = "InventoryTransactionPacket v{0} (<407)")
    @MethodSource("versionsPre407")
    void testInventoryTransactionPacketPre407(int protocolVersion) {
        var nukkitPacket = new InventoryTransactionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.transactionType = InventoryTransactionPacket.TYPE_RELEASE_ITEM;

        var action = new cn.nukkit.network.protocol.types.NetworkInventoryAction();
        action.sourceType = cn.nukkit.network.protocol.types.NetworkInventoryAction.SOURCE_CONTAINER;
        action.windowId = 0;
        action.inventorySlot = 4;
        action.oldItem = Item.AIR_ITEM;
        action.newItem = Item.AIR_ITEM;
        nukkitPacket.actions = new cn.nukkit.network.protocol.types.NetworkInventoryAction[]{action};

        var releaseItemData = new cn.nukkit.inventory.transaction.data.ReleaseItemData();
        releaseItemData.actionType = InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE;
        releaseItemData.hotbarSlot = 2;
        releaseItemData.itemInHand = Item.AIR_ITEM;
        releaseItemData.headRot = new cn.nukkit.math.Vector3(1.5f, 64.0f, -2.5f);
        nukkitPacket.transactionData = releaseItemData;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_RELEASE,
                cbPacket.getTransactionType());
        assertEquals(1, cbPacket.getActions().size());
        assertEquals(4, cbPacket.getActions().get(0).getSlot());
        assertEquals(InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE, cbPacket.getActionType());
        assertEquals(2, cbPacket.getHotbarSlot());
        assertEquals(1.5f, cbPacket.getHeadPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getHeadPosition().getY(), 0.001f);
        assertEquals(-2.5f, cbPacket.getHeadPosition().getZ(), 0.001f);
    }

    // ==================== CreativeContentPacket ====================

    @ParameterizedTest(name = "CreativeContentPacket v{0} (empty/spectator)")
    @MethodSource("versionsFrom407")
    void testCreativeContentPacketEmpty(int protocolVersion) {
        var nukkitPacket = new CreativeContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        // null entries/creativeItems = spectator mode
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket.class);

        assertTrue(cbPacket.getContents().isEmpty());
    }

    @ParameterizedTest(name = "CreativeContentPacket v{0} (with items)")
    @MethodSource("versionsFrom407")
    void testCreativeContentPacketWithItems(int protocolVersion) {
        var nukkitPacket = new CreativeContentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entries = new Item[]{Item.AIR_ITEM};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket.class);

        assertFalse(cbPacket.getContents().isEmpty());
    }
}
