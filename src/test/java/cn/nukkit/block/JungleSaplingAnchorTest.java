package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual small/large jungle generators with controlled world storage. */
@Timeout(20)
class JungleSaplingAnchorTest {
    @BeforeAll
    static void initialize() { Block.init(); }

    @ParameterizedTest
    @ValueSource(ints = {BlockSapling.JUNGLE})
    void singleJungleGrowsAtItsSource(int metadata) {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, metadata);
        assertTrue(f.sapling().grow());
        assertJungleTrunk(f, 0, 0);
        assertEquals(Block.AIR, f.block(-2, 64, -2).getId(), "failed square-search offsets must not move the trunk");
        assertEquals(Block.GRASS, f.block(-2, 63, -2).getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {BlockSapling.JUNGLE})
    void blockedSingleJungleKeepsSourceAndDoesNotGrowElsewhere(int metadata) {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, metadata);
        f.put(0, 65, 0, Block.STONE, 0);
        Map<Cell, String> before = snapshot(f);

        // Existing grow() reports true even when the generator refuses and restores the source.
        assertTrue(f.sapling().grow());

        assertEquals(Block.SAPLING, f.block(0, 64, 0).getId());
        assertEquals(metadata, f.block(0, 64, 0).getDamage());
        assertEquals(Block.STONE, f.block(0, 65, 0).getId());
        assertEquals(before, snapshot(f), "failed growth must not build a tree at another anchor");
    }

    static Stream<Arguments> squareTriggers() {
        return IntStream.of(BlockSapling.JUNGLE).boxed()
                .flatMap(metadata -> IntStream.range(0, 4)
                        .mapToObj(corner -> Arguments.of(metadata, corner / 2, corner % 2)));
    }

    @ParameterizedTest
    @MethodSource("squareTriggers")
    void matchedSquareRetainsItsAnchorFromEveryTrigger(int metadata, int sourceX, int sourceZ) {
        World f = new World();
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            f.put(x, 64, z, Block.SAPLING, metadata);
        }
        assertTrue(((BlockSapling) f.block(sourceX, 64, sourceZ)).grow());
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            assertJungleTrunk(f, x, z);
        }
    }

    private static void assertJungleTrunk(World f, int x, int z) {
        assertEquals(Block.WOOD, f.block(x, 64, z).getId());
        assertEquals(BlockSapling.JUNGLE, f.block(x, 64, z).getDamage() & 3);
    }

    private static Map<Cell, String> snapshot(World f) {
        Map<Cell, String> states = new HashMap<>();
        f.cells.forEach((cell, block) -> states.put(cell, block.getId() + ":" + block.getDamage()));
        return states;
    }

    private record Cell(int x, int y, int z) {}

    private static final class World {
        final Level level = mock(Level.class);
        final Map<Cell, Block> cells = new HashMap<>();
        int writes;

        World() {
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); return block(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)));
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getId());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c -> block(c.getArgument(0), c.getArgument(1), c.getArgument(2)).getDamage());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4)); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(c -> { write(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), 0); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
        }

        BlockSapling sapling() { return (BlockSapling) block(0, 64, 0); }

        Block block(int x, int y, int z) {
            Block source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == 63 ? Block.GRASS : Block.AIR) : source.clone();
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), Block.get(id, data)); }
        void write(int x, int y, int z, int id, int data) { writes++; put(x, y, z, id, data); }
    }
}
