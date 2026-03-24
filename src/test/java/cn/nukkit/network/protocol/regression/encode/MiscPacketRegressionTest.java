package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.level.GameRules;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.MovementEffectType;
import cn.nukkit.network.protocol.types.ServerAuthMovementMode;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class MiscPacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
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
        var decodedTag = stream.getTagNetworkLE();
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
