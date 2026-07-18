package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.Attribute;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.PacketBridgeUtil;
import dev.mot.protocol.extension.codec.v630.Bedrock_v630_NetEase;
import dev.mot.protocol.extension.codec.v686.Bedrock_v686_NetEase;
import dev.mot.protocol.extension.codec.v766.Bedrock_v766_NetEase;
import dev.mot.protocol.extension.codec.v860.Bedrock_v860_NetEase;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for NetEase-specific packets and encoding branches.
 * Uses ProtocolExtension NetEase codecs (v630, v686, v766, v860) to decode packets encoded by Nukkit-MOT.
 */
public class NetEasePacketRegressionTest {

    private static final Map<Integer, BedrockCodec> NETEASE_CODECS;
    private static final Map<Integer, GameVersion> NETEASE_GAME_VERSIONS;
    private static final int CUSTOM_PACKET_NEW_ID = 399;
    private static final byte CUSTOM_PACKET_LEGACY_ID = (byte) 0xfe;

    static {
        NETEASE_CODECS = new LinkedHashMap<>();
        NETEASE_CODECS.put(630, Bedrock_v630_NetEase.CODEC);
        NETEASE_CODECS.put(686, Bedrock_v686_NetEase.CODEC);
        NETEASE_CODECS.put(766, Bedrock_v766_NetEase.CODEC);
        NETEASE_CODECS.put(860, Bedrock_v860_NetEase.CODEC);

        NETEASE_GAME_VERSIONS = new LinkedHashMap<>();
        NETEASE_GAME_VERSIONS.put(630, GameVersion.V1_20_50_NETEASE);
        NETEASE_GAME_VERSIONS.put(686, GameVersion.V1_21_2_NETEASE);
        NETEASE_GAME_VERSIONS.put(766, GameVersion.V1_21_50_NETEASE);
        NETEASE_GAME_VERSIONS.put(860, GameVersion.V1_21_124_NETEASE);
    }

    @BeforeAll
    static void setUp() {
        MockServer.init();
        Attribute.init();
    }

    static Stream<Arguments> allNetEaseVersions() {
        return NETEASE_CODECS.keySet().stream().map(Arguments::of);
    }

    static Stream<Arguments> netEasePacketPoolVersions() {
        return Stream.of(
                GameVersion.V1_20_50_NETEASE,
                GameVersion.V1_21_2_NETEASE,
                GameVersion.V1_21_50_NETEASE,
                GameVersion.V1_21_93_NETEASE,
                GameVersion.V1_21_124_NETEASE
        ).map(Arguments::of);
    }

    static Stream<Arguments> internationalPacketPoolVersions() {
        return Stream.of(
                GameVersion.V1_20_50,
                GameVersion.V1_21_2,
                GameVersion.V1_21_50,
                GameVersion.V1_21_93,
                GameVersion.V1_21_124,
                GameVersion.getLastVersion()
        ).map(Arguments::of);
    }

    @SuppressWarnings("unchecked")
    private <T extends BedrockPacket> T crossDecodeNetEase(DataPacket nukkitPacket, Class<T> cbPacketClass) {
        ByteBuf buf = PacketBridgeUtil.nukkitPacketToByteBuf(nukkitPacket);
        try {
            BedrockCodec codec = NETEASE_CODECS.get(nukkitPacket.protocol);
            assertNotNull(codec, "No NetEase codec for protocol version: " + nukkitPacket.protocol);
            BedrockCodecHelper helper = codec.createHelper();
            BedrockPacketDefinition<T> definition = (BedrockPacketDefinition<T>)
                    codec.getPacketDefinition(cbPacketClass);
            assertNotNull(definition, cbPacketClass.getSimpleName() + " not registered in NetEase codec v" + nukkitPacket.protocol);
            T cbPacket;
            try {
                cbPacket = cbPacketClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to instantiate " + cbPacketClass.getSimpleName(), e);
            }
            definition.getSerializer().deserialize(buf, helper, cbPacket);
            assertEquals(0, buf.readableBytes(),
                    "Buffer not fully consumed for " + cbPacketClass.getSimpleName() + " v" + nukkitPacket.protocol);
            return cbPacket;
        } finally {
            buf.release();
        }
    }

    private DataPacket prepareNetEasePacket(DataPacket packet, int protocolVersion) {
        packet.protocol = protocolVersion;
        packet.gameVersion = NETEASE_GAME_VERSIONS.get(protocolVersion);
        return packet;
    }

    private cn.nukkit.network.protocol.netease.PyRpcPacket decodePyRpcPacket(byte[] data, long msgId, int protocolVersion) {
        var cbPacket = new dev.mot.protocol.extension.packet.PyRpcPacket();
        cbPacket.setData(data);
        cbPacket.setMsgId(msgId);

        BedrockCodec codec = NETEASE_CODECS.get(protocolVersion);
        byte[] buffer = PacketBridgeUtil.cbPacketToNukkitBuffer(
                cbPacket, codec, codec.createHelper(), ProtocolInfo.PY_RPC_PACKET, protocolVersion);

        var nukkitPacket = new cn.nukkit.network.protocol.netease.PyRpcPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.setBuffer(buffer);
        nukkitPacket.getUnsignedVarInt();
        nukkitPacket.decode();
        return nukkitPacket;
    }

    private static byte[] msgpackArray(byte[]... elements) {
        assertTrue(elements.length < 16, "test helper only supports fixarray");
        return concat(new byte[]{(byte) (0x90 | elements.length)}, concat(elements));
    }

    private static byte[] msgpackMap(String key, byte[] value) {
        return concat(new byte[]{(byte) 0x81}, msgpackString(key), value);
    }

    private static byte[] msgpackMap(byte[] key, byte[] value) {
        return concat(new byte[]{(byte) 0x81}, key, value);
    }

    private static byte[] msgpackString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        assertTrue(bytes.length < 32, "test helper only supports fixstr");
        return concat(new byte[]{(byte) (0xa0 | bytes.length)}, bytes);
    }

    private static byte[] msgpackBinary(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        assertTrue(bytes.length < 256, "test helper only supports bin8");
        return concat(new byte[]{(byte) 0xc4, (byte) bytes.length}, bytes);
    }

    private static byte[] msgpackBinary(byte[] bytes) {
        assertTrue(bytes.length < 256, "test helper only supports bin8");
        return concat(new byte[]{(byte) 0xc4, (byte) bytes.length}, bytes);
    }

    private static byte[] msgpackNil() {
        return new byte[]{(byte) 0xc0};
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    // ==================== ConfirmSkinPacket ====================

    @ParameterizedTest(name = "ConfirmSkinPacket empty v{0}")
    @MethodSource("allNetEaseVersions")
    void testConfirmSkinPacketEmpty(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.netease.ConfirmSkinPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        // Empty entry list - no trailing uidStr/geoStr loops needed
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.ConfirmSkinPacket.class);

        assertTrue(cbPacket.getEntries().isEmpty());
    }

    @ParameterizedTest(name = "ConfirmSkinPacket entry v{0}")
    @MethodSource("allNetEaseVersions")
    void testConfirmSkinPacketEntry(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.netease.ConfirmSkinPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);

        var uuid = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        byte[] skinBytes = new byte[]{1, 2, 3, 4};
        nukkitPacket.addEntry(new cn.nukkit.network.protocol.netease.ConfirmSkinPacket.SkinEntry(
                true, uuid, skinBytes, "uid-123", "geo-456"));
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.ConfirmSkinPacket.class);

        assertEquals(1, cbPacket.getEntries().size());
        var entry = cbPacket.getEntries().get(0);
        assertTrue(entry.isValid());
        assertEquals(uuid, entry.getUuid());
        assertArrayEquals(skinBytes, entry.getSkinBytes());
        assertEquals("uid-123", entry.getUidStr());
        assertEquals("geo-456", entry.getGeoStr());
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest(name = "ConfirmSkinPacket legacy UUID list v{0}")
    @MethodSource("allNetEaseVersions")
    void testConfirmSkinPacketLegacyUuidList(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.netease.ConfirmSkinPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);

        var uuid = UUID.fromString("87654321-4321-8765-9abc-def012345678");
        nukkitPacket.uuids.add(uuid);
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.ConfirmSkinPacket.class);

        assertEquals(1, cbPacket.getEntries().size());
        var entry = cbPacket.getEntries().get(0);
        assertTrue(entry.isValid());
        assertEquals(uuid, entry.getUuid());
        assertArrayEquals(new byte[0], entry.getSkinBytes());
        assertEquals("", entry.getUidStr());
        assertEquals("", entry.getGeoStr());
    }

    @ParameterizedTest(name = "ConfirmSkinPacket null optional fields v{0}")
    @MethodSource("allNetEaseVersions")
    void testConfirmSkinPacketNullOptionalFields(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.netease.ConfirmSkinPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        var uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        nukkitPacket.addEntry(new cn.nukkit.network.protocol.netease.ConfirmSkinPacket.SkinEntry(
                false, uuid, null, null, null));
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.ConfirmSkinPacket.class);

        assertEquals(1, cbPacket.getEntries().size());
        var entry = cbPacket.getEntries().get(0);
        assertFalse(entry.isValid());
        assertEquals(uuid, entry.getUuid());
        assertArrayEquals(new byte[0], entry.getSkinBytes());
        assertEquals("", entry.getUidStr());
        assertEquals("", entry.getGeoStr());
    }

    // ==================== PyRpcPacket ====================

    @ParameterizedTest(name = "PyRpcPacket v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.netease.PyRpcPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        byte[] data = new byte[]{(byte) 0x93, (byte) 0xa3, 'm', 's', 'g', 1, 2, 3};
        nukkitPacket.setData(data);
        nukkitPacket.setMsgId(0xfedcba98L);
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.PyRpcPacket.class);

        assertArrayEquals(data, cbPacket.getData());
        assertEquals(0xfedcba98L, cbPacket.getMsgId());
    }

    @ParameterizedTest(name = "PyRpcPacket ModEventS2C create v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketCreatesModEventS2CPacket(int protocolVersion) {
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("animName", "wave");

        var nukkitPacket = cn.nukkit.network.protocol.netease.PyRpcPacket.createModEventPacket(
                "Minecraft", "emote", "PlayEmoteEvent", eventData);

        byte[] expected = msgpackArray(
                msgpackBinary("ModEventS2C"),
                msgpackArray(
                        msgpackBinary("Minecraft"),
                        msgpackBinary("emote"),
                        msgpackBinary("PlayEmoteEvent"),
                        msgpackMap(msgpackBinary("animName"), msgpackBinary("wave"))),
                msgpackNil());
        assertArrayEquals(expected, nukkitPacket.getData());
        assertEquals(cn.nukkit.network.protocol.netease.PyRpcPacket.DEFAULT_MSG_ID, nukkitPacket.getMsgId());

        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.PyRpcPacket.class);

        assertArrayEquals(expected, cbPacket.getData());
        assertEquals(cn.nukkit.network.protocol.netease.PyRpcPacket.DEFAULT_MSG_ID, cbPacket.getMsgId());
    }

    @ParameterizedTest(name = "PyRpcPacket encrypted ModEventS2C create v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketCreatesEncryptedModEventS2CPacket(int protocolVersion) {
        var nukkitPacket = cn.nukkit.network.protocol.netease.PyRpcPacket.createEncryptedModEventPacket(
                "Minecraft", "secure", "EncryptedEvent", "payload", value -> "enc:" + value);

        byte[] expected = msgpackArray(
                msgpackBinary("ModEventS2C"),
                msgpackArray(
                        msgpackBinary("Minecraft"),
                        msgpackBinary("secure"),
                        msgpackBinary("EncryptedEvent"),
                        msgpackMap(msgpackBinary("data"), msgpackBinary("enc:payload"))),
                msgpackNil());
        assertArrayEquals(expected, nukkitPacket.getData());
        assertEquals(cn.nukkit.network.protocol.netease.PyRpcPacket.DEFAULT_MSG_ID, nukkitPacket.getMsgId());

        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.PyRpcPacket.class);

        assertArrayEquals(expected, cbPacket.getData());
        assertEquals(cn.nukkit.network.protocol.netease.PyRpcPacket.DEFAULT_MSG_ID, cbPacket.getMsgId());
    }

    @ParameterizedTest(name = "PyRpcPacket decode v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecode(int protocolVersion) {
        byte[] data = new byte[]{(byte) 0x92, (byte) 0xa4, 't', 'e', 's', 't', 42};
        var cbPacket = new dev.mot.protocol.extension.packet.PyRpcPacket();
        cbPacket.setData(data);
        cbPacket.setMsgId(0x87654321L);

        BedrockCodec codec = NETEASE_CODECS.get(protocolVersion);
        byte[] buffer = PacketBridgeUtil.cbPacketToNukkitBuffer(
                cbPacket, codec, codec.createHelper(), ProtocolInfo.PY_RPC_PACKET, protocolVersion);

        var nukkitPacket = new cn.nukkit.network.protocol.netease.PyRpcPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.setBuffer(buffer);
        nukkitPacket.getUnsignedVarInt();
        nukkitPacket.decode();

        assertArrayEquals(data, nukkitPacket.getData());
        assertEquals(0x87654321L, nukkitPacket.getMsgId());
    }

    @ParameterizedTest(name = "PyRpcPacket registered NetEase v{0}")
    @MethodSource("netEasePacketPoolVersions")
    void testPyRpcPacketRegistered(GameVersion gameVersion) {
        Network network = new Network(MockServer.get());

        assertTrue(network.getPacketPool(gameVersion).isNetEase());
        DataPacket packet = network.getPacket(ProtocolInfo.PY_RPC_PACKET, gameVersion);

        assertInstanceOf(cn.nukkit.network.protocol.netease.PyRpcPacket.class, packet);
    }

    @ParameterizedTest(name = "PyRpcPacket not registered international v{0}")
    @MethodSource("internationalPacketPoolVersions")
    void testPyRpcPacketNotRegisteredForInternationalPacketPool(GameVersion gameVersion) {
        Network network = new Network(MockServer.get());

        assertFalse(network.getPacketPool(gameVersion).isNetEase());
        assertNull(network.getPacket(ProtocolInfo.PY_RPC_PACKET, gameVersion.getProtocol()));
        assertNull(network.getPacket(ProtocolInfo.PY_RPC_PACKET, gameVersion));
    }

    @ParameterizedTest(name = "PacketPool branch flag v{0}")
    @MethodSource("netEasePacketPoolVersions")
    void testPacketPoolNetEaseFlagDistinguishesSameProtocolBranches(GameVersion gameVersion) {
        Network network = new Network(MockServer.get());

        assertFalse(network.getPacketPool(gameVersion.getProtocol()).isNetEase());
        assertTrue(network.getPacketPool(gameVersion).isNetEase());
    }

    @ParameterizedTest(name = "setPacketPool rejects mismatched branch v{0}")
    @MethodSource("netEasePacketPoolVersions")
    void testSetPacketPoolRejectsMismatchedNetEaseFlag(GameVersion gameVersion) {
        Network network = new Network(MockServer.get());

        assertThrows(IllegalArgumentException.class,
                () -> network.setPacketPool(gameVersion, network.getPacketPool(gameVersion.getProtocol())));
    }

    @Test
    void testProtocolRegisterPacketNewAlsoUpdatesCurrentNetEasePacketPool() {
        Network network = new Network(MockServer.get());

        network.registerPacketNew(ProtocolInfo.v1_21_80, CUSTOM_PACKET_NEW_ID, CustomNewProtocolPacket.class);

        assertInstanceOf(CustomNewProtocolPacket.class,
                network.getPacket(CUSTOM_PACKET_NEW_ID, GameVersion.V1_21_80));
        assertInstanceOf(CustomNewProtocolPacket.class,
                network.getPacket(CUSTOM_PACKET_NEW_ID, GameVersion.V1_21_93_NETEASE));
    }

    @Test
    void testProtocolRegisterPacketAlsoUpdatesBaseNetEasePacketPool() {
        Network network = new Network(MockServer.get());
        int packetId = ProtocolInfo.toNewProtocolID(CUSTOM_PACKET_LEGACY_ID);

        network.registerPacket(ProtocolInfo.v1_2_0, CUSTOM_PACKET_LEGACY_ID, CustomLegacyProtocolPacket.class);

        assertInstanceOf(CustomLegacyProtocolPacket.class,
                network.getPacket(packetId, GameVersion.V1_2_0));
        assertInstanceOf(CustomLegacyProtocolPacket.class,
                network.getPacket(packetId, GameVersion.V1_20_50_NETEASE));
    }

    @Test
    void testExplicitInternationalGameVersionRegistrationDoesNotUpdateNetEasePacketPool() {
        Network network = new Network(MockServer.get());

        network.registerPacketNew(GameVersion.V1_21_93, CUSTOM_PACKET_NEW_ID, CustomExplicitProtocolPacket.class);

        assertInstanceOf(CustomExplicitProtocolPacket.class,
                network.getPacket(CUSTOM_PACKET_NEW_ID, GameVersion.V1_21_93));
        assertNull(network.getPacket(CUSTOM_PACKET_NEW_ID, GameVersion.V1_21_93_NETEASE));
    }

    @ParameterizedTest(name = "PyRpcPacket ModEventC2S sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesModEventSubPacket(int protocolVersion) {
        byte[] data = msgpackArray(
                msgpackString("ModEventC2S"),
                msgpackArray(
                        msgpackString("Minecraft"),
                        msgpackString("emote"),
                        msgpackString("PlayEmoteEvent"),
                        msgpackMap("animName", msgpackString("wave"))));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertEquals("Minecraft", subPacket.getModName());
        assertEquals("emote", subPacket.getSystemName());
        assertEquals("PlayEmoteEvent", subPacket.getEventName());
        assertEquals("wave", subPacket.getEventData().get("animName"));
    }

    @ParameterizedTest(name = "PyRpcPacket ModEventC2S nil eventData v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesModEventSubPacketWithNilEventData(int protocolVersion) {
        byte[] data = msgpackArray(
                msgpackString("ModEventC2S"),
                msgpackArray(
                        msgpackString("Minecraft"),
                        msgpackString("emote"),
                        msgpackString("PlayEmoteEvent"),
                        msgpackNil()));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertNull(nukkitPacket.getMessage().getArguments().get(3));
        assertTrue(subPacket.getEventData().isEmpty());
    }

    @ParameterizedTest(name = "PyRpcPacket wrapped ModEventC2S sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesWrappedModEventSubPacket(int protocolVersion) {
        byte[] data = msgpackMap("value", msgpackArray(
                msgpackString("ModEventC2S"),
                msgpackMap("value", msgpackArray(
                        msgpackString("Minecraft"),
                        msgpackString("emote"),
                        msgpackString("PlayEmoteEvent"),
                        msgpackMap("animName", msgpackString("wave"))))));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertEquals("Minecraft", subPacket.getModName());
        assertEquals("emote", subPacket.getSystemName());
        assertEquals("PlayEmoteEvent", subPacket.getEventName());
        assertEquals("wave", subPacket.getEventData().get("animName"));
    }

    @ParameterizedTest(name = "PyRpcPacket binary ModEventC2S sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesBinaryModEventSubPacket(int protocolVersion) {
        byte[] data = msgpackMap(msgpackBinary("value"), msgpackArray(
                msgpackBinary("ModEventC2S"),
                msgpackMap(msgpackBinary("value"), msgpackArray(
                        msgpackBinary("Minecraft"),
                        msgpackBinary("emote"),
                        msgpackBinary("PlayEmoteEvent"),
                        msgpackMap(msgpackBinary("animName"), msgpackBinary("wave"))))));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertEquals("Minecraft", subPacket.getModName());
        assertEquals("emote", subPacket.getSystemName());
        assertEquals("PlayEmoteEvent", subPacket.getEventName());
        assertEquals("wave", subPacket.getEventData().get("animName"));
    }

    @ParameterizedTest(name = "PyRpcPacket StoreBuySuccServerEvent sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesStoreBuySuccessSubPacket(int protocolVersion) {
        byte[] data = msgpackArray(msgpackString("StoreBuySuccServerEvent"));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        assertInstanceOf(cn.nukkit.network.protocol.netease.pyrpc.subpacket.StoreBuySuccessPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
    }

    @ParameterizedTest(name = "PyRpcPacket wrapped StoreBuySuccServerEvent sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesWrappedStoreBuySuccessSubPacket(int protocolVersion) {
        byte[] data = msgpackMap("value", msgpackArray(msgpackString("StoreBuySuccServerEvent")));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        assertInstanceOf(cn.nukkit.network.protocol.netease.pyrpc.subpacket.StoreBuySuccessPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
    }

    @ParameterizedTest(name = "PyRpcPacket custom sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesCustomSubPacket(int protocolVersion) {
        byte[] binaryArgument = new byte[]{1, 2, 3, 4};
        byte[] data = msgpackArray(
                msgpackString("CustomEngineCall"),
                msgpackArray(
                        msgpackString("alpha"),
                        msgpackBinary(binaryArgument)));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertEquals("CustomEngineCall", subPacket.getMethod());
        assertEquals("alpha", subPacket.getArguments().get(0));
        assertArrayEquals(binaryArgument, (byte[]) subPacket.getArguments().get(1));
        assertSame(subPacket, nukkitPacket.getMessage().getSubPacket());
    }

    @ParameterizedTest(name = "PyRpcPacket custom sub-packet nil argument v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesCustomSubPacketWithNilArgument(int protocolVersion) {
        byte[] data = msgpackArray(
                msgpackString("CustomEngineCall"),
                msgpackArray(
                        msgpackString("alpha"),
                        msgpackNil(),
                        msgpackString("omega")));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertEquals("CustomEngineCall", subPacket.getMethod());
        assertEquals("alpha", subPacket.getArguments().get(0));
        assertNull(subPacket.getArguments().get(1));
        assertEquals("omega", subPacket.getArguments().get(2));
    }

    @ParameterizedTest(name = "PyRpcPacket wrapped custom sub-packet v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketDecodesWrappedCustomSubPacket(int protocolVersion) {
        byte[] data = msgpackMap("value", msgpackArray(
                msgpackString("CustomEngineCall"),
                msgpackMap("value", msgpackArray(
                        msgpackString("alpha"),
                        msgpackString("beta")))));

        var nukkitPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var subPacket = assertInstanceOf(
                cn.nukkit.network.protocol.netease.pyrpc.subpacket.RawPyRpcSubPacket.class,
                nukkitPacket.getSubPacket());
        assertEquals("CustomEngineCall", subPacket.getMethod());
        assertEquals(List.of("alpha", "beta"), subPacket.getArguments());
    }

    @ParameterizedTest(name = "PyRpcPacket custom create v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketCreatesCustomPacket(int protocolVersion) {
        var nukkitPacket = cn.nukkit.network.protocol.netease.PyRpcPacket.createCustomPacket(
                "CustomEngineCall",
                List.of("alpha", 42),
                0x12345678L);
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                dev.mot.protocol.extension.packet.PyRpcPacket.class);

        assertEquals(0x12345678L, cbPacket.getMsgId());
        assertArrayEquals(msgpackArray(
                msgpackBinary("CustomEngineCall"),
                msgpackArray(msgpackBinary("alpha"), new byte[]{42}),
                msgpackNil()), cbPacket.getData());
    }

    @ParameterizedTest(name = "PyRpcPacket registered custom codec v{0}")
    @MethodSource("allNetEaseVersions")
    void testPyRpcPacketRegisteredCustomCodec(int protocolVersion) {
        cn.nukkit.network.protocol.netease.PyRpcPacket.registerSubPacketCodec(new RegisteredCustomSubPacketCodec());

        byte[] data = msgpackArray(
                msgpackString(RegisteredCustomSubPacket.METHOD),
                msgpackArray(msgpackString("payload")));

        var decodedPacket = decodePyRpcPacket(data, 0x12345678L, protocolVersion);

        var decodedSubPacket = assertInstanceOf(RegisteredCustomSubPacket.class,
                decodedPacket.getSubPacket());
        assertEquals("payload", decodedSubPacket.payload);

        var encodedPacket = cn.nukkit.network.protocol.netease.PyRpcPacket.createSubPacket(
                new RegisteredCustomSubPacket("response"),
                0x12345678L);
        prepareNetEasePacket(encodedPacket, protocolVersion);
        encodedPacket.encode();

        var cbPacket = crossDecodeNetEase(encodedPacket,
                dev.mot.protocol.extension.packet.PyRpcPacket.class);

        assertArrayEquals(msgpackArray(
                msgpackBinary(RegisteredCustomSubPacket.METHOD),
                msgpackArray(msgpackBinary("response")),
                msgpackNil()), cbPacket.getData());
    }

    @Test
    void testPyRpcProtocolSupportsIndependentRegistries() {
        cn.nukkit.network.protocol.netease.pyrpc.PyRpcCodecRegistry registry =
                new cn.nukkit.network.protocol.netease.pyrpc.PyRpcCodecRegistry();
        registry.register(new RegisteredCustomSubPacketCodec());
        cn.nukkit.network.protocol.netease.pyrpc.PyRpcProtocol protocol =
                new cn.nukkit.network.protocol.netease.pyrpc.PyRpcProtocol(registry);

        byte[] encoded = protocol.encode(new RegisteredCustomSubPacket("payload"));
        cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage message = protocol.decode(encoded);

        assertNotNull(message);
        var subPacket = assertInstanceOf(RegisteredCustomSubPacket.class, message.getSubPacket());
        assertEquals("payload", subPacket.payload);
    }

    // ==================== TextPacket NetEase CHAT ====================

    @ParameterizedTest(name = "TextPacket CHAT NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testTextPacketChatNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TextPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.type = cn.nukkit.network.protocol.TextPacket.TYPE_CHAT;
        nukkitPacket.source = "Player1";
        nukkitPacket.message = "Hello NetEase!";
        nukkitPacket.isLocalized = false;
        nukkitPacket.xboxUserId = "";
        nukkitPacket.platformChatId = "";
        nukkitPacket.unknownNE = "";
        if (protocolVersion >= 685) {
            nukkitPacket.filteredMessage = "";
        }
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TextPacket.class);

        assertEquals(1, cbPacket.getType().ordinal()); // CHAT
        assertEquals("Player1", cbPacket.getSourceName());
        assertEquals("Hello NetEase!", cbPacket.getMessage().toString());
    }

    // ==================== TextPacket NetEase RAW ====================

    @ParameterizedTest(name = "TextPacket RAW NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testTextPacketRawNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TextPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.type = cn.nukkit.network.protocol.TextPacket.TYPE_RAW;
        nukkitPacket.message = "Server message";
        nukkitPacket.isLocalized = false;
        nukkitPacket.xboxUserId = "";
        nukkitPacket.platformChatId = "";
        if (protocolVersion >= 685) {
            nukkitPacket.filteredMessage = "";
        }
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TextPacket.class);

        assertEquals(0, cbPacket.getType().ordinal()); // RAW
        assertEquals("Server message", cbPacket.getMessage().toString());
    }

    // ==================== Standard packets via NetEase codecs ====================

    @ParameterizedTest(name = "PlayStatusPacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testPlayStatusPacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PlayStatusPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.status = cn.nukkit.network.protocol.PlayStatusPacket.LOGIN_SUCCESS;
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.class);

        assertEquals(0, cbPacket.getStatus().ordinal());
    }

    @ParameterizedTest(name = "SetTimePacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testSetTimePacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetTimePacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.time = 6000;
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetTimePacket.class);

        assertEquals(6000, cbPacket.getTime());
    }

    @ParameterizedTest(name = "DisconnectPacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testDisconnectPacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.DisconnectPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.reason = cn.nukkit.network.protocol.types.DisconnectFailReason.UNKNOWN;
        nukkitPacket.hideDisconnectionScreen = false;
        nukkitPacket.message = "Server closed";
        if (protocolVersion >= 712) {
            nukkitPacket.filteredMessage = "";
        }
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket.class);

        assertEquals(0, cbPacket.getReason().ordinal());
        assertFalse(cbPacket.isMessageSkipped());
        assertEquals("Server closed", cbPacket.getKickMessage().toString());
    }

    @ParameterizedTest(name = "TransferPacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testTransferPacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TransferPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.address = "127.0.0.1";
        nukkitPacket.port = 19132;
        if (protocolVersion >= 729) {
            nukkitPacket.reloadWorld = true;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TransferPacket.class);

        assertEquals("127.0.0.1", cbPacket.getAddress());
        assertEquals(19132, cbPacket.getPort());
    }

    @ParameterizedTest(name = "SetDifficultyPacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testSetDifficultyPacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetDifficultyPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.difficulty = 2;
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket.class);

        assertEquals(2, cbPacket.getDifficulty());
    }

    @ParameterizedTest(name = "RemoveEntityPacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testRemoveEntityPacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.RemoveEntityPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        nukkitPacket.eid = 42;
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket.class);

        assertEquals(42, cbPacket.getUniqueEntityId());
    }

    @ParameterizedTest(name = "MovePlayerPacket NetEase v{0}")
    @MethodSource("allNetEaseVersions")
    void testMovePlayerPacketNetEase(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MovePlayerPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
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
        nukkitPacket.frame = 100;
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(0, cbPacket.getMode().ordinal());
        assertTrue(cbPacket.isOnGround());
    }

    public static final class CustomNewProtocolPacket extends DataPacket {
        @Override
        public byte pid() {
            return 0;
        }

        @Override
        public void decode() {
        }

        @Override
        public void encode() {
        }
    }

    public static final class CustomLegacyProtocolPacket extends DataPacket {
        @Override
        public byte pid() {
            return CUSTOM_PACKET_LEGACY_ID;
        }

        @Override
        public void decode() {
        }

        @Override
        public void encode() {
        }
    }

    public static final class CustomExplicitProtocolPacket extends DataPacket {
        @Override
        public byte pid() {
            return 0;
        }

        @Override
        public void decode() {
        }

        @Override
        public void encode() {
        }
    }

    private static final class RegisteredCustomSubPacket implements cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket {
        private static final String METHOD = "RegisteredCustomCall";

        private final String payload;

        private RegisteredCustomSubPacket(String payload) {
            this.payload = payload;
        }

        @Override
        public String getMethod() {
            return METHOD;
        }
    }

    private static final class RegisteredCustomSubPacketCodec
            implements cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacketCodec<RegisteredCustomSubPacket> {

        @Override
        public String getMethod() {
            return RegisteredCustomSubPacket.METHOD;
        }

        @Override
        public Class<RegisteredCustomSubPacket> getSubPacketClass() {
            return RegisteredCustomSubPacket.class;
        }

        @Override
        public RegisteredCustomSubPacket decode(cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage message) {
            if (message.getArguments().isEmpty()) {
                return null;
            }
            Object payload = message.getArguments().get(0);
            if (payload instanceof String string) {
                return new RegisteredCustomSubPacket(string);
            }
            if (payload instanceof byte[] bytes) {
                return new RegisteredCustomSubPacket(new String(bytes, StandardCharsets.UTF_8));
            }
            return null;
        }

        @Override
        public void encode(RegisteredCustomSubPacket packet, cn.nukkit.network.protocol.netease.pyrpc.io.PyRpcWriter writer) {
            writer.writeMessage(packet.getMethod(), List.of(packet.payload));
        }
    }
}
