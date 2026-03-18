package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.cloudburstmc.math.vector.Vector3i;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode regression tests for simple packets (1–3 fields).
 * CB Protocol encodes, Nukkit-MOT decodes.
 */
public class SimpleDecodeRegressionTest extends AbstractPacketRegressionTest {

    static Stream<Arguments> versionsPreV800() {
        return filteredVersionsRange(291, ProtocolInfo.v1_21_80);
    }

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    static Stream<Arguments> versionsFrom582() {
        return filteredVersions(ProtocolInfo.v1_19_80);
    }

    // ==================== SetLocalPlayerAsInitializedPacket ====================

    @ParameterizedTest(name = "SetLocalPlayerAsInitializedPacket v{0}")
    @MethodSource("allVersions")
    void setLocalPlayerAsInitialized(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket();
        cb.setRuntimeEntityId(42L);

        SetLocalPlayerAsInitializedPacket nk = crossEncode(cb, SetLocalPlayerAsInitializedPacket::new, protocol);

        assertEquals(42L, nk.eid);
    }

    // ==================== RiderJumpPacket ====================

    @ParameterizedTest(name = "RiderJumpPacket v{0}")
    @MethodSource("versionsPreV800")
    void riderJump(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.RiderJumpPacket();
        cb.setJumpStrength(50);

        RiderJumpPacket nk = crossEncode(cb, RiderJumpPacket::new, protocol);

        assertEquals(50, nk.jumpStrength);
    }

    // ==================== RequestChunkRadiusPacket ====================

    @ParameterizedTest(name = "RequestChunkRadiusPacket v{0}")
    @MethodSource("allVersions")
    void requestChunkRadius(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket();
        cb.setRadius(8);
        if (protocol >= ProtocolInfo.v1_19_80) {
            cb.setMaxRadius(12);
        }

        RequestChunkRadiusPacket nk = crossEncode(cb, RequestChunkRadiusPacket::new, protocol);

        assertEquals(8, nk.radius);
    }

    // ==================== EntityFallPacket ====================

    @ParameterizedTest(name = "EntityFallPacket v{0}")
    @MethodSource("allVersions")
    void entityFall(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.EntityFallPacket();
        cb.setRuntimeEntityId(99L);
        cb.setFallDistance(5.5f);
        cb.setInVoid(false);

        EntityFallPacket nk = crossEncode(cb, EntityFallPacket::new, protocol);

        assertEquals(99L, nk.eid);
        assertEquals(5.5f, nk.fallDistance, 0.001f);
        assertFalse(nk.isInVoid);
    }

    // ==================== AnvilDamagePacket ====================

    @ParameterizedTest(name = "AnvilDamagePacket v{0}")
    @MethodSource("versionsFrom388")
    void anvilDamage(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.AnvilDamagePacket();
        cb.setDamage(3);
        cb.setPosition(Vector3i.from(10, 64, -5));

        AnvilDamagePacket nk = crossEncode(cb, AnvilDamagePacket::new, protocol);

        assertEquals(3, nk.damage);
        assertEquals(10, nk.x);
        assertEquals(64, nk.y);
        assertEquals(-5, nk.z);
    }

    // ==================== BlockPickRequestPacket ====================

    @ParameterizedTest(name = "BlockPickRequestPacket v{0}")
    @MethodSource("allVersions")
    void blockPickRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.BlockPickRequestPacket();
        cb.setBlockPosition(Vector3i.from(5, 70, -10));
        cb.setAddUserData(true);
        cb.setHotbarSlot(3);

        BlockPickRequestPacket nk = crossEncode(cb, BlockPickRequestPacket::new, protocol);

        assertEquals(5, nk.x);
        assertEquals(70, nk.y);
        assertEquals(-10, nk.z);
        assertTrue(nk.addUserData);
        assertEquals(3, nk.selectedSlot);
    }
}
