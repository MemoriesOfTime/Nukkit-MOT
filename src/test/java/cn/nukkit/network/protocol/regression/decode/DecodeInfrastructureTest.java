package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the cross-encode (CB encode → NK decode) infrastructure.
 * Verifies the decoding pipeline works correctly with client-to-server packets
 * that have full decode() implementations.
 */
public class DecodeInfrastructureTest extends AbstractPacketRegressionTest {

    @ParameterizedTest(name = "MovePlayerPacket v{0}")
    @MethodSource("allVersions")
    void movePlayer_normalMode(int protocol) {
        var cbPacket = new org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket();
        cbPacket.setRuntimeEntityId(42);
        cbPacket.setPosition(Vector3f.from(1.5f, 64.0f, -3.25f));
        cbPacket.setRotation(Vector3f.from(45.0f, 90.0f, 0.0f)); // pitch, yaw, headYaw
        cbPacket.setMode(Mode.NORMAL);
        cbPacket.setOnGround(true);
        cbPacket.setRidingRuntimeEntityId(0);
        if (protocol >= ProtocolInfo.v1_16_100) {
            cbPacket.setTick(100);
        }

        MovePlayerPacket nk = crossEncode(cbPacket, MovePlayerPacket::new, protocol);

        assertEquals(42, nk.eid);
        assertEquals(1.5f, nk.x, 0.001f);
        assertEquals(64.0f, nk.y, 0.001f);
        assertEquals(-3.25f, nk.z, 0.001f);
        assertEquals(45.0f, nk.pitch, 0.001f);
        assertEquals(90.0f, nk.yaw, 0.001f);
        assertEquals(0.0f, nk.headYaw, 0.001f);
        assertEquals(MovePlayerPacket.MODE_NORMAL, nk.mode);
        assertTrue(nk.onGround);
        assertEquals(0, nk.ridingEid);
        if (protocol >= ProtocolInfo.v1_16_100) {
            assertEquals(100, nk.frame);
        }
    }

    @ParameterizedTest(name = "MovePlayerPacket v{0}")
    @MethodSource("allVersions")
    void movePlayer_teleportMode(int protocol) {
        var cbPacket = new org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket();
        cbPacket.setRuntimeEntityId(7);
        cbPacket.setPosition(Vector3f.from(100.0f, 80.0f, 200.0f));
        cbPacket.setRotation(Vector3f.from(0.0f, 180.0f, 180.0f));
        cbPacket.setMode(Mode.TELEPORT);
        cbPacket.setOnGround(false);
        cbPacket.setRidingRuntimeEntityId(0);
        cbPacket.setTeleportationCause(org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.TeleportationCause.BEHAVIOR);
        cbPacket.setEntityType(0);
        if (protocol >= ProtocolInfo.v1_16_100) {
            cbPacket.setTick(50);
        }

        MovePlayerPacket nk = crossEncode(cbPacket, MovePlayerPacket::new, protocol);

        assertEquals(7, nk.eid);
        assertEquals(100.0f, nk.x, 0.001f);
        assertEquals(80.0f, nk.y, 0.001f);
        assertEquals(200.0f, nk.z, 0.001f);
        assertEquals(MovePlayerPacket.MODE_TELEPORT, nk.mode);
    }
}
