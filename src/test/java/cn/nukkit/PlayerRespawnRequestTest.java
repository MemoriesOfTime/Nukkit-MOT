package cn.nukkit;

import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.process.processor.v113.PlayerActionProcessor_v113;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRespawnRequestTest {

    @Test
    void respawnRequestRequiresSpawnedDeadOnlinePlayer() {
        TestPlayer player = createPlayer();
        player.spawned = true;
        player.alive = true;
        player.online = true;

        assertFalse(player.handleRespawnRequest());
        assertEquals(0, player.respawnCalls);

        player.alive = false;

        assertTrue(player.handleRespawnRequest());
        assertEquals(1, player.respawnCalls);
    }

    @Test
    void legacyPlayerActionRespawnUsesCommonRespawnRequest() {
        TestPlayer player = createPlayer();
        player.spawned = true;
        player.alive = false;
        player.online = true;

        PlayerActionPacket packet = new PlayerActionPacket();
        packet.action = PlayerActionPacket.ACTION_RESPAWN;

        PlayerActionProcessor_v113.INSTANCE.handle(new PlayerHandle(player), packet);

        assertEquals(1, player.respawnCalls);
    }

    private static TestPlayer createPlayer() {
        MockServer.reset();

        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        return new TestPlayer(sourceInterface);
    }

    private static final class TestPlayer extends Player {

        private boolean alive;
        private boolean online;
        private int respawnCalls;

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        @Override
        public boolean isAlive() {
            return this.alive;
        }

        @Override
        public boolean isOnline() {
            return this.online;
        }

        @Override
        protected void respawn() {
            this.respawnCalls++;
        }
    }
}
