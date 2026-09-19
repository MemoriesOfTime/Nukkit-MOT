package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockEndPortalFrame;
import cn.nukkit.block.BlockSlabStone;
import cn.nukkit.block.BlockStairsStone;
import cn.nukkit.block.BlockStone;
import cn.nukkit.block.BlockTrapdoor;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDeniedCreativeBreakContactTest {

    private static Stream<Arguments> movementModes() {
        return Stream.of(
                Arguments.of(ProtocolInfo.v1_20_0, 0),
                Arguments.of(ProtocolInfo.v1_20_0, 1),
                Arguments.of(ProtocolInfo.v1_21_90, 2),
                Arguments.of(ProtocolInfo.v1_26_40, 2),
                Arguments.of(ProtocolInfo.v1_26_45, 2));
    }

    private static Stream<Arguments> partialSupports() {
        return Stream.of(
                Arguments.of(new BlockTrapdoor(), 0.5, 70.1875),
                Arguments.of(new BlockEndPortalFrame(), 0.5, 70.8125),
                Arguments.of(new BlockSlabStone(), 0.5, 70.5),
                Arguments.of(new BlockSlabStone(8), 0.5, 71.0),
                Arguments.of(new BlockStairsStone(), 0.2, 70.5),
                Arguments.of(new BlockStairsStone(), 0.8, 71.0));
    }

    @ParameterizedTest
    @MethodSource("partialSupports")
    void airborneDescentLandsOnTheActualPartialSupport(Block support, double x, double top) {
        TestPlayer player = createPlayer(support, x, top);
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 70, 0));
        player.move(new Vector3(x, top + 0.42, 0.5));

        player.move(new Vector3(x, 69.8, 0.5));

        assertEquals(top, player.y, 0.000001);
        assertEquals(top, player.getBoundingBox().getMinY(), 0.000001);
        assertNotNull(player.forceMovement);
    }

    @ParameterizedTest
    @MethodSource("movementModes")
    void realLandingAcknowledgementAllowsTheNextJumpAndEdgeExit(int protocol, int mode) {
        TestPlayer player = trapdoorPlayer(protocol, mode);
        player.move(new Vector3(0.5, 70.6, 0.5));
        player.move(new Vector3(0.5, 69.8, 0.5));
        assertEquals(70.1875, player.y, 0.000001);
        assertNotNull(player.forceMovement);

        player.input(0.5, 70.1875, 0.5);
        assertNull(player.forceMovement);
        player.applyQueuedMovement();
        player.input(0.6, 70.6075, 0.5);
        assertNotNull(player.newPosition);
        assertNull(player.forceMovement);
        player.applyQueuedMovement();
        assertEquals(70.6075, player.y, 0.00001);

        player.input(1.4, 70.0, 0.5);
        assertNull(player.forceMovement);
        player.applyQueuedMovement();
        assertEquals(1.4, player.x, 0.00001);
        assertEquals(70.0, player.y, 0.00001);
    }

    @ParameterizedTest
    @MethodSource("movementModes")
    void exactlyStationaryPacketReleasesPendingCorrection(int protocol, int mode) {
        TestPlayer player = trapdoorPlayer(protocol, mode);
        float decodedFeetY = (float) (70.1875 + player.baseOffset()) - player.baseOffset();
        player.moveTo(0.5, decodedFeetY, 0.5);
        player.forceMovement = player.getLocation().add(0, 0.00001, 0);

        player.input(0.5, 70.1875, 0.5);

        assertNull(player.forceMovement, "An unchanged movement packet must acknowledge the correction");
        assertNotNull(player.newPosition);
        player.applyQueuedMovement();
        player.input(0.5, 70.6075, 0.5);
        assertNotNull(player.newPosition);
        assertNull(player.forceMovement);
    }

    @ParameterizedTest
    @MethodSource("movementModes")
    void recordedRestingContactDoesNotSendRepeatedCorrections(int protocol, int mode) {
        TestPlayer player = trapdoorPlayer(protocol, mode);
        // Recorded Bedrock resting contact, including its settling cycle.
        double[] feetY = {70.1825, 70.1825, 70.1842, 70.1853, 70.1860, 70.1868,
                70.1870, 70.1872, 70.1873, 70.1874, 70.1874, 70.1875, 70.1875};
        for (int cycle = 0; cycle < 3; cycle++) {
            for (double y : feetY) {
                player.input(0.5, y, 0.5);
                player.applyQueuedMovement();
                assertEquals(70.1875, player.y, 0.00001);
                assertNull(player.forceMovement);
                assertEquals(0, player.selfPositionPackets);
            }
        }
        player.input(0.65, 70.1825, 0.5);
        player.applyQueuedMovement();
        assertEquals(0.65, player.x, 0.00001);
        assertEquals(70.1875, player.y, 0.000001);
        player.input(0.8, 70.6075, 0.5);
        player.applyQueuedMovement();
        assertEquals(70.6075, player.y, 0.00001);
        assertEquals(0, player.selfPositionPackets);
    }

    @ParameterizedTest
    @MethodSource("movementModes")
    void smallContactCannotAccumulateAndDeeperInputStillCorrects(int protocol, int mode) {
        TestPlayer player = trapdoorPlayer(protocol, mode);
        for (int i = 0; i < 20; i++) {
            player.input(0.5, player.y - 0.005, 0.5);
            player.applyQueuedMovement();
            assertEquals(70.1875, player.y, 0.000001);
            assertEquals(70.1875, player.getBoundingBox().getMinY(), 0.000001);
            assertNull(player.forceMovement);
        }
        assertEquals(0, player.selfPositionPackets);

        player.input(0.5, 70.1675, 0.5);
        player.applyQueuedMovement();
        assertEquals(70.1875, player.y, 0.000001);
        assertNotNull(player.forceMovement);
        assertEquals(1, player.selfPositionPackets);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
    }

    @Test
    void diagonalMotionBelowTheSupportDoesNotSnapOntoIt() {
        TestPlayer player = createPlayer(new BlockTrapdoor(), 0.5, 70.1875);
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 70, 0));
        player.move(new Vector3(4.5, 72, 0.5));
        player.move(new Vector3(0.5, 66, 0.5));
        assertEquals(66, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void sideOfHigherRimDoesNotBecomeADeniedFloor() {
        TestPlayer player = createPlayer(new BlockTrapdoor(), 0.5, 70.1875);
        Block rim = new BlockStone();
        rim.setComponents(1, 70, 0);
        player.blocks.put("1:70:0", rim);
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 70, 0));
        player.move(new Vector3(0.5, 70.6, 0.5));
        player.move(new Vector3(0.8, 70.4, 0.5));
        assertEquals(70.4, player.y, 0.000001);
        assertEquals(0.8, player.x, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void ordinaryMovementWithoutADeniedBreakIsUnchanged() {
        TestPlayer player = createPlayer(new BlockTrapdoor(), 0.5, 70.1875);
        player.move(new Vector3(0.5, 69.8, 0.5));
        assertEquals(69.8, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    @Test
    void changedLevelCancelsPendingCorrection() {
        TestPlayer player = trapdoorPlayer(ProtocolInfo.v1_21_90, 2);
        player.level = Mockito.mock(Level.class);
        player.move(new Vector3(0.5, 69.8, 0.5));
        assertEquals(69.8, player.y, 0.000001);
        assertNull(player.forceMovement);
    }

    private static TestPlayer trapdoorPlayer(int protocol, int mode) {
        TestPlayer player = createPlayer(new BlockTrapdoor(), 0.5, 70.1875);
        player.protocol = protocol;
        MockServer.get().serverAuthoritativeMovementMode = mode;
        player.armDeniedCreativeBreakCorrection(new BlockVector3(0, 70, 0));
        return player;
    }

    private static TestPlayer createPlayer(Block support, double x, double top) {
        MockServer.reset();
        Map<String, Block> blocks = new HashMap<>();
        support.setComponents(0, 70, 0);
        blocks.put("0:70:0", support);
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(319);
        Mockito.when(level.getVibrationManager()).thenReturn(Mockito.mock(VibrationManager.class));
        Mockito.when(level.getBlock(Mockito.any(Vector3.class), Mockito.eq(false)))
                .thenAnswer(invocation -> {
                    Vector3 pos = invocation.getArgument(0);
                    return blocks.getOrDefault(pos.getFloorX() + ":" + pos.getFloorY() + ":" + pos.getFloorZ(), new BlockAir());
                });
        Mockito.when(level.getBlock(Mockito.any(FullChunk.class), Mockito.anyInt(), Mockito.anyInt(),
                        Mockito.anyInt(), Mockito.eq(0), Mockito.eq(false)))
                .thenAnswer(invocation -> blocks.getOrDefault(invocation.getArgument(1) + ":"
                        + invocation.getArgument(2) + ":" + invocation.getArgument(3), new BlockAir()));
        support.level = level;
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(chunk.isGenerated()).thenReturn(true);
        Server server = MockServer.get();
        Mockito.when(server.getDefaultLevel()).thenReturn(level);
        Mockito.when(server.getViewDistance()).thenReturn(1);
        Mockito.when(server.getTick()).thenReturn(1000);
        Mockito.when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        SourceInterface source = Mockito.mock(SourceInterface.class);
        Mockito.when(source.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));
        TestPlayer player = new TestPlayer(source);
        player.blocks = blocks;
        player.level = level;
        player.chunk = chunk;
        player.spawned = true;
        player.loggedIn = true;
        player.loginVerified = true;
        player.protocol = ProtocolInfo.v1_21_90;
        player.lastTeleportTick = -1000;
        player.markAlive();
        player.temporalVector = new Vector3();
        player.adventureSettings = new AdventureSettings(player);
        player.gamemode = Player.CREATIVE;
        player.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0.6, 1.8, 0.6);
        player.moveTo(x, top, 0.5);
        player.lastX = x;
        player.lastY = top;
        player.lastZ = 0.5;
        player.firstMove = true;
        return player;
    }

    private static final class TestPlayer extends Player {
        private Map<String, Block> blocks;
        private int selfPositionPackets;
        private int lastSendMode = -1;

        private TestPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private float baseOffset() {
            return getBaseOffset();
        }

        private void markAlive() {
            health = 20;
        }

        private void move(Vector3 position) {
            handleMovement(position, 1);
        }

        private void input(double x, double y, double z) {
            newPosition = null;
            if (!isMovementServerAuthoritative()) {
                MovePlayerPacket packet = new MovePlayerPacket();
                packet.x = (float) x;
                packet.y = (float) (y + getBaseOffset());
                packet.z = (float) z;
                handleDataPacket(packet);
            } else {
                PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
                packet.setPosition(new Vector3f((float) x, (float) (y + getBaseOffset()), (float) z));
                handleDataPacket(packet);
            }
        }

        private void applyQueuedMovement() {
            if (newPosition != null) move(newPosition);
        }

        private void moveTo(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            boundingBox.setBounds(x - 0.3, y, z - 0.3, x + 0.3, y + getHeight(), z + 0.3);
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
        public boolean fastMove(double dx, double dy, double dz) {
            moveTo(x + dx, y + dy, z + dz);
            return true;
        }

        @Override
        public void sendPosition(double x, double y, double z, double yaw, double pitch, double headYaw,
                                 int mode, Collection<Player> targets) {
            if (targets == null || targets.contains(this)) {
                selfPositionPackets++;
                lastSendMode = mode;
            }
        }

        @Override
        public void sendPosition(Vector3 pos, double yaw, double pitch, double headYaw,
                                 int mode, Player[] targets) {
            if (targets == null || Arrays.asList(targets).contains(this)) {
                selfPositionPackets++;
                lastSendMode = mode;
            }
        }
    }
}
