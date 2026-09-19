package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
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

/**
 * 反速测/反穿墙（软拒绝语义）回归测试：对应 Player.handleMovement 与 issue #727。
 * <p>
 * Regression tests for the anti-speed-hack / anti-noclip soft-rejection semantics in
 * Player.handleMovement (issue #727).
 */
class PlayerMovementCollisionRegressionTest {

    @Test
    void blockedMovementSoftRejectsWithoutCorrectionPacket() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);

        assertEquals(0, player.x, 0.000001);
        assertEquals(64, player.y, 0.000001);
        assertEquals(0, player.z, 0.000001);
        assertNull(player.forceMovement, "soft rejection must not set forceMovement (no rubber-band packet)");
        assertEquals(-1, player.lastSendMode, "soft rejection must not send a position correction");
        assertEquals(0, player.invalidMoveEvents, "soft rejection must not fire PlayerInvalidMoveEvent");
        assertTrue(player.checkChunksCalled, "chunk state updates still run on soft rejection");
        assertTrue(player.updateFallStateCalled, "fall state updates still run on soft rejection");
    }

    @Test
    void sustainedPhasingEventuallyRevertsViaSpeedCheck() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        // 第一拍：软拒绝，服务器位置原地不动
        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);
        assertEquals(0, player.x, 0.000001);
        assertNull(player.forceMovement);

        // 持续穿墙：客户端-服务器位移差累积超速，速度检查兜底回弹
        player.driveHandleMovement(new Vector3(3.5, 64, 0), 1);

        assertNotNull(player.forceMovement, "sustained phasing must eventually trigger the speed check revert");
        assertEquals(0, player.x, 0.000001);
        assertEquals(MovePlayerPacket.MODE_RESET, player.lastSendMode, "revert correction must use MODE_RESET so the client accepts it");
        assertTrue(player.invalidMoveEvents > 0);
    }

    @Test
    void verticalPhasingCaughtByExtremeDistanceCheck() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        // 软拒绝冻结服务器位置后垂直穿地板下落：全轴极限距离检查兜底（垂直位移不经过水平速度检查）
        player.driveHandleMovement(new Vector3(0, 55, 0), 1);

        assertNotNull(player.forceMovement, "vertical-only divergence must be caught by the all-axis extreme distance check");
        assertEquals(64, player.y, 0.000001);
        assertEquals(MovePlayerPacket.MODE_RESET, player.lastSendMode);
    }

    @Test
    void scaffoldingCollisionIsAccepted() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        Block scaffolding = Mockito.mock(Block.class);
        Mockito.when(scaffolding.getId()).thenReturn(BlockID.SCAFFOLDING);
        Mockito.when(scaffolding.canPassThrough()).thenReturn(false);
        Mockito.when(scaffolding.collidesWithBB(Mockito.any())).thenReturn(true);
        Mockito.when(level.getBlock(Mockito.any(BaseFullChunk.class), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean())).thenReturn(scaffolding);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);

        assertEquals(0.4, player.x, 0.000001, "standing inside scaffolding is vanilla-legal and must be accepted");
        assertNull(player.forceMovement);
    }

    @Test
    void powderSnowCollisionIsAccepted() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        Block powderSnow = Mockito.mock(Block.class);
        Mockito.when(powderSnow.getId()).thenReturn(BlockID.POWDER_SNOW);
        Mockito.when(powderSnow.canPassThrough()).thenReturn(false);
        Mockito.when(powderSnow.collidesWithBB(Mockito.any())).thenReturn(true);
        Mockito.when(level.getBlock(Mockito.any(BaseFullChunk.class), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean())).thenReturn(powderSnow);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);

        assertEquals(0.4, player.x, 0.000001, "sinking into powder snow is vanilla-legal and must be accepted");
        assertNull(player.forceMovement);
    }

    @Test
    void ridingSkipsCollisionValidation() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);
        player.riding = Mockito.mock(Entity.class);

        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);

        assertEquals(0.4, player.x, 0.000001, "JE skips collision validation for passengers (vehicle predicts movement)");
        assertNull(player.forceMovement);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void creativeSkipsCollisionValidation() {
        Level level = mockingLevel();
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenReturn(true);

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);
        player.gamemode = Player.CREATIVE;

        player.driveHandleMovement(new Vector3(0.4, 64, 0), 1);

        assertEquals(0.4, player.x, 0.000001, "creative players skip collision validation (JE semantics)");
        assertNull(player.forceMovement);
    }

    @Test
    void lowClearanceMovementAcceptedForSwimPose() {
        Level level = mockingLevel();
        // 障碍带 [64.65, 65.05]（模拟 1 格缝隙上方方块）：站立窗口（minY=64.6）撞上，匍匐盒（maxY=64.6）通过
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenAnswer(invocation -> {
                    AxisAlignedBB bb = invocation.getArgument(1);
                    return bb.getMaxY() > 64.65 && bb.getMinY() < 65.05;
                });

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(0.2, 64, 0), 1);

        assertEquals(0.2, player.x, 0.000001, "swimming/crawling clients use a 0.6-high hitbox and must fit low gaps");
        assertNull(player.forceMovement);
    }

    @Test
    void explosionImpulseAllowancePreventsSpeedFlag() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.setServerMotionAllowance(new Vector3(3, 0, 0));
        player.driveHandleMovement(new Vector3(3, 64, 0), 1);

        assertEquals(3, player.x, 0.000001, "explosion knockback displacement must be credited by the allowance");
        assertNull(player.forceMovement);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void extremeHorizontalSpeedStillFlagged() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(3, 64, 0), 1);

        assertNotNull(player.forceMovement, "3 blocks/tick with no server-authorized motion must be flagged");
        assertEquals(0, player.x, 0.000001);
        assertTrue(player.invalidMoveEvents > 0);
    }

    @Test
    void fastButLegalMovementNotFlagged() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(2, 64, 0), 1);

        assertEquals(2, player.x, 0.000001, "2 blocks/tick (~40 m/s, ice sprint-jump territory) must not be flagged");
        assertNull(player.forceMovement);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void tickDiffNormalizationToleratesLagSpikes() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        player.driveHandleMovement(new Vector3(3, 64, 0), 2);

        assertEquals(3, player.x, 0.000001, "3 blocks over 2 ticks averages below the limit and must pass");
        assertNull(player.forceMovement);
    }

    @Test
    void recentKnockbackWithoutServerMotionDoesNotBypassSpeedCheck() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);
        player.setKnockBackTime(10);

        player.driveHandleMovement(new Vector3(4, 64, 0), 1);

        assertNotNull(player.forceMovement, "knockBackTime alone must not exempt raw horizontal speed");
        assertEquals(0, player.x, 0.000001);
        assertTrue(player.invalidMoveEvents > 0);
    }

    @Test
    void serverMotionAllowsMatchingClientDeltaOnly() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
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
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(0.2, 64, 0), 1);

        assertNull(player.forceMovement, "server motion allowance should not require the client to consume the full vector");
        assertEquals(0.2, player.x, 0.000001);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void serverMotionAllowanceScalesWithTickDiff() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(1.92, 64, 0), 2);

        assertNull(player.forceMovement, "motion allowance should accumulate expected displacement across lagged ticks");
        assertEquals(1.92, player.x, 0.000001);
        assertEquals(0, player.invalidMoveEvents);
    }

    @Test
    void serverMotionDoesNotAllowUnboundedExtraSpeed() {
        Level level = mockingLevel();

        TestPlayer player = createPlayer(level, generatedChunk());
        player.moveTo(0, 64, 0);

        assertTrue(player.setMotion(new Vector3(1, 0, 0)));
        player.driveHandleMovement(new Vector3(4, 64, 0), 1);

        assertNotNull(player.forceMovement, "server motion should only cover the expected delta, not arbitrary extra speed");
        assertEquals(0, player.x, 0.000001);
        assertTrue(player.invalidMoveEvents > 0);
    }

    private static Level mockingLevel() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(319);
        return level;
    }

    private static TestPlayer createPlayer(Level level, BaseFullChunk chunk) {
        Server server = MockServer.get();
        Mockito.when(server.getDefaultLevel()).thenReturn(level);
        Mockito.when(server.getViewDistance()).thenReturn(1);
        Mockito.when(server.getAllowFlight()).thenReturn(false);
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);

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
        public void checkSwimmingState() {
        }

        @Override
        public void spawnToAll() {
        }
    }
}
