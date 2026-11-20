package cn.nukkit;

import cn.nukkit.block.*;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInvalidMoveEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.MovePlayerPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Movement collision regression coverage using native handleMovement and real block shapes. */
class PlayerMovementCollisionGateTest {
    private static final int FLOOR = 64;
    private static final int[] TICK = {1000};

    @Test
    void slowWalkIntoWallIsRejectedWithoutCorrection() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 2, FLOOR, 0);
        put(blocks, new BlockStone(), 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);

        for (double x = 0.7; x <= 1.71; x += 0.2) {
            player.step(x, FLOOR, 0.5);
            assertEquals(x, player.x, 1.0E-6, "open floor is accepted");
        }
        player.step(1.75, FLOOR, 0.5);
        assertEquals(1.75, player.x, 1.0E-6, "contact within the horizontal tolerance is accepted");

        for (double x = 1.9; x <= 2.31; x += 0.2) {
            player.step(x, FLOOR, 0.5);
            assertEquals(1.75, player.x, 1.0E-6, "a position inside the wall is never adopted");
        }
        assertNull(player.forceMovement, "a short rejection sends no correction");
        assertEquals(0, player.selfPositionPackets);

        // Further in, the existing correction-amount speed check (0.707 blocks) takes over.
        player.step(2.5, FLOOR, 0.5);
        assertEquals(1.75, player.x, 1.0E-6);
        assertEquals(1, player.selfPositionPackets);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
        assertNotNull(player.forceMovement);
    }

    @Test
    void sinkingWithinToleranceThenDroppingThroughAOneBlockFloorIsRejected() {
        TestPlayer player = player(ground(1));
        player.step(0.6, FLOOR, 0.5);
        player.step(0.6, FLOOR - 0.5, 0.5);
        assertEquals(FLOOR - 0.5, player.y, 1.0E-6, "foot tolerance");
        player.step(0.6, FLOOR - 1.6, 0.5);
        assertEquals(FLOOR - 0.5, player.y, 1.0E-6, "the floor cannot be crossed into the air below");
    }

    @Test
    void rejectedSinkKeepsThePlayerGrounded() {
        TestPlayer player = player(ground(4));
        player.step(0.6, FLOOR - 0.5, 0.5);
        player.onGround = false;
        player.step(0.6, FLOOR - 0.8, 0.5);
        assertEquals(FLOOR - 0.5, player.y, 1.0E-6);
        assertTrue(player.onGround, "held on the floor must not count as airborne");
        assertTrue(player.fallGrounded, "the blocked input must settle the fall");
        assertEquals(0, player.selfPositionPackets);
    }

    @Test
    void clearlyStuckClientIsResyncedAfterOneSecond() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 2, FLOOR, 0);
        put(blocks, new BlockStone(), 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        player.step(1.7, FLOOR, 0.5);
        for (int i = 0; i < 20; i++) player.step(2.3, FLOOR, 0.5);
        assertEquals(0, player.selfPositionPackets);
        player.step(2.3, FLOOR, 0.5);
        assertEquals(1, player.selfPositionPackets);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
        assertNotNull(player.forceMovement);
        assertEquals(1.7, player.x, 1.0E-6);
    }

    @Test
    void fallingIntoThickGroundIsHeldThenResyncedOntoTheFloor() {
        TestPlayer player = player(ground(4));
        double lowest = fall(player, 12);
        assertTrue(lowest >= FLOOR - 0.6 - 1.0E-6, "server position sank to " + lowest);
        assertTrue(player.selfPositionPackets >= 1);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
        assertEquals(FLOOR, player.lastCorrectionY, 1.0E-4, "correction stands on the floor");
    }

    @Test
    void fallingThroughAOneBlockFloorIsHeld() {
        TestPlayer player = player(ground(1));
        assertTrue(fall(player, 40) >= FLOOR - 0.6 - 1.0E-6);
    }

    @Test
    void resyncBeforeTheFirstAcceptedMoveUsesTheServerPosition() {
        TestPlayer player = player(ground(4));
        player.firstMove = true;
        player.lastX = player.lastY = player.lastZ = 0;
        player.step(0.5, FLOOR - 4, 0.5);
        assertEquals(1, player.selfPositionPackets);
        assertEquals(0.5, player.x, 1.0E-6);
        assertEquals(FLOOR, player.y, 1.0E-6);
        assertEquals(0.5, player.z, 1.0E-6);
    }

    @Test
    void smallShapeMismatchIsHeldWithoutRepeatedCorrections() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockCauldron(), 1, FLOOR, 0);
        TestPlayer player = player(blocks);
        player.place(1.5, FLOOR + 1, 0.5);
        for (double y = FLOOR + 0.9; y > FLOOR + 0.32; y -= 0.1) player.step(1.5, y, 0.5);
        for (int i = 0; i < 200; i++) player.step(1.5, FLOOR + 0.3125, 0.5);
        assertEquals(0, player.selfPositionPackets);
        assertNull(player.forceMovement);
        assertTrue(player.y - (FLOOR + 0.3125) < 0.2);
        for (double y = FLOOR + 0.5; y <= FLOOR + 1.41; y += 0.3) {
            player.step(1.5, y, 0.5);
            assertEquals(y, player.y, 1.0E-6, "jumping back out is accepted");
        }
    }

    @Test
    void allowFlightServerSettingDoesNotDisableTheGate() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 2, FLOOR, 0);
        put(blocks, new BlockStone(), 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        Mockito.when(MockServer.get().getAllowFlight()).thenReturn(true);
        player.setCheckMovement(false);
        player.gamemode = Player.CREATIVE;
        player.step(1.7, FLOOR, 0.5);
        player.step(1.95, FLOOR, 0.5);
        player.step(2.2, FLOOR, 0.5);
        assertEquals(1.7, player.x, 1.0E-6);
    }

    @Test
    void stepUpOntoSlabAndStairsIsAccepted() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockSlabStone(), 1, FLOOR, 0);
        put(blocks, new BlockStairsStone(), 2, FLOOR, 0);
        TestPlayer player = player(blocks);
        double[][] path = {{0.7, 64}, {0.9, 64.5}, {1.2, 64.5}, {1.5, 64.5},
                {1.8, 64.5}, {2.0, 64.5}, {2.3, 65}, {2.6, 65}};
        for (double[] point : path) {
            player.step(point[0], point[1], 0.5);
            assertEquals(point[0], player.x, 1.0E-6);
            assertEquals(point[1], player.y, 1.0E-6);
        }
        assertEquals(0, player.selfPositionPackets);
    }

    @Test
    void footLevelOverlapWithinStepHeightIsTolerated() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockSlabStone(), 1, FLOOR, 0);
        TestPlayer player = player(blocks);
        for (double x = 0.7; x <= 1.51; x += 0.2) {
            player.step(x, FLOOR, 0.5);
            assertEquals(x, player.x, 1.0E-6);
        }
    }

    @Test
    void scaffoldingAndPowderSnowAreEnterable() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockScaffolding(), 1, FLOOR, 0);
        put(blocks, new BlockScaffolding(), 1, FLOOR + 1, 0);
        put(blocks, Block.get(Block.POWDER_SNOW), 2, FLOOR, 0);
        put(blocks, Block.get(Block.POWDER_SNOW), 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        for (double x = 0.7; x <= 2.51; x += 0.2) {
            player.step(x, FLOOR, 0.5);
            assertEquals(x, player.x, 1.0E-6);
        }
        assertEquals(0, player.selfPositionPackets);
    }

    @Test
    void spectatorIsNotGated() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 1, FLOOR, 0);
        put(blocks, new BlockStone(), 1, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        player.gamemode = Player.SPECTATOR;
        for (double x = 0.7; x <= 1.51; x += 0.2) {
            player.step(x, FLOOR, 0.5);
            assertEquals(x, player.x, 1.0E-6);
        }
    }

    @Test
    void lowGapIsAcceptedWhateverPoseTheServerBelieves() {
        Map<String, Block> blocks = ground(1);
        for (int x = 1; x <= 3; x++) put(blocks, new BlockStone(), x, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        assertEquals(1.8f, player.getHeight(), 1.0E-6);
        for (double x = 0.7; x <= 2.51; x += 0.2) {
            player.step(x, FLOOR, 0.5);
            assertEquals(x, player.x, 1.0E-6);
        }
        assertEquals(0, player.selfPositionPackets);
    }

    @Test
    void lowCeilingResyncLiftsOntoSupportAndAcceptsClientAcknowledgement() {
        Map<String, Block> blocks = ground(4);
        put(blocks, new BlockStone(), 0, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        assertEquals(1.8f, player.getHeight(), 1.0E-6, "the server still records a standing pose");

        player.packetStep(0.5, FLOOR - 0.5, 0.5);
        assertEquals(FLOOR - 0.5, player.y, 0.00002, "the movement gate tolerates the foot overlap");
        assertEquals(0, player.selfPositionPackets);

        player.packetStep(0.5, FLOOR - 4.5, 0.5);
        assertEquals(1, player.selfPositionPackets);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
        assertEquals(FLOOR, player.lastCorrectionY, 1.0E-4, "resync lifts onto the floor below the low ceiling");

        player.packetStep(0.5, FLOOR, 0.5);
        assertNotNull(player.lastQueued, "the floor position acknowledges the correction");
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode, "acknowledgement must not trigger MODE_RESET");
        assertEquals(1, player.selfPositionPackets);
    }

    @Test
    void risingThroughACeilingIsRejected() {
        Map<String, Block> blocks = ground(1);
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 1; z++)
            put(blocks, new BlockStone(), x, FLOOR + 2, z);
        TestPlayer player = player(blocks);
        double highest = FLOOR;
        for (double y = FLOOR + 0.3; y <= FLOOR + 3.61; y += 0.3) {
            player.step(0.5, y, 0.5);
            highest = Math.max(highest, player.y);
        }
        assertTrue(highest < FLOOR + 1.6, "low core must not enter the ceiling, highest=" + highest);
    }

    @Test
    void droppingBesideAnOverhangAndTuckingUnderItIsAccepted() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 2, FLOOR + 2, 0);
        TestPlayer player = player(blocks);
        player.place(1.5, FLOOR + 3.2, 0.5);
        player.step(2.3, FLOOR, 0.5);
        assertEquals(2.3, player.x, 1.0E-6);
        assertEquals(FLOOR, player.y, 1.0E-6);
    }

    @Test
    void batchedJumpOntoABlockIsAccepted() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 1, FLOOR, 0);
        TestPlayer player = player(blocks);
        player.step(1.5, FLOOR + 1, 0.5);
        assertEquals(1.5, player.x, 1.0E-6);
        assertEquals(FLOOR + 1, player.y, 1.0E-6);
    }

    @Test
    void risingOutOfABlockInsertedAtTheOldPositionIsAccepted() {
        Map<String, Block> blocks = ground(1);
        TestPlayer player = player(blocks);
        put(blocks, new BlockStone(), 0, FLOOR, 0);
        player.step(0.5, FLOOR + 0.5, 0.5);
        assertEquals(FLOOR + 0.5, player.y, 1.0E-6);
        player.step(0.5, FLOOR + 1, 0.5);
        assertEquals(FLOOR + 1, player.y, 1.0E-6);
    }

    @Test
    void longMergedStepUnderCoverIsNotPathChecked() {
        Map<String, Block> blocks = new HashMap<>();
        for (int x = -1; x <= 1; x++) put(blocks, new BlockStone(), x, FLOOR + 3, 0);
        for (int x = 2; x <= 5; x++) {
            put(blocks, new BlockStone(), x, FLOOR - 1, 0);
            put(blocks, new BlockStone(), x, FLOOR + 2, 0);
        }
        TestPlayer player = player(blocks);
        player.place(0.5, FLOOR + 4, 0.5);
        player.step(4.5, FLOOR, 0.5);
        assertEquals(4.5, player.x, 1.0E-6);
        assertEquals(FLOOR, player.y, 1.0E-6);
        assertEquals(0, player.selfPositionPackets);
    }

    @Test
    void blinkToFreeSpaceBehindAWallRemainsOutsideThisFix() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 2, FLOOR, 0);
        put(blocks, new BlockStone(), 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        player.step(8.5, FLOOR, 0.5);
        assertEquals(8.5, player.x, 1.0E-6, "documents the remaining #727 speed gap");
    }

    private static Stream<Arguments> movementModes() {
        return Stream.of(
                Arguments.of(ProtocolInfo.v1_2_0, 0, false),
                Arguments.of(ProtocolInfo.v1_20_0, 0, false),
                Arguments.of(ProtocolInfo.v1_20_0, 1, false),
                Arguments.of(ProtocolInfo.v1_21_2, 0, true),
                Arguments.of(ProtocolInfo.v1_21_2, 1, true),
                Arguments.of(ProtocolInfo.v1_21_90, 2, false),
                Arguments.of(ProtocolInfo.v1_26_45, 2, false));
    }

    @ParameterizedTest
    @MethodSource("movementModes")
    void packetMovementCannotSinkAndMustAcknowledgeCorrection(int protocol, int mode, boolean netease) {
        TestPlayer player = player(ground(4));
        player.protocol = protocol;
        player.gameVersion = GameVersion.byProtocol(protocol, netease);
        MockServer.get().serverAuthoritativeMovementMode = mode;
        player.packetStep(0.5, FLOOR - 0.8, 0.5);
        assertNotNull(player.lastQueued, "real packet handling must queue the input");
        assertEquals(FLOOR, player.y, 1.0E-6);
        assertEquals(0, player.selfPositionPackets);

        player.packetStep(0.5, FLOOR - 4, 0.5);
        assertEquals(1, player.selfPositionPackets);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
        assertNotNull(player.forceMovement);
        player.packetStep(0.5, FLOOR - 4, 0.5);
        assertNull(player.lastQueued, "unacknowledged correction rejects further movement");
        assertEquals(MovePlayerPacket.MODE_RESET, player.lastSendMode);
        player.packetStep(0.5, FLOOR, 0.5);
        assertNotNull(player.lastQueued);
        assertNull(player.forceMovement);
        player.packetStep(0.6, FLOOR + 0.42, 0.5);
        assertEquals(FLOOR + 0.42, player.y, 0.00002, "jump after correction is accepted");
    }

    @ParameterizedTest
    @ValueSource(floats = {0.1f, 0.25f, 0.5f, 1f, 2f})
    void scaledProbeAndBoundingBoxSurviveAcceptedAndRejectedMovement(float scale) {
        Map<String, Block> blocks = ground(1);
        for (int x = 1; x <= 3; x++) put(blocks, new BlockStone(), x, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        player.setScale(scale);
        for (double x = 0.7; x <= 1.51; x += 0.2) player.step(x, FLOOR, 0.5);
        assertEquals(1.5, player.x, 1.0E-6, "small player must fit below the ceiling");
        assertEquals(player.getWidth() * scale, player.boundingBox.getMaxX() - player.boundingBox.getMinX(), 1.0E-6);
        assertEquals(player.getHeight() * scale, player.boundingBox.getMaxY() - player.boundingBox.getMinY(), 1.0E-6);
        put(blocks, new BlockStone(), 2, FLOOR, 0);
        player.step(2.1, FLOOR, 0.5);
        assertEquals(1.5, player.x, 1.0E-6, "scaled core still rejects a full wall");
        player.step(2.4, FLOOR, 0.5);
        assertNotNull(player.forceMovement);
        assertEquals(player.getWidth() * scale, player.boundingBox.getMaxX() - player.boundingBox.getMinX(), 1.0E-6,
                "rollback preserves scaled width");
        assertEquals(player.getHeight() * scale, player.boundingBox.getMaxY() - player.boundingBox.getMinY(), 1.0E-6,
                "rollback preserves scaled height");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pluginMaySuppressCorrectionWhilePositionStaysBlocked(boolean cancel) {
        TestPlayer player = player(ground(4));
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.isEnabled()).thenReturn(true);
        PluginManager manager = new PluginManager(MockServer.get(), null);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(manager);
        int[] events = {0};
        manager.registerEvent(PlayerInvalidMoveEvent.class, new Listener() {}, EventPriority.NORMAL,
                (listener, event) -> {
                    PlayerInvalidMoveEvent move = (PlayerInvalidMoveEvent) event;
                    events[0]++;
                    if (cancel) move.setCancelled();
                    else move.setRevert(false);
                }, plugin);
        try {
            player.step(0.5, FLOOR - 4, 0.5);
            assertEquals(1, events[0]);
            assertEquals(FLOOR, player.y, 1.0E-6);
            assertNull(player.forceMovement);
            assertEquals(0, player.selfPositionPackets);
            assertEquals(0, player.speed.lengthSquared());
        } finally {
            HandlerList.unregisterAll(plugin);
        }
    }

    @Test
    void blockedInputPublishesRotationAtTheAuthoritativePosition() {
        TestPlayer player = player(ground(4));
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.isEnabled()).thenReturn(true);
        PluginManager manager = new PluginManager(MockServer.get(), null);
        Mockito.when(MockServer.get().getPluginManager()).thenReturn(manager);
        int[] events = {0};
        manager.registerEvent(PlayerMoveEvent.class, new Listener() {}, EventPriority.NORMAL,
                (listener, event) -> {
                    PlayerMoveEvent move = (PlayerMoveEvent) event;
                    assertEquals(FLOOR, move.getFrom().y);
                    assertEquals(FLOOR, move.getTo().y);
                    assertEquals(45, move.getTo().yaw);
                    events[0]++;
                }, plugin);
        try {
            player.yaw = 45;
            player.step(0.5, FLOOR - 0.8, 0.5);
            assertEquals(1, events[0]);
            assertEquals(45, player.getYaw());
            assertEquals(0, player.speed.lengthSquared());
        } finally {
            HandlerList.unregisterAll(plugin);
        }
    }

    @Test
    void realTeleportHandshakeThenFallingCannotCrossThePlatform() {
        TestPlayer player = player(ground(1));
        player.place(0.5, FLOOR + 10, 0.5);
        assertTrue(player.teleport(new Location(0.5, FLOOR, 0.5, player.level), null));
        assertNotNull(player.teleportPosition, "teleport waits for client chunks");
        player.packetStep(0.5, FLOOR - 4, 0.5);
        assertNull(player.lastQueued, "unready teleport ignores movement");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            player.usedChunks.put(Level.chunkHash(x, z), true);
        assertTrue(player.checkTeleportPosition());
        assertNull(player.teleportPosition, "real chunk-ready handshake must retire teleport");
        player.packetStep(0.5, FLOOR, 0.5, true);
        assertNull(player.forceMovement, "client acknowledges the real teleport");
        assertTrue(fall(player, 40) >= FLOOR - 0.6 - 1.0E-6);
    }

    @Test
    void blockedWallContactCannotGroundAnAirbornePlayer() {
        Map<String, Block> blocks = new HashMap<>();
        for (int y = FLOOR - 1; y <= FLOOR + 2; y++) put(blocks, new BlockStone(), 1, y, 0);
        TestPlayer player = player(blocks);
        player.place(0.75, FLOOR, 0.5);
        player.onGround = false;
        player.step(0.9, FLOOR - 0.1, 0.5);
        assertEquals(0.75, player.x, 1.0E-6);
        assertFalse(player.onGround);
        assertFalse(player.fallGrounded);
    }

    @Test
    void acceptedInputRestartsTheStuckTimer() {
        Map<String, Block> blocks = ground(1);
        put(blocks, new BlockStone(), 2, FLOOR, 0);
        put(blocks, new BlockStone(), 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        player.step(1.7, FLOOR, 0.5);
        for (int i = 0; i < 20; i++) player.step(2.3, FLOOR, 0.5);
        player.step(1.7, FLOOR, 0.5);
        player.step(2.3, FLOOR, 0.5);
        assertEquals(0, player.selfPositionPackets);
    }

    @Test
    void disablingCollisionCheckRestoresLegacyAcceptance() {
        TestPlayer player = player(ground(4));
        assertTrue(MockServer.get().getServerConfig().playerSettings().movementCollisionCheck());
        MockServer.get().getServerConfig().playerSettings().movementCollisionCheck(false);
        player.step(0.5, FLOOR - 0.8, 0.5);
        assertEquals(FLOOR - 0.8, player.y, 1.0E-6);
        assertEquals(0, player.selfPositionPackets);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noClipAndRidingAreExempt(boolean noClip) {
        Map<String, Block> blocks = ground(4);
        TestPlayer player = player(blocks);
        if (noClip) player.noClip = true;
        else player.riding = Mockito.mock(cn.nukkit.entity.Entity.class);
        player.step(0.5, FLOOR - 0.8, 0.5);
        assertEquals(FLOOR - 0.8, player.y, 1.0E-6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gliding", "riptide", "flying"})
    void specialMotionSkipsVerticalPathInferenceButStillChecksDestination(String motion) {
        Map<String, Block> blocks = ground(1);
        TestPlayer player = player(blocks);
        if (motion.equals("gliding")) player.setGliding(true);
        else if (motion.equals("riptide")) player.onSpinAttack(1);
        else player.adventureSettings.set(AdventureSettings.Type.FLYING, true);
        player.step(0.5, FLOOR - 3, 0.5);
        assertEquals(FLOOR - 3, player.y, 1.0E-6, "thin floor path is exempt for special motion");
        player.step(0.5, FLOOR - 0.8, 0.5);
        assertEquals(FLOOR - 3, player.y, 1.0E-6, "solid destination stays blocked");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1048580, 1048576})
    void farCoordinatesKeepFloatToleranceWithoutLosingScaledCollision(int origin) {
        Map<String, Block> blocks = new HashMap<>();
        put(blocks, new BlockStone(), origin + 2, FLOOR, 0);
        put(blocks, new BlockStone(), origin + 2, FLOOR + 1, 0);
        TestPlayer player = player(blocks);
        player.place(origin + 1.7, FLOOR, 0.5);
        player.step(origin + 1.9, FLOOR, 0.5);
        assertEquals(origin + 1.9, player.x, 1.0E-6, "far-coordinate float contact is tolerated");
        player.step(origin + 2.2, FLOOR, 0.5);
        assertEquals(origin + 1.9, player.x, 1.0E-6);
        player.setScale(0.1f);
        player.place(origin + 1.9, FLOOR, 0.5);
        player.step(origin + 2.2, FLOOR, 0.5);
        assertEquals(origin + 1.9, player.x, 1.0E-6, "probe must not invert for a tiny player");
    }

    /** Free fall from rest. Model a client obeying each server correction before falling again. */
    private static double fall(TestPlayer player, int ticks) {
        double y = player.y;
        double velocity = 0;
        double lowest = player.y;
        for (int i = 0; i < ticks; i++) {
            velocity = (velocity - 0.08) * 0.98;
            y += velocity;
            int corrections = player.selfPositionPackets;
            player.step(player.x, y, player.z);
            lowest = Math.min(lowest, player.y);
            if (player.selfPositionPackets > corrections) {
                y = player.lastCorrectionY;
                velocity = 0;
            }
        }
        return lowest;
    }

    private static Map<String, Block> ground(int thickness) {
        Map<String, Block> blocks = new HashMap<>();
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int depth = 1; depth <= thickness; depth++) {
                    put(blocks, new BlockStone(), x, FLOOR - depth, z);
                }
            }
        }
        return blocks;
    }

    private static void put(Map<String, Block> blocks, Block block, int x, int y, int z) {
        block.setComponents(x, y, z);
        blocks.put(x + ":" + y + ":" + z, block);
    }

    private static TestPlayer player(Map<String, Block> blocks) {
        MockServer.reset();
        TICK[0] = 1000;
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(319);
        Mockito.when(level.getVibrationManager())
                .thenReturn(Mockito.mock(cn.nukkit.level.vibration.VibrationManager.class));
        Mockito.when(level.getChunkIfLoaded(Mockito.anyInt(), Mockito.anyInt())).thenAnswer(invocation -> {
            int cx = invocation.getArgument(0);
            int cz = invocation.getArgument(1);
            BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
            Mockito.when(chunk.getX()).thenReturn(cx);
            Mockito.when(chunk.getZ()).thenReturn(cz);
            Mockito.when(chunk.isGenerated()).thenReturn(true);
            Mockito.when(chunk.getBlockId(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.eq(0)))
                    .thenAnswer(cell -> blocks.getOrDefault((cx * 16 + (int) cell.getArgument(0)) + ":"
                            + cell.getArgument(1) + ":" + (cz * 16 + (int) cell.getArgument(2)), new BlockAir()).getId());
            return chunk;
        });
        Mockito.when(level.getBlock(Mockito.any(FullChunk.class), Mockito.anyInt(), Mockito.anyInt(),
                Mockito.anyInt(), Mockito.eq(0), Mockito.eq(false))).thenAnswer(invocation -> {
            Block block = blocks.getOrDefault(invocation.getArgument(1) + ":" + invocation.getArgument(2)
                    + ":" + invocation.getArgument(3), new BlockAir());
            block.level = level;
            return block;
        });
        Mockito.when(level.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.eq(0)))
                .thenAnswer(invocation -> {
                    Block block = blocks.getOrDefault(invocation.getArgument(0) + ":" + invocation.getArgument(1)
                            + ":" + invocation.getArgument(2), new BlockAir());
                    block.level = level;
                    return block;
                });
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false))).thenCallRealMethod();

        BaseFullChunk chunk = (BaseFullChunk) level.getChunkIfLoaded(0, 0);
        Server server = MockServer.get();
        Mockito.when(server.getDefaultLevel()).thenReturn(level);
        Mockito.when(server.getViewDistance()).thenReturn(1);
        Mockito.when(server.getAllowFlight()).thenReturn(false);
        Mockito.when(server.getTick()).thenAnswer(invocation -> TICK[0]);
        Mockito.when(level.getChunk(0, 0, false)).thenReturn(chunk);
        Mockito.when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        server.serverAuthoritativeMovementMode = 1;

        SourceInterface source = Mockito.mock(SourceInterface.class);
        Mockito.when(source.getSession(Mockito.any(InetSocketAddress.class)))
                .thenReturn(Mockito.mock(NetworkPlayerSession.class));
        TestPlayer player = new TestPlayer(source);
        player.level = level;
        player.chunk = chunk;
        player.spawned = true;
        player.loggedIn = true;
        player.loginVerified = true;
        player.protocol = ProtocolInfo.v1_21_70;
        player.gamemode = Player.SURVIVAL;
        player.setCheckMovement(true);
        player.lastTeleportTick = -1000;
        player.markAlive();
        player.temporalVector = new Vector3();
        player.adventureSettings = new AdventureSettings(player);
        player.boundingBox = new SimpleAxisAlignedBB(0.2, FLOOR, 0.2, 0.8, FLOOR + 1.8, 0.8);
        player.x = 0.5;
        player.y = FLOOR;
        player.z = 0.5;
        player.lastX = player.x;
        player.lastY = player.y;
        player.lastZ = player.z;
        player.firstMove = false;
        return player;
    }

    private static final class TestPlayer extends Player {
        private int lastSendMode = -1;
        private int selfPositionPackets;
        private double lastCorrectionY = Double.NaN;
        private boolean fallGrounded;
        private Vector3 lastQueued;
        private int inputTick;

        private TestPlayer(SourceInterface source) {
            super(source, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void markAlive() {
            this.health = 20;
        }

        private void place(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.boundingBox.setBounds(x - 0.3, y, z - 0.3, x + 0.3, y + this.getHeight(), z + 0.3);
        }

        private void step(double x, double y, double z) {
            TICK[0]++;
            this.handleMovement(new Vector3(x, y, z), 1);
        }

        private void packetStep(double x, double y, double z) {
            packetStep(x, y, z, false);
        }

        private void packetStep(double x, double y, double z, boolean acknowledgeTeleport) {
            TICK[0]++;
            newPosition = null;
            if (isMovementServerAuthoritative()) {
                PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
                packet.setTick(++inputTick);
                packet.setPosition(new Vector3f((float) x, (float) (y + getBaseOffset()), (float) z));
                if (acknowledgeTeleport) packet.getInputData().add(AuthInputAction.HANDLE_TELEPORT);
                handleDataPacket(packet);
            } else {
                MovePlayerPacket packet = new MovePlayerPacket();
                packet.x = (float) x;
                packet.y = (float) (y + getBaseOffset());
                packet.z = (float) z;
                handleDataPacket(packet);
            }
            lastQueued = newPosition;
            if (lastQueued != null) handleMovement(lastQueued, 1);
            newPosition = null;
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
        public void sendPosition(double x, double y, double z, double yaw, double pitch, double headYaw,
                                 int mode, java.util.Collection<Player> targets) {
            if (targets == null || targets.contains(this)) {
                this.lastSendMode = mode;
                this.selfPositionPackets++;
            }
        }

        @Override
        public void sendPosition(Vector3 pos, double yaw, double pitch, double headYaw, int mode, Player[] targets) {
            if (targets == null || java.util.Arrays.asList(targets).contains(this)) {
                this.lastSendMode = mode;
                this.selfPositionPackets++;
                this.lastCorrectionY = pos.y;
            }
        }

        @Override
        protected void checkChunks() {
        }

        @Override
        protected void broadcastMovement() {
        }

        @Override
        protected void updateFallState(boolean onGround) {
            this.fallGrounded = onGround;
        }

        @Override
        public void checkSwimmingState() {
        }

        @Override
        public void spawnToAll() {
        }
    }
}
