package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector2f;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for packets with version branching but manageable complexity.
 */
public class MediumPacketRegressionTest extends AbstractPacketRegressionTest {

    /**
     * Creates a helper configurer that registers mock block definitions for testing.
     */
    private static Consumer<org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper> withBlockDefinitions(int... runtimeIds) {
        return helper -> {
            var builder = SimpleDefinitionRegistry.<BlockDefinition>builder();
            for (int id : runtimeIds) {
                builder.add(new SimpleBlockDefinition("test:block_" + id, id, NbtMap.EMPTY));
            }
            helper.setBlockDefinitions(builder.build());
        };
    }

    // --- Version Sources ---

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(407);
    }

    static Stream<Arguments> versionsPre407() {
        return filteredVersionsRange(291, 407);
    }

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    static Stream<Arguments> versionsPre388() {
        return filteredVersionsRange(291, 388);
    }

    static Stream<Arguments> versionsFrom419to685() {
        return filteredVersionsRange(ProtocolInfo.v1_16_100, ProtocolInfo.v1_21_0);
    }

    static Stream<Arguments> versionsFrom685() {
        return filteredVersions(ProtocolInfo.v1_21_0);
    }

    static Stream<Arguments> versionsPre419() {
        return filteredVersionsRange(291, ProtocolInfo.v1_16_100);
    }

    static Stream<Arguments> versionsFrom448to712() {
        return filteredVersionsRange(ProtocolInfo.v1_17_10, ProtocolInfo.v1_21_20);
    }

    static Stream<Arguments> versionsFrom712() {
        return filteredVersions(ProtocolInfo.v1_21_20);
    }

    static Stream<Arguments> versionsPre448() {
        return filteredVersionsRange(291, ProtocolInfo.v1_17_10);
    }

    static Stream<Arguments> versionsPre527() {
        return filteredVersionsRange(291, ProtocolInfo.v1_19_0_29);
    }

    static Stream<Arguments> versionsFrom527() {
        return filteredVersions(ProtocolInfo.v1_19_0_29);
    }

    static Stream<Arguments> versionsFrom554() {
        return filteredVersions(ProtocolInfo.v1_19_30);
    }

    static Stream<Arguments> versionsFrom544() {
        return filteredVersions(ProtocolInfo.v1_19_20);
    }

    static Stream<Arguments> versionsPre544() {
        // NetworkChunkPublisherUpdatePacket first registered in CB Protocol v313
        return filteredVersionsRange(313, ProtocolInfo.v1_19_20);
    }

    // ==================== SetSpawnPositionPacket ====================

    @ParameterizedTest(name = "SetSpawnPositionPacket v{0} (>=407)")
    @MethodSource("versionsFrom407")
    void testSetSpawnPositionPacketFrom407(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetSpawnPositionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.spawnType = cn.nukkit.network.protocol.SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
        nukkitPacket.x = 100;
        nukkitPacket.y = 64;
        nukkitPacket.z = 200;
        nukkitPacket.dimension = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket.class);

        assertEquals(1, cbPacket.getSpawnType().ordinal());
        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertEquals(0, cbPacket.getDimensionId());
    }

    @ParameterizedTest(name = "SetSpawnPositionPacket v{0} (<407)")
    @MethodSource("versionsPre407")
    void testSetSpawnPositionPacketPre407(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetSpawnPositionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.spawnType = cn.nukkit.network.protocol.SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
        nukkitPacket.x = 100;
        nukkitPacket.y = 64;
        nukkitPacket.z = 200;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket.class);

        assertEquals(1, cbPacket.getSpawnType().ordinal());
        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
    }

    // ==================== ChangeDimensionPacket ====================

    @ParameterizedTest(name = "ChangeDimensionPacket v{0}")
    @MethodSource("allVersions")
    void testChangeDimensionPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ChangeDimensionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.dimension = 1; // Nether
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.respawn = false;
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            nukkitPacket.loadingScreenId = null;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket.class);

        assertEquals(1, cbPacket.getDimension());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertFalse(cbPacket.isRespawn());
    }

    // ==================== RespawnPacket ====================

    @ParameterizedTest(name = "RespawnPacket v{0} (>=388)")
    @MethodSource("versionsFrom388")
    void testRespawnPacketFrom388(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.RespawnPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.respawnState = cn.nukkit.network.protocol.RespawnPacket.STATE_READY_TO_SPAWN;
        nukkitPacket.runtimeEntityId = 42;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(1, cbPacket.getState().ordinal());
        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    @ParameterizedTest(name = "RespawnPacket v{0} (<388)")
    @MethodSource("versionsPre388")
    void testRespawnPacketPre388(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.RespawnPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
    }

    // ==================== ContainerClosePacket ====================

    @ParameterizedTest(name = "ContainerClosePacket v{0} (<419)")
    @MethodSource("versionsPre419")
    void testContainerClosePacketPre419(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ContainerClosePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 5;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket.class);

        assertEquals(5, cbPacket.getId());
    }

    @ParameterizedTest(name = "ContainerClosePacket v{0} (419-685)")
    @MethodSource("versionsFrom419to685")
    void testContainerClosePacketFrom419(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ContainerClosePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 5;
        nukkitPacket.wasServerInitiated = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket.class);

        assertEquals(5, cbPacket.getId());
        assertTrue(cbPacket.isServerInitiated());
    }

    @ParameterizedTest(name = "ContainerClosePacket v{0} (>=685)")
    @MethodSource("versionsFrom685")
    void testContainerClosePacketFrom685(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ContainerClosePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 5;
        nukkitPacket.wasServerInitiated = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket.class);

        assertEquals(5, cbPacket.getId());
        assertTrue(cbPacket.isServerInitiated());
    }

    // ==================== StopSoundPacket ====================

    @ParameterizedTest(name = "StopSoundPacket v{0}")
    @MethodSource("allVersions")
    void testStopSoundPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.StopSoundPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.name = "random.click";
        nukkitPacket.stopAll = true;
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            nukkitPacket.stopMusicLegacy = false;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket.class);

        assertEquals("random.click", cbPacket.getSoundName());
        assertTrue(cbPacket.isStoppingAllSound());
    }

    // ==================== SetEntityLinkPacket ====================

    @ParameterizedTest(name = "SetEntityLinkPacket v{0}")
    @MethodSource("allVersions")
    void testSetEntityLinkPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetEntityLinkPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.vehicleUniqueId = 10;
        nukkitPacket.riderUniqueId = 20;
        nukkitPacket.type = cn.nukkit.network.protocol.SetEntityLinkPacket.TYPE_RIDE;
        nukkitPacket.immediate = 1;
        if (protocolVersion >= 407) {
            nukkitPacket.riderInitiated = true;
        }
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            nukkitPacket.vehicleAngularVelocity = 0.0f;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket.class);

        assertEquals(10, cbPacket.getEntityLink().getFrom());
        assertEquals(20, cbPacket.getEntityLink().getTo());
        assertEquals(1, cbPacket.getEntityLink().getType().ordinal());
    }

    // ==================== MoveEntityAbsolutePacket ====================

    @ParameterizedTest(name = "MoveEntityAbsolutePacket v{0}")
    @MethodSource("allVersions")
    void testMoveEntityAbsolutePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MoveEntityAbsolutePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.x = 100.5;
        nukkitPacket.y = 64.0;
        nukkitPacket.z = 200.5;
        nukkitPacket.pitch = 45.0;
        nukkitPacket.headYaw = 90.0;
        nukkitPacket.yaw = 180.0;
        nukkitPacket.onGround = true;
        nukkitPacket.teleport = false;
        nukkitPacket.forceMoveLocalEntity = false;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.5f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.5f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.5f);
        assertTrue(cbPacket.isOnGround());
        assertFalse(cbPacket.isTeleported());
    }

    // ==================== SetTitlePacket ====================

    @ParameterizedTest(name = "SetTitlePacket v{0} (<448)")
    @MethodSource("versionsPre448")
    void testSetTitlePacketPre448(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetTitlePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = cn.nukkit.network.protocol.SetTitlePacket.TYPE_TITLE;
        nukkitPacket.text = "Hello";
        nukkitPacket.fadeInTime = 10;
        nukkitPacket.stayTime = 70;
        nukkitPacket.fadeOutTime = 20;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket.class);

        assertEquals(2, cbPacket.getType().ordinal());
        assertEquals("Hello", cbPacket.getText());
        assertEquals(10, cbPacket.getFadeInTime());
        assertEquals(70, cbPacket.getStayTime());
        assertEquals(20, cbPacket.getFadeOutTime());
    }

    @ParameterizedTest(name = "SetTitlePacket v{0} (448-712)")
    @MethodSource("versionsFrom448to712")
    void testSetTitlePacketFrom448(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetTitlePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = cn.nukkit.network.protocol.SetTitlePacket.TYPE_TITLE;
        nukkitPacket.text = "Hello";
        nukkitPacket.fadeInTime = 10;
        nukkitPacket.stayTime = 70;
        nukkitPacket.fadeOutTime = 20;
        nukkitPacket.xuid = "12345";
        nukkitPacket.platformOnlineId = "";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket.class);

        assertEquals(2, cbPacket.getType().ordinal());
        assertEquals("Hello", cbPacket.getText());
        assertEquals(10, cbPacket.getFadeInTime());
        assertEquals(70, cbPacket.getStayTime());
        assertEquals(20, cbPacket.getFadeOutTime());
        assertEquals("12345", cbPacket.getXuid());
        assertEquals("", cbPacket.getPlatformOnlineId());
    }

    @ParameterizedTest(name = "SetTitlePacket v{0} (>=712)")
    @MethodSource("versionsFrom712")
    void testSetTitlePacketFrom712(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetTitlePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = cn.nukkit.network.protocol.SetTitlePacket.TYPE_TITLE;
        nukkitPacket.text = "Hello";
        nukkitPacket.fadeInTime = 10;
        nukkitPacket.stayTime = 70;
        nukkitPacket.fadeOutTime = 20;
        nukkitPacket.xuid = "12345";
        nukkitPacket.platformOnlineId = "";
        nukkitPacket.filteredTitleText = "Hello Filtered";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket.class);

        assertEquals(2, cbPacket.getType().ordinal());
        assertEquals("Hello", cbPacket.getText());
        assertEquals(10, cbPacket.getFadeInTime());
        assertEquals(70, cbPacket.getStayTime());
        assertEquals(20, cbPacket.getFadeOutTime());
        assertEquals("12345", cbPacket.getXuid());
        assertEquals("", cbPacket.getPlatformOnlineId());
        assertEquals("Hello Filtered", cbPacket.getFilteredTitleText());
    }

    // ==================== EmotePacket ====================

    @ParameterizedTest(name = "EmotePacket v{0}")
    @MethodSource("versionsFrom388")
    void testEmotePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.EmotePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.runtimeId = 42;
        nukkitPacket.emoteID = "emote-test-id";
        nukkitPacket.flags = cn.nukkit.network.protocol.EmotePacket.FLAG_SERVER;
        if (protocolVersion >= ProtocolInfo.v1_20_0_23) {
            nukkitPacket.xuid = "";
            nukkitPacket.platformId = "";
            if (protocolVersion >= ProtocolInfo.v1_21_30) {
                nukkitPacket.emoteDuration = 100;
            }
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.EmotePacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals("emote-test-id", cbPacket.getEmoteId());
    }

    // ==================== NetworkChunkPublisherUpdatePacket ====================

    @ParameterizedTest(name = "NetworkChunkPublisherUpdatePacket v{0} (>=544)")
    @MethodSource("versionsFrom544")
    void testNetworkChunkPublisherUpdatePacketFrom544(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.NetworkChunkPublisherUpdatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.position = new BlockVector3(100, 64, 200);
        nukkitPacket.radius = 128;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket.class);

        assertEquals(100, cbPacket.getPosition().getX());
        assertEquals(64, cbPacket.getPosition().getY());
        assertEquals(200, cbPacket.getPosition().getZ());
        assertEquals(128, cbPacket.getRadius());
    }

    @ParameterizedTest(name = "NetworkChunkPublisherUpdatePacket v{0} (<544)")
    @MethodSource("versionsPre544")
    void testNetworkChunkPublisherUpdatePacketPre544(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.NetworkChunkPublisherUpdatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.position = new BlockVector3(100, 64, 200);
        nukkitPacket.radius = 128;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket.class);

        assertEquals(100, cbPacket.getPosition().getX());
        assertEquals(64, cbPacket.getPosition().getY());
        assertEquals(200, cbPacket.getPosition().getZ());
        assertEquals(128, cbPacket.getRadius());
    }

    // ==================== NetworkSettingsPacket ====================

    @ParameterizedTest(name = "NetworkSettingsPacket v{0} (>=554)")
    @MethodSource("versionsFrom554")
    void testNetworkSettingsPacketFrom554(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.NetworkSettingsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.compressionThreshold = 256;
        nukkitPacket.compressionAlgorithm = PacketCompressionAlgorithm.ZLIB;
        nukkitPacket.clientThrottleEnabled = false;
        nukkitPacket.clientThrottleThreshold = 0;
        nukkitPacket.clientThrottleScalar = 0.0f;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket.class);

        assertEquals(256, cbPacket.getCompressionThreshold());
        assertEquals(0, cbPacket.getCompressionAlgorithm().ordinal());
        assertFalse(cbPacket.isClientThrottleEnabled());
        assertEquals(0, cbPacket.getClientThrottleThreshold());
        assertEquals(0.0f, cbPacket.getClientThrottleScalar(), 0.001f);
    }

    // ==================== SetScorePacket ====================

    @ParameterizedTest(name = "SetScorePacket FAKE v{0}")
    @MethodSource("allVersions")
    void testSetScorePacketFake(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetScorePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.action = cn.nukkit.network.protocol.SetScorePacket.Action.SET;

        var info = new cn.nukkit.network.protocol.SetScorePacket.ScoreInfo(
                1L, "objective1", 100, "TestPlayer"
        );
        nukkitPacket.infos.add(info);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetScorePacket.class);

        assertEquals(0, cbPacket.getAction().ordinal());
        assertEquals(1, cbPacket.getInfos().size());
        var cbInfo = cbPacket.getInfos().get(0);
        assertEquals(1L, cbInfo.getScoreboardId());
        assertEquals("objective1", cbInfo.getObjectiveId());
        assertEquals(100, cbInfo.getScore());
        assertEquals("TestPlayer", cbInfo.getName());
    }

    @ParameterizedTest(name = "SetScorePacket REMOVE v{0}")
    @MethodSource("allVersions")
    void testSetScorePacketRemove(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetScorePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.action = cn.nukkit.network.protocol.SetScorePacket.Action.REMOVE;

        var info = new cn.nukkit.network.protocol.SetScorePacket.ScoreInfo(
                1L, "objective1", 50
        );
        nukkitPacket.infos.add(info);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetScorePacket.class);

        assertEquals(1, cbPacket.getAction().ordinal());
        assertEquals(1, cbPacket.getInfos().size());
        var cbInfo = cbPacket.getInfos().get(0);
        assertEquals(1L, cbInfo.getScoreboardId());
        assertEquals("objective1", cbInfo.getObjectiveId());
        assertEquals(50, cbInfo.getScore());
    }

    // --- Version Sources for HurtArmorPacket ---

    static Stream<Arguments> versionsPre428() {
        // HurtArmorPacket before v1_16_0 (428) has no cause field
        return filteredVersionsRange(291, 428);
    }

    static Stream<Arguments> versionsFrom428to475() {
        // Has cause but no armorSlots
        return filteredVersionsRange(428, 475);
    }

    static Stream<Arguments> versionsFrom475() {
        // Has cause and armorSlots (v465 in CB, but v475 in Nukkit uses v1_17_30)
        return filteredVersions(475);
    }

    // ==================== HurtArmorPacket ====================

    @ParameterizedTest(name = "HurtArmorPacket v{0} (<428)")
    @MethodSource("versionsPre428")
    void testHurtArmorPacketPre428(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.HurtArmorPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.damage = 10;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.HurtArmorPacket.class);

        assertEquals(10, cbPacket.getDamage());
    }

    @ParameterizedTest(name = "HurtArmorPacket v{0} (428-475)")
    @MethodSource("versionsFrom428to475")
    void testHurtArmorPacketFrom428(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.HurtArmorPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.cause = 1;
        nukkitPacket.damage = 10;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.HurtArmorPacket.class);

        assertEquals(1, cbPacket.getCause());
        assertEquals(10, cbPacket.getDamage());
    }

    @ParameterizedTest(name = "HurtArmorPacket v{0} (>=475)")
    @MethodSource("versionsFrom475")
    void testHurtArmorPacketFrom475(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.HurtArmorPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.cause = 1;
        nukkitPacket.damage = 10;
        nukkitPacket.armorSlots = 0b1111L; // all armor slots
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.HurtArmorPacket.class);

        assertEquals(1, cbPacket.getCause());
        assertEquals(10, cbPacket.getDamage());
        assertEquals(0b1111L, cbPacket.getArmorSlots());
    }

    // ==================== BossEventPacket ====================

    @ParameterizedTest(name = "BossEventPacket SHOW v{0}")
    @MethodSource("allVersions")
    void testBossEventPacketShow(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.BossEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.bossEid = 100;
        nukkitPacket.type = cn.nukkit.network.protocol.BossEventPacket.TYPE_SHOW;
        nukkitPacket.title = "Dragon Boss";
        nukkitPacket.healthPercent = 0.75f;
        nukkitPacket.darkenScreen = 1;
        nukkitPacket.color = 0;
        nukkitPacket.overlay = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.class);

        assertEquals(100, cbPacket.getBossUniqueEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.CREATE, cbPacket.getAction());
        assertEquals("Dragon Boss", cbPacket.getTitle());
        assertEquals(0.75f, cbPacket.getHealthPercentage(), 0.001f);
    }

    @ParameterizedTest(name = "BossEventPacket HEALTH_PERCENT v{0}")
    @MethodSource("allVersions")
    void testBossEventPacketHealthPercent(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.BossEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.bossEid = 100;
        nukkitPacket.type = cn.nukkit.network.protocol.BossEventPacket.TYPE_HEALTH_PERCENT;
        nukkitPacket.healthPercent = 0.5f;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.class);

        assertEquals(100, cbPacket.getBossUniqueEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.UPDATE_PERCENTAGE, cbPacket.getAction());
        assertEquals(0.5f, cbPacket.getHealthPercentage(), 0.001f);
    }

    @ParameterizedTest(name = "BossEventPacket TITLE v{0}")
    @MethodSource("allVersions")
    void testBossEventPacketTitle(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.BossEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.bossEid = 100;
        nukkitPacket.type = cn.nukkit.network.protocol.BossEventPacket.TYPE_TITLE;
        nukkitPacket.title = "New Title";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.class);

        assertEquals(100, cbPacket.getBossUniqueEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.UPDATE_NAME, cbPacket.getAction());
        assertEquals("New Title", cbPacket.getTitle());
    }

    @ParameterizedTest(name = "BossEventPacket HIDE v{0}")
    @MethodSource("allVersions")
    void testBossEventPacketHide(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.BossEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.bossEid = 100;
        nukkitPacket.type = cn.nukkit.network.protocol.BossEventPacket.TYPE_HIDE;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.class);

        assertEquals(100, cbPacket.getBossUniqueEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.BossEventPacket.Action.REMOVE, cbPacket.getAction());
    }

    // ==================== PlayerActionPacket ====================

    @ParameterizedTest(name = "PlayerActionPacket v{0} (pre-527)")
    @MethodSource("versionsPre527")
    void testPlayerActionPacketPre527(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlayerActionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityId = 100;
        nukkitPacket.action = cn.nukkit.network.protocol.PlayerActionPacket.ACTION_JUMP;
        nukkitPacket.x = 10;
        nukkitPacket.y = 64;
        nukkitPacket.z = 20;
        nukkitPacket.face = 1;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket.class);

        assertEquals(100, cbPacket.getRuntimeEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.PlayerActionType.JUMP, cbPacket.getAction());
        assertEquals(10, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(20, cbPacket.getBlockPosition().getZ());
        assertEquals(1, cbPacket.getFace());
    }

    @ParameterizedTest(name = "PlayerActionPacket v{0} (>=527)")
    @MethodSource("versionsFrom527")
    void testPlayerActionPacketFrom527(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlayerActionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityId = 100;
        nukkitPacket.action = cn.nukkit.network.protocol.PlayerActionPacket.ACTION_START_BREAK;
        nukkitPacket.x = 10;
        nukkitPacket.y = 64;
        nukkitPacket.z = 20;
        nukkitPacket.resultPosition = new cn.nukkit.math.BlockVector3(15, 70, 25);
        nukkitPacket.face = 2;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket.class);

        assertEquals(100, cbPacket.getRuntimeEntityId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.PlayerActionType.START_BREAK, cbPacket.getAction());
        assertEquals(10, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(20, cbPacket.getBlockPosition().getZ());
        assertEquals(15, cbPacket.getResultPosition().getX());
        assertEquals(70, cbPacket.getResultPosition().getY());
        assertEquals(25, cbPacket.getResultPosition().getZ());
        assertEquals(2, cbPacket.getFace());
    }

    // ==================== CameraAimAssistPacket ====================

    static Stream<Arguments> versionsFrom729to766() {
        return filteredVersionsRange(ProtocolInfo.v1_21_30, ProtocolInfo.v1_21_50);
    }

    static Stream<Arguments> versionsFrom766to827() {
        return filteredVersionsRange(ProtocolInfo.v1_21_50, ProtocolInfo.v1_21_100);
    }

    static Stream<Arguments> versionsFrom827() {
        return filteredVersions(ProtocolInfo.v1_21_100);
    }

    @ParameterizedTest(name = "CameraAimAssistPacket v{0} (729-766)")
    @MethodSource("versionsFrom729to766")
    void testCameraAimAssistPacketPre766(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraAimAssistPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setViewAngle(new Vector2f(45.0f, 90.0f));
        nukkitPacket.setDistance(10.5f);
        nukkitPacket.setTargetMode(cn.nukkit.network.protocol.CameraAimAssistPacket.TargetMode.ANGLE);
        nukkitPacket.setAction(cn.nukkit.network.protocol.CameraAimAssistPacket.Action.SET);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPacket.class);

        assertEquals(45.0f, cbPacket.getViewAngle().getX(), 0.001f);
        assertEquals(90.0f, cbPacket.getViewAngle().getY(), 0.001f);
        assertEquals(10.5f, cbPacket.getDistance(), 0.001f);
        assertEquals(0, cbPacket.getTargetMode().ordinal()); // ANGLE
        assertEquals(0, cbPacket.getAction().ordinal()); // SET
    }

    @ParameterizedTest(name = "CameraAimAssistPacket v{0} (766-827)")
    @MethodSource("versionsFrom766to827")
    void testCameraAimAssistPacketFrom766(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraAimAssistPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setPresetId("aim_preset_1");
        nukkitPacket.setViewAngle(new Vector2f(45.0f, 90.0f));
        nukkitPacket.setDistance(10.5f);
        nukkitPacket.setTargetMode(cn.nukkit.network.protocol.CameraAimAssistPacket.TargetMode.DISTANCE);
        nukkitPacket.setAction(cn.nukkit.network.protocol.CameraAimAssistPacket.Action.CLEAR);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPacket.class);

        assertEquals("aim_preset_1", cbPacket.getPresetId());
        assertEquals(45.0f, cbPacket.getViewAngle().getX(), 0.001f);
        assertEquals(90.0f, cbPacket.getViewAngle().getY(), 0.001f);
        assertEquals(10.5f, cbPacket.getDistance(), 0.001f);
        assertEquals(1, cbPacket.getTargetMode().ordinal()); // DISTANCE
        assertEquals(1, cbPacket.getAction().ordinal()); // CLEAR
    }

    @ParameterizedTest(name = "CameraAimAssistPacket v{0} (>=827)")
    @MethodSource("versionsFrom827")
    void testCameraAimAssistPacketFrom827(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraAimAssistPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setPresetId("aim_preset_2");
        nukkitPacket.setViewAngle(new Vector2f(30.0f, 60.0f));
        nukkitPacket.setDistance(5.0f);
        nukkitPacket.setTargetMode(cn.nukkit.network.protocol.CameraAimAssistPacket.TargetMode.ANGLE);
        nukkitPacket.setAction(cn.nukkit.network.protocol.CameraAimAssistPacket.Action.SET);
        nukkitPacket.setShowDebugRender(true);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistPacket.class);

        assertEquals("aim_preset_2", cbPacket.getPresetId());
        assertEquals(30.0f, cbPacket.getViewAngle().getX(), 0.001f);
        assertEquals(60.0f, cbPacket.getViewAngle().getY(), 0.001f);
        assertEquals(5.0f, cbPacket.getDistance(), 0.001f);
        assertEquals(0, cbPacket.getTargetMode().ordinal()); // ANGLE
        assertEquals(0, cbPacket.getAction().ordinal()); // SET
        assertTrue(cbPacket.isShowDebugRender());
    }

    // ==================== UpdateBlockPacket ====================

    static Stream<Arguments> versionsPre201() {
        // v1_2_10 = 201, before that uses blockId/blockData format
        return filteredVersionsRange(291, 201);
    }

    static Stream<Arguments> versionsFrom201to224() {
        // v1_2_10 to v1_2_13_11, no dataLayer
        return filteredVersionsRange(201, 224);
    }

    static Stream<Arguments> versionsFrom224() {
        // v1_2_13_11 and above, has dataLayer
        return filteredVersions(224);
    }

    @ParameterizedTest(name = "UpdateBlockPacket v{0} (>=224)")
    @MethodSource("versionsFrom224")
    void testUpdateBlockPacketFrom224(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateBlockPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 100;
        nukkitPacket.y = 64;
        nukkitPacket.z = 200;
        nukkitPacket.blockRuntimeId = 42;
        nukkitPacket.flags = cn.nukkit.network.protocol.UpdateBlockPacket.FLAG_ALL;
        nukkitPacket.dataLayer = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.class,
                withBlockDefinitions(42));

        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertEquals(42, cbPacket.getDefinition().getRuntimeId());
        assertTrue(cbPacket.getFlags().contains(
                org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.Flag.NEIGHBORS));
        assertTrue(cbPacket.getFlags().contains(
                org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.Flag.NETWORK));
        assertEquals(0, cbPacket.getDataLayer());
    }

    @ParameterizedTest(name = "UpdateBlockPacket dataLayer=1 v{0}")
    @MethodSource("versionsFrom224")
    void testUpdateBlockPacketWithDataLayer(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateBlockPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 50;
        nukkitPacket.y = 32;
        nukkitPacket.z = 100;
        nukkitPacket.blockRuntimeId = 99;
        nukkitPacket.flags = cn.nukkit.network.protocol.UpdateBlockPacket.FLAG_ALL_PRIORITY;
        nukkitPacket.dataLayer = 1;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.class,
                withBlockDefinitions(99));

        assertEquals(50, cbPacket.getBlockPosition().getX());
        assertEquals(32, cbPacket.getBlockPosition().getY());
        assertEquals(100, cbPacket.getBlockPosition().getZ());
        assertEquals(99, cbPacket.getDefinition().getRuntimeId());
        // Nukkit FLAG_ALL_PRIORITY = 11 (0b1011) = NEIGHBORS|NETWORK|PRIORITY(bit3)
        // CB Protocol maps bit3 to UNUSED, not PRIORITY (which is bit4)
        assertTrue(cbPacket.getFlags().contains(
                org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.Flag.NEIGHBORS));
        assertTrue(cbPacket.getFlags().contains(
                org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.Flag.NETWORK));
        assertEquals(1, cbPacket.getDataLayer());
    }

    // ==================== LevelChunkPacket ====================

    static Stream<Arguments> versionsFrom340to486() {
        // v1.12.0 (361) to before v1.18.0 (486)
        return filteredVersionsRange(361, ProtocolInfo.v1_18_0);
    }

    static Stream<Arguments> versionsFrom486to649() {
        // v1.18.10 (486) to before v1.20.60 (649); requestSubChunks support starts in CB v486
        return filteredVersionsRange(486, ProtocolInfo.v1_20_60);
    }

    static Stream<Arguments> versionsFrom649() {
        return filteredVersions(ProtocolInfo.v1_20_60);
    }

    @ParameterizedTest(name = "LevelChunkPacket v{0} (361-486, basic)")
    @MethodSource("versionsFrom340to486")
    void testLevelChunkPacketBasic(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelChunkPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.chunkX = 10;
        nukkitPacket.chunkZ = 20;
        nukkitPacket.subChunkCount = 8;
        nukkitPacket.cacheEnabled = false;
        nukkitPacket.data = new byte[]{1, 2, 3, 4};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket.class);

        assertEquals(10, cbPacket.getChunkX());
        assertEquals(20, cbPacket.getChunkZ());
        assertEquals(8, cbPacket.getSubChunksLength());
        assertFalse(cbPacket.isCachingEnabled());
    }

    @ParameterizedTest(name = "LevelChunkPacket v{0} (486-649, requestSubChunks)")
    @MethodSource("versionsFrom486to649")
    void testLevelChunkPacketWithRequestSubChunks(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelChunkPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.chunkX = 10;
        nukkitPacket.chunkZ = 20;
        nukkitPacket.requestSubChunks = true;
        nukkitPacket.subChunkLimit = 16;
        nukkitPacket.cacheEnabled = false;
        nukkitPacket.data = new byte[]{5, 6, 7};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket.class);

        assertEquals(10, cbPacket.getChunkX());
        assertEquals(20, cbPacket.getChunkZ());
        assertTrue(cbPacket.isRequestSubChunks());
        assertEquals(16, cbPacket.getSubChunkLimit());
        assertFalse(cbPacket.isCachingEnabled());
    }

    @ParameterizedTest(name = "LevelChunkPacket v{0} (>=649, with dimension)")
    @MethodSource("versionsFrom649")
    void testLevelChunkPacketWithDimension(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelChunkPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.chunkX = 5;
        nukkitPacket.chunkZ = 15;
        nukkitPacket.dimension = 1; // Nether
        nukkitPacket.subChunkCount = 4;
        nukkitPacket.requestSubChunks = false;
        nukkitPacket.cacheEnabled = false;
        nukkitPacket.data = new byte[]{10, 20};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket.class);

        assertEquals(5, cbPacket.getChunkX());
        assertEquals(15, cbPacket.getChunkZ());
        assertEquals(1, cbPacket.getDimension());
        assertEquals(4, cbPacket.getSubChunksLength());
        assertFalse(cbPacket.isRequestSubChunks());
        assertFalse(cbPacket.isCachingEnabled());
    }

    @ParameterizedTest(name = "LevelChunkPacket v{0} (>=649, cacheEnabled)")
    @MethodSource("versionsFrom649")
    void testLevelChunkPacketWithCache(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelChunkPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.chunkX = 3;
        nukkitPacket.chunkZ = 7;
        nukkitPacket.dimension = 0;
        nukkitPacket.subChunkCount = 16;
        nukkitPacket.cacheEnabled = true;
        nukkitPacket.blobIds = new long[]{111L, 222L};
        nukkitPacket.data = new byte[]{};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket.class);

        assertEquals(3, cbPacket.getChunkX());
        assertEquals(7, cbPacket.getChunkZ());
        assertEquals(16, cbPacket.getSubChunksLength());
        assertTrue(cbPacket.isCachingEnabled());
        assertEquals(2, cbPacket.getBlobIds().size());
        assertEquals(111L, cbPacket.getBlobIds().getLong(0));
        assertEquals(222L, cbPacket.getBlobIds().getLong(1));
    }
}
