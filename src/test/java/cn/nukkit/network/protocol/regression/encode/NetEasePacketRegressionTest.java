package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.Attribute;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.regression.PacketBridgeUtil;
import io.netty.buffer.ByteBuf;
import org.allaymc.protocol.extension.codec.v630.Bedrock_v630_NetEase;
import org.allaymc.protocol.extension.codec.v686.Bedrock_v686_NetEase;
import org.allaymc.protocol.extension.codec.v766.Bedrock_v766_NetEase;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for NetEase-specific packets and encoding branches.
 * Uses ProtocolExtension NetEase codecs (v630, v686, v766) to decode packets encoded by Nukkit-MOT.
 */
public class NetEasePacketRegressionTest {

    private static final Map<Integer, BedrockCodec> NETEASE_CODECS;
    private static final Map<Integer, GameVersion> NETEASE_GAME_VERSIONS;

    static {
        NETEASE_CODECS = new LinkedHashMap<>();
        NETEASE_CODECS.put(630, Bedrock_v630_NetEase.CODEC);
        NETEASE_CODECS.put(686, Bedrock_v686_NetEase.CODEC);
        NETEASE_CODECS.put(766, Bedrock_v766_NetEase.CODEC);

        NETEASE_GAME_VERSIONS = new LinkedHashMap<>();
        NETEASE_GAME_VERSIONS.put(630, GameVersion.V1_20_50_NETEASE);
        NETEASE_GAME_VERSIONS.put(686, GameVersion.V1_21_2_NETEASE);
        NETEASE_GAME_VERSIONS.put(766, GameVersion.V1_21_50_NETEASE);
    }

    @BeforeAll
    static void setUp() {
        MockServer.init();
        Attribute.init();
    }

    static Stream<Arguments> allNetEaseVersions() {
        return NETEASE_CODECS.keySet().stream().map(Arguments::of);
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

    // ==================== ConfirmSkinPacket ====================

    @ParameterizedTest(name = "ConfirmSkinPacket empty v{0}")
    @MethodSource("allNetEaseVersions")
    void testConfirmSkinPacketEmpty(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.netease.ConfirmSkinPacket();
        prepareNetEasePacket(nukkitPacket, protocolVersion);
        // Empty UUID list - no trailing uidStr/geoStr loops needed
        nukkitPacket.encode();

        var cbPacket = crossDecodeNetEase(nukkitPacket,
                org.allaymc.protocol.extension.packet.ConfirmSkinPacket.class);

        assertTrue(cbPacket.getEntries().isEmpty());
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
}
