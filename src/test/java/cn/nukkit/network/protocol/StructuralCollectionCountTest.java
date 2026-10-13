package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.network.protocol.netease.SyncSkinPacket;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Structural bounds reject impossible allocations without imposing a collection policy. */
class StructuralCollectionCountTest {
    private static final long HUGE_COUNT = 0x20000000L;

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    static Stream<GameVersion> cameraVersions() {
        return Arrays.stream(GameVersion.values())
                .filter(v -> !v.isNetEase() && v.getProtocol() >= ProtocolInfo.v1_26_0);
    }

    static Stream<GameVersion> windowVersions() {
        return Arrays.stream(GameVersion.values())
                .filter(v -> !v.isNetEase() && v.getProtocol() >= ProtocolInfo.v1_20_0);
    }

    static Stream<Arguments> impossibleCounts() {
        List<Arguments> cases = new ArrayList<>();
        List<Supplier<DataPacket>> simple = List.of(CameraSplinePacket::new,
                VoxelShapesPacket::new, CameraAimAssistActorPriorityPacket::new, SyncSkinPacket::new);
        for (Supplier<DataPacket> factory : simple) {
            BinaryStream wire = new BinaryStream();
            wire.putUnsignedVarInt(HUGE_COUNT);
            cases.add(Arguments.of(factory.get().getClass().getSimpleName(), factory, wire.getBuffer()));
        }
        BinaryStream texture = texturePrefix();
        texture.putUnsignedVarInt(HUGE_COUNT);
        cases.add(Arguments.of("texture steps", (Supplier<DataPacket>) ClientboundTextureShiftPacket::new,
                texture.getBuffer()));
        for (int field = 0; field < 3; field++) {
            BinaryStream wire = new BinaryStream();
            wire.putUnsignedVarInt(1);
            splinePrefix(wire);
            for (int earlier = 0; earlier < field; earlier++) wire.putUnsignedVarInt(0);
            wire.putUnsignedVarInt(HUGE_COUNT);
            cases.add(Arguments.of("spline nested " + field, (Supplier<DataPacket>) CameraSplinePacket::new,
                    wire.getBuffer()));
        }
        for (int field = 0; field < 4; field++) {
            BinaryStream wire = new BinaryStream();
            wire.putUnsignedVarInt(1);
            wire.put(new byte[3]);
            for (int earlier = 0; earlier < field; earlier++) wire.putUnsignedVarInt(0);
            wire.putUnsignedVarInt(HUGE_COUNT);
            cases.add(Arguments.of("voxel nested " + field, (Supplier<DataPacket>) VoxelShapesPacket::new,
                    wire.getBuffer()));
        }
        BinaryStream names = new BinaryStream();
        names.putUnsignedVarInt(0);
        names.putUnsignedVarInt(HUGE_COUNT);
        cases.add(Arguments.of("voxel names", (Supplier<DataPacket>) VoxelShapesPacket::new,
                names.getBuffer()));
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("impossibleCounts")
    void impossibleCountsFailBeforeAllocation(String name, Supplier<DataPacket> factory, byte[] wire) {
        DataPacket packet = packet(factory.get(), GameVersion.V1_26_50, wire);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, packet::decode);
        assertTrue(error.getMessage().contains("exceeds remaining payload"));
    }

    @Test
    void allocatedBufferCapacityIsNotReadablePayload() {
        CameraSplinePacket packet = new CameraSplinePacket();
        BinaryStream wire = new BinaryStream();
        wire.putUnsignedVarInt(100);
        packet.setBuffer(Arrays.copyOf(wire.getBuffer(), 200));
        packet.setCount(wire.getCount());
        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @Test
    void unsignedCountCannotNarrowToNegativeEvenWithTrailingData() {
        BinaryStream wire = new BinaryStream();
        wire.putUnsignedVarInt(0xFFFFFFFFL);
        wire.put(new byte[32]);
        CameraSplinePacket packet = packet(new CameraSplinePacket(), GameVersion.V1_26_50, wire.getBuffer());
        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    @ParameterizedTest
    @MethodSource("cameraVersions")
    void validSplinesAboveFormerTopAndNestedCeilingsDecode(GameVersion version) {
        BinaryStream wire = new BinaryStream();
        wire.putUnsignedVarInt(1025);
        for (int spline = 0; spline < 1025; spline++) {
            splinePrefix(wire);
            int nested = spline == 0 ? 4097 : 0;
            wire.putUnsignedVarInt(nested);
            wire.put(new byte[nested * 12]);
            wire.putUnsignedVarInt(nested);
            for (int i = 0; i < nested; i++) {
                wire.put(new byte[8]);
                wire.putString("linear");
            }
            wire.putUnsignedVarInt(nested);
            for (int i = 0; i < nested; i++) {
                wire.put(new byte[16]);
                wire.putString("linear");
            }
            if (version.getProtocol() >= ProtocolInfo.v1_26_10) {
                wire.putString("");
                wire.putBoolean(false);
            }
        }
        CameraSplinePacket packet = packet(new CameraSplinePacket(), version, wire.getBuffer());
        packet.decode();
        assertEquals(1025, packet.splines.size());
        var first = packet.splines.get(0).getInstruction();
        assertEquals(4097, first.getCurve().size());
        assertEquals(4097, first.getProgressKeyFrames().size());
        assertEquals(4097, first.getRotationOption().size());
        assertEquals(packet.getCount(), packet.getOffset());
    }

    @ParameterizedTest
    @MethodSource("cameraVersions")
    void validVoxelShapesAndNamesAboveFormerCeilingsDecode(GameVersion version) {
        BinaryStream wire = new BinaryStream();
        wire.putUnsignedVarInt(4097);
        for (int shape = 0; shape < 4097; shape++) {
            wire.put(new byte[3]);
            int storage = shape == 0 ? 4097 : 0;
            wire.putUnsignedVarInt(storage);
            wire.put(new byte[storage]);
            for (int axis = 0; axis < 3; axis++) {
                int coordinates = shape == 0 ? 257 : 0;
                wire.putUnsignedVarInt(coordinates);
                wire.put(new byte[coordinates * 4]);
            }
        }
        wire.putUnsignedVarInt(4097);
        for (int i = 0; i < 4097; i++) {
            wire.putString("shape" + i);
            wire.putLShort(i);
        }
        if (version.getProtocol() >= ProtocolInfo.v1_26_10) wire.putLShort(0);
        VoxelShapesPacket packet = packet(new VoxelShapesPacket(), version, wire.getBuffer());
        packet.decode();
        assertEquals(4097, packet.shapes.size());
        assertEquals(4097, packet.nameMap.size());
        var first = packet.shapes.get(0);
        assertEquals(4097, first.getCells().getStorage().size());
        assertEquals(257, first.getXCoordinates().size());
        assertEquals(257, first.getYCoordinates().size());
        assertEquals(257, first.getZCoordinates().size());
        assertEquals(packet.getCount(), packet.getOffset());
    }

    @ParameterizedTest
    @MethodSource("cameraVersions")
    void validClientboundCollectionsAboveFormerCeilingsDecode(GameVersion version) {
        BinaryStream texture = texturePrefix();
        texture.putUnsignedVarInt(4097);
        for (int i = 0; i < 4097; i++) texture.putString("");
        texture.putUnsignedVarLong(0);
        texture.putUnsignedVarLong(0);
        texture.putBoolean(false);
        ClientboundTextureShiftPacket shifts = packet(new ClientboundTextureShiftPacket(), version,
                texture.getBuffer());
        shifts.decode();
        assertEquals(4097, shifts.allSteps.length);
        assertEquals(shifts.getCount(), shifts.getOffset());
        BinaryStream priority = new BinaryStream();
        priority.putUnsignedVarInt(4097);
        priority.put(new byte[4097 * 16]);
        CameraAimAssistActorPriorityPacket priorities = packet(new CameraAimAssistActorPriorityPacket(),
                version, priority.getBuffer());
        priorities.decode();
        assertEquals(4097, priorities.priorities.size());
        assertEquals(priorities.getCount(), priorities.getOffset());
    }

    static Stream<GameVersion> netEaseVersions() {
        return Stream.of(GameVersion.V1_21_93_NETEASE, GameVersion.V1_21_124_NETEASE);
    }

    @ParameterizedTest
    @MethodSource("netEaseVersions")
    void validNetEaseEntriesAboveFormerCeilingDecode(GameVersion version) {
        BinaryStream wire = new BinaryStream();
        wire.putUnsignedVarInt(1025);
        for (int i = 0; i < 1025; i++) {
            wire.putBoolean(false);
            wire.putUUID(new UUID(0, i));
            wire.putByteArray(new byte[0]);
        }
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 1025; i++) wire.putString("");
        }
        Skin skin = new Skin();
        skin.setSkinId("structural-test");
        skin.setSkinData(new byte[64 * 32 * 4]);
        wire.putSkin(version, skin);
        SyncSkinPacket packet = packet(new SyncSkinPacket(), version, wire.getBuffer());
        packet.decode();
        assertEquals(1025, packet.entries.size());
        assertEquals(new UUID(0, 1024), packet.entries.get(1024).uuid);
        assertEquals(packet.getCount(), packet.getOffset());
    }

    @ParameterizedTest
    @MethodSource("windowVersions")
    void subchunkExistingAcceptedBoundariesStayUnchanged(GameVersion version) {
        int count = version.getProtocol() >= ProtocolInfo.v1_26_30 ? 8192 : 8193;
        BinaryStream wire = new BinaryStream();
        wire.putVarInt(0);
        if (version.getProtocol() >= ProtocolInfo.v1_26_30) {
            wire.putUnsignedVarInt(count);
            wire.put(new byte[count * 3]);
            wire.put(new byte[12]);
        } else {
            wire.putSignedBlockPosition(new cn.nukkit.math.BlockVector3(0, 0, 0));
            wire.putLInt(count);
            wire.put(new byte[count * 3]);
        }
        SubChunkRequestPacket packet = packet(new SubChunkRequestPacket(), version, wire.getBuffer());
        packet.decode();
        assertEquals(count, packet.positionOffsets.size());
        assertEquals(packet.getCount(), packet.getOffset());
    }

    static Stream<GameVersion> legacySubchunkVersions() {
        return windowVersions().filter(v -> v.getProtocol() < ProtocolInfo.v1_26_30);
    }

    @ParameterizedTest
    @MethodSource("legacySubchunkVersions")
    void legacySubchunkRejectsCountsThatCannotFitFixedWidthOffsets(GameVersion version) {
        for (int count : new int[] {-1, 2, Integer.MAX_VALUE}) {
            BinaryStream wire = new BinaryStream();
            wire.putVarInt(0);
            wire.putSignedBlockPosition(new cn.nukkit.math.BlockVector3(0, 0, 0));
            wire.putLInt(count);
            wire.put(new byte[5]); // At most one complete three-byte offset fits.
            SubChunkRequestPacket packet = packet(new SubChunkRequestPacket(), version, wire.getBuffer());
            assertThrows(IllegalArgumentException.class, packet::decode);
            assertTrue(packet.positionOffsets.isEmpty());
        }
    }

    @Test
    void modernSubchunkRejectsUnsignedOverflowBeforeNarrowing() {
        BinaryStream wire = new BinaryStream();
        wire.putVarInt(0);
        wire.putUnsignedVarInt(0xFFFFFFFFL);
        wire.put(new byte[12]);
        SubChunkRequestPacket packet = packet(new SubChunkRequestPacket(), GameVersion.V1_26_50,
                wire.getBuffer());
        assertThrows(IllegalArgumentException.class, packet::decode);
    }

    private static void splinePrefix(BinaryStream wire) {
        wire.putString("");
        wire.putLFloat(0);
        wire.putString("catmullrom");
    }

    private static BinaryStream texturePrefix() {
        BinaryStream wire = new BinaryStream();
        wire.putByte((byte) ClientboundTextureShiftPacket.ACTION_INITIALIZE);
        wire.putString("");
        wire.putString("");
        wire.putString("");
        return wire;
    }

    private static <T extends DataPacket> T packet(T packet, GameVersion version, byte[] wire) {
        packet.protocol = version.getProtocol();
        packet.gameVersion = version;
        packet.setBuffer(wire);
        return packet;
    }
}
