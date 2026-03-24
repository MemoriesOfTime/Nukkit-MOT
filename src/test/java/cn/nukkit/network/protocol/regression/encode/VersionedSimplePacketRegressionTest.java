package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.DisplaySlot;
import cn.nukkit.network.protocol.types.SortOrder;
import cn.nukkit.network.protocol.types.camera.*;
import cn.nukkit.network.protocol.types.voxel.SerializableVoxelShape;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cross-decode regression tests for simple packets with minimum protocol version requirements.
 */
public class VersionedSimplePacketRegressionTest extends AbstractPacketRegressionTest {

    // --- Version Sources ---

    static Stream<Arguments> versionsFrom527() {
        return filteredVersions(ProtocolInfo.v1_19_0);
    }

    static Stream<Arguments> versionsFrom388() {
        // TickSyncPacket was removed from CB Protocol codecs starting at v685
        return filteredVersionsRange(ProtocolInfo.v1_13_0, ProtocolInfo.v1_21_0);
    }

    static Stream<Arguments> versionsFrom428() {
        return filteredVersions(ProtocolInfo.v1_16_210);
    }

    static Stream<Arguments> versionsFrom448() {
        return filteredVersions(ProtocolInfo.v1_17_10);
    }

    // --- Tests ---

    @ParameterizedTest(name = "ToastRequestPacket v{0}")
    @MethodSource("versionsFrom527")
    void testToastRequestPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ToastRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.title = "Achievement";
        nukkitPacket.content = "You got it!";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket.class);

        assertEquals("Achievement", cbPacket.getTitle());
        assertEquals("You got it!", cbPacket.getContent());
    }

    @ParameterizedTest(name = "TickSyncPacket v{0}")
    @MethodSource("versionsFrom388")
    void testTickSyncPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.TickSyncPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setRequestTimestamp(12345L);
        nukkitPacket.setResponseTimestamp(12346L);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TickSyncPacket.class);

        assertEquals(12345L, cbPacket.getRequestTimestamp());
        assertEquals(12346L, cbPacket.getResponseTimestamp());
    }

    @ParameterizedTest(name = "CameraShakePacket v{0}")
    @MethodSource("versionsFrom428")
    void testCameraShakePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraShakePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.intensity = 0.5f;
        nukkitPacket.duration = 1.0f;
        nukkitPacket.shakeType = cn.nukkit.network.protocol.CameraShakePacket.CameraShakeType.POSITIONAL;
        nukkitPacket.shakeAction = cn.nukkit.network.protocol.CameraShakePacket.CameraShakeAction.ADD;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraShakePacket.class);

        assertEquals(0.5f, cbPacket.getIntensity(), 0.001f);
        assertEquals(1.0f, cbPacket.getDuration(), 0.001f);
        assertEquals(0, cbPacket.getShakeType().ordinal());
        assertEquals(0, cbPacket.getShakeAction().ordinal());
    }

    @ParameterizedTest(name = "NPCDialoguePacket v{0}")
    @MethodSource("versionsFrom448")
    void testNPCDialoguePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.NPCDialoguePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setUniqueEntityId(100L);
        nukkitPacket.setAction(cn.nukkit.network.protocol.NPCDialoguePacket.Action.OPEN);
        nukkitPacket.setDialogue("Hello!");
        nukkitPacket.setSceneName("scene1");
        nukkitPacket.setNpcName("Villager");
        nukkitPacket.setActionJson("{}");
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.NpcDialoguePacket.class);

        assertEquals(100L, cbPacket.getUniqueEntityId());
        assertEquals(0, cbPacket.getAction().ordinal());
        assertEquals("Hello!", cbPacket.getDialogue());
        assertEquals("scene1", cbPacket.getSceneName());
        assertEquals("Villager", cbPacket.getNpcName());
        assertEquals("{}", cbPacket.getActionJson());
    }

    @ParameterizedTest(name = "SetDisplayObjectivePacket v{0}")
    @MethodSource("allVersions")
    void testSetDisplayObjectivePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SetDisplayObjectivePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.displaySlot = DisplaySlot.SIDEBAR;
        nukkitPacket.objectiveId = "obj1";
        nukkitPacket.displayName = "Test Objective";
        nukkitPacket.criteria = "dummy";
        nukkitPacket.sortOrder = SortOrder.ASCENDING;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetDisplayObjectivePacket.class);

        assertEquals("sidebar", cbPacket.getDisplaySlot());
        assertEquals("obj1", cbPacket.getObjectiveId());
        assertEquals("Test Objective", cbPacket.getDisplayName());
        assertEquals("dummy", cbPacket.getCriteria());
        assertEquals(0, cbPacket.getSortOrder());
    }

    // ==================== PositionTrackingDBClientRequestPacket ====================

    static Stream<Arguments> versionsFrom428to748() {
        // PositionTrackingDB packets registered in CB Protocol from v428 to before they were removed
        return filteredVersionsRange(428, ProtocolInfo.v1_21_40);
    }

    @ParameterizedTest(name = "PositionTrackingDBClientRequestPacket v{0}")
    @MethodSource("versionsFrom428to748")
    void testPositionTrackingDBClientRequestPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PositionTrackingDBClientRequestPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setAction(cn.nukkit.network.protocol.PositionTrackingDBClientRequestPacket.Action.QUERY);
        nukkitPacket.setTrackingId(42);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBClientRequestPacket.class);

        assertEquals(0, cbPacket.getAction().ordinal());
        assertEquals(42, cbPacket.getTrackingId());
    }

    // ==================== PositionTrackingDBServerBroadcastPacket ====================

    @ParameterizedTest(name = "PositionTrackingDBServerBroadcastPacket v{0}")
    @MethodSource("versionsFrom428to748")
    void testPositionTrackingDBServerBroadcastPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.PositionTrackingDBServerBroadcastPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setAction(cn.nukkit.network.protocol.PositionTrackingDBServerBroadcastPacket.Action.UPDATE);
        nukkitPacket.setTrackingId(42);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBServerBroadcastPacket.class);

        assertEquals(0, cbPacket.getAction().ordinal());
        assertEquals(42, cbPacket.getTrackingId());
    }

    // ==================== JigsawStructureDataPacket ====================

    static Stream<Arguments> versionsFrom712() {
        return filteredVersions(ProtocolInfo.v1_21_20);
    }

    @ParameterizedTest(name = "JigsawStructureDataPacket v{0}")
    @MethodSource("versionsFrom712")
    void testJigsawStructureDataPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.JigsawStructureDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.nbt = new CompoundTag("")
                .putString("name", "test")
                .putInt("dimension", 0);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.JigsawStructureDataPacket.class);

        assertNotNull(cbPacket);
    }

    // ==================== ServerboundDiagnosticsPacket ====================

    @ParameterizedTest(name = "ServerboundDiagnosticsPacket v{0}")
    @MethodSource("versionsFrom712")
    void testServerboundDiagnosticsPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ServerboundDiagnosticsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.avgFps = 60.0f;
        nukkitPacket.avgServerSimTickTimeMS = 5.0f;
        nukkitPacket.avgClientSimTickTimeMS = 3.0f;
        nukkitPacket.avgBeginFrameTimeMS = 1.0f;
        nukkitPacket.avgInputTimeMS = 0.5f;
        nukkitPacket.avgRenderTimeMS = 8.0f;
        nukkitPacket.avgEndFrameTimeMS = 0.5f;
        nukkitPacket.avgRemainderTimePercent = 2.0f;
        nukkitPacket.avgUnaccountedTimePercent = 1.0f;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ServerboundDiagnosticsPacket.class);

        assertEquals(60.0f, cbPacket.getAvgFps(), 0.001f);
        assertEquals(5.0f, cbPacket.getAvgServerSimTickTimeMS(), 0.001f);
        assertEquals(8.0f, cbPacket.getAvgRenderTimeMS(), 0.001f);
    }

    // ==================== ScriptCustomEventPacket ====================

    static Stream<Arguments> versionsFrom291to594() {
        // ScriptCustomEventPacket deprecated and removed from CB Protocol at v594
        return filteredVersionsRange(291, ProtocolInfo.v1_20_10);
    }

    @ParameterizedTest(name = "ScriptCustomEventPacket v{0}")
    @MethodSource("versionsFrom291to594")
    void testScriptCustomEventPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ScriptCustomEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eventName = "custom:test_event";
        nukkitPacket.eventData = "test_payload".getBytes(StandardCharsets.UTF_8);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ScriptCustomEventPacket.class);

        assertEquals("custom:test_event", cbPacket.getEventName());
        assertEquals("test_payload", cbPacket.getData());
    }

    // ==================== CameraAimAssistActorPriorityPacket ====================

    static Stream<Arguments> versionsFrom924() {
        return filteredVersions(ProtocolInfo.v1_26_0);
    }

    @ParameterizedTest(name = "CameraAimAssistActorPriorityPacket v{0}")
    @MethodSource("versionsFrom924")
    void testCameraAimAssistActorPriorityPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraAimAssistActorPriorityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.priorities.add(new AimAssistActorPriorityData(0, 1, 2, 100));
        nukkitPacket.priorities.add(new AimAssistActorPriorityData(1, 0, 3, 50));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraAimAssistActorPriorityPacket.class);

        assertEquals(2, cbPacket.getPriorityData().size());
        var first = cbPacket.getPriorityData().get(0);
        assertEquals(0, first.getPresetIndex());
        assertEquals(1, first.getCategoryIndex());
        assertEquals(2, first.getActorIndex());
        assertEquals(100, first.getPriorityValue());
        var second = cbPacket.getPriorityData().get(1);
        assertEquals(1, second.getPresetIndex());
        assertEquals(50, second.getPriorityValue());
    }

    // ==================== CameraSplinePacket ====================

    @ParameterizedTest(name = "CameraSplinePacket v{0}")
    @MethodSource("versionsFrom924")
    void testCameraSplinePacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraSplinePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);

        var curve = new ArrayList<Vector3f>();
        curve.add(new Vector3f(0, 0, 0));
        curve.add(new Vector3f(10, 20, 30));

        var progressKeyFrames = new ArrayList<CameraSplineInstruction.SplineProgressOption>();
        progressKeyFrames.add(new CameraSplineInstruction.SplineProgressOption(
                0.5f, 1.0f, CameraEase.LINEAR));

        var rotationOptions = new ArrayList<CameraSplineInstruction.SplineRotationOption>();
        rotationOptions.add(new CameraSplineInstruction.SplineRotationOption(
                new Vector3f(45, 90, 0), 2.0f));

        var instruction = new CameraSplineInstruction(
                5.0f, CameraSplineType.CATMULL_ROM,
                curve, progressKeyFrames, rotationOptions);

        nukkitPacket.splines.add(new CameraSplineDefinition("spline1", instruction));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraSplinePacket.class);

        assertEquals(1, cbPacket.getSplines().size());
        var spline = cbPacket.getSplines().get(0);
        assertEquals("spline1", spline.getName());
        assertEquals(5.0f, spline.getInstruction().getTotalTime(), 0.001f);
        assertEquals(org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineType.CATMULL_ROM,
                spline.getInstruction().getType());
        assertEquals(2, spline.getInstruction().getCurve().size());
        assertEquals(1, spline.getInstruction().getProgressKeyFrames().size());
        assertEquals(0.5f, spline.getInstruction().getProgressKeyFrames().get(0).getValue(), 0.001f);
        assertEquals(org.cloudburstmc.protocol.bedrock.data.camera.CameraEase.LINEAR,
                spline.getInstruction().getProgressKeyFrames().get(0).getEasingFunc());
        assertEquals(1, spline.getInstruction().getRotationOption().size());
        assertEquals(2.0f, spline.getInstruction().getRotationOption().get(0).getKeyFrameTimes(), 0.001f);
    }

    // ==================== VoxelShapesPacket ====================

    @ParameterizedTest(name = "VoxelShapesPacket v{0}")
    @MethodSource("versionsFrom924")
    void testVoxelShapesPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.VoxelShapesPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);

        var storage = new ArrayList<Short>();
        storage.add((short) 1);
        storage.add((short) 0);
        storage.add((short) 1);

        var cells = new SerializableVoxelShape.SerializableCells((short) 2, (short) 3, (short) 2, storage);
        var xCoords = new ArrayList<>(java.util.List.of(0.0f, 0.5f, 1.0f));
        var yCoords = new ArrayList<>(java.util.List.of(0.0f, 1.0f, 2.0f, 3.0f));
        var zCoords = new ArrayList<>(java.util.List.of(0.0f, 0.5f, 1.0f));

        nukkitPacket.shapes.add(new SerializableVoxelShape(cells, xCoords, yCoords, zCoords));
        nukkitPacket.nameMap.put("minecraft:stone", 1);
        nukkitPacket.nameMap.put("minecraft:dirt", 2);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket.class);

        assertEquals(1, cbPacket.getShapes().size());
        var shape = cbPacket.getShapes().get(0);
        assertEquals(2, shape.getCells().getXSize());
        assertEquals(3, shape.getCells().getYSize());
        assertEquals(2, shape.getCells().getZSize());
        assertEquals(3, shape.getCells().getStorage().size());
        assertEquals(3, shape.getXCoordinates().size());
        assertEquals(4, shape.getYCoordinates().size());
        assertEquals(3, shape.getZCoordinates().size());
        assertEquals(0.5f, shape.getXCoordinates().get(1), 0.001f);
        assertEquals(2, cbPacket.getNameMap().size());
        assertEquals(1, cbPacket.getNameMap().get("minecraft:stone"));
        assertEquals(2, cbPacket.getNameMap().get("minecraft:dirt"));
    }
}
