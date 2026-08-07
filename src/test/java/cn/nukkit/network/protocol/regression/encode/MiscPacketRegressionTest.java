package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.level.GameRules;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.MovementEffectType;
import cn.nukkit.network.protocol.types.ServerAuthMovementMode;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacket_v113;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class MiscPacketRegressionTest extends AbstractPacketRegressionTest {

    private static final int LEGACY_PROTOCOL_113 = ProtocolInfo.v1_1_0;
    private static final String LEGACY_113_MOVE_PLAYER_HEX =
            "0B0000A03F00008042000020C0000048410000624200000A42000163";
    private static final String LEGACY_113_START_GAME_HEX =
            "0102060000A03F00008142000070C0000028410000A241540204080A14400F0101000000803E0000003F01000008776F726C642D696405526574726F00";
    private static final String LEGACY_113_CONTAINER_SET_CONTENT_HEX = "000B0000";
    private static final java.util.UUID LEGACY_PLAYER_LIST_UUID =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static Network network;

    @BeforeAll
    static void setUp() {
        MockServer.init();
        network = new Network(MockServer.get());
        org.mockito.Mockito.lenient().when(MockServer.get().getNetwork()).thenReturn(network);
    }

    @Test
    void testLegacy113LoginPacketBatchDecodesWhenPlayerProtocolUnknown() {
        Player player = org.mockito.Mockito.mock(Player.class);
        player.protocol = Integer.MAX_VALUE;

        byte[] skinBytes = new byte[64 * 32 * 4];
        java.util.UUID clientUuid = java.util.UUID.fromString("12345678-1234-5678-9abc-def012345678");
        byte[] batchPayload = buildLegacy113LoginBatch("TestUser", clientUuid, 12345L, skinBytes);

        var packets = new ArrayList<DataPacket>();
        assertTrue(network.processBatch(batchPayload, packets, CompressionProvider.NONE, 7, player));
        assertEquals(1, packets.size());
        assertInstanceOf(LoginPacket.class, packets.get(0));

        LoginPacket loginPacket = (LoginPacket) packets.get(0);
        assertEquals(ProtocolInfo.v1_1_0, loginPacket.getProtocol());
        assertEquals("TestUser", loginPacket.username);
        assertEquals(clientUuid, loginPacket.clientUUID);
        assertEquals(12345L, loginPacket.clientId);
        assertNotNull(loginPacket.skin);
        assertEquals("legacy-skin", loginPacket.skin.getSkinId());
        assertEquals(64, loginPacket.skin.getSkinData().width);
        assertEquals(32, loginPacket.skin.getSkinData().height);
    }

    @Test
    void testLegacy137LoginPacketBatchDecodesWhenPlayerProtocolUnknown() {
        Player player = org.mockito.Mockito.mock(Player.class);
        player.protocol = Integer.MAX_VALUE;

        byte[] skinBytes = new byte[64 * 32 * 4];
        java.util.UUID clientUuid = java.util.UUID.fromString("87654321-4321-8765-cba9-876543210fed");
        byte[] batchPayload = buildLegacy137LoginBatch("TestUser137", clientUuid, 54321L, skinBytes);

        var packets = new ArrayList<DataPacket>();
        assertTrue(network.processBatch(batchPayload, packets, CompressionProvider.NONE, 8, player));
        assertEquals(1, packets.size());
        assertInstanceOf(LoginPacket.class, packets.get(0));

        LoginPacket loginPacket = (LoginPacket) packets.get(0);
        assertEquals(ProtocolInfo.v1_2_0, loginPacket.getProtocol());
        assertEquals("TestUser137", loginPacket.username);
        assertEquals(clientUuid, loginPacket.clientUUID);
        assertEquals(54321L, loginPacket.clientId);
        assertNotNull(loginPacket.skin);
        assertEquals("legacy137-skin", loginPacket.skin.getSkinId());
        assertEquals(64, loginPacket.skin.getSkinData().width);
        assertEquals(32, loginPacket.skin.getSkinData().height);
    }

    static Stream<Arguments> versionsFrom313to799() {
        // RiderJumpPacket was removed in v800
        return filteredVersionsRange(313, 799);
    }

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
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

    static Stream<Arguments> versionsFrom503() {
        return filteredVersions(503);
    }

    static Stream<Arguments> versionsFrom685() {
        return filteredVersions(685);
    }

    static Stream<Arguments> versionsFrom575() {
        return filteredVersions(575);
    }

    static Stream<Arguments> versionsFrom748() {
        return filteredVersions(748);
    }

    static Stream<Arguments> versionsFrom748to818() {
        // SetMovementAuthorityPacket only exists in v748-v818
        return filteredVersionsRange(748, 818);
    }

    static Stream<Arguments> versionsFrom800() {
        return filteredVersions(800);
    }

    private static String encodeLegacy113BodyHex(DataPacket packet) {
        packet.encode();
        byte[] raw = packet.getBuffer();
        return Binary.bytesToHexString(Binary.subBytes(raw, 1, raw.length - 1));
    }

    private static byte[] buildLegacy113LoginBatch(String username, java.util.UUID clientUuid, long clientId, byte[] skinBytes) {
        String authJwt = fakeJwt("{\"extraData\":{\"displayName\":\"" + username + "\",\"identity\":\"" + clientUuid + "\"}}");
        String authPayload = "{\"chain\":[\"" + authJwt + "\"]}";
        String skinJwt = fakeJwt("{\"ClientRandomId\":" + clientId
                + ",\"SkinId\":\"legacy-skin\",\"SkinData\":\""
                + Base64.getEncoder().encodeToString(skinBytes) + "\"}");

        BinaryStream loginPayload = new BinaryStream();
        byte[] authBytes = authPayload.getBytes(StandardCharsets.UTF_8);
        byte[] skinTokenBytes = skinJwt.getBytes(StandardCharsets.UTF_8);
        loginPayload.putLInt(authBytes.length);
        loginPayload.put(authBytes);
        loginPayload.putLInt(skinTokenBytes.length);
        loginPayload.put(skinTokenBytes);

        BinaryStream loginPacket = new BinaryStream();
        loginPacket.putInt(ProtocolInfo.v1_1_0);
        loginPacket.putByte((byte) 0); // gameEdition
        loginPacket.putByteArray(loginPayload.getBuffer());

        BinaryStream rawPacket = new BinaryStream();
        rawPacket.putByte(LoginPacket.NETWORK_ID);
        rawPacket.put(loginPacket.getBuffer());

        BinaryStream batch = new BinaryStream();
        batch.putByteArray(rawPacket.getBuffer());
        return batch.getBuffer();
    }

    private static byte[] buildLegacy137LoginBatch(String username, java.util.UUID clientUuid, long clientId, byte[] skinBytes) {
        String authJwt = fakeJwt("{\"extraData\":{\"displayName\":\"" + username + "\",\"identity\":\"" + clientUuid + "\"}}");
        String authPayload = "{\"chain\":[\"" + authJwt + "\"]}";
        String skinJwt = fakeJwt("{\"ClientRandomId\":" + clientId
                + ",\"SkinId\":\"legacy137-skin\",\"SkinData\":\""
                + Base64.getEncoder().encodeToString(skinBytes) + "\"}");

        BinaryStream loginPayload = new BinaryStream();
        byte[] authBytes = authPayload.getBytes(StandardCharsets.UTF_8);
        byte[] skinTokenBytes = skinJwt.getBytes(StandardCharsets.UTF_8);
        loginPayload.putLInt(authBytes.length);
        loginPayload.put(authBytes);
        loginPayload.putLInt(skinTokenBytes.length);
        loginPayload.put(skinTokenBytes);

        BinaryStream loginPacket = new BinaryStream();
        loginPacket.putInt(ProtocolInfo.v1_2_0);
        loginPacket.putByteArray(loginPayload.getBuffer());

        BinaryStream rawPacket = new BinaryStream();
        rawPacket.putByte(LoginPacket.NETWORK_ID);
        rawPacket.putShort(0);
        rawPacket.put(loginPacket.getBuffer());

        BinaryStream batch = new BinaryStream();
        batch.putByteArray(rawPacket.getBuffer());
        return batch.getBuffer();
    }

    private static String fakeJwt(String payloadJson) {
        return encodeBase64("{}") + "." + encodeBase64(payloadJson) + ".signature";
    }

    private static String encodeBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testMovePlayerPacketLegacy113MatchesGenisysProHex() {
        var nukkitPacket = new MovePlayerPacket();
        nukkitPacket.protocol = LEGACY_PROTOCOL_113;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(LEGACY_PROTOCOL_113, false);
        nukkitPacket.eid = 11;
        nukkitPacket.x = 1.25f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = -2.5f;
        nukkitPacket.pitch = 12.5f;
        nukkitPacket.yaw = 56.5f;
        nukkitPacket.headYaw = 34.5f;
        nukkitPacket.mode = MovePlayerPacket.MODE_NORMAL;
        nukkitPacket.onGround = true;
        nukkitPacket.ridingEid = 99;

        assertEquals(LEGACY_113_MOVE_PLAYER_HEX, encodeLegacy113BodyHex(nukkitPacket));
    }

    @Test
    void testMovePlayerPacketLegacy113DecodesGenisysProHex() {
        var nukkitPacket = new MovePlayerPacket();
        nukkitPacket.protocol = LEGACY_PROTOCOL_113;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(LEGACY_PROTOCOL_113, false);
        nukkitPacket.setBuffer(Binary.hexStringToBytes(LEGACY_113_MOVE_PLAYER_HEX));
        nukkitPacket.decode();

        assertEquals(11, nukkitPacket.eid);
        assertEquals(1.25f, nukkitPacket.x);
        assertEquals(64.0f, nukkitPacket.y);
        assertEquals(-2.5f, nukkitPacket.z);
        assertEquals(12.5f, nukkitPacket.pitch);
        assertEquals(56.5f, nukkitPacket.yaw);
        assertEquals(34.5f, nukkitPacket.headYaw);
        assertEquals(MovePlayerPacket.MODE_NORMAL, nukkitPacket.mode);
        assertTrue(nukkitPacket.onGround);
        assertEquals(99, nukkitPacket.ridingEid);
    }

    @Test
    void testStartGamePacketLegacy113MatchesGenisysProHex() {
        var nukkitPacket = new StartGamePacket();
        nukkitPacket.protocol = LEGACY_PROTOCOL_113;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(LEGACY_PROTOCOL_113, false);
        nukkitPacket.entityUniqueId = 1;
        nukkitPacket.entityRuntimeId = 2;
        nukkitPacket.playerGamemode = 3;
        nukkitPacket.x = 1.25f;
        nukkitPacket.y = 64.5f;
        nukkitPacket.z = -3.75f;
        nukkitPacket.pitch = 10.5f;
        nukkitPacket.yaw = 20.25f;
        nukkitPacket.seed = 42;
        nukkitPacket.dimension = 1;
        nukkitPacket.generator = 2;
        nukkitPacket.worldGamemode = 4;
        nukkitPacket.difficulty = 5;
        nukkitPacket.spawnX = 10;
        nukkitPacket.spawnY = 64;
        nukkitPacket.spawnZ = -8;
        nukkitPacket.hasAchievementsDisabled = true;
        nukkitPacket.dayCycleStopTime = -1;
        nukkitPacket.eduMode = false;
        nukkitPacket.rainLevel = 0.25f;
        nukkitPacket.lightningLevel = 0.5f;
        nukkitPacket.commandsEnabled = true;
        nukkitPacket.isTexturePacksRequired = false;
        nukkitPacket.gameRules = new GameRules();
        nukkitPacket.levelId = "world-id";
        nukkitPacket.worldName = "Retro";
        nukkitPacket.premiumWorldTemplateId = "";

        assertEquals(LEGACY_113_START_GAME_HEX, encodeLegacy113BodyHex(nukkitPacket));
    }

    @Test
    void testContainerSetContentPacketLegacy113MatchesGenisysProEntityIdEncoding() {
        var nukkitPacket = new ContainerSetContentPacket_v113();
        nukkitPacket.protocol = LEGACY_PROTOCOL_113;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(LEGACY_PROTOCOL_113, false);
        nukkitPacket.windowid = ContainerSetContentPacket_v113.SPECIAL_INVENTORY;
        nukkitPacket.eid = 11;
        nukkitPacket.slots = cn.nukkit.item.Item.EMPTY_ARRAY;
        nukkitPacket.hotbar = new int[0];

        assertEquals(LEGACY_113_CONTAINER_SET_CONTENT_HEX, encodeLegacy113BodyHex(nukkitPacket));
    }

    @Test
    void testContainerSetContentPacketLegacy113DecodesLegacyEntityIdEncoding() {
        var nukkitPacket = new ContainerSetContentPacket_v113();
        nukkitPacket.protocol = LEGACY_PROTOCOL_113;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(LEGACY_PROTOCOL_113, false);
        nukkitPacket.setBuffer(Binary.hexStringToBytes(LEGACY_113_CONTAINER_SET_CONTENT_HEX));
        nukkitPacket.decode();

        assertEquals(ContainerSetContentPacket_v113.SPECIAL_INVENTORY, nukkitPacket.windowid);
        assertEquals(11, nukkitPacket.eid);
        assertEquals(0, nukkitPacket.slots.length);
        assertEquals(0, nukkitPacket.hotbar.length);
    }

    @Test
    void testPlayerListPacketLegacy113UsesLegacyEntityIdEncoding() {
        var nukkitPacket = new PlayerListPacket();
        nukkitPacket.protocol = LEGACY_PROTOCOL_113;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(LEGACY_PROTOCOL_113, false);
        nukkitPacket.type = PlayerListPacket.TYPE_ADD;
        nukkitPacket.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(LEGACY_PLAYER_LIST_UUID, 11, "Tester", cn.nukkit.entity.data.Skin.NO_PERSONA_SKIN)
        };

        String bodyHex = encodeLegacy113BodyHex(nukkitPacket);
        // Pre-v223 (v1_2_13) does not write UUID.
        // The encoding starts with: type(ADD=0x00) + count(1=0x01) + entityId + name + skin...
        // Verify type and count are correct, and the name "Tester" appears in the encoded output.
        assertTrue(bodyHex.startsWith("0001"), "type and count should be ADD=00, count=01, got: " + bodyHex.substring(0, Math.min(20, bodyHex.length())));
        // Name "Tester" as UTF-8 hex should be present in the output
        assertTrue(bodyHex.contains("546573746572"), "name 'Tester' hex should appear");
        // Skin id "Standard_Custom" should be present
        assertTrue(bodyHex.contains("5374616E646172645F437573746F6D"), "skin id 'Standard_Custom' hex should appear");
        assertTrue(bodyHex.contains("546573746572"), "name 'Tester' should be in output: " + bodyHex.substring(0, 100));
    }

    @ParameterizedTest(name = "GameRulesChangedPacket v{0}")
    @MethodSource("versionsFrom313")
    void testGameRulesChangedPacket(int protocolVersion) {
        var nukkitPacket = new GameRulesChangedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.gameRules = new GameRules();
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket.class);

        assertNotNull(cbPacket.getGameRules());
    }

    @ParameterizedTest(name = "AvailableEntityIdentifiersPacket v{0}")
    @MethodSource("versionsFrom313")
    void testAvailableEntityIdentifiersPacket(int protocolVersion) {
        var nukkitPacket = new AvailableEntityIdentifiersPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket.class);

        assertNotNull(cbPacket);
    }

    @ParameterizedTest(name = "BiomeDefinitionListPacket v{0}")
    @MethodSource("versionsFrom313to799")
    void testBiomeDefinitionListPacket(int protocolVersion) {
        var nukkitPacket = new BiomeDefinitionListPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket.class);

        assertNotNull(cbPacket);
    }

    @ParameterizedTest(name = "RiderJumpPacket v{0}")
    @MethodSource("versionsFrom313to799")
    void testRiderJumpPacket(int protocolVersion) {
        var nukkitPacket = new RiderJumpPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.jumpStrength = 5;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RiderJumpPacket.class);

        assertEquals(5, cbPacket.getJumpStrength());
    }

    @ParameterizedTest(name = "ResourcePacksInfoPacket v{0}")
    @MethodSource("versionsFrom313")
    void testResourcePacksInfoPacket(int protocolVersion) {
        var nukkitPacket = new ResourcePacksInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket.class);

        assertFalse(cbPacket.isForcedToAccept());
    }

    @ParameterizedTest(name = "ResourcePackStackPacket v{0}")
    @MethodSource("versionsFrom313")
    void testResourcePackStackPacket(int protocolVersion) {
        var nukkitPacket = new ResourcePackStackPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket.class);

        assertFalse(cbPacket.isForcedToAccept());
        if (protocolVersion >= 388) {
            assertEquals("*", cbPacket.getGameVersion());
        }
    }

    @ParameterizedTest(name = "HurtArmorPacket v{0}")
    @MethodSource("versionsFrom313")
    void testHurtArmorPacket(int protocolVersion) {
        var nukkitPacket = new HurtArmorPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.damage = 10;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.HurtArmorPacket.class);

        assertEquals(10, cbPacket.getDamage());
    }

    // ==================== MovementEffectPacket ====================

    @ParameterizedTest(name = "MovementEffectPacket v{0}")
    @MethodSource("versionsFrom748")
    void testMovementEffectPacket(int protocolVersion) {
        var nukkitPacket = new MovementEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.targetRuntimeID = 100;
        nukkitPacket.effectType = MovementEffectType.DOLPHIN_BOOST;
        nukkitPacket.effectDuration = 200;
        nukkitPacket.tick = 12345L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MovementEffectPacket.class);

        assertEquals(100, cbPacket.getEntityRuntimeId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.MovementEffectType.DOLPHIN_BOOST, cbPacket.getEffectType());
        assertEquals(200, cbPacket.getDuration());
        assertEquals(12345L, cbPacket.getTick());
    }

    // ==================== SetMovementAuthorityPacket ====================

    @ParameterizedTest(name = "SetMovementAuthorityPacket v{0}")
    @MethodSource("versionsFrom748to818")
    void testSetMovementAuthorityPacket(int protocolVersion) {
        var nukkitPacket = new SetMovementAuthorityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.serverAuthMovementMode = ServerAuthMovementMode.SERVER_AUTHORITATIVE_V2;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetMovementAuthorityPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode.SERVER, cbPacket.getMovementMode());
    }

    // ==================== PlayerLocationPacket ====================

    @ParameterizedTest(name = "PlayerLocationPacket COORDINATES v{0}")
    @MethodSource("versionsFrom800")
    void testPlayerLocationPacketCoordinates(int protocolVersion) {
        var nukkitPacket = new PlayerLocationPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = PlayerLocationPacket.Type.COORDINATES;
        nukkitPacket.targetEntityId = 100;
        nukkitPacket.position = new Vector3f(100.5f, 64.0f, 200.5f);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket.Type.COORDINATES, cbPacket.getType());
        assertEquals(100, cbPacket.getTargetEntityId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
    }

    @ParameterizedTest(name = "PlayerLocationPacket HIDE v{0}")
    @MethodSource("versionsFrom800")
    void testPlayerLocationPacketHide(int protocolVersion) {
        var nukkitPacket = new PlayerLocationPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = PlayerLocationPacket.Type.HIDE;
        nukkitPacket.targetEntityId = 100;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket.Type.HIDE, cbPacket.getType());
        assertEquals(100, cbPacket.getTargetEntityId());
    }

    // ==================== ToggleCrafterSlotRequestPacket ====================

    @ParameterizedTest(name = "ToggleCrafterSlotRequestPacket v{0}")
    @MethodSource("versionsFrom748")
    void testToggleCrafterSlotRequestPacket(int protocolVersion) {
        var nukkitPacket = new ToggleCrafterSlotRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setBlockPosition(new cn.nukkit.math.Vector3f(100, 64, 200));
        nukkitPacket.slot = 3;
        nukkitPacket.disabled = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ToggleCrafterSlotRequestPacket.class);

        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertEquals(3, cbPacket.getSlot());
        assertTrue(cbPacket.isDisabled());
    }

    // ==================== CommandBlockUpdatePacket ====================

    @ParameterizedTest(name = "CommandBlockUpdatePacket BLOCK v{0}")
    @MethodSource("versionsFrom313")
    void testCommandBlockUpdatePacketBlock(int protocolVersion) {
        var nukkitPacket = new CommandBlockUpdatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.isBlock = true;
        nukkitPacket.x = 100;
        nukkitPacket.y = 64;
        nukkitPacket.z = 200;
        nukkitPacket.commandBlockMode = 0;
        nukkitPacket.isRedstoneMode = false;
        nukkitPacket.isConditional = false;
        nukkitPacket.command = "say hello";
        nukkitPacket.lastOutput = "";
        nukkitPacket.name = "TestCommand";
        nukkitPacket.shouldTrackOutput = false;
        if (protocolVersion >= ProtocolInfo.v1_12_0) {
            nukkitPacket.tickDelay = 0;
            nukkitPacket.executingOnFirstTick = false;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket.class);

        assertTrue(cbPacket.isBlock());
        assertEquals(100, cbPacket.getBlockPosition().getX());
        assertEquals(64, cbPacket.getBlockPosition().getY());
        assertEquals(200, cbPacket.getBlockPosition().getZ());
        assertEquals("say hello", cbPacket.getCommand());
        assertEquals("TestCommand", cbPacket.getName());
    }

    @ParameterizedTest(name = "CommandBlockUpdatePacket MINECART v{0}")
    @MethodSource("versionsFrom313")
    void testCommandBlockUpdatePacketMinecart(int protocolVersion) {
        var nukkitPacket = new CommandBlockUpdatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.isBlock = false;
        nukkitPacket.minecartEid = 42;
        nukkitPacket.command = "say world";
        nukkitPacket.lastOutput = "";
        nukkitPacket.name = "MinecartCmd";
        nukkitPacket.shouldTrackOutput = true;
        if (protocolVersion >= ProtocolInfo.v1_12_0) {
            nukkitPacket.tickDelay = 10;
            nukkitPacket.executingOnFirstTick = true;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket.class);

        assertFalse(cbPacket.isBlock());
        assertEquals(42, cbPacket.getMinecartRuntimeEntityId());
        assertEquals("say world", cbPacket.getCommand());
        assertEquals("MinecartCmd", cbPacket.getName());
        assertTrue(cbPacket.isOutputTracked());
        if (protocolVersion >= ProtocolInfo.v1_12_0) {
            assertEquals(10, cbPacket.getTickDelay());
            assertTrue(cbPacket.isExecutingOnFirstTick());
        }
    }

    // ==================== AwardAchievementPacket ====================

    @ParameterizedTest(name = "AwardAchievementPacket v{0}")
    @MethodSource("versionsFrom685")
    void testAwardAchievementPacket(int protocolVersion) {
        var nukkitPacket = new AwardAchievementPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.achievementId = 42;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AwardAchievementPacket.class);

        assertEquals(42, cbPacket.getAchievementId());
    }

    // ==================== ChangeMobPropertyPacket ====================

    @ParameterizedTest(name = "ChangeMobPropertyPacket v{0}")
    @MethodSource("versionsFrom503")
    void testChangeMobPropertyPacket(int protocolVersion) {
        var nukkitPacket = new ChangeMobPropertyPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setUniqueEntityId(12345L);
        nukkitPacket.setProperty("test_property");
        nukkitPacket.setBoolValue(true);
        nukkitPacket.setStringValue("test_string");
        nukkitPacket.setIntValue(100);
        nukkitPacket.setFloatValue(1.5f);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ChangeMobPropertyPacket.class);

        assertEquals(12345L, cbPacket.getUniqueEntityId());
        assertEquals("test_property", cbPacket.getProperty());
        assertTrue(cbPacket.isBoolValue());
        assertEquals("test_string", cbPacket.getStringValue());
        assertEquals(100, cbPacket.getIntValue());
        assertEquals(1.5f, cbPacket.getFloatValue(), 0.001f);
    }

    // ==================== UpdateSoftEnumPacket ====================
    // TODO: Encoding format mismatch - CB Protocol uses helper.writeCommandEnum() which has different format

    // @ParameterizedTest(name = "UpdateSoftEnumPacket v{0}")
    // @MethodSource("allVersions")
    // void testUpdateSoftEnumPacket(int protocolVersion) {
    //     var nukkitPacket = new UpdateSoftEnumPacket();
    //     nukkitPacket.protocol = protocolVersion;
    //     nukkitPacket.name = "test_enum";
    //     nukkitPacket.values = new String[]{"value1", "value2"};
    //     nukkitPacket.type = UpdateSoftEnumPacket.Type.SET;
    //     nukkitPacket.encode();
    //
    //     var cbPacket = crossDecode(nukkitPacket,
    //             org.cloudburstmc.protocol.bedrock.packet.UpdateSoftEnumPacket.class);
    //
    //     assertNotNull(cbPacket.getSoftEnum());
    //     assertEquals("test_enum", cbPacket.getSoftEnum().getName());
    //     assertEquals(2, cbPacket.getSoftEnum().getValues().size());
    //     assertEquals("value1", cbPacket.getSoftEnum().getValues().get(0));
    //     assertEquals("value2", cbPacket.getSoftEnum().getValues().get(1));
    //     assertEquals(org.cloudburstmc.protocol.bedrock.data.command.SoftEnumUpdateType.REPLACE, cbPacket.getType());
    // }

    // ==================== UnlockedRecipesPacket ====================

    @ParameterizedTest(name = "UnlockedRecipesPacket v{0}")
    @MethodSource("versionsFrom575")
    void testUnlockedRecipesPacket(int protocolVersion) {
        var nukkitPacket = new UnlockedRecipesPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.action = UnlockedRecipesPacket.ActionType.NEWLY_UNLOCKED;
        nukkitPacket.getUnlockedRecipes().add("recipe1");
        nukkitPacket.getUnlockedRecipes().add("recipe2");
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket.class);

        assertEquals(2, cbPacket.getUnlockedRecipes().size());
        assertTrue(cbPacket.getUnlockedRecipes().contains("recipe1"));
        assertTrue(cbPacket.getUnlockedRecipes().contains("recipe2"));
    }

    // ==================== TrimDataPacket ====================

    static Stream<Arguments> versionsFrom582() {
        return filteredVersions(582);
    }

    @ParameterizedTest(name = "TrimDataPacket v{0}")
    @MethodSource("versionsFrom582")
    void testTrimDataPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TrimDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.getPatterns().add(new cn.nukkit.network.protocol.types.TrimPattern("item1", "pattern1"));
        nukkitPacket.getPatterns().add(new cn.nukkit.network.protocol.types.TrimPattern("item2", "pattern2"));
        nukkitPacket.getMaterials().add(new cn.nukkit.network.protocol.types.TrimMaterial("material1", "color1", "itemName1"));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket.class);

        assertEquals(2, cbPacket.getPatterns().size());
        assertEquals("item1", cbPacket.getPatterns().get(0).getItemName());
        assertEquals("pattern1", cbPacket.getPatterns().get(0).getPatternId());
        assertEquals(1, cbPacket.getMaterials().size());
        assertEquals("material1", cbPacket.getMaterials().get(0).getMaterialId());
    }

    // ==================== EventPacket ====================

    @ParameterizedTest(name = "EventPacket v{0}")
    @MethodSource("allVersions")
    void testEventPacket(int protocolVersion) {
        var nukkitPacket = new EventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 123456789L;
        nukkitPacket.unknown1 = 42;
        nukkitPacket.unknown2 = (byte) EventPacket.TYPE_BELL_BLOCK_USED;
        nukkitPacket.encode();

        var stream = new BinaryStream(nukkitPacket.getBuffer());
        stream.getUnsignedVarInt();

        assertEquals(123456789L, stream.getVarLong());
        assertEquals(42, stream.getVarInt());
        assertEquals((byte) EventPacket.TYPE_BELL_BLOCK_USED, stream.getByte());
        assertEquals(0, stream.readableBytes());
    }

    // ==================== LevelEventGenericPacket ====================

    @ParameterizedTest(name = "LevelEventGenericPacket v{0}")
    @MethodSource("versionsFrom388")
    void testLevelEventGenericPacket(int protocolVersion) {
        var nukkitPacket = new LevelEventGenericPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eventId = 17;
        nukkitPacket.tag = new CompoundTag("")
                .putString("name", "test")
                .putInt("value", 7);
        nukkitPacket.encode();

        var stream = new BinaryStream(nukkitPacket.getBuffer());
        stream.getUnsignedVarInt();

        assertEquals(17, stream.getVarInt());
        // LevelEventGenericPacket writes a headerless (no root type id / name) NBT value.
        var decodedTag = stream.getTagValueNetworkLE();
        assertEquals("test", decodedTag.getString("name"));
        assertEquals(7, decodedTag.getInt("value"));
        assertEquals(0, stream.readableBytes());
    }

    // ==================== AddVolumeEntityPacket ====================

    static Stream<Arguments> versionsFrom440to465() {
        return filteredVersionsRange(440, 465);
    }

    static Stream<Arguments> versionsFrom465to486() {
        return filteredVersionsRange(465, 486);
    }

    static Stream<Arguments> versionsFrom486() {
        return filteredVersions(486);
    }

    @ParameterizedTest(name = "AddVolumeEntityPacket v{0} (<465, minimal)")
    @MethodSource("versionsFrom440to465")
    void testAddVolumeEntityPacketMinimal(int protocolVersion) {
        var nukkitPacket = new AddVolumeEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setId(100);
        nukkitPacket.setData(new CompoundTag("").putString("name", "test_volume"));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket.class);

        assertEquals(100, cbPacket.getId());
    }

    @ParameterizedTest(name = "AddVolumeEntityPacket v{0} (465-485, +engineVersion)")
    @MethodSource("versionsFrom465to486")
    void testAddVolumeEntityPacketWithEngine(int protocolVersion) {
        var nukkitPacket = new AddVolumeEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setId(100);
        nukkitPacket.setData(new CompoundTag("").putString("name", "test_volume"));
        nukkitPacket.setEngineVersion("1.17.40");
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket.class);

        assertEquals(100, cbPacket.getId());
        assertEquals("1.17.40", cbPacket.getEngineVersion());
    }

    @ParameterizedTest(name = "AddVolumeEntityPacket v{0} (>=485, full)")
    @MethodSource("versionsFrom486")
    void testAddVolumeEntityPacketFull(int protocolVersion) {
        var nukkitPacket = new AddVolumeEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setId(100);
        nukkitPacket.setData(new CompoundTag("").putString("name", "test_volume"));
        nukkitPacket.setEngineVersion("1.20.0");
        nukkitPacket.setIdentifier("custom:volume");
        nukkitPacket.setInstanceName("test_instance");
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket.class);

        assertEquals(100, cbPacket.getId());
        assertEquals("1.20.0", cbPacket.getEngineVersion());
        assertEquals("custom:volume", cbPacket.getIdentifier());
        assertEquals("test_instance", cbPacket.getInstanceName());
    }

    // ==================== UpdateTradePacket ====================

    @ParameterizedTest(name = "UpdateTradePacket v{0} (>=354)")
    @MethodSource("versionsFrom354")
    void testUpdateTradePacket(int protocolVersion) {
        var nukkitPacket = new UpdateTradePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.windowId = 1;
        nukkitPacket.windowType = 15;
        nukkitPacket.size = 0;
        nukkitPacket.tradeTier = 2;
        nukkitPacket.traderUniqueEntityId = 42;
        nukkitPacket.playerUniqueEntityId = 1;
        nukkitPacket.displayName = "Villager";
        nukkitPacket.newTradingUi = true;
        nukkitPacket.usingEconomyTrade = false;
        // Empty offers NBT
        try {
            nukkitPacket.offers = cn.nukkit.nbt.NBTIO.write(new CompoundTag(""), java.nio.ByteOrder.LITTLE_ENDIAN, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateTradePacket.class);

        assertEquals(1, cbPacket.getContainerId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType.TRADE, cbPacket.getContainerType());
        assertEquals(2, cbPacket.getTradeTier());
        assertEquals(42, cbPacket.getTraderUniqueEntityId());
        assertEquals(1, cbPacket.getPlayerUniqueEntityId());
        assertEquals("Villager", cbPacket.getDisplayName());
        assertTrue(cbPacket.isNewTradingUi());
        assertFalse(cbPacket.isUsingEconomyTrade());
    }
}
