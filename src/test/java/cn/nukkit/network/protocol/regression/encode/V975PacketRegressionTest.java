package cn.nukkit.network.protocol.regression.encode;

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
 * Cross-decode regression tests for v975 (1.26.20) new packets:
 * ServerStoreInfoPacket and ServerPresenceInfoPacket.
 *
 * These are server→client packets, so we test NK encode → CB decode.
 */
public class V975PacketRegressionTest extends AbstractPacketRegressionTest {

    static Stream<Arguments> v975Only() {
        return Stream.of(Arguments.of(975));
    }

    // --- ServerStoreInfoPacket ---

    @ParameterizedTest(name = "ServerStoreInfoPacket v{0}")
    @MethodSource("v975Only")
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
    @MethodSource("v975Only")
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
    @MethodSource("v975Only")
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
    @MethodSource("v975Only")
    void serverPresenceInfoPacket_nullConfig(int protocolVersion) {
        ServerPresenceInfoPacket nukkitPacket = new ServerPresenceInfoPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.presenceConfiguration = null;
        nukkitPacket.encode();

        org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket cbPacket =
                crossDecode(nukkitPacket, org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket.class);

        assertNull(cbPacket.getPresenceConfiguration(), "PresenceConfiguration should be null");
    }
}
