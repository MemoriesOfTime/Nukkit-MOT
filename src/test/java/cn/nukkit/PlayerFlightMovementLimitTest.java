package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class PlayerFlightMovementLimitTest {

    private Player player;
    private AdventureSettings settings;

    @BeforeEach
    void setUp() {
        player = mock(Player.class, CALLS_REAL_METHODS);
        settings = new AdventureSettings(player);
        player.adventureSettings = settings;
    }

    @Test
    void scalesSquaredLimitsForAuthorizedFastFlight() {
        settings.set(AdventureSettings.Type.ALLOW_FLIGHT, true);
        settings.set(AdventureSettings.Type.FLYING, true);
        player.setFlySpeed(1.0f);

        assertEquals(40_000d, player.movementSanityLimitSquared(100d), 0.01d);
        assertEquals(90_000d, player.movementSanityLimitSquared(225d), 0.01d);
    }

    @Test
    void permissionWithoutActiveFlightKeepsVanillaLimit() {
        settings.set(AdventureSettings.Type.ALLOW_FLIGHT, true);
        player.setFlySpeed(1.0f);

        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);
    }

    @Test
    void activeFlightWithoutPermissionKeepsVanillaLimit() {
        settings.set(AdventureSettings.Type.FLYING, true);
        player.setFlySpeed(1.0f);

        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);
    }

    @Test
    void vanillaAndInvalidSpeedsKeepVanillaLimit() {
        settings.set(AdventureSettings.Type.ALLOW_FLIGHT, true);
        settings.set(AdventureSettings.Type.FLYING, true);

        player.setFlySpeed(Player.DEFAULT_FLY_SPEED);
        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);

        player.setFlySpeed(Float.POSITIVE_INFINITY);
        assertEquals(100d, player.movementSanityLimitSquared(100d), 1e-6);

        player.setFlySpeed(Float.MAX_VALUE);
        assertTrue(Double.isFinite(player.movementSanityLimitSquared(100d)));
    }

    @Test
    void deniedBreakOnPartialSupportKeepsHorizontalProgress() {
        Level level = mockingLevel();
        Block support = partialSupport(level);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));

        player.driveServerAuthMovement(new Vector3(0.9, 64.6, 0.5));

        assertEquals(0.9, player.x, 0.000001);
        assertEquals(64.8125, player.y, 0.000001);
        assertNotNull(player.forceMovement);
        assertEquals(0.9, player.forceMovement.x, 0.000001);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);

        Vector3 correctionAck = new Vector3(
                player.forceMovement.x, player.forceMovement.y, player.forceMovement.z);
        player.driveServerAuthMovement(correctionAck);
        assertNull(player.forceMovement);

        player.driveServerAuthMovement(new Vector3(1.26, 64.7, 0.5));

        assertEquals(1.26, player.x, 0.000001);
        assertEquals(64.7, player.y, 0.000001);
        assertNull(player.forceMovement);
        Mockito.verify(support, Mockito.atLeastOnce()).collidesWithBB(Mockito.any());
    }

    @Test
    void packetFloatNoiseDoesNotTriggerDeniedBreakCorrection() {
        Level level = mockingLevel();
        partialSupport(level, 126, 126.8125);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.moveTo(0.5, 126.8125, 0.5);
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 126, 0));

        float baseOffset = player.baseOffset();
        float packetY = (float) (player.y + baseOffset);
        double decodedY = packetY - baseOffset;
        assertTrue(decodedY < player.y);
        player.driveHandleMovement(new Vector3(0.6, decodedY, 0.5));

        assertEquals(0.6, player.x, 0.000001);
        assertEquals(decodedY, player.y, 0.000000001);
        assertNull(player.forceMovement);
        assertEquals(-1, player.lastSendMode);
    }

    @Test
    void onePacketCannotSkipBelowDeniedSupport() {
        Level level = mockingLevel();
        partialSupport(level);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));

        player.driveHandleMovement(new Vector3(0.6, 63.5, 0.5));

        assertEquals(0.6, player.x, 0.000001);
        assertEquals(64.8125, player.y, 0.000001);
        assertNotNull(player.forceMovement);
    }

    @Test
    void unrelatedDeniedBreakDoesNotClearPendingSupport() {
        Level level = mockingLevel();
        partialSupport(level);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));

        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 65, 0));
        player.armDeniedCreativeBreakCorrection(new BlockVector3(100, 64, 0));
        player.driveHandleMovement(new Vector3(0.9, 64.6, 0.5));

        assertEquals(0.9, player.x, 0.000001);
        assertEquals(64.8125, player.y, 0.000001);
        assertNotNull(player.forceMovement);
    }

    @Test
    void changedSupportCancelsPendingCorrection() {
        Level level = mockingLevel();
        Block support = partialSupport(level);
        Block replacement = Mockito.mock(Block.class);
        Mockito.when(level.getBlock(Mockito.any(Vector3.class), Mockito.eq(false)))
                .thenReturn(support, replacement);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));

        player.driveHandleMovement(new Vector3(0.9, 64.4, 0.5));

        assertEquals(0.9, player.x, 0.000001);
        assertEquals(64.4, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void collidingReplacementKeepsPendingCorrection() {
        Level level = mockingLevel();
        Block support = partialSupport(level);
        Block replacement = Mockito.mock(Block.class);
        AxisAlignedBB collision = new SimpleAxisAlignedBB(0, 64, 0, 1, 65, 1);
        Mockito.when(replacement.collidesWithBB(Mockito.any()))
                .thenAnswer(invocation -> collision.intersectsWith(invocation.getArgument(0)));
        Mockito.when(level.getBlock(Mockito.any(Vector3.class), Mockito.eq(false)))
                .thenReturn(support, replacement);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));

        player.driveHandleMovement(new Vector3(0.9, 64.4, 0.5));

        assertEquals(0.9, player.x, 0.000001);
        assertEquals(64.8125, player.y, 0.000001);
        assertNotNull(player.forceMovement);
    }

    @Test
    void expiredCorrectionDoesNotAffectMovement() {
        Level level = mockingLevel();
        partialSupport(level);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        Mockito.when(MockServer.get().getTick()).thenReturn(10, 16);
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));

        player.driveHandleMovement(new Vector3(0.9, 64.4, 0.5));

        assertEquals(0.9, player.x, 0.000001);
        assertEquals(64.4, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void sideBlockDistantBlockAndSurvivalDoNotArmCorrection() {
        Level level = mockingLevel();
        partialSupport(level);
        TestPlayer player = createPlayer(level);
        player.gamemode = Player.CREATIVE;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 65, 0));
        player.armDeniedCreativeBreakCorrection(new BlockVector3(100, 64, 0));
        Mockito.verify(level, Mockito.never())
                .getBlock(Mockito.any(Vector3.class), Mockito.eq(false));
        player.driveHandleMovement(new Vector3(0.9, 64.4, 0.5));
        assertEquals(64.4, player.y, 0.000001);

        player.moveTo(0.5, 64.8125, 0.5);
        player.gamemode = Player.SURVIVAL;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 64, 0));
        player.driveHandleMovement(new Vector3(0.9, 64.4, 0.5));
        assertEquals(64.4, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    private static Level mockingLevel() {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(319);
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenReturn(false);
        return level;
    }

    private static Block partialSupport(Level level) {
        return partialSupport(level, 64, 64.8125);
    }

    private static Block partialSupport(Level level, double minY, double maxY) {
        Block block = Mockito.mock(Block.class);
        AxisAlignedBB collision = new SimpleAxisAlignedBB(0, minY, 0, 1, maxY, 1);
        Mockito.when(block.collidesWithBB(Mockito.any()))
                .thenAnswer(invocation -> collision.intersectsWith(invocation.getArgument(0)));
        Mockito.when(level.getBlock(Mockito.any(Vector3.class), Mockito.eq(false)))
                .thenReturn(block);
        return block;
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
        player.boundingBox = new SimpleAxisAlignedBB(0.2, 64.8125, 0.2, 0.8, 66.6125, 0.8);
        player.moveTo(0.5, 64.8125, 0.5);
        player.lastX = 0.5;
        player.lastY = 64.8125;
        player.lastZ = 0.5;
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

        private void driveServerAuthMovement(Vector3 clientPos) {
            if (this.forceMovement != null
                    && clientPos.distanceSquared(this.forceMovement) > 0.1) {
                this.sendPosition(this.forceMovement, MovePlayerPacket.MODE_RESET);
                return;
            }
            this.forceMovement = null;
            this.handleMovement(clientPos, 1);
        }

        private float baseOffset() {
            return this.getBaseOffset();
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
        public boolean isInsideOfWater() {
            return false;
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
        public boolean fastMove(double dx, double dy, double dz) {
            this.boundingBox.offset(dx, dy, dz);
            this.x += dx;
            this.y += dy;
            this.z += dz;
            return true;
        }
    }
}
