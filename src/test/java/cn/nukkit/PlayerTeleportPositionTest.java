package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.session.NetworkPlayerSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTeleportPositionTest {

    @Test
    void checkTeleportPositionIgnoresUnsentDiagonalsWhenChunkRadiusIsOne() {
        TestPlayer player = createPlayer(1);
        player.spawned = true;
        player.teleportPosition = new Location(8, 64, 8, Mockito.mock(Level.class));
        markCrossChunksSent(player, 0, 0);

        assertTrue(player.checkTeleportPosition());
        assertNull(player.teleportPosition);
    }

    @Test
    void checkTeleportPositionStillRequiresDiagonalsWhenChunkRadiusAllowsThreeByThree() {
        TestPlayer player = createPlayer(2);
        player.spawned = true;
        player.teleportPosition = new Location(8, 64, 8, Mockito.mock(Level.class));
        markCrossChunksSent(player, 0, 0);

        assertFalse(player.checkTeleportPosition());
        assertNotNull(player.teleportPosition);
    }

    private static TestPlayer createPlayer(int chunkRadius) {
        MockServer.reset();
        Server server = MockServer.get();
        Mockito.when(server.getViewDistance()).thenReturn(chunkRadius);

        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        TestPlayer player = new TestPlayer(sourceInterface);
        player.chunkRadius = chunkRadius;
        return player;
    }

    private static void markCrossChunksSent(Player player, int chunkX, int chunkZ) {
        markChunkSent(player, chunkX, chunkZ);
        markChunkSent(player, chunkX + 1, chunkZ);
        markChunkSent(player, chunkX - 1, chunkZ);
        markChunkSent(player, chunkX, chunkZ + 1);
        markChunkSent(player, chunkX, chunkZ - 1);
    }

    private static void markChunkSent(Player player, int chunkX, int chunkZ) {
        player.usedChunks.put(Level.chunkHash(chunkX, chunkZ), Boolean.TRUE);
    }

    private static final class TestPlayer extends Player {

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        @Override
        public void spawnToAll() {
        }
    }
}
