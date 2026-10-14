package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema reader, independent of BinaryStream and Cloudburst's ability flag mapping.
 * Protocol 776 added vertical speed between fly and walk; older layers contain two floats.
 *
 * References:
 * https://mojang.github.io/bedrock-protocol-docs/1.26.50/packets/add-player-packet/
 * https://mojang.github.io/bedrock-protocol-docs/changelog/legacy/entries/changelog-776-1-14-25/
 * CloudburstMC/Protocol 4128f334: BedrockCodecHelper_v534 and BedrockCodecHelper_v776.
 */
class AddPlayerAbilitiesWireBoundaryTest {
    private static final UUID UUID_SENTINEL = UUID.fromString("13579bdf-2468-ace0-0246-8ace13579bdf");
    private static final long UNIQUE_ID = 0x0123456789abcdefL;
    private static final long RUNTIME_ID = 0x123456789L;
    private static final String DEVICE_ID = "wire-device-\u03bb-\u2603";

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    static Stream<GameVersion> supportedVersions() {
        // Include both 2168 aliases: their GameVersion objects can select different item codecs.
        return Arrays.stream(GameVersion.values())
                .filter(version -> !version.isNetEase())
                .filter(version -> version.getProtocol() >= 589);
    }

    @ParameterizedTest(name = "AddPlayer abilities schema {0}")
    @MethodSource("supportedVersions")
    void encodedLayerMatchesVersionedSchemaAndKeepsSuffixAligned(GameVersion version) {
        AddPlayerPacket packet = new AddPlayerPacket();
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        packet.uuid = UUID_SENTINEL;
        packet.username = "schema-player";
        packet.entityUniqueId = UNIQUE_ID;
        packet.entityRuntimeId = RUNTIME_ID;
        packet.platformChatId = "chat-sentinel";
        packet.x = 12.25f;
        packet.y = -8.5f;
        packet.z = 31.75f;
        packet.speedX = 0.25f;
        packet.speedY = -0.5f;
        packet.speedZ = 0.75f;
        packet.pitch = 10.5f;
        packet.yaw = 20.25f;
        packet.headYaw = 30.75f;
        packet.item = Item.AIR_ITEM;
        packet.gameType = 1;
        packet.metadata = new EntityMetadata();
        packet.deviceId = DEVICE_ID;
        packet.buildPlatform = 8;
        packet.encode();

        SchemaReader wire = new SchemaReader(packet.getBuffer());
        assertEquals(12, wire.varUInt(), "packet id");
        assertEquals(UUID_SENTINEL.getMostSignificantBits(), wire.i64(), "UUID MSB");
        assertEquals(UUID_SENTINEL.getLeastSignificantBits(), wire.i64(), "UUID LSB");
        assertEquals("schema-player", wire.string());
        assertEquals(RUNTIME_ID, wire.varUInt(), "runtime id");
        assertEquals("chat-sentinel", wire.string());
        for (float value : new float[]{12.25f, -8.5f, 31.75f, 0.25f, -0.5f, 0.75f,
                10.5f, 20.25f, 30.75f}) {
            assertEquals(Float.floatToIntBits(value), wire.i32(), "position/rotation framing");
        }
        if (version.getProtocol() >= 2168) {
            // Cereal NetworkItemStackDescriptor: Cloudburst helper v2168 reads this AIR fixture
            // as id/count uint16, aux varuint, optional stack id, block id and user-data length.
            assertEquals(0, wire.u16(), "empty carried item runtime id");
            assertEquals(0, wire.u16(), "empty carried item count");
            assertEquals(0, wire.varUInt(), "empty carried item aux");
            assertEquals(0, wire.u8(), "absent carried item stack id");
            assertEquals(0, wire.varUInt(), "empty carried item block id");
            assertEquals(0, wire.varUInt(), "empty carried item user-data length");
        } else {
            assertEquals(0, wire.varUInt(), "empty carried item id");
        }
        assertEquals(2, wire.varUInt(), "game type 1 encoded as zigzag varint");
        assertEquals(0, wire.varUInt(), "empty metadata count");
        assertEquals(0, wire.varUInt(), "empty integer properties count");
        assertEquals(0, wire.varUInt(), "empty float properties count");
        assertEquals(UNIQUE_ID, wire.i64(), "ability player id");
        assertEquals(0, wire.u8(), "player permission is a single byte");
        assertEquals(0, wire.u8(), "command permission is a single byte");
        assertEquals(1, wire.varUInt(), "one ability layer");
        assertEquals(1, wire.u16(), "BASE layer");
        assertEquals(262143, wire.i32(), "ability set flags");
        assertEquals(63, wire.i32(), "survival ability values");

        int layerFloatsStart = wire.position();
        assertEquals(Float.floatToIntBits(0.05f), wire.i32(), "fly speed");
        // Literal boundary comes from Mojang changelog 776, not the encoder's ProtocolInfo gate.
        if (version.getProtocol() >= 776) {
            assertEquals(Float.floatToIntBits(1.0f), wire.i32(), "vertical fly speed");
        }
        assertEquals(Float.floatToIntBits(0.1f), wire.i32(), "walk speed");
        assertEquals(version.getProtocol() >= 776 ? 12 : 8,
                wire.position() - layerFloatsStart, "versioned layer float width");

        assertEquals(0, wire.varUInt(), "actor links immediately follow the layer");
        assertEquals(DEVICE_ID, wire.string(), "device sentinel catches missing/extra layer floats");
        assertEquals(8, wire.i32(), "build platform sentinel");
        assertEquals(0, wire.remaining(), "entire packet consumed, including the suffix");
    }

    private static final class SchemaReader {
        private final ByteBuffer bytes;

        SchemaReader(byte[] encoded) {
            bytes = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        }

        int u8() {
            assertTrue(bytes.hasRemaining(), "truncated schema field");
            return Byte.toUnsignedInt(bytes.get());
        }

        int u16() {
            return Short.toUnsignedInt(bytes.getShort());
        }

        int i32() {
            return bytes.getInt();
        }

        long i64() {
            return bytes.getLong();
        }

        long varUInt() {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                int next = u8();
                value |= (long) (next & 0x7f) << shift;
                if ((next & 0x80) == 0) return value;
            }
            throw new AssertionError("overlong schema varuint");
        }

        String string() {
            long length = varUInt();
            assertTrue(length <= bytes.remaining(), "string length exceeds packet remainder");
            byte[] utf8 = new byte[(int) length];
            bytes.get(utf8);
            return new String(utf8, StandardCharsets.UTF_8);
        }

        int position() {
            return bytes.position();
        }

        int remaining() {
            return bytes.remaining();
        }
    }
}
