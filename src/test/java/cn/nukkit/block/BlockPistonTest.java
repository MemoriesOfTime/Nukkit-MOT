package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.event.block.BlockPistonEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.serverconfig.ServerConfig;
import cn.nukkit.utils.serverconfig.category.GameFeatureSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockPistonTest {

    @BeforeAll
    static void initBlocks() {
        Block.init();
    }

    @Test
    void blockedExtensionDoesNotCommitPoweredStateSoLaterUpdatesCanRetry() {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        when(level.getServer()).thenReturn(server);
        when(server.getServerConfig()).thenReturn(new ServerConfig());
        BlockEntityPistonArm arm = mock(BlockEntityPistonArm.class);
        arm.state = 0;
        arm.powered = false;

        when(level.getBlockEntity(any(Vector3.class))).thenReturn(arm);
        when(level.isSidePowered(any(Block.class), any())).thenReturn(true);
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Vector3 position = new Vector3(
                    ((Number) invocation.getArgument(0)).intValue(),
                    ((Number) invocation.getArgument(1)).intValue(),
                    ((Number) invocation.getArgument(2)).intValue());
            Block block = Block.get(BlockID.BEDROCK);
            block.position(Position.fromObject(position, level));
            block.level = level;
            return block;
        });

        BlockPiston piston = new BlockPiston();
        piston.position(new Position(0, 64, 0, level));
        piston.level = level;

        piston.onUpdate(Level.BLOCK_UPDATE_NORMAL);

        assertFalse(arm.powered,
                "A blocked piston must retain the previous powered state so a later neighbor update retries extension");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void movingPistonSkipsPowerReadsWithoutMutatingPendingMovement(int state) {
        PowerScanFixture fixture = new PowerScanFixture();
        fixture.arm.state = state;
        fixture.arm.powered = state == 1;
        for (int type : new int[] {Level.BLOCK_UPDATE_NORMAL, Level.BLOCK_UPDATE_REDSTONE, Level.BLOCK_UPDATE_SCHEDULED}) {
            assertEquals(type, fixture.piston.onUpdate(type));
        }
        assertEquals(0, fixture.reads.get(), "Moving arm ignores power; no neighbor blocks need fetching");
        assertEquals(state, fixture.arm.state);
        assertEquals(state == 1, fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());
        verify(fixture.plugins, never()).callEvent(any(BlockPistonEvent.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void stationaryPistonStillChecksAllFiveUnpoweredNeighbors(int state) {
        PowerScanFixture fixture = new PowerScanFixture();
        fixture.arm.state = state;
        assertEquals(Level.BLOCK_UPDATE_NORMAL, fixture.piston.onUpdate(Level.BLOCK_UPDATE_NORMAL));
        assertEquals(10, fixture.reads.get(), "Each air neighbor is fetched by getSide and isSidePowered");
        assertFalse(fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());
    }

    @Test
    void pulseDuringAnimationIsRecheckedWhenMovementCompletes() {
        PowerScanFixture fixture = new PowerScanFixture();
        fixture.arm.state = 3;
        fixture.wirePowered = true;
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertEquals(0, fixture.reads.get());
        assertFalse(fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());

        fixture.arm.state = 0;
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertTrue(fixture.arm.powered);
        verify(fixture.arm).move(eq(true), anyList());
        verify(fixture.plugins).callEvent(any(BlockPistonEvent.class));
    }

    @Test
    void stationaryExtendedPistonStillRetractsAfterPowerDisappears() {
        PowerScanFixture fixture = new PowerScanFixture();
        fixture.arm.state = 2;
        fixture.arm.powered = true;
        fixture.piston.extended = true;
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertFalse(fixture.arm.powered);
        verify(fixture.arm).move(eq(false), anyList());
    }

    @Test
    void cancelledMoveStillRetainsOldPowerSoFutureUpdatesCanRetry() {
        PowerScanFixture fixture = new PowerScanFixture();
        fixture.arm.state = 0;
        fixture.wirePowered = true;
        doAnswer(invocation -> {
            ((BlockPistonEvent) invocation.getArgument(0)).setCancelled();
            return null;
        }).when(fixture.plugins).callEvent(any(BlockPistonEvent.class));
        fixture.piston.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertFalse(fixture.arm.powered);
        verify(fixture.arm, never()).move(anyBoolean(), anyList());
    }

    @Test
    void unchangedConfigurationKeepsVanillaTwelveBlockLimit() {
        PushLimitFixture fixture = new PushLimitFixture();
        assertEquals(12, fixture.settings.pistonPushLimit());
        fixture.line(1, 12, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 12, 12);
        fixture.put(13, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 12);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12, 64})
    void straightPushCountsEveryBlockExactlyOnce(int limit) {
        PushLimitFixture fixture = new PushLimitFixture().limit(limit);
        fixture.line(1, limit, 0, Block.STONE);
        assertMoves(fixture.calculate(true), limit, limit);
        fixture.put(limit + 1, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE})
    void invalidConfigurationFallsBackToTwelveWithoutOverflow(int invalidLimit) {
        PushLimitFixture fixture = new PushLimitFixture().limit(invalidLimit);
        fixture.line(1, 12, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 12, 12);
        fixture.put(13, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 12);
    }

    @Test
    void calculatorCapturesOneLimitForItsEntireMovement() {
        PushLimitFixture fixture = new PushLimitFixture().limit(64);
        fixture.line(1, 64, 0, Block.STONE);
        BlockPistonBase.BlocksCalculator captured = fixture.calculate(true);
        fixture.limit(12);
        assertMoves(captured, 64, 64);
        assertBlocked(fixture.calculate(true), 12);
        fixture.limit(64);
        assertMoves(captured, 64, 64);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12, 64})
    void stickyRetractionCountsRearSlimeChainWithoutCountingOriginTwice(int limit) {
        PushLimitFixture fixture = new PushLimitFixture(true, 64, BlockFace.EAST).limit(limit);
        fixture.line(2, limit, 0, Block.SLIME_BLOCK);
        assertMoves(fixture.calculate(false), limit, limit);
        fixture.put(limit + 2, 0, Block.SLIME_BLOCK);
        assertBlocked(fixture.calculate(false), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 64})
    void aSideBranchSharesTheSameBudgetAsTheMainLine(int limit) {
        PushLimitFixture fixture = new PushLimitFixture().limit(limit);
        fixture.put(1, 0, Block.SLIME_BLOCK);
        fixture.line(1, limit - 1, 1, Block.STONE);
        assertMoves(fixture.calculate(true), limit, limit);
        fixture.put(limit, 1, Block.STONE);
        assertBlocked(fixture.calculate(true), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 64})
    void discoveringAnotherBranchCannotExceedTheGlobalLimit(int limit) {
        PushLimitFixture fixture = new PushLimitFixture().limit(limit);
        fixture.line(1, limit / 2, 0, Block.SLIME_BLOCK);
        fixture.line(1, limit / 2, 1, Block.SLIME_BLOCK);
        assertMoves(fixture.calculate(true), limit, limit);
        fixture.put(1, -1, Block.STONE);
        assertBlocked(fixture.calculate(true), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {-64, -32, 306, 319})
    void horizontalMovementWorksAtNegativeAndHighWorldCoordinates(int y) {
        PushLimitFixture fixture = new PushLimitFixture(false, y, BlockFace.EAST).limit(64);
        fixture.line(1, 64, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 64, 64);
        PushLimitFixture sticky = new PushLimitFixture(true, y, BlockFace.EAST).limit(64);
        sticky.line(2, 64, 0, Block.SLIME_BLOCK);
        assertMoves(sticky.calculate(false), 64, 64);
    }

    @Test
    void upwardMovementStillCannotMoveBeyondTheWorldCeiling() {
        PushLimitFixture fixture = new PushLimitFixture(false, 306, BlockFace.UP).limit(64);
        fixture.line(1, 12, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 12, 64);
        fixture.put(13, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 64);
    }

    @Test
    void downwardMovementStillCannotMoveBelowNegativeWorldFloor() {
        PushLimitFixture fixture = new PushLimitFixture(false, -50, BlockFace.DOWN).limit(64);
        fixture.line(1, 13, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 13, 64);
        fixture.put(14, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 64);
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.BEDROCK, Block.OBSIDIAN, Block.PISTON_HEAD})
    void largerLimitDoesNotPermitImmovableBlocks(int blockId) {
        PushLimitFixture fixture = new PushLimitFixture().limit(64);
        fixture.line(1, 20, 0, Block.STONE);
        fixture.put(21, 0, blockId);
        assertBlocked(fixture.calculate(true), 64);
    }

    @Test
    void immovableBlockEntityStillBlocksPushAndPull() {
        PushLimitFixture fixture = new PushLimitFixture(true, 64, BlockFace.EAST).limit(64);
        fixture.put(1, 0, Block.STONE);
        fixture.put(2, 0, Block.SLIME_BLOCK);
        BlockEntity tile = mock(BlockEntity.class);
        when(tile.isMovable()).thenReturn(false);
        when(fixture.level.getBlockEntity(any(Vector3.class))).thenReturn(tile);
        assertBlocked(fixture.calculate(true), 64);
        assertBlocked(fixture.calculate(false), 64);
    }

    @Test
    void aBlockThatBreaksWhenPushedUsesTheDestroyListAtTheLimit() {
        PushLimitFixture fixture = new PushLimitFixture().limit(64);
        fixture.line(1, 64, 0, Block.STONE);
        fixture.put(65, 0, Block.TORCH);
        BlockPistonBase.BlocksCalculator calculator = fixture.calculate(true);
        assertMoves(calculator, 64, 64);
        assertEquals(List.of(new BlockVector3(65, 64, 0)),
                calculator.getBlocksToDestroy().stream().map(Vector3::asBlockVector3).toList());
    }

    @Test
    void intersectingSlimeAssembliesNeverDuplicateMovementCells() {
        PushLimitFixture fixture = new PushLimitFixture().limit(64);
        Random random = new Random(745);
        for (int sample = 0; sample < 300; sample++) {
            fixture.blocks.clear();
            for (int x = -2; x <= 6; x++) {
                for (int z = -2; z <= 2; z++) {
                    if ((x != 0 || z != 0) && random.nextInt(4) != 0) {
                        fixture.put(x, z, random.nextBoolean() ? Block.SLIME_BLOCK : Block.STONE);
                    }
                }
            }
            fixture.put(1, 0, Block.SLIME_BLOCK);
            BlockPistonBase.BlocksCalculator calculator = fixture.calculate(true);
            calculator.canMove();
            assertUniqueWithinLimit(calculator, 64);
        }
    }

    private static void assertMoves(BlockPistonBase.BlocksCalculator calculator, int count, int limit) {
        assertTrue(calculator.canMove(), "The complete assembly should move");
        assertEquals(count, calculator.getBlocksToMove().size());
        assertUniqueWithinLimit(calculator, limit);
    }

    private static void assertBlocked(BlockPistonBase.BlocksCalculator calculator, int limit) {
        assertFalse(calculator.canMove(), "The complete movement should be rejected");
        assertUniqueWithinLimit(calculator, limit);
    }

    private static void assertUniqueWithinLimit(BlockPistonBase.BlocksCalculator calculator, int limit) {
        List<BlockVector3> positions = calculator.getBlocksToMove().stream()
                .map(Vector3::asBlockVector3).toList();
        assertEquals(positions.size(), new HashSet<>(positions).size(), positions.toString());
        assertTrue(positions.size() <= limit, "Movement list exceeds its captured limit");
    }

    private static final class TestPiston extends BlockPiston {
        boolean extended;
        @Override
        public boolean isExtended() {
            return extended;
        }
    }

    private static final class PowerScanFixture {
        final Level level = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockEntityPistonArm arm = mock(BlockEntityPistonArm.class);
        final TestPiston piston = new TestPiston();
        final AtomicInteger reads = new AtomicInteger();
        boolean wirePowered;

        PowerScanFixture() {
            when(level.getServer()).thenReturn(server);
            when(server.getServerConfig()).thenReturn(new ServerConfig());
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getVibrationManager()).thenReturn(mock(VibrationManager.class));
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getBlockEntity(any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                return pos.getFloorX() == 0 && pos.getFloorY() == 64 && pos.getFloorZ() == 0 ? arm : null;
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    blockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            when(level.getBlock(any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                return blockAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.isSidePowered(any(Vector3.class), any(BlockFace.class))).thenCallRealMethod();
            when(level.getRedstonePower(any(Vector3.class), any(BlockFace.class))).thenCallRealMethod();
            piston.position(new Position(0, 64, 0, level));
            piston.level = level;
        }

        private Block blockAt(int x, int y, int z) {
            reads.incrementAndGet();
            // Default piston faces DOWN; UP is an eligible input while DOWN stays empty for extension.
            Block block = wirePowered && x == 0 && y == 65 && z == 0
                    ? new BlockRedstoneWire(15) : new BlockAir();
            block.position(new Position(x, y, z, level));
            block.level = level;
            return block;
        }
    }

    private static class PushLimitFixture {
        private final Level level = mock(Level.class);
        private final ServerConfig config = new ServerConfig();
        private final GameFeatureSettings settings = config.gameFeatureSettings();
        private final Map<BlockVector3, Block> blocks = new HashMap<>();
        private final BlockPistonBase piston;
        private final BlockFace face;

        private PushLimitFixture() {
            this(false, 64, BlockFace.EAST);
        }

        private PushLimitFixture(boolean sticky, int y, BlockFace face) {
            this.face = face;
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getServerConfig()).thenReturn(config);
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                BlockVector3 pos = new BlockVector3(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2));
                Block block = blocks.getOrDefault(pos, Block.get(Block.AIR)).clone();
                block.position(new Position(pos.x, pos.y, pos.z, level));
                return block;
            });
            int meta = face.getHorizontalIndex() < 0 ? face.getIndex() : face.getOpposite().getIndex();
            piston = sticky ? new BlockPistonSticky(meta) : new BlockPiston(meta);
            piston.position(new Position(0, y, 0, level));
            blocks.put(piston.asBlockVector3(), piston);
        }

        private PushLimitFixture limit(int limit) {
            settings.pistonPushLimit(limit);
            return this;
        }

        private void line(int start, int count, int z, int blockId) {
            for (int offset = start; offset < start + count; offset++) {
                put(offset, z, blockId);
            }
        }

        private void put(int offset, int z, int blockId) {
            Vector3 pos = piston.add(0).getSide(face, offset).add(0, 0, z);
            blocks.put(pos.asBlockVector3(), Block.get(blockId));
        }

        private BlockPistonBase.BlocksCalculator calculate(boolean extending) {
            return piston.new BlocksCalculator(extending);
        }
    }
}
