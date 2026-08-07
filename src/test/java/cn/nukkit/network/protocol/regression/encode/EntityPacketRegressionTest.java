package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.EntityLink;
import cn.nukkit.network.protocol.types.PropertySyncData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityPacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
        Attribute.init();
    }

    static Stream<Arguments> versionsFrom291() {
        return filteredVersions(291);
    }

    static Stream<Arguments> versionsFrom313() {
        return filteredVersions(313);
    }

    static Stream<Arguments> versionsFrom428() {
        return filteredVersions(428);
    }

    @ParameterizedTest(name = "AnimateEntityPacket v{0}")
    @MethodSource("versionsFrom428")
    void testAnimateEntityPacket(int protocolVersion) {
        var nukkitPacket = new AnimateEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.animation = "animation.player.attack.rotations";
        nukkitPacket.nextState = "default";
        nukkitPacket.stopExpression = "query.any_animation_finished";
        if (protocolVersion >= ProtocolInfo.v1_17_30) {
            nukkitPacket.stopExpressionVersion = 16777216;
        }
        nukkitPacket.controller = "__runtime_controller";
        nukkitPacket.blendOutTime = 0.0f;
        nukkitPacket.entityRuntimeIds.add(100L);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket.class);

        assertEquals("animation.player.attack.rotations", cbPacket.getAnimation());
        assertEquals(1, cbPacket.getRuntimeEntityIds().size());
        assertEquals(100L, cbPacket.getRuntimeEntityIds().get(0));
    }

    @ParameterizedTest(name = "SetEntityDataPacket v{0}")
    @MethodSource("versionsFrom313")
    void testSetEntityDataPacket(int protocolVersion) {
        var nukkitPacket = new SetEntityDataPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.eid = 42;
        nukkitPacket.metadata = new EntityMetadata();
        if (protocolVersion >= ProtocolInfo.v1_16_100) {
            nukkitPacket.frame = 0L;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket.class);

        assertEquals(42, cbPacket.getRuntimeEntityId());
    }

    // ==================== AddEntityPacket ====================

    @ParameterizedTest(name = "AddEntityPacket v{0}")
    @MethodSource("versionsFrom313")
    void testAddEntityPacket(int protocolVersion) {
        var nukkitPacket = new AddEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityUniqueId = 100;
        nukkitPacket.entityRuntimeId = 100;
        nukkitPacket.id = "minecraft:zombie";
        nukkitPacket.x = 100.5f;
        nukkitPacket.y = 64.0f;
        nukkitPacket.z = 200.5f;
        nukkitPacket.speedX = 0.0f;
        nukkitPacket.speedY = 0.0f;
        nukkitPacket.speedZ = 0.0f;
        nukkitPacket.pitch = 0.0f;
        nukkitPacket.yaw = 90.0f;
        nukkitPacket.headYaw = 90.0f;
        nukkitPacket.bodyYaw = 90.0f;
        nukkitPacket.metadata = new EntityMetadata();
        nukkitPacket.attributes = new Attribute[0];
        nukkitPacket.links = new EntityLink[0];
        nukkitPacket.properties = new PropertySyncData(new int[]{}, new float[]{});
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket.class);

        assertEquals(100, cbPacket.getUniqueEntityId());
        assertEquals(100, cbPacket.getRuntimeEntityId());
        assertEquals("minecraft:zombie", cbPacket.getIdentifier());
        assertEquals(100.5f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(64.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(200.5f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(90.0f, cbPacket.getRotation().getY(), 0.001f);
    }

    // ==================== AddItemEntityPacket ====================

    @ParameterizedTest(name = "AddItemEntityPacket v{0}")
    @MethodSource("versionsFrom291")
    void testAddItemEntityPacket(int protocolVersion) {
        var nukkitPacket = new AddItemEntityPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.entityUniqueId = 200;
        nukkitPacket.entityRuntimeId = 200;
        nukkitPacket.item = Item.AIR_ITEM;
        nukkitPacket.x = 50.0f;
        nukkitPacket.y = 65.0f;
        nukkitPacket.z = 100.0f;
        nukkitPacket.speedX = 0.1f;
        nukkitPacket.speedY = 0.2f;
        nukkitPacket.speedZ = 0.3f;
        nukkitPacket.metadata = new EntityMetadata();
        nukkitPacket.isFromFishing = false;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket.class);

        assertEquals(200, cbPacket.getUniqueEntityId());
        assertEquals(200, cbPacket.getRuntimeEntityId());
        assertEquals(50.0f, cbPacket.getPosition().getX(), 0.001f);
        assertEquals(65.0f, cbPacket.getPosition().getY(), 0.001f);
        assertEquals(100.0f, cbPacket.getPosition().getZ(), 0.001f);
        assertEquals(0.1f, cbPacket.getMotion().getX(), 0.001f);
        assertEquals(0.2f, cbPacket.getMotion().getY(), 0.001f);
        assertEquals(0.3f, cbPacket.getMotion().getZ(), 0.001f);
    }
}
