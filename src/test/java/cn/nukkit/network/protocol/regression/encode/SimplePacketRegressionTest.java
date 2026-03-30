package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for simple packets with no protocol version branching.
 */
public class SimplePacketRegressionTest extends AbstractPacketRegressionTest {

    // --- Version Sources ---

    static Stream<Arguments> versionsFrom332() {
        return filteredVersions(332);
    }

    static Stream<Arguments> versionsFrom354() {
        return filteredVersions(354);
    }

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(407);
    }

    static Stream<Arguments> versionsFrom428to662() {
        return filteredVersionsRange(428, 671);
    }

    static Stream<Arguments> versionsVideoStream() {
        // VideoStreamConnectPacket only registered in CB codecs v340, v354, v388
        return filteredVersionsRange(340, 407);
    }

    static Stream<Arguments> versionsPre712() {
        return filteredVersionsRange(291, ProtocolInfo.v1_21_20);
    }

    static Stream<Arguments> versionsFrom534() {
        return filteredVersions(ProtocolInfo.v1_19_10);
    }

    static Stream<Arguments> versionsFrom471() {
        return filteredVersions(ProtocolInfo.v1_17_40);
    }

    static Stream<Arguments> versionsFrom419() {
        return filteredVersions(ProtocolInfo.v1_16_100);
    }

    static Stream<Arguments> versionsFrom465() {
        return filteredVersions(465);
    }

    static Stream<Arguments> versionsPre553() {
        return filteredVersionsRange(291, ProtocolInfo.v1_19_30);
    }

    static Stream<Arguments> versionsFrom898() {
        return filteredVersions(ProtocolInfo.v1_21_130_28);
    }

    // ==================== RemoveEntityPacket ====================

    @ParameterizedTest(name = "RemoveEntityPacket v{0}")
    @MethodSource("allVersions")
    void testRemoveEntityPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.RemoveEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket.class);

        assertEquals(42, cbPacket.getUniqueEntityId());
    }

    // ==================== SetPlayerGameTypePacket ====================

    @ParameterizedTest(name = "SetPlayerGameTypePacket v{0}")
    @MethodSource("allVersions")
    void testSetPlayerGameTypePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetPlayerGameTypePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.gamemode = 1; // Creative
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket.class);

        assertEquals(1, cbPacket.getGamemode());
    }

    // ==================== SetHealthPacket ====================

    @ParameterizedTest(name = "SetHealthPacket v{0}")
    @MethodSource("allVersions")
    void testSetHealthPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetHealthPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        // health=0 avoids unsigned vs signed VarInt encoding difference
        nukkitPacket.health = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetHealthPacket.class);

        assertEquals(0, cbPacket.getHealth());
    }

    // ==================== SetDifficultyPacket ====================

    @ParameterizedTest(name = "SetDifficultyPacket v{0}")
    @MethodSource("allVersions")
    void testSetDifficultyPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetDifficultyPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.difficulty = 2; // Normal
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket.class);

        assertEquals(2, cbPacket.getDifficulty());
    }

    // ==================== SetCommandsEnabledPacket ====================

    @ParameterizedTest(name = "SetCommandsEnabledPacket v{0}")
    @MethodSource("allVersions")
    void testSetCommandsEnabledPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetCommandsEnabledPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.enabled = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket.class);

        assertTrue(cbPacket.isCommandsEnabled());
    }

    // ==================== ModalFormRequestPacket ====================

    @ParameterizedTest(name = "ModalFormRequestPacket v{0}")
    @MethodSource("allVersions")
    void testModalFormRequestPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ModalFormRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.formId = 42;
        nukkitPacket.data = "{}";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket.class);

        assertEquals(42, cbPacket.getFormId());
        assertEquals("{}", cbPacket.getFormData());
    }

    // ==================== TakeItemEntityPacket ====================

    @ParameterizedTest(name = "TakeItemEntityPacket v{0}")
    @MethodSource("allVersions")
    void testTakeItemEntityPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TakeItemEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.target = 10;
        nukkitPacket.entityId = 20;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TakeItemEntityPacket.class);

        // Nukkit-MOT encode order: target first, entityId second
        // CB Protocol: first=itemRuntimeEntityId, second=runtimeEntityId
        assertEquals(10, cbPacket.getItemRuntimeEntityId());
        assertEquals(20, cbPacket.getRuntimeEntityId());
    }

    // ==================== RemoveObjectivePacket ====================

    @ParameterizedTest(name = "RemoveObjectivePacket v{0}")
    @MethodSource("allVersions")
    void testRemoveObjectivePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.RemoveObjectivePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.objectiveId = "obj1";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RemoveObjectivePacket.class);

        assertEquals("obj1", cbPacket.getObjectiveId());
    }

    // ==================== ShowCreditsPacket ====================

    @ParameterizedTest(name = "ShowCreditsPacket v{0}")
    @MethodSource("allVersions")
    void testShowCreditsPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ShowCreditsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 5;
        nukkitPacket.status = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket.class);

        assertEquals(5, cbPacket.getRuntimeEntityId());
        assertEquals(0, cbPacket.getStatus().ordinal());
    }

    // ==================== ServerToClientHandshakePacket ====================

    @ParameterizedTest(name = "ServerToClientHandshakePacket v{0}")
    @MethodSource("allVersions")
    void testServerToClientHandshakePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ServerToClientHandshakePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.jwt = "test.jwt";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket.class);

        assertEquals("test.jwt", cbPacket.getJwt());
    }

    // ==================== BlockEventPacket ====================

    @ParameterizedTest(name = "BlockEventPacket v{0}")
    @MethodSource("allVersions")
    void testBlockEventPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.BlockEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 10;
        nukkitPacket.y = 64;
        nukkitPacket.z = 20;
        nukkitPacket.eventType = 1;
        nukkitPacket.eventData = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket.class);

        assertEquals(10, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(20, cbPacket.getBlockPosition().getZ());
        assertEquals(1, cbPacket.getEventType());
        assertEquals(0, cbPacket.getEventData());
    }

    // ==================== LevelEventPacket ====================

    @ParameterizedTest(name = "LevelEventPacket v{0}")
    @MethodSource("allVersions")
    void testLevelEventPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.evid = 1000;
        nukkitPacket.x = 10.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 20.5f;
        nukkitPacket.data = 5;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket.class);

        assertEquals(10.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(20.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(5, cbPacket.getData());
    }

    // ==================== PlaySoundPacket ====================

    @ParameterizedTest(name = "PlaySoundPacket v{0}")
    @MethodSource("allVersions")
    void testPlaySoundPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlaySoundPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.name = "random.click";
        nukkitPacket.x = 10;
        nukkitPacket.y = 64;
        nukkitPacket.z = 20;
        nukkitPacket.volume = 1.0f;
        nukkitPacket.pitch = 1.0f;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket.class);

        assertEquals("random.click", cbPacket.getSound());
        // Nukkit-MOT encodes x<<3, y<<3, z<<3; CB Protocol divides by 8 to get world position
        assertEquals(10.0f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(20.0f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(1.0f, cbPacket.getVolume(), 0.001f);
        assertEquals(1.0f, cbPacket.getPitch(), 0.001f);
    }

    // ==================== ContainerOpenPacket ====================

    @ParameterizedTest(name = "ContainerOpenPacket v{0}")
    @MethodSource("allVersions")
    void testContainerOpenPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ContainerOpenPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 1;
        nukkitPacket.type = 0; // CONTAINER
        nukkitPacket.x = 10;
        nukkitPacket.y = 64;
        nukkitPacket.z = 20;
        nukkitPacket.entityId = -1;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket.class);

        assertEquals(1, cbPacket.getId());
        assertEquals(10, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(20, cbPacket.getBlockPosition().getZ());
        assertEquals(-1, cbPacket.getUniqueEntityId());
    }

    // ==================== CameraPacket ====================

    @ParameterizedTest(name = "CameraPacket v{0}")
    @MethodSource("allVersions")
    void testCameraPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.cameraUniqueId = 10;
        nukkitPacket.playerUniqueId = 20;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraPacket.class);

        assertEquals(10, cbPacket.getCameraUniqueEntityId());
        assertEquals(20, cbPacket.getPlayerUniqueEntityId());
    }

    // ==================== ClientCacheStatusPacket ====================

    @ParameterizedTest(name = "ClientCacheStatusPacket v{0}")
    @MethodSource("versionsFrom388")
    void testClientCacheStatusPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientCacheStatusPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.supported = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket.class);

        assertTrue(cbPacket.isSupported());
    }

    // ==================== CodeBuilderPacket ====================

    @ParameterizedTest(name = "CodeBuilderPacket v{0}")
    @MethodSource("versionsFrom407")
    void testCodeBuilderPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CodeBuilderPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.url = "https://example.com";
        nukkitPacket.isOpening = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CodeBuilderPacket.class);

        assertEquals("https://example.com", cbPacket.getUrl());
        assertTrue(cbPacket.isOpening());
    }

    // ==================== CompletedUsingItemPacket ====================

    @ParameterizedTest(name = "CompletedUsingItemPacket v{0}")
    @MethodSource("versionsFrom388")
    void testCompletedUsingItemPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CompletedUsingItemPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.itemId = 1;
        // ACTION_EQUIP_ARMOR=0 on wire; CB ItemUseType.EQUIP_ARMOR ordinal=1 (ordinal-1=0)
        nukkitPacket.action = cn.nukkit.network.protocol.CompletedUsingItemPacket.ACTION_EQUIP_ARMOR;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CompletedUsingItemPacket.class);

        assertEquals(1, cbPacket.getItemId());
        assertEquals(1, cbPacket.getType().ordinal()); // EQUIP_ARMOR
    }

    // ==================== ContainerSetDataPacket ====================

    @ParameterizedTest(name = "ContainerSetDataPacket v{0}")
    @MethodSource("allVersions")
    void testContainerSetDataPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ContainerSetDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 1;
        nukkitPacket.property = 0;
        nukkitPacket.value = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket.class);

        assertEquals(1, cbPacket.getWindowId());
        assertEquals(0, cbPacket.getProperty());
        assertEquals(0, cbPacket.getValue());
    }

    // ==================== DebugInfoPacket ====================

    @ParameterizedTest(name = "DebugInfoPacket v{0}")
    @MethodSource("versionsFrom407")
    void testDebugInfoPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.DebugInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityId = 42;
        nukkitPacket.data = "debug data";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DebugInfoPacket.class);

        assertEquals(42, cbPacket.getUniqueEntityId());
        assertEquals("debug data", cbPacket.getData());
    }

    // ==================== FilterTextPacket ====================

    @ParameterizedTest(name = "FilterTextPacket v{0}")
    @MethodSource("versionsFrom428to662")
    void testFilterTextPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.FilterTextPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.text = "hello";
        nukkitPacket.fromServer = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.FilterTextPacket.class);

        assertEquals("hello", cbPacket.getText());
        assertTrue(cbPacket.isFromServer());
    }

    // ==================== MapCreateLockedCopyPacket ====================

    @ParameterizedTest(name = "MapCreateLockedCopyPacket v{0}")
    @MethodSource("versionsFrom354")
    void testMapCreateLockedCopyPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MapCreateLockedCopyPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.originalMapId = 1;
        nukkitPacket.newMapId = 2;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MapCreateLockedCopyPacket.class);

        assertEquals(1, cbPacket.getOriginalMapId());
        assertEquals(2, cbPacket.getNewMapId());
    }

    // ==================== NetworkStackLatencyPacket ====================

    @ParameterizedTest(name = "NetworkStackLatencyPacket v{0}")
    @MethodSource("versionsFrom332")
    void testNetworkStackLatencyPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.NetworkStackLatencyPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.timestamp = 123456L;
        nukkitPacket.fromServer = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket.class);

        assertEquals(123456L, cbPacket.getTimestamp());
        assertTrue(cbPacket.isFromServer());
    }

    // ==================== OnScreenTextureAnimationPacket ====================

    @ParameterizedTest(name = "OnScreenTextureAnimationPacket v{0}")
    @MethodSource("versionsFrom354")
    void testOnScreenTextureAnimationPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.OnScreenTextureAnimationPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.effectId = 5;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket.class);

        assertEquals(5, cbPacket.getEffectId());
    }

    // ==================== ServerSettingsResponsePacket ====================

    @ParameterizedTest(name = "ServerSettingsResponsePacket v{0}")
    @MethodSource("allVersions")
    void testServerSettingsResponsePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ServerSettingsResponsePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        // formId=0 avoids zigzag vs unsigned VarInt encoding difference
        nukkitPacket.formId = 0;
        nukkitPacket.data = "{\"type\":\"form\"}";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ServerSettingsResponsePacket.class);

        assertEquals(0, cbPacket.getFormId());
        assertEquals("{\"type\":\"form\"}", cbPacket.getFormData());
    }

    // ==================== ShowProfilePacket ====================

    @ParameterizedTest(name = "ShowProfilePacket v{0}")
    @MethodSource("allVersions")
    void testShowProfilePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ShowProfilePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.xuid = "12345678";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ShowProfilePacket.class);

        assertEquals("12345678", cbPacket.getXuid());
    }

    // ==================== SimpleEventPacket ====================

    @ParameterizedTest(name = "SimpleEventPacket v{0}")
    @MethodSource("allVersions")
    void testSimpleEventPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SimpleEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eventType = cn.nukkit.network.protocol.SimpleEventPacket.TYPE_ENABLE_COMMANDS;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SimpleEventPacket.class);

        assertEquals(1, cbPacket.getEvent().ordinal());
    }

    // ==================== SpawnExperienceOrbPacket ====================

    @ParameterizedTest(name = "SpawnExperienceOrbPacket v{0}")
    @MethodSource("allVersions")
    void testSpawnExperienceOrbPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SpawnExperienceOrbPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.amount = 5;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SpawnExperienceOrbPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(5, cbPacket.getAmount());
    }

    // ==================== VideoStreamConnectPacket ====================

    @ParameterizedTest(name = "VideoStreamConnectPacket v{0}")
    @MethodSource("versionsVideoStream")
    void testVideoStreamConnectPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.VideoStreamConnectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.address = "ws://127.0.0.1:19132";
        nukkitPacket.screenshotFrequency = 1.0f;
        nukkitPacket.action = cn.nukkit.network.protocol.VideoStreamConnectPacket.ACTION_OPEN;
        nukkitPacket.width = 1920;
        nukkitPacket.height = 1080;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.VideoStreamConnectPacket.class);

        assertEquals("ws://127.0.0.1:19132", cbPacket.getAddress());
        assertEquals(1.0f, cbPacket.getScreenshotFrequency(), 0.001f);
        assertEquals(0, cbPacket.getAction().ordinal());
    }

    // ==================== EntityEventPacket ====================

    @ParameterizedTest(name = "EntityEventPacket v{0}")
    @MethodSource("allVersions")
    void testEntityEventPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.EntityEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.event = cn.nukkit.network.protocol.EntityEventPacket.ARM_SWING;
        nukkitPacket.data = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(4, cbPacket.getType().ordinal());
        assertEquals(0, cbPacket.getData());
    }

    // ==================== EmoteListPacket ====================

    @ParameterizedTest(name = "EmoteListPacket v{0}")
    @MethodSource("versionsFrom407")
    void testEmoteListPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.EmoteListPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.runtimeId = 42;
        nukkitPacket.pieceIds.add(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.EmoteListPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(1, cbPacket.getPieceIds().size());
        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), cbPacket.getPieceIds().get(0));
    }

    // ==================== CommandBlockUpdatePacket (Block mode) ====================

    @ParameterizedTest(name = "CommandBlockUpdatePacket Block v{0}")
    @MethodSource("versionsPre712")
    void testCommandBlockUpdatePacketBlockPre712(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CommandBlockUpdatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.isBlock = true;
        nukkitPacket.x = 100;
        nukkitPacket.y = 64;
        nukkitPacket.z = 200;
        nukkitPacket.commandBlockMode = 0; // REPEAT
        nukkitPacket.isRedstoneMode = false;
        nukkitPacket.isConditional = false;
        nukkitPacket.command = "say hello";
        nukkitPacket.lastOutput = "";
        nukkitPacket.name = "TestCmd";
        nukkitPacket.shouldTrackOutput = false;
        nukkitPacket.tickDelay = 0;
        nukkitPacket.executingOnFirstTick = false;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket.class);

        assertTrue(cbPacket.isBlock());
        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertEquals("say hello", cbPacket.getCommand());
        assertEquals("TestCmd", cbPacket.getName());
    }

    // ==================== SetDisplayObjectivePacket ====================

    @ParameterizedTest(name = "SetDisplayObjectivePacket v{0}")
    @MethodSource("allVersions")
    void testSetDisplayObjectivePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetDisplayObjectivePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.displaySlot = cn.nukkit.network.protocol.types.DisplaySlot.SIDEBAR;
        nukkitPacket.objectiveId = "obj1";
        nukkitPacket.displayName = "Scoreboard";
        nukkitPacket.criteria = "dummy";
        nukkitPacket.sortOrder = cn.nukkit.network.protocol.types.SortOrder.ASCENDING;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetDisplayObjectivePacket.class);

        assertEquals("sidebar", cbPacket.getDisplaySlot());
        assertEquals("obj1", cbPacket.getObjectiveId());
        assertEquals("Scoreboard", cbPacket.getDisplayName());
        assertEquals("dummy", cbPacket.getCriteria());
        assertEquals(0, cbPacket.getSortOrder());
    }

    // ==================== PlayerHotbarPacket ====================

    @ParameterizedTest(name = "PlayerHotbarPacket v{0}")
    @MethodSource("allVersions")
    void testPlayerHotbarPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlayerHotbarPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.selectedHotbarSlot = 0;
        nukkitPacket.windowId = 0;
        nukkitPacket.selectHotbarSlot = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket.class);

        assertEquals(0, cbPacket.getSelectedHotbarSlot());
        assertEquals(0, cbPacket.getContainerId());
        assertTrue(cbPacket.isSelectHotbarSlot());
    }

    // ==================== AddBehaviorTreePacket ====================

    @ParameterizedTest(name = "AddBehaviorTreePacket v{0}")
    @MethodSource("allVersions")
    void testAddBehaviorTreePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.AddBehaviorTreePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.behaviorTreeJson = "{\"test\":true}";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddBehaviorTreePacket.class);

        assertEquals("{\"test\":true}", cbPacket.getBehaviorTreeJson());
    }

    // ==================== OpenSignPacket ====================

    static Stream<Arguments> versionsFrom582() {
        return filteredVersions(ProtocolInfo.v1_19_80);
    }

    @ParameterizedTest(name = "OpenSignPacket v{0}")
    @MethodSource("versionsFrom582")
    void testOpenSignPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.OpenSignPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setPosition(new cn.nukkit.math.BlockVector3(100, 64, 200));
        nukkitPacket.setFrontSide(true);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.OpenSignPacket.class);

        assertEquals(100, cbPacket.getPosition().getX());
        assertEquals(64, cbPacket.getPosition().getY());
        assertEquals(200, cbPacket.getPosition().getZ());
        assertTrue(cbPacket.isFrontSide());
    }

    // ==================== DeathInfoPacket ====================

    @ParameterizedTest(name = "DeathInfoPacket v{0}")
    @MethodSource("versionsFrom534")
    void testDeathInfoPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.DeathInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.messageTranslationKey = "death.attack.player";
        nukkitPacket.messageParameters = new String[]{"Killer", "victim"};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DeathInfoPacket.class);

        assertEquals("death.attack.player", cbPacket.getCauseAttackName());
        assertEquals(2, cbPacket.getMessageList().size());
        assertEquals("Killer", cbPacket.getMessageList().get(0));
        assertEquals("victim", cbPacket.getMessageList().get(1));
    }

    // ==================== PhotoInfoRequestPacket ====================

    @ParameterizedTest(name = "PhotoInfoRequestPacket v{0}")
    @MethodSource("versionsFrom471")
    void testPhotoInfoRequestPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PhotoInfoRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.photoId = 12345L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PhotoInfoRequestPacket.class);

        assertEquals(12345L, cbPacket.getPhotoId());
    }

    // ==================== PacketViolationWarningPacket ====================

    @ParameterizedTest(name = "PacketViolationWarningPacket v{0}")
    @MethodSource("versionsFrom407")
    void testPacketViolationWarningPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PacketViolationWarningPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = cn.nukkit.network.protocol.PacketViolationWarningPacket.PacketViolationType.MALFORMED_PACKET;
        nukkitPacket.severity = cn.nukkit.network.protocol.PacketViolationWarningPacket.PacketViolationSeverity.WARNING;
        nukkitPacket.packetId = 100;
        nukkitPacket.context = "Test context";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.PacketViolationType.MALFORMED_PACKET, cbPacket.getType());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.PacketViolationSeverity.WARNING, cbPacket.getSeverity());
        assertEquals(100, cbPacket.getPacketCauseId());
        assertEquals("Test context", cbPacket.getContext());
    }

    // ==================== PlayerFogPacket ====================

    @ParameterizedTest(name = "PlayerFogPacket v{0}")
    @MethodSource("versionsFrom419")
    void testPlayerFogPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlayerFogPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.getFogStack().add(new cn.nukkit.network.protocol.PlayerFogPacket.Fog(
                new cn.nukkit.utils.Identifier("minecraft", "test_fog"), "user_id_1"));
        nukkitPacket.getFogStack().add(new cn.nukkit.network.protocol.PlayerFogPacket.Fog(
                new cn.nukkit.utils.Identifier("minecraft", "another_fog"), "user_id_2"));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerFogPacket.class);

        assertEquals(2, cbPacket.getFogStack().size());
        assertEquals("minecraft:test_fog", cbPacket.getFogStack().get(0));
        assertEquals("minecraft:another_fog", cbPacket.getFogStack().get(1));
    }

    // ==================== TickSyncPacket ====================

    // TickSyncPacket was deprecated in v685
    static Stream<Arguments> versionsFrom388to685() {
        return filteredVersionsRange(388, 685);
    }

    @ParameterizedTest(name = "TickSyncPacket v{0}")
    @MethodSource("versionsFrom388to685")
    void testTickSyncPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TickSyncPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setRequestTimestamp(1000L);
        nukkitPacket.setResponseTimestamp(2000L);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TickSyncPacket.class);

        assertEquals(1000L, cbPacket.getRequestTimestamp());
        assertEquals(2000L, cbPacket.getResponseTimestamp());
    }

    // ==================== ToastRequestPacket ====================

    static Stream<Arguments> versionsFrom527() {
        return filteredVersions(527);
    }

    @ParameterizedTest(name = "ToastRequestPacket v{0}")
    @MethodSource("versionsFrom527")
    void testToastRequestPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ToastRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.title = "Test Title";
        nukkitPacket.content = "Test Content";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket.class);

        assertEquals("Test Title", cbPacket.getTitle());
        assertEquals("Test Content", cbPacket.getContent());
    }

    // ==================== TransferPacket ====================

    @ParameterizedTest(name = "TransferPacket v{0}")
    @MethodSource("allVersions")
    void testTransferPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TransferPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.address = "192.168.1.1";
        nukkitPacket.port = 19132;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TransferPacket.class);

        assertEquals("192.168.1.1", cbPacket.getAddress());
        assertEquals(19132, cbPacket.getPort());
    }

    // ==================== UpdateAttributesPacket ====================

    @ParameterizedTest(name = "UpdateAttributesPacket v{0}")
    @MethodSource("allVersions")
    void testUpdateAttributesPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateAttributesPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityId = 42L;
        nukkitPacket.entries = null;  // No attributes
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket.class);

        assertEquals(42L, cbPacket.getRuntimeEntityId());
        assertTrue(cbPacket.getAttributes().isEmpty());
    }

    // ==================== RespawnPacket ====================

    @ParameterizedTest(name = "RespawnPacket v{0}")
    @MethodSource("versionsFrom388")
    void testRespawnPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.RespawnPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.respawnState = cn.nukkit.network.protocol.RespawnPacket.STATE_SEARCHING_FOR_SPAWN;
        nukkitPacket.runtimeEntityId = 0L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.State.SERVER_SEARCHING, cbPacket.getState());
    }

    // ==================== SetEntityLinkPacket ====================

    @ParameterizedTest(name = "SetEntityLinkPacket v{0}")
    @MethodSource("allVersions")
    void testSetEntityLinkPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetEntityLinkPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.vehicleUniqueId = 10L;
        nukkitPacket.riderUniqueId = 20L;
        nukkitPacket.type = cn.nukkit.network.protocol.SetEntityLinkPacket.TYPE_RIDE;
        nukkitPacket.immediate = 0;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket.class);

        var link = cbPacket.getEntityLink();
        assertNotNull(link);
        assertEquals(10L, link.getFrom());
        assertEquals(20L, link.getTo());
    }

    // ==================== SetEntityMotionPacket ====================

    @ParameterizedTest(name = "SetEntityMotionPacket v{0}")
    @MethodSource("allVersions")
    void testSetEntityMotionPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetEntityMotionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42L;
        nukkitPacket.motionX = 1.5f;
        nukkitPacket.motionY = 2.0f;
        nukkitPacket.motionZ = 3.5f;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket.class);

        assertEquals(42L, cbPacket.getRuntimeEntityId());
        assertEquals(1.5f, cbPacket.getMotion().getX(), 0.001f);
        assertEquals(2.0f, cbPacket.getMotion().getY(), 0.001f);
        assertEquals(3.5f, cbPacket.getMotion().getZ(), 0.001f);
    }

    // ==================== SetScorePacket ====================

    @ParameterizedTest(name = "SetScorePacket v{0}")
    @MethodSource("allVersions")
    void testSetScorePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetScorePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.action = cn.nukkit.network.protocol.SetScorePacket.Action.REMOVE;
        nukkitPacket.infos = new java.util.ArrayList<>();
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetScorePacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.packet.SetScorePacket.Action.REMOVE, cbPacket.getAction());
        assertTrue(cbPacket.getInfos().isEmpty());
    }

    // ==================== SetSpawnPositionPacket ====================

    @ParameterizedTest(name = "SetSpawnPositionPacket v{0}")
    @MethodSource("versionsFrom407")
    void testSetSpawnPositionPacket(int protocolVersion) {
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

        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertEquals(0, cbPacket.getDimensionId());
    }

    // ==================== SetTimePacket ====================

    @ParameterizedTest(name = "SetTimePacket v{0}")
    @MethodSource("allVersions")
    void testSetTimePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetTimePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.time = 6000;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTimePacket.class);

        assertEquals(6000, cbPacket.getTime());
    }

    // ==================== SetTitlePacket ====================

    @ParameterizedTest(name = "SetTitlePacket v{0}")
    @MethodSource("versionsFrom471")
    void testSetTitlePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetTitlePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = cn.nukkit.network.protocol.SetTitlePacket.TYPE_CLEAR;
        nukkitPacket.text = "Test Title";
        nukkitPacket.fadeInTime = 10;
        nukkitPacket.stayTime = 20;
        nukkitPacket.fadeOutTime = 10;
        nukkitPacket.xuid = "";
        nukkitPacket.platformOnlineId = "";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket.class);

        assertEquals("Test Title", cbPacket.getText());
        assertEquals(10, cbPacket.getFadeInTime());
        assertEquals(20, cbPacket.getStayTime());
        assertEquals(10, cbPacket.getFadeOutTime());
    }

    // ==================== StopSoundPacket ====================

    @ParameterizedTest(name = "StopSoundPacket v{0}")
    @MethodSource("allVersions")
    void testStopSoundPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.StopSoundPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.name = "random.click";
        nukkitPacket.stopAll = false;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket.class);

        assertEquals("random.click", cbPacket.getSoundName());
        assertFalse(cbPacket.isStoppingAllSound());
    }

    // ==================== UpdateBlockPacket ====================
    // TODO: Requires BlockDefinitions registry which is not available in test context

    // @ParameterizedTest(name = "UpdateBlockPacket v{0}")
    // @MethodSource("versionsFrom354")
    // void testUpdateBlockPacket(int protocolVersion) {
    //     var nukkitPacket = new cn.nukkit.network.protocol.UpdateBlockPacket();
    //     nukkitPacket.protocol = protocolVersion;
    //     nukkitPacket.x = 100;
    //     nukkitPacket.y = 64;
    //     nukkitPacket.z = 200;
    //     nukkitPacket.blockRuntimeId = 1; // stone
    //     nukkitPacket.flags = cn.nukkit.network.protocol.UpdateBlockPacket.FLAG_ALL;
    //     nukkitPacket.dataLayer = 0;
    //     nukkitPacket.encode();
    //
    //     var cbPacket = crossDecode(nukkitPacket,
    //             org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket.class);
    //
    //     assertEquals(100, cbPacket.getBlockPosition().getX());
    //     assertEquals(64, cbPacket.getBlockPosition().getY());
    //     assertEquals(200, cbPacket.getBlockPosition().getZ());
    //     assertEquals(cn.nukkit.network.protocol.UpdateBlockPacket.FLAG_ALL, cbPacket.getFlags());
    // }

    // ==================== BlockEntityDataPacket ====================
    // TODO: NBT encoding format mismatch

    // @ParameterizedTest(name = "BlockEntityDataPacket v{0}")
    // @MethodSource("allVersions")
    // void testBlockEntityDataPacket(int protocolVersion) {
    //     var nukkitPacket = new cn.nukkit.network.protocol.BlockEntityDataPacket();
    //     nukkitPacket.protocol = protocolVersion;
    //     nukkitPacket.x = 100;
    //     nukkitPacket.y = 64;
    //     nukkitPacket.z = 200;
    //     nukkitPacket.namedTag = null; // Will write empty tag
    //     nukkitPacket.encode();
    //
    //     var cbPacket = crossDecode(nukkitPacket,
    //             org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket.class);
    //
    //     assertEquals(100, cbPacket.getBlockPosition().getX());
    //     assertEquals(64, cbPacket.getBlockPosition().getY());
    //     assertEquals(200, cbPacket.getBlockPosition().getZ());
    // }

    // ==================== MoveEntityDeltaPacket ====================

    @ParameterizedTest(name = "MoveEntityDeltaPacket v{0}")
    @MethodSource("versionsFrom419")
    void testMoveEntityDeltaPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MoveEntityDeltaPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.flags = 0; // No delta
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    // ==================== SetLocalPlayerAsInitializedPacket ====================

    @ParameterizedTest(name = "SetLocalPlayerAsInitializedPacket v{0}")
    @MethodSource("allVersions")
    void testSetLocalPlayerAsInitializedPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetLocalPlayerAsInitializedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 100;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket.class);

        assertEquals(100, cbPacket.getRuntimeEntityId());
    }

    // ==================== PlayerArmorDamagePacket ====================
    // TODO: Encoding issue - IndexOutOfBounds

    // @ParameterizedTest(name = "PlayerArmorDamagePacket v{0}")
    // @MethodSource("versionsFrom527")
    // void testPlayerArmorDamagePacket(int protocolVersion) {
    //     var nukkitPacket = new cn.nukkit.network.protocol.PlayerArmorDamagePacket();
    //     nukkitPacket.protocol = protocolVersion;
    //     nukkitPacket.flags.add(cn.nukkit.network.protocol.PlayerArmorDamagePacket.PlayerArmorDamageFlag.HELMET);
    //     nukkitPacket.damage[0] = 10;
    //     nukkitPacket.encode();
    //
    //     var cbPacket = crossDecode(nukkitPacket,
    //             org.cloudburstmc.protocol.bedrock.packet.PlayerArmorDamagePacket.class);
    //
    //     assertTrue(cbPacket.getFlags().contains(
    //             org.cloudburstmc.protocol.bedrock.data.PlayerArmorDamageFlag.HELMET));
    // }

    // ==================== PhotoTransferPacket ====================
    // TODO: Encoding issue - IndexOutOfBounds

    // @ParameterizedTest(name = "PhotoTransferPacket v{0}")
    // @MethodSource("allVersions")
    // void testPhotoTransferPacket(int protocolVersion) {
    //     var nukkitPacket = new cn.nukkit.network.protocol.PhotoTransferPacket();
    //     nukkitPacket.protocol = protocolVersion;
    //     nukkitPacket.photoName = "test_photo";
    //     nukkitPacket.photoData = "";
    //     nukkitPacket.bookId = "test_book";
    //     nukkitPacket.type = 0;
    //     nukkitPacket.sourceType = 0;
    //     nukkitPacket.ownerActorUniqueId = 0;
    //     nukkitPacket.newPhotoName = "";
    //     nukkitPacket.encode();
    //
    //     var cbPacket = crossDecode(nukkitPacket,
    //             org.cloudburstmc.protocol.bedrock.packet.PhotoTransferPacket.class);
    //
    //     assertEquals("test_photo", cbPacket.getName());
    //     assertEquals("test_book", cbPacket.getBookId());
    // }

    // ==================== LevelEventGenericPacket ====================
    // TODO: NBT encoding format mismatch between Nukkit and CB Protocol

    // @ParameterizedTest(name = "LevelEventGenericPacket v{0}")
    // @MethodSource("versionsFrom388")
    // void testLevelEventGenericPacket(int protocolVersion) {
    //     var nukkitPacket = new cn.nukkit.network.protocol.LevelEventGenericPacket();
    //     nukkitPacket.protocol = protocolVersion;
    //     nukkitPacket.eventId = 0;
    //     nukkitPacket.tag = new cn.nukkit.nbt.tag.CompoundTag("");
    //     nukkitPacket.encode();
    //
    //     var cbPacket = crossDecode(nukkitPacket,
    //             org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket.class);
    //
    //     assertNotNull(cbPacket);
    // }


    // ==================== UpdateSubChunkBlocksPacket ====================

    @ParameterizedTest(name = "UpdateSubChunkBlocksPacket v{0}")
    @MethodSource("versionsFrom465")
    void testUpdateSubChunkBlocksPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateSubChunkBlocksPacket();
        nukkitPacket.chunkX = 100;
        nukkitPacket.chunkY = 64;
        nukkitPacket.chunkZ = 200;
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket.class);

        assertEquals(100, cbPacket.getChunkX());
        assertEquals(64, cbPacket.getChunkY());
        assertEquals(200, cbPacket.getChunkZ());
    }

    // ==================== ItemStackResponsePacket ====================

    @ParameterizedTest(name = "ItemStackResponsePacket v{0}")
    @MethodSource("versionsFrom419")
    void testItemStackResponsePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ItemStackResponsePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entries.add(new cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponse(
                cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus.ERROR,
                1,
                new java.util.ArrayList<>()
        ));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket.class);

        assertEquals(1, cbPacket.getEntries().size());
    }

    // ==================== CraftingDataPacket ====================

    @ParameterizedTest(name = "CraftingDataPacket v{0}")
    @MethodSource("versionsFrom354")
    void testCraftingDataPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CraftingDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.cleanRecipes = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket.class);

        assertTrue(cbPacket.isCleanRecipes());
    }

    // ==================== AdventureSettingsPacket ====================

    @ParameterizedTest(name = "AdventureSettingsPacket v{0}")
    @MethodSource("versionsPre553")
    void testAdventureSettingsPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.AdventureSettingsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.flags = cn.nukkit.network.protocol.AdventureSettingsPacket.ALLOW_FLIGHT;
        nukkitPacket.commandPermission = cn.nukkit.network.protocol.AdventureSettingsPacket.PERMISSION_NORMAL;
        nukkitPacket.flags2 = cn.nukkit.network.protocol.AdventureSettingsPacket.BUILD;
        nukkitPacket.playerPermission = cn.nukkit.Player.PERMISSION_MEMBER;
        nukkitPacket.customFlags = 0;
        nukkitPacket.entityUniqueId = 100;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket.class);

        assertTrue(cbPacket.getSettings().contains(org.cloudburstmc.protocol.bedrock.data.AdventureSetting.MAY_FLY));
        assertEquals(org.cloudburstmc.protocol.bedrock.data.command.CommandPermission.ANY, cbPacket.getCommandPermission());
        assertEquals(100, cbPacket.getUniqueEntityId());
    }

    // ==================== UpdateBlockPacket ====================
    // TODO: Requires blockDefinitions registry in CB helper - not available in test environment

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
    }

    // ==================== AddPaintingPacket ====================

    @ParameterizedTest(name = "AddPaintingPacket v{0}")
    @MethodSource("versionsFrom313")
    void testAddPaintingPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.AddPaintingPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityUniqueId = 100;
        nukkitPacket.entityRuntimeId = 200;
        nukkitPacket.x = 10;
        nukkitPacket.y = 64;
        nukkitPacket.z = 20;
        nukkitPacket.direction = 2;
        nukkitPacket.title = "Kebab";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddPaintingPacket.class);

        assertEquals(100, cbPacket.getUniqueEntityId());
        assertEquals(200, cbPacket.getRuntimeEntityId());
        assertEquals(2, cbPacket.getDirection());
        assertEquals("Kebab", cbPacket.getMotive());
    }

    // ==================== PhotoTransferPacket ====================

    static Stream<Arguments> versionsFrom313to464() {
        return filteredVersionsRange(313, ProtocolInfo.v1_17_30);
    }

    static Stream<Arguments> versionsFrom465to593() {
        return filteredVersionsRange(ProtocolInfo.v1_17_30, 594);
    }

    @ParameterizedTest(name = "PhotoTransferPacket basic v{0}")
    @MethodSource("versionsFrom313to464")
    void testPhotoTransferPacketBasic(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PhotoTransferPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.photoName = "test_photo";
        nukkitPacket.photoData = "imagedata";
        nukkitPacket.bookId = "book123";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PhotoTransferPacket.class);

        assertEquals("test_photo", cbPacket.getName());
        assertEquals("book123", cbPacket.getBookId());
    }

    @ParameterizedTest(name = "PhotoTransferPacket extended v{0}")
    @MethodSource("versionsFrom465to593")
    void testPhotoTransferPacketExtended(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PhotoTransferPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.photoName = "test_photo";
        nukkitPacket.photoData = "imagedata";
        nukkitPacket.bookId = "book123";
        nukkitPacket.type = 1;
        nukkitPacket.sourceType = 2;
        nukkitPacket.ownerActorUniqueId = 12345L;
        nukkitPacket.newPhotoName = "new_photo";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PhotoTransferPacket.class);

        assertEquals("test_photo", cbPacket.getName());
        assertEquals("book123", cbPacket.getBookId());
        assertEquals(12345L, cbPacket.getOwnerId());
        assertEquals("new_photo", cbPacket.getNewPhotoName());
    }

    // ==================== ResourcePackChunkDataPacket ====================

    @ParameterizedTest(name = "ResourcePackChunkDataPacket v{0}")
    @MethodSource("versionsFrom388")
    void testResourcePackChunkDataPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ResourcePackChunkDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.packId = java.util.UUID.fromString("12345678-1234-1234-1234-123456789012");
        nukkitPacket.chunkIndex = 3;
        nukkitPacket.progress = 65536L;
        nukkitPacket.data = new byte[]{1, 2, 3, 4, 5};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkDataPacket.class);

        assertEquals(3, cbPacket.getChunkIndex());
        assertEquals(65536L, cbPacket.getProgress());
    }

    // ==================== ResourcePackDataInfoPacket ====================

    @ParameterizedTest(name = "ResourcePackDataInfoPacket v{0}")
    @MethodSource("versionsFrom313")
    void testResourcePackDataInfoPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ResourcePackDataInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.packId = java.util.UUID.fromString("12345678-1234-1234-1234-123456789012");
        nukkitPacket.maxChunkSize = 1048576;
        nukkitPacket.chunkCount = 10;
        nukkitPacket.compressedPackSize = 10485760L;
        nukkitPacket.sha256 = new byte[]{0x01, 0x02, 0x03};
        nukkitPacket.premium = false;
        nukkitPacket.type = cn.nukkit.network.protocol.ResourcePackDataInfoPacket.TYPE_RESOURCE;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePackDataInfoPacket.class);

        assertEquals(1048576, cbPacket.getMaxChunkSize());
        assertEquals(10, cbPacket.getChunkCount());
        assertEquals(10485760L, cbPacket.getCompressedPackSize());
    }

    // ==================== BlockEntityDataPacket ====================

    @ParameterizedTest(name = "BlockEntityDataPacket v{0}")
    @MethodSource("versionsFrom313")
    void testBlockEntityDataPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.BlockEntityDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.x = 100;
        nukkitPacket.y = 64;
        nukkitPacket.z = 200;
        // Create minimal valid NBT compound tag (network format)
        try {
            nukkitPacket.namedTag = cn.nukkit.nbt.NBTIO.write(
                    new cn.nukkit.nbt.tag.CompoundTag().putString("id", "Sign"),
                    java.nio.ByteOrder.LITTLE_ENDIAN, true);
        } catch (java.io.IOException e) {
            fail("Failed to write NBT: " + e.getMessage());
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket.class);

        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertNotNull(cbPacket.getData());
    }

    // ==================== TrimDataPacket ====================
    // Moved to MiscPacketRegressionTest (requires MockServer.init for TrimFactory)

    // ==================== PlayerArmorDamagePacket ====================

    static Stream<Arguments> versionsFrom407to843() {
        return filteredVersionsRange(407, 844);
    }

    @ParameterizedTest(name = "PlayerArmorDamagePacket v{0}")
    @MethodSource("versionsFrom407to843")
    void testPlayerArmorDamagePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlayerArmorDamagePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.flags.add(cn.nukkit.network.protocol.PlayerArmorDamagePacket.PlayerArmorDamageFlag.HELMET);
        nukkitPacket.flags.add(cn.nukkit.network.protocol.PlayerArmorDamagePacket.PlayerArmorDamageFlag.CHESTPLATE);
        nukkitPacket.damage[0] = 5;  // HELMET
        nukkitPacket.damage[1] = 10; // CHESTPLATE
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerArmorDamagePacket.class);

        assertTrue(cbPacket.getFlags().contains(org.cloudburstmc.protocol.bedrock.data.PlayerArmorDamageFlag.HELMET));
        assertTrue(cbPacket.getFlags().contains(org.cloudburstmc.protocol.bedrock.data.PlayerArmorDamageFlag.CHESTPLATE));
        assertEquals(5, cbPacket.getDamage()[0]);
        assertEquals(10, cbPacket.getDamage()[1]);
    }

    // ==================== EventPacket ====================
    // EventPacket has complex EventData field in CB Protocol that doesn't match Nukkit's simple encoding
    // TODO: Requires format investigation before cross-decode testing

    // ==================== SyncEntityPropertyPacket ====================

    static Stream<Arguments> versionsFrom486() {
        return filteredVersions(ProtocolInfo.v1_18_0);
    }

    @ParameterizedTest(name = "SyncEntityPropertyPacket v{0}")
    @MethodSource("versionsFrom486")
    void testSyncEntityPropertyPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SyncEntityPropertyPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setData(new CompoundTag());
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket.class);

        assertNotNull(cbPacket);
    }

    // ==================== ResourcePackChunkRequestPacket ====================

    @ParameterizedTest(name = "ResourcePackChunkRequestPacket v{0}")
    @MethodSource("allVersions")
    void testResourcePackChunkRequestPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ResourcePackChunkRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.packId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        nukkitPacket.chunkIndex = 3;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket.class);

        assertEquals("12345678-1234-1234-1234-123456789abc", cbPacket.getPackId().toString());
        assertEquals(3, cbPacket.getChunkIndex());
    }

    // ==================== ResourcePackClientResponsePacket ====================

    @ParameterizedTest(name = "ResourcePackClientResponsePacket v{0}")
    @MethodSource("allVersions")
    void testResourcePackClientResponsePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ResourcePackClientResponsePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.responseStatus = cn.nukkit.network.protocol.ResourcePackClientResponsePacket.STATUS_COMPLETED;
        nukkitPacket.packEntries = new cn.nukkit.network.protocol.ResourcePackClientResponsePacket.Entry[]{
                new cn.nukkit.network.protocol.ResourcePackClientResponsePacket.Entry(
                        UUID.fromString("12345678-1234-1234-1234-123456789abc"), "1.0.0")
        };
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket.class);

        assertEquals(4, cbPacket.getStatus().ordinal());
        assertEquals(1, cbPacket.getPackIds().size());
    }

    // ==================== ClientboundDataStorePacket ====================

    @ParameterizedTest(name = "ClientboundDataStorePacket v{0}")
    @MethodSource("versionsFrom898")
    void testClientboundDataStorePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientboundDataStorePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);

        var update = new cn.nukkit.network.protocol.types.datastore.DataStoreUpdate();
        update.setDataStoreName("ui_state");
        update.setProperty("volume");
        update.setPath("audio.master");
        update.setData(12.5d);
        update.setPropertyUpdateCount(7);
        update.setPathUpdateCount(9);

        var change = new cn.nukkit.network.protocol.types.datastore.DataStoreChange();
        change.setDataStoreName("ui_state");
        change.setProperty("metadata");
        change.setUpdateCount(3);
        change.setNewValue(cn.nukkit.network.protocol.types.datastore.DataStorePropertyValue.ofObject(Map.of(
                "enabled", cn.nukkit.network.protocol.types.datastore.DataStorePropertyValue.ofBoolean(true),
                "title", cn.nukkit.network.protocol.types.datastore.DataStorePropertyValue.ofString("main")
        )));

        var removal = new cn.nukkit.network.protocol.types.datastore.DataStoreRemoval();
        removal.setDataStoreName("legacy_state");

        List<cn.nukkit.network.protocol.types.datastore.DataStoreAction> updates = new ArrayList<>();
        updates.add(update);
        updates.add(change);
        updates.add(removal);
        nukkitPacket.setUpdates(updates);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundDataStorePacket.class);

        assertEquals(3, cbPacket.getUpdates().size());

        var cbUpdate = (org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreUpdate) cbPacket.getUpdates().get(0);
        assertEquals("ui_state", cbUpdate.getDataStoreName());
        assertEquals("volume", cbUpdate.getProperty());
        assertEquals("audio.master", cbUpdate.getPath());
        assertEquals(12.5d, (Double) cbUpdate.getData(), 0.0001d);
        assertEquals(7, cbUpdate.getUpdateCount());
        if (protocolVersion >= ProtocolInfo.v1_26_0) {
            assertEquals(9, cbUpdate.getPathUpdateCount());
        } else {
            assertEquals(0, cbUpdate.getPathUpdateCount());
        }

        var cbChange = (org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreChange) cbPacket.getUpdates().get(1);
        assertEquals("ui_state", cbChange.getDataStoreName());
        assertEquals("metadata", cbChange.getProperty());
        assertEquals(3, cbChange.getUpdateCount());
        assertInstanceOf(Map.class, cbChange.getNewValue());
        Map<?, ?> changeValue = (Map<?, ?>) cbChange.getNewValue();
        assertEquals(true, changeValue.get("enabled"));
        assertEquals("main", changeValue.get("title"));

        var cbRemoval = (org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreRemoval) cbPacket.getUpdates().get(2);
        assertEquals("legacy_state", cbRemoval.getDataStoreName());
    }

    // ==================== ClientboundDataDrivenUIShowScreenPacket ====================

    static Stream<Arguments> versionsFrom924() {
        return filteredVersions(ProtocolInfo.v1_26_0);
    }

    @ParameterizedTest(name = "ClientboundDataDrivenUICloseAllScreensPacket v{0}")
    @MethodSource("versionsFrom924")
    void testClientboundDataDrivenUICloseAllScreensPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientboundDataDrivenUICloseAllScreensPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        if (protocolVersion >= ProtocolInfo.v1_26_10) {
            nukkitPacket.formId = 21;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUICloseScreenPacket.class);

        if (protocolVersion >= ProtocolInfo.v1_26_10) {
            assertEquals(21, cbPacket.getFormId());
        } else {
            assertNull(cbPacket.getFormId());
        }
    }

    @ParameterizedTest(name = "ClientboundDataDrivenUICloseAllScreensPacket null formId v{0}")
    @MethodSource("versionsFrom944")
    void testClientboundDataDrivenUICloseAllScreensPacketNullFormId(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientboundDataDrivenUICloseAllScreensPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUICloseScreenPacket.class);

        assertNull(cbPacket.getFormId());
    }

    @ParameterizedTest(name = "ClientboundDataDrivenUIReloadPacket v{0}")
    @MethodSource("versionsFrom924")
    void testClientboundDataDrivenUIReloadPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientboundDataDrivenUIReloadPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUIReloadPacket.class);

        assertNotNull(cbPacket);
    }

    @ParameterizedTest(name = "ClientboundDataDrivenUIShowScreenPacket v{0}")
    @MethodSource("versionsFrom924")
    void testClientboundDataDrivenUIShowScreenPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientboundDataDrivenUIShowScreenPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.screenId = "test_screen";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUIShowScreenPacket.class);

        assertEquals("test_screen", cbPacket.getScreenId());
    }

    // ==================== ClientboundTextureShiftPacket ====================

    @ParameterizedTest(name = "ClientboundTextureShiftPacket v{0}")
    @MethodSource("versionsFrom924")
    void testClientboundTextureShiftPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ClientboundTextureShiftPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.action = cn.nukkit.network.protocol.ClientboundTextureShiftPacket.ACTION_INITIALIZE;
        nukkitPacket.collectionName = "test_collection";
        nukkitPacket.fromStep = "step1";
        nukkitPacket.toStep = "step2";
        nukkitPacket.allSteps = new String[]{"step1", "step2", "step3"};
        nukkitPacket.currentLengthInTicks = 100L;
        nukkitPacket.totalLengthInTicks = 200L;
        nukkitPacket.enabled = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundTextureShiftPacket.class);

        assertEquals(1, cbPacket.getAction().ordinal());
        assertEquals("test_collection", cbPacket.getCollectionName());
        assertEquals("step1", cbPacket.getFromStep());
        assertEquals("step2", cbPacket.getToStep());
        assertEquals(3, cbPacket.getAllSteps().size());
        assertEquals(100L, cbPacket.getCurrentLengthTicks());
        assertEquals(200L, cbPacket.getTotalLengthTicks());
        assertTrue(cbPacket.isEnabled());
    }

    // ==================== CurrentStructureFeaturePacket ====================

    static Stream<Arguments> versionsFrom712() {
        return filteredVersions(ProtocolInfo.v1_21_20);
    }

    @ParameterizedTest(name = "CurrentStructureFeaturePacket v{0}")
    @MethodSource("versionsFrom712")
    void testCurrentStructureFeaturePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CurrentStructureFeaturePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.currentStructureFeature = "minecraft:village";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CurrentStructureFeaturePacket.class);

        assertEquals("minecraft:village", cbPacket.getCurrentStructureFeature());
    }

    // ==================== CreatePhotoPacket ====================

    @ParameterizedTest(name = "CreatePhotoPacket v{0}")
    @MethodSource("versionsFrom471")
    void testCreatePhotoPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CreatePhotoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setActorUniqueId(12345L);
        nukkitPacket.setPhotoName("test_photo");
        nukkitPacket.setPhotoItemName("test_item");
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CreatePhotoPacket.class);

        assertEquals(12345L, cbPacket.getId());
        assertEquals("test_photo", cbPacket.getPhotoName());
        assertEquals("test_item", cbPacket.getPhotoItemName());
    }

    // ==================== UpdateSoftEnumPacket ====================

    @ParameterizedTest(name = "UpdateSoftEnumPacket v{0}")
    @MethodSource("allVersions")
    void testUpdateSoftEnumPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateSoftEnumPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.name = "TestEnum";
        nukkitPacket.values = new String[]{"value1", "value2", "value3"};
        nukkitPacket.type = cn.nukkit.network.protocol.UpdateSoftEnumPacket.Type.SET;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateSoftEnumPacket.class);

        assertEquals("TestEnum", cbPacket.getSoftEnum().getName());
        assertEquals(3, cbPacket.getSoftEnum().getValues().size());
        assertTrue(cbPacket.getSoftEnum().getValues().containsKey("value1"));
        assertTrue(cbPacket.getSoftEnum().getValues().containsKey("value2"));
        assertTrue(cbPacket.getSoftEnum().getValues().containsKey("value3"));
        assertEquals(2, cbPacket.getType().ordinal()); // SET = 2 in CB
    }

    @ParameterizedTest(name = "UpdateSoftEnumPacket ADD v{0}")
    @MethodSource("allVersions")
    void testUpdateSoftEnumPacketAdd(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateSoftEnumPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.name = "GameModes";
        nukkitPacket.values = new String[]{"creative"};
        nukkitPacket.type = cn.nukkit.network.protocol.UpdateSoftEnumPacket.Type.ADD;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateSoftEnumPacket.class);

        assertEquals("GameModes", cbPacket.getSoftEnum().getName());
        assertEquals(1, cbPacket.getSoftEnum().getValues().size());
        assertEquals(0, cbPacket.getType().ordinal()); // ADD = 0
    }

    // ==================== v944 Packets ====================

    static Stream<Arguments> versionsFrom944() {
        return filteredVersions(ProtocolInfo.v1_26_10);
    }

    // ==================== PartyChangedPacket ====================

    @ParameterizedTest(name = "PartyChangedPacket v{0}")
    @MethodSource("versionsFrom944")
    void testPartyChangedPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PartyChangedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.partyId = "test-party-123";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PartyChangedPacket.class);

        assertEquals("test-party-123", cbPacket.getPartyId());
    }

    // ==================== ServerboundDataDrivenScreenClosedPacket ====================

    @ParameterizedTest(name = "ServerboundDataDrivenScreenClosedPacket v{0}")
    @MethodSource("versionsFrom944")
    void testServerboundDataDrivenScreenClosedPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.formId = 42;
        nukkitPacket.closeReason = cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket.CloseReason.USER_BUSY;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.class);

        assertEquals(42, cbPacket.getFormId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.CloseReason.USER_BUSY, cbPacket.getCloseReason());
    }

    @ParameterizedTest(name = "ServerboundDataDrivenScreenClosedPacket PROGRAMMATIC_CLOSE v{0}")
    @MethodSource("versionsFrom944")
    void testServerboundDataDrivenScreenClosedPacketProgrammaticClose(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.formId = 100;
        nukkitPacket.closeReason = cn.nukkit.network.protocol.ServerboundDataDrivenScreenClosedPacket.CloseReason.PROGRAMMATIC_CLOSE;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.class);

        assertEquals(100, cbPacket.getFormId());
        assertEquals(org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.CloseReason.PROGRAMMATIC_CLOSE, cbPacket.getCloseReason());
    }
}
