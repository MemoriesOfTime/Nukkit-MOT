package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerCreativeDownwardCollisionTest {

    @Test
    void creativeDownwardCollisionKeepsLastSafePositionAndCorrectsClient() {
        Level level = mockingLevel(true);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(64, player.y, 0.000001);
        assertEquals(64, player.getBoundingBox().getMinY(), 0.000001);
        assertNotNull(player.forceMovement);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
    }

    @Test
    void creativeDownwardMovementIntoAirRemainsAccepted() {
        Level level = mockingLevel(false);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(63, player.y, 0.000001);
        assertNull(player.forceMovement);
        assertEquals(-1, player.lastSendMode);
    }

    @Test
    void activeCreativeFlightStillCannotEnterLiveFloor() {
        Level level = mockingLevel(true);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.adventureSettings.set(AdventureSettings.Type.FLYING, true);

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(64, player.y, 0.000001);
        assertNotNull(player.forceMovement);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
    }

    @Test
    void globalAllowFlightCannotBypassCreativeFloorProbe() {
        Level level = mockingLevel(true);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        Mockito.when(MockServer.get().getAllowFlight()).thenReturn(true);

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(64, player.y, 0.000001);
        assertEquals(64, player.getBoundingBox().getMinY(), 0.000001);
        assertNotNull(player.forceMovement);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
    }

    @Test
    void sideCollisionAboveFeetDuringDescentDoesNotMasqueradeAsFloorCollision() {
        Level level = mockingLevel(false);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenAnswer(
                        invocation -> {
                            cn.nukkit.math.AxisAlignedBB box = invocation.getArgument(1);
                            return box.getMaxY() - box.getMinY() > 0.1;
                        });
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;

        player.driveHandleMovement(new Vector3(0.4, 63.8, 0));

        assertEquals(0.4, player.x, 0.000001);
        assertEquals(63.8, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void diagonalDescentProbesFeetAtTargetColumn() {
        Level level = mockingLevel(false);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenAnswer(
                        invocation -> {
                            cn.nukkit.math.AxisAlignedBB box = invocation.getArgument(1);
                            return box.getMinX() > 0 && box.getMaxY() - box.getMinY() < 0.1;
                        });
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;

        player.driveHandleMovement(new Vector3(0.4, 63, 0));

        assertEquals(0, player.x, 0.000001);
        assertEquals(64, player.y, 0.000001);
        assertNotNull(player.forceMovement);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
    }

    @Test
    void spectatorDownwardMovementKeepsNoClipSemantics() {
        Level level = mockingLevel(true);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.SPECTATOR;
        player.noClip = true;

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(63, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void ridingCreativePlayerKeepsVehicleMovementSemantics() {
        Level level = mockingLevel(true);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.riding = Mockito.mock(Entity.class);

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(63, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void survivalCollisionBehaviorIsUnchanged() {
        Level level = mockingLevel(true);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.SURVIVAL;

        player.driveHandleMovement(new Vector3(0, 63, 0));

        assertEquals(63, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    private static Level mockingLevel(boolean collision) {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(319);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenReturn(collision);
        return level;
    }

    private static TestPlayer createPlayer(Level level) {
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(chunk.isGenerated()).thenReturn(true);

        Server server = MockServer.get();
        Mockito.when(server.getDefaultLevel()).thenReturn(level);
        Mockito.when(server.getViewDistance()).thenReturn(1);
        Mockito.when(server.getAllowFlight()).thenReturn(false);
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));

        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        TestPlayer player = new TestPlayer(sourceInterface);
        player.level = level;
        player.chunk = chunk;
        player.spawned = true;
        player.markAlive();
        player.temporalVector = new Vector3();
        player.adventureSettings = new AdventureSettings(player);
        player.boundingBox = new SimpleAxisAlignedBB(-0.3, 64, -0.3, 0.3, 65.8, 0.3);
        player.moveTo(0, 64, 0);
        player.lastX = 0;
        player.lastY = 64;
        player.lastZ = 0;
        player.lastYaw = 0;
        player.lastPitch = 0;
        player.yaw = 0;
        player.pitch = 0;
        player.headYaw = 0;
        player.firstMove = true;
        return player;
    }

    private static final class TestPlayer extends Player {

        private int lastSendMode = -1;

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void driveHandleMovement(Vector3 clientPos) {
            this.handleMovement(clientPos, 1);
        }

        private void moveTo(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.boundingBox.setBounds(
                    x - 0.3, y, z - 0.3, x + 0.3, y + this.getHeight(), z + 0.3);
        }

        private void markAlive() {
            this.health = 20;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            return true;
        }

        @Override
        public void sendPosition(
                double x,
                double y,
                double z,
                double yaw,
                double pitch,
                double headYaw,
                int mode,
                java.util.Collection<Player> targets) {
            this.lastSendMode = mode;
        }

        @Override
        public void sendPosition(
                Vector3 pos,
                double yaw,
                double pitch,
                double headYaw,
                int mode,
                Player[] targets) {
            this.lastSendMode = mode;
        }

        @Override
        protected void checkChunks() {
        }

        @Override
        protected void broadcastMovement() {
        }

        @Override
        protected void updateFallState(boolean onGround) {
        }

        @Override
        public void checkSwimmingState() {
        }

        @Override
        public void spawnToAll() {
        }
    }
}
