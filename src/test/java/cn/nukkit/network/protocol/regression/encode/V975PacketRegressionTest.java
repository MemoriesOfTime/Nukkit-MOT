package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ServerPresenceInfoPacket;
import cn.nukkit.network.protocol.ServerStoreInfoPacket;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import cn.nukkit.network.protocol.types.store.ClientStoreEntrypointConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-decode regression tests for v975 (1.26.20) and v1001 (1.26.30) new packets:
 * ServerStoreInfoPacket and ServerPresenceInfoPacket.
 *
 * These are server→client packets, so we test NK encode → CB decode.
 */
public class V975PacketRegressionTest extends AbstractPacketRegressionTest {

    static Stream<Arguments> v975Only() {
        return Stream.of(Arguments.of(975));
    }

    /**
     * v975 (1.26.20) and v1001 (1.26.30) both implement the ServerStoreInfoPacket
     * and the outer ServerPresenceInfoPacket envelope identically.
     */
    static Stream<Arguments> v975AndV1001() {
        return Stream.of(
                Arguments.of(975),
                Arguments.of(ProtocolInfo.v1_26_30)
        );
    }

    static Stream<Arguments> v1001Only() {
        return Stream.of(Arguments.of(ProtocolInfo.v1_26_30));
    }

    // --- ServerStoreInfoPacket ---

    @ParameterizedTest(name = "ServerStoreInfoPacket v{0}")
    @MethodSource("v975AndV1001")
    void serverStoreInfoPacket_encodeDecode(int protocolVersion) {
        ServerStoreInfoPacket nukkitPacket = new ServerStoreInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.store = new ClientStoreEntrypointConfiguration("store-id-1", "Test Store");
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerStoreInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerStoreInfoPacket.class);

        assertNotNull(cbPacket.getStore(), "Store should not be null");
        assertEquals("store-id-1", cbPacket.getStore().getStoreId());
        assertEquals("Test Store", cbPacket.getStore().getStoreName());
    }

    @ParameterizedTest(name = "ServerStoreInfoPacket null store v{0}")
    @MethodSource("v975AndV1001")
    void serverStoreInfoPacket_nullStore(int protocolVersion) {
        ServerStoreInfoPacket nukkitPacket = new ServerStoreInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.store = null;
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerStoreInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerStoreInfoPacket.class);

        assertNull(cbPacket.getStore(), "Store should be null");
    }

    // --- ServerPresenceInfoPacket ---

    @ParameterizedTest(name = "ServerPresenceInfoPacket v{0}")
    @MethodSource("v975AndV1001")
    void serverPresenceInfoPacket_encodeDecode(int protocolVersion) {
        ServerPresenceInfoPacket nukkitPacket = new ServerPresenceInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.presenceConfiguration = new ServerPresenceInfoPacket.PresenceConfiguration("exp-name", "world-name");
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket.class);

        assertNotNull(cbPacket.getPresenceConfiguration(), "PresenceConfiguration should not be null");
        assertEquals("exp-name", cbPacket.getPresenceConfiguration().getExperienceName());
        assertEquals("world-name", cbPacket.getPresenceConfiguration().getWorldName());
    }

    @ParameterizedTest(name = "ServerPresenceInfoPacket null config v{0}")
    @MethodSource("v975AndV1001")
    void serverPresenceInfoPacket_nullConfig(int protocolVersion) {
        ServerPresenceInfoPacket nukkitPacket = new ServerPresenceInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.presenceConfiguration = null;
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket.class);

        assertNull(cbPacket.getPresenceConfiguration(), "PresenceConfiguration should be null");
    }

    // --- ServerPresenceInfoPacket v1001 specific (richPresenceId + optional names) ---

    @ParameterizedTest(name = "ServerPresenceInfoPacket v{0} rich presence id and all fields")
    @MethodSource("v1001Only")
    void serverPresenceInfoPacket_v1001RichPresenceId(int protocolVersion) {
        ServerPresenceInfoPacket nukkitPacket = new ServerPresenceInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.presenceConfiguration = new ServerPresenceInfoPacket.PresenceConfiguration(
                "exp-name", "world-name", "rich-presence-1"
        );
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket.class);

        assertNotNull(cbPacket.getPresenceConfiguration(), "PresenceConfiguration should not be null");
        assertEquals("exp-name", cbPacket.getPresenceConfiguration().getExperienceName());
        assertEquals("world-name", cbPacket.getPresenceConfiguration().getWorldName());
        assertEquals("rich-presence-1", cbPacket.getPresenceConfiguration().getRichPresenceId());
    }

    @ParameterizedTest(name = "ServerPresenceInfoPacket v{0} null names with rich presence id")
    @MethodSource("v1001Only")
    void serverPresenceInfoPacket_v1001NullNames(int protocolVersion) {
        ServerPresenceInfoPacket nukkitPacket = new ServerPresenceInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.presenceConfiguration = new ServerPresenceInfoPacket.PresenceConfiguration(
                null, null, "rich-presence-2"
        );
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket.class);

        assertNotNull(cbPacket.getPresenceConfiguration(), "PresenceConfiguration should not be null");
        assertNull(cbPacket.getPresenceConfiguration().getExperienceName());
        assertNull(cbPacket.getPresenceConfiguration().getWorldName());
        assertEquals("rich-presence-2", cbPacket.getPresenceConfiguration().getRichPresenceId());
    }
}
