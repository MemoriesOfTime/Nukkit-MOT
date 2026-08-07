package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.regression.ProtocolCodecMapping;
import cn.nukkit.network.protocol.types.DisconnectFailReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests: Nukkit-MOT encodes packets, CB Protocol decodes them.
 * This verifies that bytes sent to clients are correctly formatted.
 */
public class PacketEncodingRegressionTest extends AbstractPacketRegressionTest {

    // --- Version Sources ---

    static Stream<Arguments> versionsWithDisconnectReason() {
        // v622 (1.20.40) added reason field
        return filteredVersions(ProtocolInfo.v1_20_40);
    }

    static Stream<Arguments> versionsPreDisconnectReason() {
        return ProtocolCodecMapping.getSupportedVersions().stream()
                .filter(v -> v < ProtocolInfo.v1_20_40)
                .map(Arguments::of);
    }

    static Stream<Arguments> textPacketLegacyVersions() {
        // Only test legacy format (pre-v898)
        return ProtocolCodecMapping.getSupportedVersions().stream()
                .filter(v -> v < ProtocolInfo.v1_21_130_28)
                .map(Arguments::of);
    }

    // ==================== Batch 1: Simple Packets ====================

    @ParameterizedTest(name = "PlayStatusPacket v{0}")
    @MethodSource("allVersions")
    void testPlayStatusPacket(int protocolVersion) {
        cn.nukkit.network.protocol.PlayStatusPacket nukkitPacket =
                new cn.nukkit.network.protocol.PlayStatusPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.status = cn.nukkit.network.protocol.PlayStatusPacket.LOGIN_SUCCESS;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.class);

        assertEquals(0, cbPacket.getStatus().ordinal(), "LOGIN_SUCCESS should be ordinal 0");
    }

    @ParameterizedTest(name = "SetTimePacket v{0}")
    @MethodSource("allVersions")
    void testSetTimePacket(int protocolVersion) {
        cn.nukkit.network.protocol.SetTimePacket nukkitPacket =
                new cn.nukkit.network.protocol.SetTimePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.time = 6000;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTimePacket.class);

        assertEquals(6000, cbPacket.getTime());
    }

    @ParameterizedTest(name = "ChunkRadiusUpdatedPacket v{0}")
    @MethodSource("allVersions")
    void testChunkRadiusUpdatedPacket(int protocolVersion) {
        cn.nukkit.network.protocol.ChunkRadiusUpdatedPacket nukkitPacket =
                new cn.nukkit.network.protocol.ChunkRadiusUpdatedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.radius = 8;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket.class);

        assertEquals(8, cbPacket.getRadius());
    }

    @ParameterizedTest(name = "DisconnectPacket pre-reason v{0}")
    @MethodSource("versionsPreDisconnectReason")
    void testDisconnectPacketPreReason(int protocolVersion) {
        cn.nukkit.network.protocol.DisconnectPacket nukkitPacket =
                new cn.nukkit.network.protocol.DisconnectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.hideDisconnectionScreen = false;
        nukkitPacket.message = "Server closed";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket.class);

        assertFalse(cbPacket.isMessageSkipped());
        assertEquals("Server closed", cbPacket.getKickMessage().toString());
    }

    @ParameterizedTest(name = "DisconnectPacket with-reason v{0}")
    @MethodSource("versionsWithDisconnectReason")
    void testDisconnectPacketWithReason(int protocolVersion) {
        cn.nukkit.network.protocol.DisconnectPacket nukkitPacket =
                new cn.nukkit.network.protocol.DisconnectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.reason = DisconnectFailReason.UNKNOWN; // ordinal 0, safe across both implementations
        nukkitPacket.hideDisconnectionScreen = false;
        nukkitPacket.message = "Server closed";
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            nukkitPacket.filteredMessage = "Filtered reason";
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket.class);

        assertEquals(0, cbPacket.getReason().ordinal(), "Reason should be UNKNOWN (ordinal 0)");
        assertFalse(cbPacket.isMessageSkipped());
        assertEquals("Server closed", cbPacket.getKickMessage().toString());
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            assertEquals("Filtered reason", cbPacket.getFilteredMessage().toString());
        }
    }

    @ParameterizedTest(name = "TransferPacket v{0}")
    @MethodSource("allVersions")
    void testTransferPacket(int protocolVersion) {
        cn.nukkit.network.protocol.TransferPacket nukkitPacket =
                new cn.nukkit.network.protocol.TransferPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.address = "127.0.0.1";
        nukkitPacket.port = 19132;
        if (protocolVersion >= ProtocolInfo.v1_21_30) {
            nukkitPacket.reloadWorld = true;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TransferPacket.class);

        assertEquals("127.0.0.1", cbPacket.getAddress());
        assertEquals(19132, cbPacket.getPort());
        if (protocolVersion >= ProtocolInfo.v1_21_30) {
            assertTrue(cbPacket.isReloadWorld());
        }
    }

    // ==================== Batch 2: Core Game Packets ====================

    @ParameterizedTest(name = "MovePlayerPacket v{0}")
    @MethodSource("allVersions")
    void testMovePlayerPacket(int protocolVersion) {
        cn.nukkit.network.protocol.MovePlayerPacket nukkitPacket =
                new cn.nukkit.network.protocol.MovePlayerPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.pitch = 45.0f;
        nukkitPacket.yaw = 90.0f;
        nukkitPacket.headYaw = 90.0f;
        nukkitPacket.mode = cn.nukkit.network.protocol.MovePlayerPacket.MODE_NORMAL;
        nukkitPacket.onGround = true;
        nukkitPacket.ridingEid = 0;
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.frame = 100;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(45.0f, cbPacket.getRotation().getX(), 0.001f);  // pitch
        assertEquals(90.0f, cbPacket.getRotation().getY(), 0.001f);  // yaw
        assertEquals(90.0f, cbPacket.getRotation().getZ(), 0.001f);  // headYaw
        assertEquals(0, cbPacket.getMode().ordinal(), "MODE_NORMAL should be ordinal 0");
        assertTrue(cbPacket.isOnGround());
        assertEquals(0, cbPacket.getRidingRuntimeEntityId());
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            assertEquals(100, cbPacket.getTick());
        }
    }

    @ParameterizedTest(name = "SetEntityMotionPacket v{0}")
    @MethodSource("allVersions")
    void testSetEntityMotionPacket(int protocolVersion) {
        cn.nukkit.network.protocol.SetEntityMotionPacket nukkitPacket =
                new cn.nukkit.network.protocol.SetEntityMotionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 7;
        nukkitPacket.motionX = 0.5f;
        nukkitPacket.motionY = 1.0f;
        nukkitPacket.motionZ = -0.5f;
        if (protocolVersion >= ProtocolInfo.v1_20_70) {
            nukkitPacket.tick = 500;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket.class);

        assertEquals(7, cbPacket.getRuntimeEntityId());
        assertEquals(0.5f, cbPacket.getMotion().getX(), 0.001f);
        assertEquals(1.0f, cbPacket.getMotion().getY(), 0.001f);
        assertEquals(-0.5f, cbPacket.getMotion().getZ(), 0.001f);
        if (protocolVersion >= ProtocolInfo.v1_20_70) {
            assertEquals(500, cbPacket.getTick());
        }
    }

    @ParameterizedTest(name = "TextPacket legacy v{0}")
    @MethodSource("textPacketLegacyVersions")
    void testTextPacketLegacyRaw(int protocolVersion) {
        cn.nukkit.network.protocol.TextPacket nukkitPacket =
                new cn.nukkit.network.protocol.TextPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = cn.nukkit.network.protocol.TextPacket.TYPE_RAW;
        nukkitPacket.isLocalized = false;
        nukkitPacket.message = "Hello World";
        nukkitPacket.xboxUserId = "";
        nukkitPacket.platformChatId = "";
        if (protocolVersion >= ProtocolInfo.v1_21_0) {
            nukkitPacket.filteredMessage = "";
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TextPacket.class);

        assertEquals(0, cbPacket.getType().ordinal(), "TYPE_RAW should be ordinal 0");
        assertFalse(cbPacket.isNeedsTranslation());
        assertEquals("Hello World", cbPacket.getMessage().toString());
        assertEquals("", cbPacket.getXuid());
        assertEquals("", cbPacket.getPlatformChatId());
    }
}
