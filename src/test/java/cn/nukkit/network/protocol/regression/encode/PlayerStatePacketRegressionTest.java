package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.GameType;
import cn.nukkit.network.protocol.types.hub.HudElement;
import cn.nukkit.network.protocol.types.hub.HudVisibility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for player state related packets.
 */
public class PlayerStatePacketRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsFrom407() {
        return filteredVersions(407);
    }

    static Stream<Arguments> versionsFrom534() {
        return filteredVersions(534);
    }

    static Stream<Arguments> versionsFrom630() {
        return filteredVersions(630);
    }

    static Stream<Arguments> versionsFrom649() {
        return filteredVersions(649);
    }

    static Stream<Arguments> versionsFrom671() {
        return filteredVersions(671);
    }

    // ==================== UpdatePlayerGameTypePacket ====================

    @ParameterizedTest(name = "UpdatePlayerGameTypePacket v{0}")
    @MethodSource("versionsFrom407")
    void testUpdatePlayerGameTypePacket(int protocolVersion) {
        var nukkitPacket = new UpdatePlayerGameTypePacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.gameType = GameType.CREATIVE;
        nukkitPacket.entityId = 12345L;
        if (protocolVersion >= ProtocolInfo.v1_20_80) {
            nukkitPacket.tick = 100L;
        }
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.GameType.CREATIVE, cbPacket.getGameType());
        assertEquals(12345L, cbPacket.getEntityId());
        if (protocolVersion >= ProtocolInfo.v1_20_80) {
            assertEquals(100L, cbPacket.getTick());
        }
    }

    // ==================== UpdateAdventureSettingsPacket ====================

    @ParameterizedTest(name = "UpdateAdventureSettingsPacket v{0}")
    @MethodSource("versionsFrom534")
    void testUpdateAdventureSettingsPacket(int protocolVersion) {
        var nukkitPacket = new UpdateAdventureSettingsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setNoPvM(true);
        nukkitPacket.setNoMvP(false);
        nukkitPacket.setImmutableWorld(true);
        nukkitPacket.setShowNameTags(true);
        nukkitPacket.setAutoJump(false);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket.class);

        assertTrue(cbPacket.isNoPvM());
        assertFalse(cbPacket.isNoMvP());
        assertTrue(cbPacket.isImmutableWorld());
        assertTrue(cbPacket.isShowNameTags());
        assertFalse(cbPacket.isAutoJump());
    }

    // ==================== SetHudPacket ====================

    @ParameterizedTest(name = "SetHudPacket v{0}")
    @MethodSource("versionsFrom649")
    void testSetHudPacket(int protocolVersion) {
        var nukkitPacket = new SetHudPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.elements.add(HudElement.PAPER_DOLL);
        nukkitPacket.elements.add(HudElement.HEALTH);
        nukkitPacket.visibility = HudVisibility.HIDE;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetHudPacket.class);

        assertEquals(2, cbPacket.getElements().size());
        assertTrue(cbPacket.getElements().contains(org.cloudburstmc.protocol.bedrock.data.HudElement.PAPER_DOLL));
        assertTrue(cbPacket.getElements().contains(org.cloudburstmc.protocol.bedrock.data.HudElement.HEALTH));
        assertEquals(org.cloudburstmc.protocol.bedrock.data.HudVisibility.HIDE, cbPacket.getVisibility());
    }

    // ==================== UpdateAbilitiesPacket ====================

    @ParameterizedTest(name = "UpdateAbilitiesPacket v{0}")
    @MethodSource("versionsFrom534")
    void testUpdateAbilitiesPacket(int protocolVersion) {
        var nukkitPacket = new UpdateAbilitiesPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.setEntityId(12345L);
        nukkitPacket.setPlayerPermission(UpdateAbilitiesPacket.PlayerPermission.OPERATOR);
        nukkitPacket.setCommandPermission(UpdateAbilitiesPacket.CommandPermission.OPERATOR);

        var abilityLayer = new cn.nukkit.network.protocol.types.AbilityLayer();
        abilityLayer.setLayerType(cn.nukkit.network.protocol.types.AbilityLayer.Type.BASE);
        abilityLayer.getAbilitiesSet().addAll(java.util.EnumSet.of(
                cn.nukkit.network.protocol.types.PlayerAbility.BUILD,
                cn.nukkit.network.protocol.types.PlayerAbility.MINE,
                cn.nukkit.network.protocol.types.PlayerAbility.MAY_FLY,
                cn.nukkit.network.protocol.types.PlayerAbility.FLY_SPEED,
                cn.nukkit.network.protocol.types.PlayerAbility.WALK_SPEED
        ));
        abilityLayer.getAbilityValues().addAll(java.util.EnumSet.of(
                cn.nukkit.network.protocol.types.PlayerAbility.BUILD,
                cn.nukkit.network.protocol.types.PlayerAbility.MINE
        ));
        abilityLayer.setFlySpeed(0.05f);
        abilityLayer.setWalkSpeed(0.1f);
        nukkitPacket.getAbilityLayers().add(abilityLayer);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket.class);

        assertEquals(12345L, cbPacket.getUniqueEntityId());
        assertNotNull(cbPacket.getAbilityLayers());
        assertFalse(cbPacket.getAbilityLayers().isEmpty());
    }

    // ==================== UpdateClientInputLocksPacket ====================

    @ParameterizedTest(name = "UpdateClientInputLocksPacket v{0}")
    @MethodSource("versionsFrom671")
    void testUpdateClientInputLocksPacket(int protocolVersion) {
        var nukkitPacket = new UpdateClientInputLocksPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.inputLockType = java.util.EnumSet.of(
                UpdateClientInputLocksPacket.InputLockType.CAMERA,
                UpdateClientInputLocksPacket.InputLockType.MOVEMENT
        );
        nukkitPacket.serverPosition = new cn.nukkit.math.Vector3f(100.5f, 64.0f, 200.5f);
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket.class);

        // CAMERA (1<<1=2) | MOVEMENT (1<<2=4) = 6
        assertEquals(6, cbPacket.getLockComponentData());
        if (protocolVersion < ProtocolInfo.v1_26_10) {
            assertEquals(100.5f, cbPacket.getServerPosition().getX(), 0.001f);
            assertEquals(64.0f, cbPacket.getServerPosition().getY(), 0.001f);
            assertEquals(200.5f, cbPacket.getServerPosition().getZ(), 0.001f);
        }
    }

    // ==================== SetPlayerInventoryOptionsPacket ====================

    @ParameterizedTest(name = "SetPlayerInventoryOptionsPacket v{0}")
    @MethodSource("versionsFrom630")
    void testSetPlayerInventoryOptionsPacket(int protocolVersion) {
        var nukkitPacket = new SetPlayerInventoryOptionsPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.leftTab = cn.nukkit.network.protocol.types.inventory.InventoryTabLeft.NONE;
        nukkitPacket.rightTab = cn.nukkit.network.protocol.types.inventory.InventoryTabRight.NONE;
        nukkitPacket.filtering = false;
        nukkitPacket.layout = cn.nukkit.network.protocol.types.inventory.InventoryLayout.NONE;
        nukkitPacket.craftingLayout = cn.nukkit.network.protocol.types.inventory.InventoryLayout.NONE;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket.class);

        assertEquals(org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabLeft.NONE, cbPacket.getLeftTab());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabRight.NONE, cbPacket.getRightTab());
    }
}
