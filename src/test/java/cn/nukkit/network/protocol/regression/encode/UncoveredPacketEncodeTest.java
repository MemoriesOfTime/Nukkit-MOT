package cn.nukkit.network.protocol.regression.encode;

import cn.nukkit.MockServer;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.regression.AbstractPacketRegressionTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encode tests for packets that previously had no encode-side coverage at all
 * (GUIDataPickItem, AgentAnimation, TickingAreasLoadStatus, RecordStarted).
 */
public class UncoveredPacketEncodeTest extends AbstractPacketRegressionTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    static Stream<Arguments> versionsFrom503() {
        return filteredVersions(503);
    }

    static Stream<Arguments> versionsFrom594() {
        return filteredVersions(594);
    }

    static Stream<Arguments> versionsFrom2192() {
        return filteredVersions(ProtocolInfo.v1_26_50_27);
    }

    @ParameterizedTest(name = "GUIDataPickItemPacket v{0}")
    @MethodSource("allVersions")
    void testGUIDataPickItemPacket(int protocolVersion) {
        var nukkitPacket = new GUIDataPickItemPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.itemName = "minecraft:emerald";
        nukkitPacket.itemEffects = "minecraft:night_vision";
        nukkitPacket.hotbarSlot = 7;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.GuiDataPickItemPacket.class);

        assertEquals("minecraft:emerald", cbPacket.getDescription());
        assertEquals("minecraft:night_vision", cbPacket.getItemEffects());
        assertEquals(7, cbPacket.getHotbarSlot());
    }

    @ParameterizedTest(name = "AgentAnimationPacket v{0}")
    @MethodSource("versionsFrom594")
    void testAgentAnimationPacket(int protocolVersion) {
        var nukkitPacket = new AgentAnimationPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.animation = 3;
        nukkitPacket.runtimeEntityId = 12345L;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.AgentAnimationPacket.class);

        assertEquals(3, cbPacket.getAnimation());
        assertEquals(12345L, cbPacket.getRuntimeEntityId());
    }

    @ParameterizedTest(name = "TickingAreasLoadStatusPacket v{0}")
    @MethodSource("versionsFrom503")
    void testTickingAreasLoadStatusPacket(int protocolVersion) {
        var nukkitPacket = new TickingAreasLoadStatusPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        nukkitPacket.waitingForPreload = true;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.TickingAreasLoadStatusPacket.class);

        assertTrue(cbPacket.isWaitingForPreload());
    }

    @ParameterizedTest(name = "RecordStartedPacket v{0}")
    @MethodSource("versionsFrom2192")
    void testRecordStartedPacket(int protocolVersion) {
        var nukkitPacket = new RecordStartedPacket();
        nukkitPacket.protocol = protocolVersion;
        nukkitPacket.gameVersion = cn.nukkit.GameVersion.byProtocol(protocolVersion, false);
        // negative y exercises zigzag; an unsigned-y encoding would decode as double
        nukkitPacket.blockPos = new BlockVector3(10, -64, 5);
        nukkitPacket.serverSoundHandle = 0x0123456789ABCDEFL;
        nukkitPacket.encode();

        var cbPacket = crossDecode(nukkitPacket,
                org.cloudburstmc.protocol.bedrock.packet.RecordStartedPacket.class);

        assertEquals(10, cbPacket.getBlockPos().getX());
        assertEquals(-64, cbPacket.getBlockPos().getY());
        assertEquals(5, cbPacket.getBlockPos().getZ());
        assertEquals(0x0123456789ABCDEFL, cbPacket.getServerSoundHandle());
    }
}
