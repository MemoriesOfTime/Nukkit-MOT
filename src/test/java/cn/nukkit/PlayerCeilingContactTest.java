package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockLeaves;
import cn.nukkit.block.BlockStone;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.FullChunk;
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
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Regression for legal sprint-jumps rejected by float-only ceiling contact. */
class PlayerCeilingContactTest {
    private enum Shape { AIR, EXACT_CEILING, WIRE_CEILING, WALL }

    private static Stream<Integer> protocols() {
        return Stream.of(ProtocolInfo.v1_20_0, ProtocolInfo.v1_21_70, ProtocolInfo.v1_26_45);
    }

    private static Stream<Arguments> environments() {
        return protocols().flatMap(protocol -> Stream.of(44, 61, 91, 95, 101, 128, 151)
                .flatMap(floor -> Stream.of(Shape.values())
                        .map(shape -> Arguments.of(protocol, floor, shape))));
    }

    @ParameterizedTest(name = "protocol={0} floor={1} shape={2}")
    @MethodSource("environments")
    void batchedCeilingContactKeepsHorizontalMovement(int protocol, int floor, Shape shape) {
        boolean ceiling = shape == Shape.EXACT_CEILING || shape == Shape.WIRE_CEILING;
        Map<String, Block> blocks = corridor(floor, ceiling, false, 3);
        if (shape == Shape.WALL) {
            putBlock(blocks, new BlockStone(), 1, floor, 0);
            putBlock(blocks, new BlockStone(), 1, floor + 1, 0);
        }
        TestPlayer player = createPlayer(level(blocks), protocol, 1, floor);
        double feetAtCeiling = (double) (floor + 2) - player.getHeight();
        if (shape == Shape.EXACT_CEILING) {
            // Exact geometry control: no float packet round-trip.
            player.newPosition = new Vector3(1.55, feetAtCeiling, 0.5);
        } else {
            queueThreeSteps(player, 1.55, feetAtCeiling, 0.5);
        }
        Vector3 requested = processQueued(player);
        if (shape == Shape.WALL) {
            assertCorrected(player, 0.5, floor, 0.5);
        } else {
            assertAccepted(player, requested);
        }
    }

    private static Stream<Arguments> contactCycles() {
        return protocols().flatMap(protocol -> Stream.of(44, 128)
                .flatMap(floor -> Stream.of(false, true)
                        .map(stone -> Arguments.of(protocol, floor, stone))));
    }

    @ParameterizedTest(name = "protocol={0} floor={1} stone={2}")
    @MethodSource("contactCycles")
    void repeatedCeilingContactsDoNotAccumulateCorrections(int protocol, int floor, boolean stone) {
        TestPlayer player = createPlayer(level(corridor(floor, true, stone, 20)), protocol, 1, floor);
        double feetAtCeiling = (double) (floor + 2) - player.getHeight();
        for (int cycle = 0; cycle < 8; cycle++) {
            queueThreeSteps(player, player.x + 1.05, feetAtCeiling, 0.5);
            assertAccepted(player, processQueued(player));
            queueThreeSteps(player, player.x + 1.05, feetAtCeiling - 0.05, 0.5);
            assertAccepted(player, processQueued(player));
            assertEquals(feetAtCeiling - 0.05, player.y, 0.00002,
                    "Repeated contact must not accumulate vertical displacement");
        }
    }

    @ParameterizedTest
    @MethodSource("protocols")
    void legacyMovePlayerContactUsesTheSameCollisionTolerance(int protocol) {
        TestPlayer player = createPlayer(level(corridor(44, true, false, 3)), protocol, 0, 44);
        queueThreeSteps(player, 1.55, 46d - player.getHeight(), 0.5);
        assertAccepted(player, processQueued(player));
    }

    private static Stream<Arguments> blockedContacts() {
        return protocols().flatMap(protocol -> Stream.of("ceiling", "floor", "wall_x", "wall_z")
                .map(contact -> Arguments.of(protocol, contact)));
    }

    @ParameterizedTest(name = "protocol={0} blocked={1}")
    @MethodSource("blockedContacts")
    void meaningfulVerticalPenetrationAndEvenTinyWallPenetrationStayBlocked(int protocol, String contact) {
        int floor = 128;
        Map<String, Block> blocks = corridor(floor, true, false, 3);
        if (contact.equals("wall_x")) {
            putBlock(blocks, new BlockStone(), 2, floor, 0);
            putBlock(blocks, new BlockStone(), 2, floor + 1, 0);
        } else if (contact.equals("wall_z")) {
            putBlock(blocks, new BlockStone(), 0, floor, 2);
            putBlock(blocks, new BlockStone(), 0, floor + 1, 2);
        }
        TestPlayer player = createPlayer(level(blocks), protocol, 1, floor);
        double y = (double) (floor + 2) - player.getHeight();
        double x = 1.55;
        double z = 0.5;
        if (contact.equals("ceiling")) y += 0.001;
        if (contact.equals("floor")) y = floor - 0.001;
        // X/Z are deliberately within the vertical tolerance: the fix must never
        // shrink the side faces and turn a tiny wall penetration into an escape.
        if (contact.equals("wall_x")) x = 1.70001;
        if (contact.equals("wall_z")) { x = 0.5; z = 1.70001; }
        queueThreeSteps(player, x, y, z);
        processQueued(player);
        assertCorrected(player, 0.5, floor, 0.5);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void tallBlockCollisionCannotDisappearByRaisingTheScannedMinY(boolean wall) {
        int floor = 65;
        Map<String, Block> blocks = corridor(floor, false, false, 3);
        putBlock(blocks, wall ? new cn.nukkit.block.BlockWall() : new cn.nukkit.block.BlockFence(), 1, 64, 0);
        TestPlayer player = createPlayer(level(blocks), ProtocolInfo.v1_21_70, 1, floor);
        // Fence/wall belongs to cell 64 but extends to 65.5. Raising minY from
        // 64.99995 to 65.00005 would skip its cell, hiding about half a block.
        Block obstacle = blocks.get("1:64:0");
        obstacle.level = player.level;
        assertEquals(65.5, obstacle.getBoundingBox().getMaxY(), 0.000001);
        queueThreeSteps(player, 1.55, floor - 0.00005, 0.5);
        processQueued(player);
        assertCorrected(player, 0.5, floor, 0.5);
    }

    @Test
    void nonPlayerFastMoveRetainsItsStrictCollisionCheck() {
        Level level = level(corridor(44, true, false, 3));
        TestEntity entity = Mockito.mock(TestEntity.class, Mockito.CALLS_REAL_METHODS);
        entity.prepare(level);
        entity.fastMove(1.05, 0, 0);
        assertEquals(0.5, entity.x, 0.000001, "The player contact exception must not apply to mobs");
    }

    private static void queueThreeSteps(TestPlayer player, double x, double y, double z) {
        double fromX = player.x;
        double fromZ = player.z;
        player.newPosition = null;
        // Each client step is small, but three packets may reach one server tick.
        // The real input handler queues only the final point for handleMovement.
        for (int input = 1; input <= 3; input++) {
            player.clientMovement(fromX + (x - fromX) * input / 3, y,
                    fromZ + (z - fromZ) * input / 3);
        }
    }

    private static Vector3 processQueued(TestPlayer player) {
        assertNotNull(player.newPosition, "The actual packet handler must queue movement");
        Vector3 requested = player.newPosition.clone();
        player.driveHandleMovement(requested);
        player.newPosition = null;
        return requested;
    }

    private static void assertAccepted(TestPlayer player, Vector3 requested) {
        assertEquals(requested.x, player.x, 0.000001);
        assertEquals(requested.y, player.y, 0.000001);
        assertEquals(requested.z, player.z, 0.000001);
        assertNull(player.forceMovement, "Surface contact must not create a movement acknowledgement fence");
        assertEquals(0, player.selfPositionPackets);
    }

    private static void assertCorrected(TestPlayer player, double x, double y, double z) {
        assertNotNull(player.forceMovement);
        assertEquals(MovePlayerPacket.MODE_NORMAL, player.lastSendMode);
        assertEquals(x, player.x, 0.000001);
        assertEquals(y, player.y, 0.000001);
        assertEquals(z, player.z, 0.000001);
    }

    private static Map<String, Block> corridor(int floor, boolean ceiling, boolean stone, int length) {
        Map<String, Block> blocks = new HashMap<>();
        for (int x = -1; x <= length; x++) {
            for (int z = -1; z <= 1; z++) {
                putBlock(blocks, new BlockStone(), x, floor - 1, z);
                if (ceiling) putBlock(blocks, stone ? new BlockStone() : new BlockLeaves(), x, floor + 2, z);
            }
        }
        return blocks;
    }

    private static Level level(Map<String, Block> blocks) {
        MockServer.reset();
        Level level = Mockito.mock(Level.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(319);
        Mockito.when(level.getVibrationManager())
                .thenReturn(Mockito.mock(cn.nukkit.level.vibration.VibrationManager.class));
        Mockito.when(MockServer.get().getAllowFlight()).thenReturn(false);
        geometry(level, blocks);
        return level;
    }
    private static void putBlock(Map<String, Block> blocks, Block block, int x, int y, int z) {
        block.setComponents(x, y, z);
        blocks.put(x + ":" + y + ":" + z, block);
    }

    private static void geometry(Level level, Map<String, Block> blocks) {
        Mockito.when(level.getChunkIfLoaded(Mockito.anyInt(), Mockito.anyInt()))
                .thenAnswer(invocation -> {
                    int cx = invocation.getArgument(0);
                    int cz = invocation.getArgument(1);
                    BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
                    Mockito.when(chunk.getX()).thenReturn(cx);
                    Mockito.when(chunk.getZ()).thenReturn(cz);
                    Mockito.when(chunk.getBlockId(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.eq(0)))
                            .thenAnswer(cell -> blocks.getOrDefault(
                                    (cx * 16 + (int) cell.getArgument(0)) + ":" + cell.getArgument(1)
                                            + ":" + (cz * 16 + (int) cell.getArgument(2)), new BlockAir()).getId());
                    return chunk;
                });
        Mockito.when(level.getBlock(Mockito.any(FullChunk.class), Mockito.anyInt(), Mockito.anyInt(),
                        Mockito.anyInt(), Mockito.eq(0), Mockito.eq(false)))
                .thenAnswer(invocation -> {
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
        Mockito.when(level.hasCollision(Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenCallRealMethod();
    }

    private static TestPlayer createPlayer(Level level, int protocol, int movementMode, int floor) {
        BaseFullChunk chunk = (BaseFullChunk) level.getChunkIfLoaded(0, 0);
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
        player.loggedIn = true;
        player.loginVerified = true;
        player.protocol = protocol;
        player.gamemode = Player.SURVIVAL;
        player.setCheckMovement(true);
        server.serverAuthoritativeMovementMode = movementMode;
        player.lastTeleportTick = -1000;
        Mockito.when(server.getTick()).thenReturn(1000);
        player.markAlive();
        player.temporalVector = new Vector3();
        player.adventureSettings = new AdventureSettings(player);
        player.boundingBox = new SimpleAxisAlignedBB(-0.3, 64, -0.3, 0.3, 65.8, 0.3);
        player.moveTo(0.5, floor, 0.5);
        player.lastX = player.x;
        player.lastY = player.y;
        player.lastZ = player.z;
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
        private int selfPositionPackets;
        private int inputTick;

        private TestPlayer(SourceInterface sourceInterface) {
            super(sourceInterface, 1L, new InetSocketAddress("127.0.0.1", 19132));
        }

        private void driveHandleMovement(Vector3 clientPos) {
            this.handleMovement(clientPos, 1);
        }

        private void clientMovement(double x, double y, double z) {
            if (!this.isMovementServerAuthoritative()) {
                MovePlayerPacket packet = new MovePlayerPacket();
                packet.x = (float) x;
                packet.y = (float) (y + this.getBaseOffset());
                packet.z = (float) z;
                this.handleDataPacket(packet);
                return;
            }
            PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
            packet.setTick(++inputTick);
            packet.setPosition(new Vector3f((float) x, (float) (y + this.getBaseOffset()), (float) z));
            packet.getInputData().add(AuthInputAction.SPRINTING);
            packet.getInputData().add(AuthInputAction.JUMPING);
            packet.getInputData().add(AuthInputAction.VERTICAL_COLLISION);
            this.handleDataPacket(packet);
        }

        @Override
        public boolean isInsideOfWater() {
            return false;
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
            if (targets == null || targets.contains(this)) {
                this.lastSendMode = mode;
                this.selfPositionPackets++;
            }
        }

        @Override
        public void sendPosition(
                Vector3 pos,
                double yaw,
                double pitch,
                double headYaw,
                int mode,
                Player[] targets) {
            if (targets == null || java.util.Arrays.asList(targets).contains(this)) {
                this.lastSendMode = mode;
                this.selfPositionPackets++;
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
        }

        @Override
        public void checkSwimmingState() {
        }

        @Override
        public void spawnToAll() {
        }
    }
    private abstract static class TestEntity extends Entity {
        private TestEntity() {
            super(null, null);
        }

        private void prepare(Level level) {
            this.server = MockServer.get();
            this.level = level;
            this.onGround = true;
            this.x = 0.5;
            this.y = 44.20000076293945;
            this.z = 0.5;
            this.boundingBox = new SimpleAxisAlignedBB(0.2, y, 0.2, 0.8, y + 1.8f, 0.8);
        }

        @Override
        protected void checkChunks() {
        }

        @Override
        protected void updateFallState(boolean onGround) {
        }
    }
}
