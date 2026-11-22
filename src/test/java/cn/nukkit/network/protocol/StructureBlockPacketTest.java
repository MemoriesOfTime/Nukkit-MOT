package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.cloudburstmc.protocol.bedrock.data.structure.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结构方块三个包与 Cloudburst 参考实现的逐版本双向对拍。
 * <p>
 * Cross-checks the three structure block packets against the Cloudburst reference
 * codec in both directions, for every supported protocol version.
 */
class StructureBlockPacketTest extends AbstractPacketRegressionTest {

    /**
     * 结构模板数据请求/响应包自 1.12（v361）起才存在。
     * <p>
     * The structure template data request/response packets only exist since 1.12 (v361).
     */
    static Stream<Arguments> requestVersions() {
        return filteredVersions(ProtocolInfo.v1_12_0);
    }

    private static StructureBlockUpdatePacket updatePacket(int protocol) {
        StructureBlockUpdatePacket packet = new StructureBlockUpdatePacket();
        packet.protocol = protocol;
        packet.gameVersion = GameVersion.byProtocol(protocol, false);
        packet.x = 10;
        packet.y = 64;
        packet.z = -20;
        packet.name = "demo:house";
        packet.filteredName = "demo:house";
        packet.dataField = "field";
        packet.includingPlayers = true;
        packet.boundingBoxVisible = true;
        packet.type = 2;
        cn.nukkit.network.protocol.types.structure.StructureSettings settings = packet.settings;
        settings.paletteName = "demo";
        settings.ignoringEntities = true;
        settings.ignoringBlocks = false;
        settings.nonTickingPlayersAndTickingAreasEnabled = false;
        settings.sizeX = 7;
        settings.sizeY = 9;
        settings.sizeZ = 11;
        settings.offsetX = -1;
        settings.offsetY = -2;
        settings.offsetZ = 3;
        settings.lastEditedByEntityId = 12345L;
        settings.rotation = 3;
        settings.mirror = 2;
        settings.animationMode = 1;
        settings.animationSeconds = 2.5f;
        settings.integrity = 42.5f;
        settings.integritySeed = 987654;
        settings.pivotX = 1.5f;
        settings.pivotY = 2.5f;
        settings.pivotZ = 3.5f;
        packet.redstoneSaveMode = 1;
        packet.powered = true;
        packet.waterlogged = true;
        return packet;
    }

    @ParameterizedTest(name = "update encode v{0}")
    @MethodSource("allVersions")
    void updateEncodesLikeReference(int protocol) {
        StructureBlockUpdatePacket packet = updatePacket(protocol);
        packet.tryEncode();
        org.cloudburstmc.protocol.bedrock.packet.StructureBlockUpdatePacket cb =
                crossDecode(packet, org.cloudburstmc.protocol.bedrock.packet.StructureBlockUpdatePacket.class);

        assertEquals(10, cb.getBlockPosition().getX());
        assertEquals(64, cb.getBlockPosition().getY());
        assertEquals(-20, cb.getBlockPosition().getZ());
        assertEquals("demo:house", cb.getEditorData().getName());
        assertEquals(packet.type, cb.getEditorData().getType().ordinal());
        assertEquals(7, cb.getEditorData().getSettings().getSize().getX());
        assertEquals(9, cb.getEditorData().getSettings().getSize().getY());
        assertEquals(11, cb.getEditorData().getSettings().getSize().getZ());
        assertEquals(-1, cb.getEditorData().getSettings().getOffset().getX());
        assertEquals(-2, cb.getEditorData().getSettings().getOffset().getY());
        assertEquals(3, cb.getEditorData().getSettings().getOffset().getZ());
        assertEquals(42.5f, cb.getEditorData().getSettings().getIntegrityValue());
        assertEquals(987654, cb.getEditorData().getSettings().getIntegritySeed());
        assertEquals(2, cb.getEditorData().getSettings().getMirror().ordinal());
        assertEquals(3, cb.getEditorData().getSettings().getRotation().ordinal());
        assertEquals(true, cb.getEditorData().isIncludingPlayers());
        assertEquals(true, cb.getEditorData().isBoundingBoxVisible());
        assertEquals(true, cb.isPowered());
        if (protocol >= ProtocolInfo.v1_19_30) {
            assertEquals(true, cb.isWaterlogged());
        }
        if (protocol >= ProtocolInfo.v1_21_60) {
            assertEquals("demo:house", cb.getEditorData().getFilteredName());
        }
        if (protocol >= ProtocolInfo.v1_17_0) {
            assertEquals(1, cb.getEditorData().getSettings().getAnimationMode().ordinal());
            assertEquals(2.5f, cb.getEditorData().getSettings().getAnimationSeconds());
        }
        if (protocol >= ProtocolInfo.v1_13_0) {
            assertEquals(1, cb.getEditorData().getRedstoneSaveMode().ordinal());
            assertEquals(1.5f, cb.getEditorData().getSettings().getPivot().getX());
        }
    }

    @ParameterizedTest(name = "update decode v{0}")
    @MethodSource("allVersions")
    void updateDecodesReference(int protocol) {
        StructureSettings settings = new StructureSettings("demo", true, false, false,
                org.cloudburstmc.math.vector.Vector3i.from(7, 9, 11),
                org.cloudburstmc.math.vector.Vector3i.from(-1, -2, 3),
                12345L, StructureRotation.ROTATE_270, StructureMirror.Z,
                StructureAnimationMode.LAYER, 2.5f, 42.5f, 987654,
                org.cloudburstmc.math.vector.Vector3f.from(1.5f, 2.5f, 3.5f));
        StructureEditorData editorData = new StructureEditorData("demo:house", "demo:house", "field",
                true, true, StructureBlockType.LOAD, settings, StructureRedstoneSaveMode.SAVES_TO_DISK);
        org.cloudburstmc.protocol.bedrock.packet.StructureBlockUpdatePacket cb =
                new org.cloudburstmc.protocol.bedrock.packet.StructureBlockUpdatePacket();
        cb.setBlockPosition(org.cloudburstmc.math.vector.Vector3i.from(10, 64, -20));
        cb.setEditorData(editorData);
        cb.setPowered(true);
        cb.setWaterlogged(true);

        StructureBlockUpdatePacket decoded = crossEncode(cb, StructureBlockUpdatePacket::new, protocol);
        assertEquals(10, decoded.x);
        assertEquals(64, decoded.y);
        assertEquals(-20, decoded.z);
        assertEquals("demo:house", decoded.name);
        // 1.7/1.10 的扁平布局只有两个名字字段，第二个被客户端当作数据字段回显
        // The 1.7/1.10 flat layout only carries the name twice, so the data field echoes it.
        assertEquals(protocol >= ProtocolInfo.v1_12_0 ? "field" : "demo:house", decoded.dataField);
        assertEquals(StructureBlockType.LOAD.ordinal(), decoded.type);
        assertEquals(7, decoded.settings.sizeX);
        assertEquals(9, decoded.settings.sizeY);
        assertEquals(11, decoded.settings.sizeZ);
        assertEquals(-1, decoded.settings.offsetX);
        assertEquals(-2, decoded.settings.offsetY);
        assertEquals(3, decoded.settings.offsetZ);
        assertEquals(42.5f, decoded.settings.integrity);
        assertEquals(987654, decoded.settings.integritySeed);
        assertEquals(StructureMirror.Z.ordinal(), decoded.settings.mirror);
        assertEquals(StructureRotation.ROTATE_270.ordinal(), decoded.settings.rotation);
        assertEquals(true, decoded.includingPlayers);
        assertEquals(true, decoded.boundingBoxVisible);
        assertEquals(true, decoded.powered);
        if (protocol >= ProtocolInfo.v1_19_30) {
            assertEquals(true, decoded.waterlogged);
        }
        if (protocol >= ProtocolInfo.v1_17_0) {
            assertEquals(StructureAnimationMode.LAYER.ordinal(), decoded.settings.animationMode);
            assertEquals(2.5f, decoded.settings.animationSeconds);
        }
        if (protocol >= ProtocolInfo.v1_13_0) {
            assertEquals(StructureRedstoneSaveMode.SAVES_TO_DISK.ordinal(), decoded.redstoneSaveMode);
            assertEquals(1.5f, decoded.settings.pivotX);
        }
    }

    @ParameterizedTest(name = "request decode v{0}")
    @MethodSource("requestVersions")
    void requestDecodesReference(int protocol) {
        StructureSettings settings = new StructureSettings("demo", false, true, true,
                org.cloudburstmc.math.vector.Vector3i.from(4, 5, 6),
                org.cloudburstmc.math.vector.Vector3i.from(0, -1, 0),
                777L, StructureRotation.ROTATE_90, StructureMirror.X,
                StructureAnimationMode.NONE, 0f, 100f, 0,
                org.cloudburstmc.math.vector.Vector3f.ZERO);
        org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataRequestPacket cb =
                new org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataRequestPacket();
        cb.setName("demo:house");
        cb.setPosition(org.cloudburstmc.math.vector.Vector3i.from(1, 2, 3));
        cb.setSettings(settings);
        cb.setOperation(StructureTemplateRequestOperation.EXPORT_FROM_SAVED_MODE);

        StructureTemplateDataRequestPacket decoded =
                crossEncode(cb, StructureTemplateDataRequestPacket::new, protocol);
        assertEquals("demo:house", decoded.name);
        assertEquals(1, decoded.x);
        assertEquals(2, decoded.y);
        assertEquals(3, decoded.z);
        assertEquals(4, decoded.settings.sizeX);
        assertEquals(5, decoded.settings.sizeY);
        assertEquals(6, decoded.settings.sizeZ);
        assertEquals(-1, decoded.settings.offsetY);
        assertEquals(StructureTemplateRequestOperation.EXPORT_FROM_SAVED_MODE.ordinal(), decoded.operation);
    }

    @ParameterizedTest(name = "response encode v{0}")
    @MethodSource("requestVersions")
    void responseEncodesLikeReference(int protocol) {
        StructureTemplateDataResponsePacket packet = new StructureTemplateDataResponsePacket();
        packet.protocol = protocol;
        packet.gameVersion = GameVersion.byProtocol(protocol, false);
        packet.name = "demo:house";
        packet.save = true;
        // 用真实 v1 模板形状（两层 int 列表 + 调色板），确保网络 NBT 里的层编码也能被参考实现解析
        // Use a real v1 template shape (two int-list layers plus a palette) so the reference
        // implementation also parses the layer encoding we put on the wire.
        packet.tag = new CompoundTag()
                .putInt("format_version", 1)
                .putCompound("structure", new CompoundTag()
                        .putList("block_indices", new ListTag<ListTag<IntTag>>()
                                .add(new ListTag<IntTag>()
                                        .add(new IntTag(0))
                                        .add(new IntTag(1)))
                                .add(new ListTag<IntTag>()
                                        .add(new IntTag(-1))
                                        .add(new IntTag(-1))))
                        .putCompound("palette", new CompoundTag().putCompound("default", new CompoundTag()
                                .putList("block_palette", new ListTag<CompoundTag>()
                                        .add(new CompoundTag().putString("name", "minecraft:stone"))))));
        packet.type = StructureTemplateDataResponsePacket.RESPONSE_EXPORT;
        packet.tryEncode();

        org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataResponsePacket cb = crossDecode(
                packet, org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataResponsePacket.class);
        assertEquals("demo:house", cb.getName());
        assertEquals(true, cb.isSave());
        org.cloudburstmc.nbt.NbtMap tag = cb.getTag();
        assertEquals(1, tag.getInt("format_version"));
        java.util.List<org.cloudburstmc.nbt.NbtList> layers = tag.getCompound("structure")
                .getList("block_indices", org.cloudburstmc.nbt.NbtType.LIST);
        assertEquals(2, layers.size(), "v1 templates carry both int-list layers");
        assertEquals(2, layers.get(0).size());
        assertEquals(0, layers.get(0).get(0));
        assertEquals(-1, layers.get(1).get(0));
        assertEquals("minecraft:stone", tag.getCompound("structure").getCompound("palette")
                .getCompound("default").getList("block_palette", org.cloudburstmc.nbt.NbtType.COMPOUND)
                .get(0).getString("name"));
        if (protocol >= ProtocolInfo.v1_13_0) {
            assertEquals(StructureTemplateDataResponsePacket.RESPONSE_EXPORT, cb.getType().ordinal());
        }
    }
}
