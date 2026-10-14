package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.ProtocolCodecMapping;
import cn.nukkit.network.protocol.types.debugshape.DebugShape;
import cn.nukkit.network.protocol.types.debugshape.DebugLine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Native encoders, independent field meaning, whole frames and the following batch frame. */
class PacketFieldOrderRegressionTest {
    @BeforeAll static void setup() { MockServer.init(); }
    private static Stream<Integer> protocols() {
        return ProtocolInfo.SUPPORTED_PROTOCOLS.stream().distinct().filter(p -> p >= 589 && p <= 2193);
    }
    static Stream<Arguments> equipmentCases() {
        return protocols().flatMap(p -> Stream.of(0, 128).map(size -> Arguments.of(p, size)));
    }
    static Stream<Arguments> debugCases() {
        return protocols().filter(p -> p >= 818)
                .flatMap(p -> Stream.of(0, 1, 2, -1).map(count -> Arguments.of(p, count)));
    }
    static Stream<Arguments> loadingCases() {
        return protocols().filter(p -> p >= 712).flatMap(p -> Stream.of(0, 1, 2)
                .flatMap(type -> Stream.of(false, true).map(present -> Arguments.of(p, type, present))));
    }
    // Only these three packet layouts are aliased; this is not a general preview codec mapping.
    private static BedrockCodec codec(int p) {
        int reference = switch (p) {
            case 593 -> 594;
            case 617 -> 618;
            case 765 -> 748;
            case 785 -> 776;
            case 843 -> 827;
            case 897 -> 898;
            case 974 -> 975;
            default -> p;
        };
        return ProtocolCodecMapping.getCodec(reference);
    }
    @ParameterizedTest(name = "equipment v{0} size{1}") @MethodSource("equipmentCases")
    void equipmentSizePrecedesEntityAndNbt(int protocol, int size) throws Exception {
        var p = new UpdateEquipmentPacket(); p.windowId = 128; p.windowType = 15;
        p.unknown = size; p.eid = -127;
        p.namedtag = NBTIO.writeNetwork(new CompoundTag().putString("proof", "данные").putInt("marker", 128));
        var d = frame(p, protocol, org.cloudburstmc.protocol.bedrock.packet.UpdateEquipPacket.class);
        assertEquals(128, d.getWindowId()); assertEquals(15, d.getWindowType());
        assertEquals(size, d.getSize()); assertEquals(-127, d.getUniqueEntityId());
        assertNotNull(d.getTag()); assertEquals(2, d.getTag().size());
        assertEquals("данные", d.getTag().getString("proof")); assertEquals(128, d.getTag().getInt("marker"));
    }
    @ParameterizedTest(name = "debug v{0} count{1}") @MethodSource("debugCases")
    void debugRemovalIncludesAllLegacyOptionalFields(int protocol, int count) {
        var p = new DebugDrawerPacket();
        if (count < 0) p.shapes.add(new DebugLine(128, 0, new Vector3f(1, 2, 3),
                null, null, null, null, new Vector3f(4, 5, 6)));
        else for (int i = 0; i < count; i++) p.shapes.add(new DebugShape(128 + i));
        var d = frame(p, protocol, org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket.class);
        assertEquals(p.shapes.size(), d.getShapes().size());
        for (int i = 0; i < d.getShapes().size(); i++) {
            var shape = d.getShapes().get(i); assertEquals(128 + i, shape.getId());
            if (count >= 0) assertNull(shape.getType());
            else {
                assertEquals("LINE", shape.getType().name());
                assertEquals(4f, ((org.cloudburstmc.protocol.bedrock.data.debugshape.DebugLine) shape).getLineEndPosition().getX());
                assertEquals(5f, ((org.cloudburstmc.protocol.bedrock.data.debugshape.DebugLine) shape).getLineEndPosition().getY());
                assertEquals(6f, ((org.cloudburstmc.protocol.bedrock.data.debugshape.DebugLine) shape).getLineEndPosition().getZ());
            }
        }
    }
    @Test void loadingConstantsAreIndependentWireOrdinals() throws Exception {
        var c = ServerboundLoadingScreenPacket.class;
        assertEquals(0, c.getField("TYPE_UNKNOWN").getInt(null));
        assertEquals(1, c.getField("TYPE_START_LOADING_SCREEN").getInt(null));
        assertEquals(2, c.getField("TYPE_STOP_LOADING_SCREEN").getInt(null));
        assertEquals(2169, ProtocolCodecMapping.getCodec(2169).getProtocolVersion());
    }
    @ParameterizedTest(name = "loading v{0} type{1} id{2}") @MethodSource("loadingCases")
    void loadingConstantsHaveIndependentMeaningInBothDirections(int protocol, int type, boolean present) {
        var p = new ServerboundLoadingScreenPacket();
        p.loadingScreenType = switch (type) {
            case 0 -> ServerboundLoadingScreenPacket.TYPE_UNKNOWN;
            case 1 -> ServerboundLoadingScreenPacket.TYPE_START_LOADING_SCREEN;
            default -> ServerboundLoadingScreenPacket.TYPE_STOP_LOADING_SCREEN;
        };
        p.loadingScreenId = present ? 0x01020304 : null;
        var d = frame(p, protocol, org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket.class);
        assertEquals(type, d.getType().ordinal()); assertEquals(p.loadingScreenId, d.getLoadingScreenId());
        ByteBuf body = Unpooled.buffer();
        try {
            VarInts.writeInt(body, type); body.writeBoolean(present);
            if (present) body.writeIntLE(0x01020304);
            byte[] bytes = new byte[body.readableBytes()]; body.readBytes(bytes);
            var nativeInput = new ServerboundLoadingScreenPacket(); nativeInput.protocol = protocol;
            nativeInput.setBuffer(bytes, 0); nativeInput.decode();
            assertEquals(p.loadingScreenType, nativeInput.loadingScreenType);
            assertEquals(p.loadingScreenId, nativeInput.loadingScreenId);
        } finally { body.release(); }
    }
    private static <T extends BedrockPacket> T frame(DataPacket p, int protocol, Class<T> type) {
        p.protocol = protocol; p.gameVersion = GameVersion.byProtocol(protocol, false); p.encode();
        byte[] bytes = p.getBuffer(); p.encode(); assertArrayEquals(bytes, p.getBuffer());
        var next = new SetTimePacket(); next.protocol = protocol; next.gameVersion = p.gameVersion;
        next.time = 357911; next.encode();
        ByteBuf batch = Unpooled.buffer();
        try {
            VarInts.writeUnsignedInt(batch, bytes.length); batch.writeBytes(bytes);
            VarInts.writeUnsignedInt(batch, next.getBuffer().length); batch.writeBytes(next.getBuffer());
            ByteBuf first = batch.readSlice(VarInts.readUnsignedInt(batch));
            var cb = codec(protocol); var definition = cb.getPacketDefinition(type);
            assertNotNull(definition); assertEquals(definition.getId(), VarInts.readUnsignedInt(first));
            // A native encoder must produce a well-formed frame; malformed production bytes are a failure.
            T decoded = assertDoesNotThrow(() -> {
                T result = type.getDeclaredConstructor().newInstance();
                definition.getSerializer().deserialize(first, cb.createHelper(), result); return result;
            }, "native body must decode independently");
            assertFalse(first.isReadable(), "reference must consume the entire native frame");
            ByteBuf second = batch.readSlice(VarInts.readUnsignedInt(batch));
            assertEquals(ProtocolInfo.SET_TIME_PACKET & 0xff, VarInts.readUnsignedInt(second));
            assertEquals(357911, VarInts.readInt(second));
            assertFalse(second.isReadable()); assertFalse(batch.isReadable()); return decoded;
        } finally { batch.release(); }
    }
}
