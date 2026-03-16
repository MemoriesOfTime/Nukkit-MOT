package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Attribute;
import cn.nukkit.level.GameRules;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.*;
import cn.nukkit.network.protocol.types.camera.CameraFadeInstruction;
import cn.nukkit.network.protocol.types.camera.CameraPreset;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.*;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for packets with complex version branching or initialization requirements.
 */
public class ComplexPacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
        Attribute.init();
    }

    // --- Version Sources ---

    static Stream<Arguments> versionsFrom332() {
        return filteredVersions(332);
    }

    static Stream<Arguments> versionsPre332() {
        // SpawnParticleEffectPacket first registered in CB Protocol v313
        return filteredVersionsRange(313, 332);
    }

    static Stream<Arguments> versionsFrom662to748() {
        return filteredVersionsRange(ProtocolInfo.v1_20_70, ProtocolInfo.v1_21_40);
    }

    static Stream<Arguments> versionsFrom748to898() {
        return filteredVersionsRange(ProtocolInfo.v1_21_40, ProtocolInfo.v1_21_130_28);
    }

    static Stream<Arguments> versionsFrom898() {
        return filteredVersions(ProtocolInfo.v1_21_130_28);
    }

    static Stream<Arguments> versionsPre662() {
        return filteredVersionsRange(291, ProtocolInfo.v1_20_70);
    }

    static Stream<Arguments> versionsPre898() {
        return filteredVersionsRange(291, ProtocolInfo.v1_21_130_28);
    }

    static Stream<Arguments> versionsFrom786() {
        return filteredVersions(ProtocolInfo.v1_21_70);
    }

    static Stream<Arguments> versionsPre786ForLSE() {
        // LevelSoundEventPacket exists from v332 but entityUniqueId not until v786
        return filteredVersionsRange(332, ProtocolInfo.v1_21_70);
    }

    // ==================== MobEffectPacket ====================

    @ParameterizedTest(name = "MobEffectPacket v{0} (<662)")
    @MethodSource("versionsPre662")
    void testMobEffectPacketPre662(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MobEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.eid = 42;
        nukkitPacket.eventId = cn.nukkit.network.protocol.MobEffectPacket.EVENT_ADD;
        nukkitPacket.effectId = 1; // Speed
        nukkitPacket.amplifier = 0;
        nukkitPacket.particles = true;
        nukkitPacket.duration = 600;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(1, cbPacket.getEvent().ordinal());
        assertEquals(1, cbPacket.getEffectId());
        assertEquals(0, cbPacket.getAmplifier());
        assertTrue(cbPacket.isParticles());
        assertEquals(600, cbPacket.getDuration());
    }

    @ParameterizedTest(name = "MobEffectPacket v{0} (662-748, LLong tick)")
    @MethodSource("versionsFrom662to748")
    void testMobEffectPacketLLongTick(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MobEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.eid = 42;
        nukkitPacket.eventId = cn.nukkit.network.protocol.MobEffectPacket.EVENT_ADD;
        nukkitPacket.effectId = 1;
        nukkitPacket.amplifier = 0;
        nukkitPacket.particles = true;
        nukkitPacket.duration = 600;
        nukkitPacket.tick = 1000L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(1, cbPacket.getEffectId());
        assertEquals(600, cbPacket.getDuration());
        assertEquals(1000L, cbPacket.getTick());
    }

    @ParameterizedTest(name = "MobEffectPacket v{0} (748-898, VarLong tick)")
    @MethodSource("versionsFrom748to898")
    void testMobEffectPacketVarLongTick(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MobEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.eid = 42;
        nukkitPacket.eventId = cn.nukkit.network.protocol.MobEffectPacket.EVENT_ADD;
        nukkitPacket.effectId = 1;
        nukkitPacket.amplifier = 0;
        nukkitPacket.particles = true;
        nukkitPacket.duration = 600;
        nukkitPacket.tick = 1000L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(1, cbPacket.getEffectId());
        assertEquals(600, cbPacket.getDuration());
        assertEquals(1000L, cbPacket.getTick());
    }

    @ParameterizedTest(name = "MobEffectPacket v{0} (>=898, + ambient)")
    @MethodSource("versionsFrom898")
    void testMobEffectPacketWithAmbient(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.MobEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.eid = 42;
        nukkitPacket.eventId = cn.nukkit.network.protocol.MobEffectPacket.EVENT_ADD;
        nukkitPacket.effectId = 1;
        nukkitPacket.amplifier = 0;
        nukkitPacket.particles = true;
        nukkitPacket.duration = 600;
        nukkitPacket.tick = 1000L;
        nukkitPacket.ambient = false;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertEquals(1, cbPacket.getEffectId());
        assertEquals(600, cbPacket.getDuration());
    }

    // ==================== AnimatePacket ====================

    @ParameterizedTest(name = "AnimatePacket v{0} (<898, VarInt action)")
    @MethodSource("versionsPre898")
    void testAnimatePacketPreV898(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.AnimatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.action = cn.nukkit.network.protocol.AnimatePacket.Action.SWING_ARM;
        nukkitPacket.eid = 42;
        if (protocolVersion >= ProtocolInfo.v1_21_120) {
            nukkitPacket.data = 0.0f;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.class);

        assertEquals(1, cbPacket.getAction().ordinal());
        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    @ParameterizedTest(name = "AnimatePacket v{0} (>=898, Byte action)")
    @MethodSource("versionsFrom898")
    void testAnimatePacketFromV898(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.AnimatePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.action = cn.nukkit.network.protocol.AnimatePacket.Action.SWING_ARM;
        nukkitPacket.eid = 42;
        nukkitPacket.data = 0.0f;
        nukkitPacket.swingSource = cn.nukkit.network.protocol.AnimatePacket.SwingSource.NONE;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.class);

        assertEquals(1, cbPacket.getAction().ordinal());
        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    // ==================== UpdateAttributesPacket ====================

    @ParameterizedTest(name = "UpdateAttributesPacket v{0}")
    @MethodSource("allVersions")
    void testUpdateAttributesPacket(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.UpdateAttributesPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.entityId = 42;
        nukkitPacket.entries = new Attribute[]{
                Attribute.getAttribute(Attribute.MAX_HEALTH).setValue(20.0f)
        };
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.frame = 100;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
        assertFalse(cbPacket.getAttributes().isEmpty());
        var attr = cbPacket.getAttributes().get(0);
        assertEquals("minecraft:health", attr.getName());
        assertEquals(20.0f, attr.getValue(), 0.001f);
    }

    // ==================== LevelSoundEventPacket ====================

    @ParameterizedTest(name = "LevelSoundEventPacket v{0} (<786)")
    @MethodSource("versionsPre786ForLSE")
    void testLevelSoundEventPacketPre786(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelSoundEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.sound = cn.nukkit.network.protocol.LevelSoundEventPacket.SOUND_EXPLODE; // 48
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.extraData = -1;
        nukkitPacket.entityIdentifier = "";
        nukkitPacket.isBabyMob = false;
        nukkitPacket.isGlobal = false;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(-1, cbPacket.getExtraData());
        assertEquals("", cbPacket.getIdentifier());
        assertFalse(cbPacket.isBabySound());
        assertFalse(cbPacket.isRelativeVolumeDisabled());
    }

    @ParameterizedTest(name = "LevelSoundEventPacket v{0} (>=786, entityUniqueId)")
    @MethodSource("versionsFrom786")
    void testLevelSoundEventPacketFrom786(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.LevelSoundEventPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.sound = cn.nukkit.network.protocol.LevelSoundEventPacket.SOUND_EXPLODE;
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.extraData = -1;
        nukkitPacket.entityIdentifier = "";
        nukkitPacket.isBabyMob = false;
        nukkitPacket.isGlobal = false;
        nukkitPacket.entityUniqueId = -1L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(-1, cbPacket.getExtraData());
        assertFalse(cbPacket.isBabySound());
        assertFalse(cbPacket.isRelativeVolumeDisabled());
    }

    // ==================== SpawnParticleEffectPacket ====================

    @ParameterizedTest(name = "SpawnParticleEffectPacket v{0} (<332)")
    @MethodSource("versionsPre332")
    void testSpawnParticleEffectPacketPre332(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SpawnParticleEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.dimensionId = 0;
        nukkitPacket.position = new Vector3f(100.5f, 64.0f, 200.5f);
        nukkitPacket.identifier = "minecraft:explosion_particle";
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket.class);

        assertEquals(0, cbPacket.getDimensionId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals("minecraft:explosion_particle", cbPacket.getIdentifier());
    }

    @ParameterizedTest(name = "SpawnParticleEffectPacket v{0} (>=332)")
    @MethodSource("versionsFrom332")
    void testSpawnParticleEffectPacketFrom332(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.SpawnParticleEffectPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.dimensionId = 0;
        nukkitPacket.uniqueEntityId = -1;
        nukkitPacket.position = new Vector3f(100.5f, 64.0f, 200.5f);
        nukkitPacket.identifier = "minecraft:explosion_particle";
        if (protocolVersion >= ProtocolInfo.v1_18_30) {
            nukkitPacket.molangVariablesJson = Optional.empty();
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket.class);

        assertEquals(0, cbPacket.getDimensionId());
        assertEquals(-1, cbPacket.getUniqueEntityId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals("minecraft:explosion_particle", cbPacket.getIdentifier());
    }

    // ==================== CommandOutputPacket ====================

    static Stream<Arguments> versionsPreV898() {
        // v291 serializer path: origin ordinal, byte type, UVarInt successCount, internal→messageId order
        return filteredVersionsRange(291, 898);
    }

    static Stream<Arguments> versionsFromV898() {
        // v898 serializer path: string origin, LLong playerId, string type, LInt successCount, messageId→internal order
        return filteredVersions(898);
    }

    @ParameterizedTest(name = "CommandOutputPacket v{0} (<898, PLAYER origin)")
    @MethodSource("versionsPreV898")
    void testCommandOutputPacketPreV898(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CommandOutputPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.commandOriginData = new CommandOriginData(
                CommandOriginData.Origin.PLAYER,
                UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                "req-1",
                null
        );
        nukkitPacket.type = CommandOutputType.LAST_OUTPUT;
        nukkitPacket.successCount = 1;
        nukkitPacket.messages.add(new CommandOutputMessage(false, "commands.success", new String[]{"param1"}));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType.PLAYER,
                cbPacket.getCommandOriginData().getOrigin());
        assertEquals("req-1", cbPacket.getCommandOriginData().getRequestId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.command.CommandOutputType.LAST_OUTPUT, cbPacket.getType());
        assertEquals(1, cbPacket.getSuccessCount());
        assertEquals(1, cbPacket.getMessages().size());
        assertEquals("commands.success", cbPacket.getMessages().get(0).getMessageId());
        assertFalse(cbPacket.getMessages().get(0).isInternal());
        assertEquals(1, cbPacket.getMessages().get(0).getParameters().length);
        assertEquals("param1", cbPacket.getMessages().get(0).getParameters()[0]);
    }

    @ParameterizedTest(name = "CommandOutputPacket v{0} (<898, DEV_CONSOLE with playerId)")
    @MethodSource("versionsPreV898")
    void testCommandOutputPacketDevConsole(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CommandOutputPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.commandOriginData = new CommandOriginData(
                CommandOriginData.Origin.DEV_CONSOLE,
                UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                "req-2",
                99L
        );
        nukkitPacket.type = CommandOutputType.ALL_OUTPUT;
        nukkitPacket.successCount = 3;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType.DEV_CONSOLE,
                cbPacket.getCommandOriginData().getOrigin());
        assertEquals("req-2", cbPacket.getCommandOriginData().getRequestId());
        assertEquals(99L, cbPacket.getCommandOriginData().getPlayerId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.command.CommandOutputType.ALL_OUTPUT, cbPacket.getType());
        assertEquals(3, cbPacket.getSuccessCount());
    }

    @ParameterizedTest(name = "CommandOutputPacket v{0} (>=898, new format)")
    @MethodSource("versionsFromV898")
    void testCommandOutputPacketV898(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CommandOutputPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.commandOriginData = new CommandOriginData(
                CommandOriginData.Origin.PLAYER,
                UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                "req-3",
                -1L
        );
        nukkitPacket.type = CommandOutputType.LAST_OUTPUT;
        nukkitPacket.successCount = 2;
        nukkitPacket.messages.add(new CommandOutputMessage(true, "msg.id1", new String[]{"a", "b"}));
        nukkitPacket.messages.add(new CommandOutputMessage(false, "msg.id2", new String[]{}));
        nukkitPacket.data = null;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket.class);

        assertEquals("req-3", cbPacket.getCommandOriginData().getRequestId());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.command.CommandOutputType.LAST_OUTPUT, cbPacket.getType());
        assertEquals(2, cbPacket.getSuccessCount());
        assertEquals(2, cbPacket.getMessages().size());
        assertEquals("msg.id1", cbPacket.getMessages().get(0).getMessageId());
        assertTrue(cbPacket.getMessages().get(0).isInternal());
        assertEquals(2, cbPacket.getMessages().get(0).getParameters().length);
        assertEquals("msg.id2", cbPacket.getMessages().get(1).getMessageId());
        assertFalse(cbPacket.getMessages().get(1).isInternal());
    }

    // ==================== ItemComponentPacket ====================

    static Stream<Arguments> versionsFrom419to776() {
        // v419 serializer: identifier + NBT only
        return filteredVersionsRange(419, ProtocolInfo.v1_21_60);
    }

    static Stream<Arguments> versionsFrom776() {
        // v776 serializer: identifier + runtimeId + componentBased + version + NBT
        return filteredVersions(ProtocolInfo.v1_21_60);
    }

    @ParameterizedTest(name = "ItemComponentPacket v{0} (419-776, simple)")
    @MethodSource("versionsFrom419to776")
    void testItemComponentPacketPre776(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ItemComponentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.entries.add(new cn.nukkit.network.protocol.ItemComponentPacket.ItemDefinition(
                "custom:test_item", 0, false, 0,
                new CompoundTag("").putString("name", "test").putInt("maxStack", 64)
        ));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket.class);

        assertEquals(1, cbPacket.getItems().size());
        assertEquals("custom:test_item", cbPacket.getItems().get(0).getIdentifier());
    }

    @ParameterizedTest(name = "ItemComponentPacket v{0} (>=776, with runtimeId)")
    @MethodSource("versionsFrom776")
    void testItemComponentPacketFrom776(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ItemComponentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.entries.add(new cn.nukkit.network.protocol.ItemComponentPacket.ItemDefinition(
                "custom:sword", 500, true, 1,
                new CompoundTag("").putString("name", "sword")
        ));
        nukkitPacket.entries.add(new cn.nukkit.network.protocol.ItemComponentPacket.ItemDefinition(
                "custom:shield", 501, false, 0,
                new CompoundTag("")
        ));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket.class);

        assertEquals(2, cbPacket.getItems().size());
        assertEquals("custom:sword", cbPacket.getItems().get(0).getIdentifier());
        assertEquals(500, cbPacket.getItems().get(0).getRuntimeId());
        assertTrue(cbPacket.getItems().get(0).isComponentBased());
        assertEquals("custom:shield", cbPacket.getItems().get(1).getIdentifier());
        assertEquals(501, cbPacket.getItems().get(1).getRuntimeId());
        assertFalse(cbPacket.getItems().get(1).isComponentBased());
    }

    @ParameterizedTest(name = "ItemComponentPacket v{0} (empty entries)")
    @MethodSource("versionsFrom419to776")
    void testItemComponentPacketEmpty(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.ItemComponentPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket.class);

        assertTrue(cbPacket.getItems().isEmpty());
    }

    // ==================== CameraPresetsPacket ====================

    static Stream<Arguments> versionsFrom818() {
        // v818 serializer: binary format with OptionalNull fields
        return filteredVersions(818);
    }

    static Stream<Arguments> cameraPresetsVersions618to818() {
        return filteredVersionsRange(618, 818);
    }

    @ParameterizedTest(name = "CameraPresetsPacket v{0} (618-818)")
    @MethodSource("cameraPresetsVersions618to818")
    void testCameraPresetsPacketPreV818(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraPresetsPacket();
        nukkitPacket.protocol = protocolVersion;

        var preset = new CameraPreset();
        preset.setIdentifier("minecraft:free");
        preset.setParentPreset("");
        preset.setPlayEffect(OptionalBoolean.empty());
        preset.setSnapToTarget(OptionalBoolean.empty());
        preset.setContinueTargeting(OptionalBoolean.empty());
        preset.setAlignTargetAndCameraForward(OptionalBoolean.empty());
        nukkitPacket.getPresets().add(preset);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket.class);

        assertEquals(1, cbPacket.getPresets().size());
        assertEquals("minecraft:free", cbPacket.getPresets().get(0).getIdentifier());
    }

    @ParameterizedTest(name = "CameraPresetsPacket v{0} (>=818, minimal preset)")
    @MethodSource("versionsFrom818")
    void testCameraPresetsPacketMinimal(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraPresetsPacket();
        nukkitPacket.protocol = protocolVersion;

        var preset = new CameraPreset();
        preset.setIdentifier("minecraft:free");
        preset.setParentPreset("");
        preset.setPlayEffect(OptionalBoolean.empty());
        preset.setSnapToTarget(OptionalBoolean.empty());
        preset.setContinueTargeting(OptionalBoolean.empty());
        preset.setAlignTargetAndCameraForward(OptionalBoolean.empty());
        nukkitPacket.getPresets().add(preset);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket.class);

        assertEquals(1, cbPacket.getPresets().size());
        var cbPreset = cbPacket.getPresets().get(0);
        assertEquals("minecraft:free", cbPreset.getIdentifier());
        assertEquals("", cbPreset.getParentPreset());
    }

    @ParameterizedTest(name = "CameraPresetsPacket v{0} (>=818, with optional fields)")
    @MethodSource("versionsFrom818")
    void testCameraPresetsPacketWithFields(int protocolVersion) {
        var nukkitPacket = new cn.nukkit.network.protocol.CameraPresetsPacket();
        nukkitPacket.protocol = protocolVersion;

        var preset = new CameraPreset();
        preset.setIdentifier("minecraft:custom_cam");
        preset.setParentPreset("minecraft:free");
        preset.setPos(new Vector3f(10.0f, 20.0f, 30.0f));
        preset.setPitch(45.0f);
        preset.setYaw(90.0f);
        preset.setPlayEffect(OptionalBoolean.of(true));
        preset.setSnapToTarget(OptionalBoolean.empty());
        preset.setContinueTargeting(OptionalBoolean.empty());
        preset.setAlignTargetAndCameraForward(OptionalBoolean.empty());
        if (protocolVersion >= ProtocolInfo.v1_21_20) {
            preset.setViewOffset(new Vector2f(1.0f, 2.0f));
            preset.setRadius(5.0f);
        }
        nukkitPacket.getPresets().add(preset);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket.class);

        assertEquals(1, cbPacket.getPresets().size());
        var cbPreset = cbPacket.getPresets().get(0);
        assertEquals("minecraft:custom_cam", cbPreset.getIdentifier());
        assertEquals("minecraft:free", cbPreset.getParentPreset());
        assertNotNull(cbPreset.getPos());
        assertEquals(10.0f, cbPreset.getPos().getX(), 0.001f);
        assertEquals(20.0f, cbPreset.getPos().getY(), 0.001f);
        assertEquals(30.0f, cbPreset.getPos().getZ(), 0.001f);
        assertEquals(45.0f, cbPreset.getPitch(), 0.001f);
        assertEquals(90.0f, cbPreset.getYaw(), 0.001f);
    }

    // ==================== StartGamePacket ====================
    // Skipped: StartGamePacket has extremely complex version branching with 50+ fields,
    // block/item palettes, game rules, and experiments. The encoded payload includes
    // large binary blobs (block palette, item palette) that vary significantly per version.
    // Cross-decode testing is not practical without exhaustive per-version field alignment.

    //@ParameterizedTest(name = "StartGamePacket v{0}")
    //@MethodSource("allVersions")
    void _skipped_testStartGamePacket(int protocolVersion) {
        var nukkitPacket = new StartGamePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.entityUniqueId = 1;
        nukkitPacket.entityRuntimeId = 1;
        nukkitPacket.playerGamemode = 0;
        nukkitPacket.x = 0.0f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 0.0f;
        nukkitPacket.yaw = 0.0f;
        nukkitPacket.pitch = 0.0f;
        nukkitPacket.seed = 12345;
        nukkitPacket.dimension = 0;
        nukkitPacket.generator = 1;
        nukkitPacket.worldGamemode = 0;
        nukkitPacket.difficulty = 2;
        nukkitPacket.spawnX = 0;
        nukkitPacket.spawnY = 64;
        nukkitPacket.spawnZ = 0;
        nukkitPacket.gameRules = new GameRules();
        nukkitPacket.levelId = "";
        nukkitPacket.worldName = "TestWorld";
        nukkitPacket.premiumWorldTemplateId = "";
        nukkitPacket.currentTick = 0L;
        nukkitPacket.enchantmentSeed = 0;
        nukkitPacket.multiplayerCorrelationId = "";
        nukkitPacket.vanillaVersion = cn.nukkit.utils.Utils.getVersionByProtocol(protocolVersion);
        nukkitPacket.authoritativeMovementMode = AuthoritativeMovementMode.SERVER;
        nukkitPacket.rewindHistorySize = 40;
        nukkitPacket.blockDefinitions = java.util.Collections.emptyList();
        nukkitPacket.playerPropertyData = new CompoundTag("");
        nukkitPacket.networkPermissions = NetworkPermissions.DEFAULT;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.StartGamePacket.class);

        assertEquals(1, cbPacket.getUniqueEntityId());
        assertEquals(1, cbPacket.getRuntimeEntityId());
        assertEquals("TestWorld", cbPacket.getLevelName());
    }

    // ==================== ClientboundMapItemDataPacket ====================

    static Stream<Arguments> versionsFrom354() {
        return filteredVersions(354);
    }

    @ParameterizedTest(name = "ClientboundMapItemDataPacket v{0} (scale only)")
    @MethodSource("versionsFrom354")
    void testClientboundMapItemDataPacketScaleOnly(int protocolVersion) {
        var nukkitPacket = new ClientboundMapItemDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.mapId = 1;
        nukkitPacket.dimensionId = 0;
        nukkitPacket.isLocked = false;
        nukkitPacket.origin = new BlockVector3(0, 0, 0);
        nukkitPacket.scale = 4;
        nukkitPacket.eids = new long[]{10L};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundMapItemDataPacket.class);

        assertEquals(1, cbPacket.getUniqueMapId());
        assertEquals(4, cbPacket.getScale());
    }

    static Stream<Arguments> versionsFrom544() {
        return filteredVersions(544);
    }

    // Note: CB Protocol v354 serializer has a bug where deserialize() calls writeMapDecorations()
    // instead of readMapDecorations(). Fixed in v544 serializer. Test starts from v544.
    @ParameterizedTest(name = "ClientboundMapItemDataPacket v{0} (decorations)")
    @MethodSource("versionsFrom544")
    void testClientboundMapItemDataPacketDecorations(int protocolVersion) {
        var nukkitPacket = new ClientboundMapItemDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.mapId = 2;
        nukkitPacket.dimensionId = 0;
        nukkitPacket.isLocked = true;
        nukkitPacket.origin = new BlockVector3(100, 64, 200);
        nukkitPacket.scale = 1;

        var tracked = new ClientboundMapItemDataPacket.MapTrackedObject();
        tracked.type = ClientboundMapItemDataPacket.MapTrackedObject.TYPE_BLOCK;
        tracked.x = 100;
        tracked.y = 64;
        tracked.z = 200;
        nukkitPacket.trackedEntities = new ClientboundMapItemDataPacket.MapTrackedObject[]{tracked};

        var decorator = new ClientboundMapItemDataPacket.MapDecorator();
        decorator.icon = 0;
        decorator.rotation = 8;
        decorator.offsetX = 10;
        decorator.offsetZ = 20;
        decorator.label = "";
        decorator.color = new Color(255, 0, 0);
        nukkitPacket.decorators = new ClientboundMapItemDataPacket.MapDecorator[]{decorator};
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.ClientboundMapItemDataPacket.class);

        assertEquals(2, cbPacket.getUniqueMapId());
        assertTrue(cbPacket.isLocked());
    }

    // ==================== CameraInstructionPacket ====================

    static Stream<Arguments> versionsFromV630() {
        return filteredVersions(ProtocolInfo.v1_20_30_24);
    }

    @ParameterizedTest(name = "CameraInstructionPacket v{0} (clear)")
    @MethodSource("versionsFromV630")
    void testCameraInstructionPacketClear(int protocolVersion) {
        var nukkitPacket = new CameraInstructionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.setClear(OptionalBoolean.of(true));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket.class);

        assertTrue(cbPacket.getClear().isPresent());
        assertTrue(cbPacket.getClear().getAsBoolean());
    }

    @ParameterizedTest(name = "CameraInstructionPacket v{0} (fade)")
    @MethodSource("versionsFromV630")
    void testCameraInstructionPacketFade(int protocolVersion) {
        var nukkitPacket = new CameraInstructionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.setFadeInstruction(new CameraFadeInstruction(
                new CameraFadeInstruction.TimeData(1.0f, 2.0f, 1.0f),
                new Color(255, 0, 0)
        ));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket.class);

        assertNotNull(cbPacket.getFadeInstruction());
    }

    // ==================== DebugDrawerPacket ====================

    static Stream<Arguments> versionsFrom859() {
        return filteredVersions(859);
    }

    @ParameterizedTest(name = "DebugDrawerPacket v{0} (empty shapes)")
    @MethodSource("versionsFrom818")
    void testDebugDrawerPacketEmpty(int protocolVersion) {
        var nukkitPacket = new DebugDrawerPacket();
        nukkitPacket.protocol = protocolVersion;
        // empty shapes list
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket.class);

        assertTrue(cbPacket.getShapes().isEmpty());
    }

    @ParameterizedTest(name = "DebugDrawerPacket v{0} (text shape, v859+)")
    @MethodSource("versionsFrom859")
    void testDebugDrawerPacketTextShape(int protocolVersion) {
        var nukkitPacket = new DebugDrawerPacket();
        nukkitPacket.protocol = protocolVersion;
        var textShape = new cn.nukkit.network.protocol.types.debugshape.DebugText(
                1L, 0,
                new Vector3f(10.0f, 20.0f, 30.0f),
                1.0f,
                null, // rotation
                null, // totalTimeLeft
                new Color(255, 0, 0),
                "hello"
        );
        nukkitPacket.shapes.add(textShape);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket.class);

        assertEquals(1, cbPacket.getShapes().size());
        var cbShape = cbPacket.getShapes().get(0);
        assertInstanceOf(org.cloudburstmc.protocol.bedrock.data.debugshape.DebugText.class, cbShape);
        assertEquals("hello", ((org.cloudburstmc.protocol.bedrock.data.debugshape.DebugText) cbShape).getText());
    }

    // ==================== AvailableCommandsPacket ====================

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
    }

    @ParameterizedTest(name = "AvailableCommandsPacket v{0} (empty)")
    @MethodSource("versionsFrom313")
    void testAvailableCommandsPacketEmpty(int protocolVersion) {
        var nukkitPacket = new AvailableCommandsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.commands = java.util.Collections.emptyMap();
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket.class);

        assertTrue(cbPacket.getCommands().isEmpty());
    }

    // ==================== InventoryTransactionPacket ====================
    // Skipped: Nukkit writes hasNetworkIds as a separate boolean for all protocol >= 407,
    // but CB Protocol v407 serializer embeds network ID handling inside readInventoryActions().
    // The encoding formats are incompatible for cross-decode testing.
}
