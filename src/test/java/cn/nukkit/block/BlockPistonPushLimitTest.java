package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.serverconfig.ServerConfig;
import cn.nukkit.utils.serverconfig.category.GameFeatureSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class BlockPistonPushLimitTest {

    @BeforeAll
    static void initBlocks() {
        Block.init();
    }

    @Test
    void unchangedConfigurationKeepsVanillaTwelveBlockLimit() {
        Fixture fixture = new Fixture();
        assertEquals(12, fixture.settings.pistonPushLimit());
        fixture.line(1, 12, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 12, 12);
        fixture.put(13, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 12);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12, 64})
    void straightPushCountsEveryBlockExactlyOnce(int limit) {
        Fixture fixture = new Fixture().limit(limit);
        fixture.line(1, limit, 0, Block.STONE);
        assertMoves(fixture.calculate(true), limit, limit);
        fixture.put(limit + 1, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE})
    void invalidConfigurationFallsBackToTwelveWithoutOverflow(int invalidLimit) {
        Fixture fixture = new Fixture().limit(invalidLimit);
        fixture.line(1, 12, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 12, 12);
        fixture.put(13, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 12);
    }

    @Test
    void calculatorCapturesOneLimitForItsEntireMovement() {
        Fixture fixture = new Fixture().limit(64);
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
        Fixture fixture = new Fixture(true, 64, BlockFace.EAST).limit(limit);
        fixture.line(2, limit, 0, Block.SLIME_BLOCK);
        assertMoves(fixture.calculate(false), limit, limit);
        fixture.put(limit + 2, 0, Block.SLIME_BLOCK);
        assertBlocked(fixture.calculate(false), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 64})
    void aSideBranchSharesTheSameBudgetAsTheMainLine(int limit) {
        Fixture fixture = new Fixture().limit(limit);
        fixture.put(1, 0, Block.SLIME_BLOCK);
        fixture.line(1, limit - 1, 1, Block.STONE);
        assertMoves(fixture.calculate(true), limit, limit);
        fixture.put(limit, 1, Block.STONE);
        assertBlocked(fixture.calculate(true), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 64})
    void discoveringAnotherBranchCannotExceedTheGlobalLimit(int limit) {
        Fixture fixture = new Fixture().limit(limit);
        fixture.line(1, limit / 2, 0, Block.SLIME_BLOCK);
        fixture.line(1, limit / 2, 1, Block.SLIME_BLOCK);
        assertMoves(fixture.calculate(true), limit, limit);
        fixture.put(1, -1, Block.STONE);
        assertBlocked(fixture.calculate(true), limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {-64, -32, 306, 319})
    void horizontalMovementWorksAtNegativeAndHighWorldCoordinates(int y) {
        Fixture fixture = new Fixture(false, y, BlockFace.EAST).limit(64);
        fixture.line(1, 64, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 64, 64);
        Fixture sticky = new Fixture(true, y, BlockFace.EAST).limit(64);
        sticky.line(2, 64, 0, Block.SLIME_BLOCK);
        assertMoves(sticky.calculate(false), 64, 64);
    }

    @Test
    void upwardMovementStillCannotMoveBeyondTheWorldCeiling() {
        Fixture fixture = new Fixture(false, 306, BlockFace.UP).limit(64);
        fixture.line(1, 12, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 12, 64);
        fixture.put(13, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 64);
    }

    @Test
    void downwardMovementStillCannotMoveBelowNegativeWorldFloor() {
        Fixture fixture = new Fixture(false, -50, BlockFace.DOWN).limit(64);
        fixture.line(1, 13, 0, Block.STONE);
        assertMoves(fixture.calculate(true), 13, 64);
        fixture.put(14, 0, Block.STONE);
        assertBlocked(fixture.calculate(true), 64);
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.BEDROCK, Block.OBSIDIAN, Block.PISTON_HEAD})
    void largerLimitDoesNotPermitImmovableBlocks(int blockId) {
        Fixture fixture = new Fixture().limit(64);
        fixture.line(1, 20, 0, Block.STONE);
        fixture.put(21, 0, blockId);
        assertBlocked(fixture.calculate(true), 64);
    }

    @Test
    void immovableBlockEntityStillBlocksPushAndPull() {
        Fixture fixture = new Fixture(true, 64, BlockFace.EAST).limit(64);
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
        Fixture fixture = new Fixture().limit(64);
        fixture.line(1, 64, 0, Block.STONE);
        fixture.put(65, 0, Block.TORCH);
        BlockPistonBase.BlocksCalculator calculator = fixture.calculate(true);
        assertMoves(calculator, 64, 64);
        assertEquals(List.of(new BlockVector3(65, 64, 0)),
                calculator.getBlocksToDestroy().stream().map(Vector3::asBlockVector3).toList());
    }

    @Test
    void intersectingSlimeAssembliesNeverDuplicateMovementCells() {
        Fixture fixture = new Fixture().limit(64);
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

    private static class Fixture {
        private final Level level = mock(Level.class);
        private final ServerConfig config = new ServerConfig();
        private final GameFeatureSettings settings = config.gameFeatureSettings();
        private final Map<BlockVector3, Block> blocks = new HashMap<>();
        private final BlockPistonBase piston;
        private final BlockFace face;

        private Fixture() {
            this(false, 64, BlockFace.EAST);
        }

        private Fixture(boolean sticky, int y, BlockFace face) {
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

        private Fixture limit(int limit) {
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
