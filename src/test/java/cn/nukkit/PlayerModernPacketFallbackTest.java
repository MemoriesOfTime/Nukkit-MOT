package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerModernPacketFallbackTest {
    @Test
    void unhandledModernIdsNeverProbeLegacyPidAcrossClientWindow() throws Exception {
        MockServer.init();
        for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS) {
            if (protocol < ProtocolInfo.v1_20_0 || protocol > ProtocolInfo.v1_26_50) continue;
            Player player = player();
            for (int id : new int[]{0x100, 307, 312, 321, 0x100fa}) {
                ProbePacket packet = new ProbePacket(id, true);
                packet.protocol = protocol;
                player.handleDataPacket(packet);
                assertEquals(0, packet.legacyCalls, "modern id=" + id + " protocol=" + protocol);
            }
            ProbePacket legacy = new ProbePacket(0xfa, false);
            legacy.protocol = protocol;
            player.handleDataPacket(legacy);
            assertEquals(1, legacy.legacyCalls, "legacy fallback must remain reachable");
        }
    }

    private static Player player() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        player.connected = true;
        player.loginVerified = true;
        player.loggedIn = true;
        Field field = Entity.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(player, MockServer.get());
        return player;
    }

    private static class ProbePacket extends DataPacket {
        private final int id;
        private final boolean modern;
        int legacyCalls;
        ProbePacket(int id, boolean modern) { this.id = id; this.modern = modern; }
        @Override public int packetId() { return id; }
        @Override public byte pid() {
            legacyCalls++;
            if (modern) throw new AssertionError("modern pid() must never be called");
            return (byte) id;
        }
        @Override public void decode() { }
        @Override public void encode() { }
    }
}
