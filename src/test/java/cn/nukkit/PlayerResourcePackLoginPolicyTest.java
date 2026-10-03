package cn.nukkit;

import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ResourcePackChunkDataPacket;
import cn.nukkit.network.protocol.ResourcePackChunkRequestPacket;
import cn.nukkit.network.protocol.ResourcePackDataInfoPacket;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PlayerResourcePackLoginPolicyTest {

    @Test
    void resourcePackValidationNotificationIsAllowedDuringResourcePackLogin() {
        int packetId = ProtocolInfo.RESOURCE_PACKS_READY_FOR_VALIDATION_PACKET;

        assertTrue(Player.isPreLoginVerifiedPacketAllowed(SessionLoginPhase.RESOURCE_PACK, packetId));
        assertTrue(Player.isPreLoginPacketAllowed(SessionLoginPhase.RESOURCE_PACK, packetId));
    }

    @Test
    void tenMiBTransferUsesBdsSizedChunksInsteadOfARequestBurst() {
        int packSize = 10 * 1024 * 1024;
        int chunkCount = (packSize + Player.RESOURCE_PACK_CHUNK_SIZE - 1) / Player.RESOURCE_PACK_CHUNK_SIZE;

        assertEquals(103, chunkCount);
    }

    @Test
    void resourcePackIdentitySuffixStartsAtProtocol281() {
        UUID packId = UUID.fromString("12345678-1234-1234-1234-123456789abc");

        assertPackIdentityBoundary(createChunkRequest(packId));
        assertPackIdentityBoundary(createChunkData(packId));
        assertPackIdentityBoundary(createDataInfo(packId));
    }

    @Test
    void pendingResourcePackAllowsOutOfOrderAndRepeatedRequests() {
        Player.PendingResourcePack pending = new Player.PendingResourcePack(mock(ResourcePack.class));

        pending.request(2);
        assertEquals(2, pending.pollChunkIndex());
        assertTrue(pending.isEmpty());

        pending.request(2);
        pending.request(0);
        assertEquals(0, pending.pollChunkIndex());
        assertEquals(2, pending.pollChunkIndex());
        assertTrue(pending.isEmpty());
    }

    private static void assertPackIdentityBoundary(cn.nukkit.network.protocol.DataPacket packet) {
        String uuid = "12345678-1234-1234-1234-123456789abc";

        packet.protocol = ProtocolInfo.v1_5_0;
        packet.encode();
        assertEquals(uuid, readPackIdentity(packet));

        packet.protocol = ProtocolInfo.v1_6_0_5;
        packet.encode();
        assertEquals(uuid + "_1.2.3", readPackIdentity(packet));
    }

    private static String readPackIdentity(cn.nukkit.network.protocol.DataPacket packet) {
        BinaryStream stream = new BinaryStream(packet.getBuffer());
        if (packet.protocol <= 274) {
            stream.getByte();
            stream.getShort();
        } else {
            stream.getUnsignedVarInt();
        }
        return stream.getString();
    }

    private static ResourcePackChunkRequestPacket createChunkRequest(UUID packId) {
        ResourcePackChunkRequestPacket packet = new ResourcePackChunkRequestPacket();
        packet.packId = packId;
        packet.packVersion = "1.2.3";
        return packet;
    }

    private static ResourcePackChunkDataPacket createChunkData(UUID packId) {
        ResourcePackChunkDataPacket packet = new ResourcePackChunkDataPacket();
        packet.packId = packId;
        packet.packVersion = "1.2.3";
        packet.data = new byte[0];
        return packet;
    }

    private static ResourcePackDataInfoPacket createDataInfo(UUID packId) {
        ResourcePackDataInfoPacket packet = new ResourcePackDataInfoPacket();
        packet.packId = packId;
        packet.packVersion = "1.2.3";
        packet.sha256 = new byte[0];
        return packet;
    }
}
