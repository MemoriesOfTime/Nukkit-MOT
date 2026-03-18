package cn.nukkit.network.protocol.regression.decode;

import cn.nukkit.MockServer;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.PlayerAbility;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode regression tests for client-to-server action/control packets.
 * CB Protocol encodes, Nukkit-MOT decodes.
 */
public class ClientActionDecodeRegressionTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsPreV800() {
        return filteredVersionsRange(291, ProtocolInfo.v1_21_80);
    }

    static Stream<Arguments> versionsFrom340() {
        return filteredVersions(340);
    }

    static Stream<Arguments> versionsFrom388() {
        return filteredVersions(388);
    }

    static Stream<Arguments> versionsPreV649() {
        return filteredVersionsRange(291, ProtocolInfo.v1_20_60);
    }

    static Stream<Arguments> versionsFrom554() {
        return filteredVersions(554);
    }

    static Stream<Arguments> versionsFrom544() {
        return filteredVersions(ProtocolInfo.v1_19_20);
    }

    static Stream<Arguments> versionsFrom527() {
        return filteredVersions(527);
    }

    static Stream<Arguments> versionsFrom712() {
        return filteredVersions(712);
    }

    // ==================== ItemFrameDropItemPacket ====================

    @ParameterizedTest(name = "ItemFrameDropItemPacket v{0}")
    @MethodSource("versionsPreV649")
    void itemFrameDropItem(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ItemFrameDropItemPacket();
        cb.setBlockPosition(Vector3i.from(7, 63, -3));

        ItemFrameDropItemPacket nk = crossEncode(cb, ItemFrameDropItemPacket::new, protocol);

        assertEquals(7, nk.x);
        assertEquals(63, nk.y);
        assertEquals(-3, nk.z);
    }

    // ==================== PlayerInputPacket ====================

    @ParameterizedTest(name = "PlayerInputPacket v{0}")
    @MethodSource("versionsPreV800")
    void playerInput(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.PlayerInputPacket();
        cb.setInputMotion(org.cloudburstmc.math.vector.Vector2f.from(0.5f, -0.75f));
        cb.setJumping(true);
        cb.setSneaking(false);

        PlayerInputPacket nk = crossEncode(cb, PlayerInputPacket::new, protocol);

        assertEquals(0.5f, nk.motionX, 0.001f);
        assertEquals(-0.75f, nk.motionY, 0.001f);
        assertTrue(nk.jumping);
        assertFalse(nk.sneaking);
    }

    // ==================== RequestNetworkSettingsPacket ====================

    @ParameterizedTest(name = "RequestNetworkSettingsPacket v{0}")
    @MethodSource("versionsFrom554")
    void requestNetworkSettings(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket();
        cb.setProtocolVersion(protocol);

        RequestNetworkSettingsPacket nk = crossEncode(cb, RequestNetworkSettingsPacket::new, protocol);

        assertEquals(protocol, nk.protocolVersion);
    }

    // ==================== SettingsCommandPacket ====================

    @ParameterizedTest(name = "SettingsCommandPacket v{0}")
    @MethodSource("versionsFrom388")
    void settingsCommand(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.SettingsCommandPacket();
        cb.setCommand("/gamerule sendcommandfeedback true");
        cb.setSuppressingOutput(false);

        SettingsCommandPacket nk = crossEncode(cb, SettingsCommandPacket::new, protocol);

        assertEquals("/gamerule sendcommandfeedback true", nk.command);
        assertFalse(nk.suppressOutput);
    }

    // ==================== ServerboundLoadingScreenPacket ====================

    @ParameterizedTest(name = "ServerboundLoadingScreenPacket v{0}")
    @MethodSource("versionsFrom712")
    void serverboundLoadingScreen(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket();
        cb.setType(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN);
        cb.setLoadingScreenId(42);

        ServerboundLoadingScreenPacket nk = crossEncode(cb, ServerboundLoadingScreenPacket::new, protocol);

        // START_LOADING_SCREEN has ordinal 1 in the CB enum, which is the raw wire value NK stores
        assertEquals(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN.ordinal(), nk.loadingScreenType);
        assertEquals(42, nk.loadingScreenId);
    }

    // ==================== LecternUpdatePacket ====================

    @ParameterizedTest(name = "LecternUpdatePacket v{0}")
    @MethodSource("versionsFrom340")
    void lecternUpdate(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket();
        cb.setPage(3);
        cb.setTotalPages(10);
        cb.setBlockPosition(Vector3i.from(5, 60, -10));
        cb.setDroppingBook(true);

        LecternUpdatePacket nk = crossEncode(cb, LecternUpdatePacket::new, protocol);

        assertEquals(3, nk.page);
        assertEquals(5, nk.blockPosition.x);
        assertEquals(60, nk.blockPosition.y);
        assertEquals(-10, nk.blockPosition.z);
        if (protocol >= ProtocolInfo.v1_11_0) {
            assertEquals(10, nk.totalPages);
        }
        if (protocol < ProtocolInfo.v1_20_70) {
            assertTrue(nk.dropBook);
        }
    }

    // ==================== RequestAbilityPacket ====================

    @ParameterizedTest(name = "RequestAbilityPacket v{0}")
    @MethodSource("versionsFrom527")
    void requestAbility(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket();
        cb.setAbility(Ability.FLYING);
        cb.setType(Ability.Type.BOOLEAN);
        cb.setBoolValue(true);
        cb.setFloatValue(0.0f);

        RequestAbilityPacket nk = crossEncode(cb, RequestAbilityPacket::new, protocol);

        assertEquals(PlayerAbility.FLYING, nk.ability);
        assertEquals(RequestAbilityPacket.AbilityType.BOOLEAN, nk.type);
        assertTrue(nk.boolValue);
    }

    // ==================== ClientCacheStatusPacket ====================

    @ParameterizedTest(name = "ClientCacheStatusPacket v{0}")
    @MethodSource("versionsFrom388")
    void clientCacheStatus(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket();
        cb.setSupported(true);

        ClientCacheStatusPacket nk = crossEncode(cb, ClientCacheStatusPacket::new, protocol);

        assertTrue(nk.supported);
    }

    // ==================== MapInfoRequestPacket ====================

    @ParameterizedTest(name = "MapInfoRequestPacket v{0}")
    @MethodSource("allVersions")
    void mapInfoRequest(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.MapInfoRequestPacket();
        cb.setUniqueMapId(12345678L);

        MapInfoRequestPacket nk = crossEncode(cb, MapInfoRequestPacket::new, protocol);

        assertEquals(12345678L, nk.mapId);
    }

    // ==================== CraftingEventPacket ====================

    @ParameterizedTest(name = "CraftingEventPacket v{0}")
    @MethodSource("allVersions")
    void craftingEvent(int protocol) {
        UUID recipeId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        var cb = new org.cloudburstmc.protocol.bedrock.packet.CraftingEventPacket();
        cb.setContainerId((byte) 1);
        cb.setType(org.cloudburstmc.protocol.bedrock.data.inventory.CraftingType.CRAFTING);
        cb.setUuid(recipeId);
        cb.getInputs().add(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        cb.getOutputs().add(org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);

        CraftingEventPacket nk = crossEncode(cb, CraftingEventPacket::new, protocol);

        assertEquals(1, nk.windowId);
        assertEquals(2, nk.type);
        assertEquals(recipeId, nk.id);
        assertEquals(1, nk.input.length);
        assertEquals(1, nk.output.length);
        assertTrue(nk.input[0].isNull());
        assertTrue(nk.output[0].isNull());
    }

    // ==================== ModalFormResponsePacket ====================

    @ParameterizedTest(name = "ModalFormResponsePacket submit v{0}")
    @MethodSource("allVersions")
    void modalFormResponseSubmit(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket();
        cb.setFormId(7);
        cb.setFormData("{\"key\":true}");
        cb.setCancelReason(Optional.empty());

        ModalFormResponsePacket nk = crossEncode(cb, ModalFormResponsePacket::new, protocol);

        assertEquals(7, nk.formId);
        assertEquals("{\"key\":true}", nk.data);
    }

    @ParameterizedTest(name = "ModalFormResponsePacket cancelled v{0}")
    @MethodSource("versionsFrom544")
    void modalFormResponseCancelled(int protocol) {
        var cb = new org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket();
        cb.setFormId(9);
        cb.setFormData(null);
        cb.setCancelReason(Optional.of(
                org.cloudburstmc.protocol.bedrock.data.ModalFormCancelReason.USER_CLOSED));

        ModalFormResponsePacket nk = crossEncode(cb, ModalFormResponsePacket::new, protocol);

        assertEquals(9, nk.formId);
        // null formData → NK retains default "null"
        assertEquals("null", nk.data);
        // USER_CLOSED ordinal = 0
        assertEquals(org.cloudburstmc.protocol.bedrock.data.ModalFormCancelReason.USER_CLOSED.ordinal(),
                nk.cancelReason);
    }

}
