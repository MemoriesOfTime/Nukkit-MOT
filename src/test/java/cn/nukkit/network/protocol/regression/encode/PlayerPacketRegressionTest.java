package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.item.Item;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlayerPacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
    }

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(407);
    }

    static Stream<Arguments> versionsFrom419() {
        return filteredVersions(419);
    }

    static Stream<Arguments> versionsFrom671() {
        return filteredVersions(671);
    }

    static Stream<Arguments> versionsPre553() {
        return filteredVersionsRange(291, 553);
    }

    @ParameterizedTest(name = "PlayerListPacket REMOVE v{0}")
    @MethodSource("versionsFrom313")
    void testPlayerListPacketRemove(int protocolVersion) {
        var nukkitPacket = new PlayerListPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.type = PlayerListPacket.TYPE_REMOVE;
        nukkitPacket.entries = new PlayerListPacket.Entry[]{
                new PlayerListPacket.Entry(UUID.randomUUID())
        };
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action.REMOVE, cbPacket.getAction());
        assertEquals(1, cbPacket.getEntries().size());
    }

    // ==================== CorrectPlayerMovePredictionPacket ====================

    @ParameterizedTest(name = "CorrectPlayerMovePredictionPacket PLAYER v{0}")
    @MethodSource("versionsFrom419")
    void testCorrectPlayerMovePredictionPacketPlayer(int protocolVersion) {
        var nukkitPacket = new CorrectPlayerMovePredictionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setPosition(new Vector3f(100.5f, 64.0f, 200.5f));
        nukkitPacket.setDelta(new Vector3f(1.0f, 0.5f, 2.0f));
        nukkitPacket.setOnGround(true);
        nukkitPacket.setTick(12345L);
        nukkitPacket.setPredictionType(CorrectPlayerMovePredictionPacket.PredictionType.PLAYER);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket.class);

        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(1.0f, cbPacket.getDelta().getX(), 0.001f);
        assertEquals(0.5f, cbPacket.getDelta().getY(), 0.001f);
        assertEquals(2.0f, cbPacket.getDelta().getZ(), 0.001f);
        assertTrue(cbPacket.isOnGround());
        assertEquals(12345L, cbPacket.getTick());
    }

    @ParameterizedTest(name = "CorrectPlayerMovePredictionPacket VEHICLE v{0}")
    @MethodSource("versionsFrom671")
    void testCorrectPlayerMovePredictionPacketVehicle(int protocolVersion) {
        var nukkitPacket = new CorrectPlayerMovePredictionPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setPosition(new Vector3f(100.5f, 64.0f, 200.5f));
        nukkitPacket.setDelta(new Vector3f(1.0f, 0.5f, 2.0f));
        nukkitPacket.setOnGround(false);
        nukkitPacket.setTick(12345L);
        nukkitPacket.setPredictionType(CorrectPlayerMovePredictionPacket.PredictionType.VEHICLE);
        nukkitPacket.setVehicleRotation(new Vector2f(90.0f, 45.0f));
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.PredictionType.VEHICLE, cbPacket.getPredictionType());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(90.0f, cbPacket.getVehicleRotation().getX(), 0.001f);
        assertEquals(45.0f, cbPacket.getVehicleRotation().getY(), 0.001f);
    }

    // ==================== PlayerEnchantOptionsPacket ====================

    @ParameterizedTest(name = "PlayerEnchantOptionsPacket v{0}")
    @MethodSource("versionsFrom407")
    void testPlayerEnchantOptionsPacket(int protocolVersion) {
        var nukkitPacket = new PlayerEnchantOptionsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        var enchantOption = new PlayerEnchantOptionsPacket.EnchantOptionData(
                1, // minLevel
                0, // primarySlot
                java.util.List.of(new PlayerEnchantOptionsPacket.EnchantData(0, 1)),
                java.util.List.of(),
                java.util.List.of(),
                "test_enchant",
                1 // enchantNetId
        );
        nukkitPacket.options.add(enchantOption);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket.class);

        assertEquals(1, cbPacket.getOptions().size());
        var option = cbPacket.getOptions().get(0);
        assertEquals(1, option.getCost());
        assertEquals(0, option.getPrimarySlot());
        assertEquals("test_enchant", option.getEnchantName());
        assertEquals(1, option.getEnchantNetId());
    }

    // ==================== AddPlayerPacket ====================

    static Stream<Arguments> versionsFrom291() {
        return filteredVersions(291);
    }

    @ParameterizedTest(name = "AddPlayerPacket v{0}")
    @MethodSource("versionsFrom291")
    void testAddPlayerPacket(int protocolVersion) {
        var nukkitPacket = new AddPlayerPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        nukkitPacket.username = "TestPlayer";
        nukkitPacket.entityUniqueId = 1;
        nukkitPacket.entityRuntimeId = 1;
        nukkitPacket.platformChatId = "";
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.speedX = 0.0f;
        nukkitPacket.speedY = 0.0f;
        nukkitPacket.speedZ = 0.0f;
        nukkitPacket.pitch = 0.0f;
        nukkitPacket.yaw = 90.0f;
        nukkitPacket.headYaw = 90.0f;
        nukkitPacket.item = Item.AIR_ITEM;
        nukkitPacket.gameType = 0;
        nukkitPacket.metadata = new EntityMetadata();
        nukkitPacket.deviceId = "";
        nukkitPacket.buildPlatform = -1;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket.class);

        assertEquals("TestPlayer", cbPacket.getUsername());
        assertEquals(1, cbPacket.getRuntimeEntityId());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
    }

    // ==================== PlayerSkinPacket ====================

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    @ParameterizedTest(name = "PlayerSkinPacket v{0} (>=388)")
    @MethodSource("versionsFrom388")
    void testPlayerSkinPacket(int protocolVersion) {
        var nukkitPacket = new PlayerSkinPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        nukkitPacket.newSkinName = "newSkin";
        nukkitPacket.oldSkinName = "oldSkin";

        // Create a minimal valid skin
        var skin = new Skin();
        skin.setSkinId("test_skin_id");
        skin.setSkinData(new byte[64 * 32 * 4]); // 64x32 skin
        skin.setCapeData(new byte[0]);
        skin.setGeometryName("");
        skin.setGeometryData("");
        skin.setSkinResourcePatch("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}");
        skin.setTrusted(true);
        nukkitPacket.skin = skin;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket.class);

        assertEquals("newSkin", cbPacket.getNewSkinName());
        assertEquals("oldSkin", cbPacket.getOldSkinName());
    }
}
