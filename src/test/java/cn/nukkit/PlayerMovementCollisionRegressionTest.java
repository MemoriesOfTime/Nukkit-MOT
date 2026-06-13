package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.SetEntityMotionPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMovementCollisionRegressionTest {

    @Test
    void blockedMovementTriggersRevertAndKeepsServerPosition() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);

        assertEquals(0, player.x, 0.000001);
        assertEquals(64, player.y, 0.000001);
        assertEquals(0, player.z, 0.000001);
        assertNotNull(player.forceMovement, "blocked movement must set forceMovement so the client is pulled back");
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode, "blocked movement should go through the unified revert correction path");
        assertFalse(player.checkChunksCalled, "blocked movement must not proceed with chunk state updates");
        assertFalse(player.updateFallStateCalled, "blocked movement must not proceed with landing/fall state updates");
    }

    @Test
    void bottomHalfCollisionWithinStepHeightStillBlocksMovement() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenAnswer(invocation -> {
                    AxisAlignedBB bb = invocation.getArgument(1);
                    return bb.getMinY() < 64.5;
                });

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.2, 64, 0), 1);

        assertNotNull(player.forceMovement, "destination collision at the feet must not be hidden by stepHeight Y shrink");
        assertEquals(0, player.x, 0.000001);
        assertEquals(64, player.y, 0.000001);
        assertEquals(0, player.z, 0.000001);
    }

    @Test
    void collisionAboveStepHeightStillBlocksMovement() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        // A block intruding at chest/head height (above stepHeight) must still trigger a revert.
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenAnswer(invocation -> {
                    SimpleAxisAlignedBB bb = (SimpleAxisAlignedBB) invocation.getArgument(1);
                    return bb.getMaxY() > 65.0;
                });

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.2, 64, 0), 1);

        assertNotNull(player.forceMovement, "blocks above stepHeight must be caught by anti-noclip");
        assertEquals(0, player.x, 0.000001);
        assertEquals(64, player.y, 0.000001);
        assertEquals(0, player.z, 0.000001);
    }

    @Test
    void collapsedLowProfileProbeStillBlocksMovement() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenAnswer(invocation -> {
                    AxisAlignedBB bb = invocation.getArgument(1);
                    return bb.getMaxY() > bb.getMinY() && bb.getMinY() < 64.6 && bb.getMaxY() > 64.1;
                });

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);
        player.setSwimming(true);

        player.driveHandleMovement(new Vector3(0.2, 64, 0), 1);

        assertNotNull(player.forceMovement, "low-profile player collision probe must not collapse and skip destination collisions");
        assertEquals(0, player.x, 0.000001);
        assertEquals(64, player.y, 0.000001);
        assertEquals(0, player.z, 0.000001);
    }

    @Test
    void recentKnockbackWithoutServerMotionDoesNotBypassSpeedCheck() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(false);

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);
        player.setKnockBackTime(10);

        player.driveHandleMovement(new Vector3(4, 64, 0), 1);

        assertNotNull(player.forceMovement, "knockBackTime alone must not exempt raw horizontal speed");
        assertEquals(0, player.x, 0.000001);
        assertTrue(player.invalidMoveEvents > 0, "speed rejection should still fire PlayerInvalidMoveEvent");
    }

    @Test
    void serverMotionAllowsMatchingClientDeltaOnly() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(false);

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(1, 64, 0), 1);

        assertNull(player.forceMovement, "client delta matching server-sent motion should be accepted");
        assertEquals(1, player.x, 0.000001);
        assertEquals(0, player.invalidMoveEvents);
        assertTrue(player.sentMotionPackets > 0, "test must exercise server-sent motion path");
    }

    @Test
    void serverMotionAllowsSmallerClientDelta() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(false);

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(0.2, 64, 0), 1);

        assertNull(player.forceMovement, "server motion allowance should not require the client to consume the full vector");
        assertEquals(0.2, player.x, 0.000001);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void serverMotionAllowanceScalesWithTickDiff() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(false);

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(1.92, 64, 0), 2);

        assertNull(player.forceMovement, "motion allowance should accumulate expected displacement across lagged ticks");
        assertEquals(1.92, player.x, 0.000001);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void serverMotionDoesNotAllowUnboundedExtraSpeed() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = generatedChunk();
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(false);

        TestPlayer player = createPlayer(level, chunk);
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(4, 64, 0), 1);

        assertNotNull(player.forceMovement, "server motion should only cover the expected delta, not arbitrary extra speed");
        assertEquals(0, player.x, 0.000001);
        assertTrue(player.invalidMoveEvents > 0);
    }

    private static TestPlayer createPlayer(Level level, BaseFullChunk chunk) {
        Server server = MockServer.get();
        Mockito.when(server.getDefaultLevel()).thenReturn(level);
        Mockito.when(server.getViewDistance()).thenReturn(1);
        Mockito.when(server.getAllowFlight()).thenReturn(false);

        SourceInterface sourceInterface = Mockito.mock(SourceInterface.class);
        Mockito.when(sourceInterface.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));

        TestPlayer player = new TestPlayer(sourceInterface);
        PluginManager pluginManager = Mockito.mock(PluginManager.class);
        Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof cn.nukkit.event.player.PlayerInvalidMoveEvent) {
                player.invalidMoveEvents++;
            }
            return null;
        }).when(pluginManager).callEvent(Mockito.any());
        Mockito.when(server.getPluginManager()).thenReturn(pluginManager);
        player.level = level;
        player.chunk = chunk;
        player.spawned = true;
        player.markAlive();
        player.temporalVector = new Vector3();
        player.adventureSettings = new AdventureSettings(player);
        player.boundingBox = new SimpleAxisAlignedBB(-0.3, 64, -0.3, 0.3, 65.8, 0.3);
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

    private static BaseFullChunk generatedChunk() {
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(chunk.isGenerated()).thenReturn(true);
        return chunk;
    }

    private static final class TestPlayer extends Player {

        private int lastSendMode = -1;
        private boolean checkChunksCalled;
        private boolean updateFallStateCalled;
        private int invalidMoveEvents;
        private int sentMotionPackets;

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void moveTo(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.boundingBox.setBounds(x - 0.3, y, z - 0.3, x + 0.3, y + this.getHeight(), z + 0.3);
        }

        private void markAlive() {
            this.health = 20;
        }

        private void driveHandleMovement(Vector3 clientPos, int tickDiff) {
            this.handleMovement(clientPos, tickDiff);
        }

        private void setKnockBackTime(int knockBackTime) {
            this.knockBackTime = knockBackTime;
        }

        @Override
        public boolean dataPacket(DataPacket packet) {
            if (packet instanceof SetEntityMotionPacket) {
                this.sentMotionPackets++;
            }
            return true;
        }

        @Override
        public void sendPosition(double x, double y, double z, double yaw, double pitch, double headYaw, int mode, java.util.Collection<Player> targets) {
            this.lastSendMode = mode;
        }

        @Override
        public void sendPosition(Vector3 pos, double yaw, double pitch, double headYaw, int mode, Player[] targets) {
            this.lastSendMode = mode;
        }

        @Override
        protected void checkChunks() {
            this.checkChunksCalled = true;
        }

        @Override
        protected void broadcastMovement() {
        }

        @Override
        protected void updateFallState(boolean onGround) {
            this.updateFallStateCalled = true;
        }

        @Override
        public void spawnToAll() {
        }
    }
}
