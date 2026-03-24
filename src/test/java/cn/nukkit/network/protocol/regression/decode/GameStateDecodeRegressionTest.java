package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode regression tests for bidirectional and client-to-server game-state packets.
 * CB Protocol encodes, Nukkit-MOT decodes.
 * <p>
 * Server-to-client only packets (TransferPacket, ChangeDimensionPacket, etc.) are excluded.
 */
public class GameStateDecodeRegressionTest extends AbstractPacketRegressionTest {

    static Stream<Arguments> versionsPreV622() {
        return filteredVersionsRange(291, ProtocolInfo.v1_20_40);
    }

    static Stream<Arguments> versionsFrom622() {
        return filteredVersions(ProtocolInfo.v1_20_40);
    }

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    // ==================== DisconnectPacket (bidirectional) ====================

    @ParameterizedTest(name = "DisconnectPacket v{0}")
    @MethodSource("versionsPreV622")
    void disconnectPreReason(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket();
        cb.setMessageSkipped(false);
        cb.setKickMessage("Server closed");

        DisconnectPacket nk = crossEncode(cb, DisconnectPacket::new, protocol);

        assertFalse(nk.hideDisconnectionScreen);
        assertEquals("Server closed", nk.message);
    }

    @ParameterizedTest(name = "DisconnectPacket v{0}")
    @MethodSource("versionsFrom622")
    void disconnectWithReason(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket();
        cb.setReason(org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason.UNKNOWN);
        cb.setMessageSkipped(false);
        cb.setKickMessage("Server closed");
        if (protocol >= ProtocolInfo.v1_21_20) {
            cb.setFilteredMessage("Server closed");
        }

        DisconnectPacket nk = crossEncode(cb, DisconnectPacket::new, protocol);

        assertEquals(0, nk.reason.ordinal());  // UNKNOWN ordinal = 0
        assertFalse(nk.hideDisconnectionScreen);
        assertEquals("Server closed", nk.message);
        if (protocol >= ProtocolInfo.v1_21_20) {
            assertEquals("Server closed", nk.filteredMessage);
        }
    }

    // ==================== RespawnPacket (bidirectional) ====================

    @ParameterizedTest(name = "RespawnPacket v{0}")
    @MethodSource("allVersions")
    void respawnPacket(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.RespawnPacket();
        cb.setPosition(Vector3f.from(50f, 70f, -20f));
        if (protocol >= 388) {
            cb.setState(org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.State.CLIENT_READY);
            cb.setRuntimeEntityId(7L);
        }

        RespawnPacket nk = crossEncode(cb, RespawnPacket::new, protocol);

        assertEquals(50f, nk.x, 0.001f);
        assertEquals(70f, nk.y, 0.001f);
        assertEquals(-20f, nk.z, 0.001f);
        if (protocol >= 388) {
            assertEquals(RespawnPacket.STATE_CLIENT_READY_TO_SPAWN, nk.respawnState);
            assertEquals(7L, nk.runtimeEntityId);
        }
    }

    // ==================== ContainerClosePacket (bidirectional) ====================

    @ParameterizedTest(name = "ContainerClosePacket v{0}")
    @MethodSource("allVersions")
    void containerClose(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket();
        cb.setId((byte) 5);
        if (protocol >= ProtocolInfo.v1_16_100) {
            cb.setServerInitiated(true);
        }
        if (protocol >= ProtocolInfo.v1_21_0) {
            cb.setType(org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType.CONTAINER);
        }

        ContainerClosePacket nk = crossEncode(cb, ContainerClosePacket::new, protocol);

        assertEquals(5, nk.windowId);
        if (protocol >= ProtocolInfo.v1_16_100) {
            assertTrue(nk.wasServerInitiated);
        }
        if (protocol >= ProtocolInfo.v1_21_0) {
            assertEquals(ContainerType.CONTAINER, nk.type);
        }
    }

    // ==================== InteractPacket (client → server) ====================

    @ParameterizedTest(name = "InteractPacket v{0}")
    @MethodSource("allVersions")
    void interactOpenInventory(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InteractPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action.OPEN_INVENTORY);
        cb.setRuntimeEntityId(10L);

        InteractPacket nk = crossEncode(cb, InteractPacket::new, protocol);

        assertEquals(InteractPacket.ACTION_OPEN_INVENTORY, nk.action);
        assertEquals(10L, nk.target);
    }

    @ParameterizedTest(name = "InteractPacket v{0}")
    @MethodSource("versionsFrom388")
    void interactMouseover(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.InteractPacket();
        cb.setAction(org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action.MOUSEOVER);
        cb.setRuntimeEntityId(15L);
        cb.setMousePosition(Vector3f.from(1.5f, 2.0f, -3.5f));

        InteractPacket nk = crossEncode(cb, InteractPacket::new, protocol);

        assertEquals(InteractPacket.ACTION_MOUSEOVER, nk.action);
        assertEquals(15L, nk.target);
        assertEquals(1.5f, nk.x, 0.001f);
        assertEquals(2.0f, nk.y, 0.001f);
        assertEquals(-3.5f, nk.z, 0.001f);
    }

    // ==================== PlayerActionPacket (client → server) ====================

    @ParameterizedTest(name = "PlayerActionPacket v{0}")
    @MethodSource("allVersions")
    void playerActionStartBreak(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket();
        cb.setRuntimeEntityId(1L);
        cb.setAction(PlayerActionType.START_BREAK);
        cb.setBlockPosition(Vector3i.from(10, 64, -5));
        cb.setResultPosition(Vector3i.from(10, 65, -5));
        cb.setFace(0); // DOWN

        PlayerActionPacket nk = crossEncode(cb, PlayerActionPacket::new, protocol);

        assertEquals(1L, nk.entityId);
        assertEquals(PlayerActionPacket.ACTION_START_BREAK, nk.action);
        assertEquals(10, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-5, nk.z);
        assertEquals(0, nk.face);
    }
}
