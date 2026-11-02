package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real generators; setBlock storage also models Level.setBlock's block mutation. */
@Timeout(20)
class SaplingSquareRollbackTest {
    @BeforeAll
    static void initialize() { Block.init(); }

    private enum Refusal { OBSTRUCTION, SOIL, HEIGHT }

    static Stream<Arguments> refusedSquares() {
        return IntStream.of(BlockSapling.JUNGLE, BlockSapling.DARK_OAK).boxed()
                .flatMap(species -> IntStream.range(0, 4).boxed()
                        .flatMap(corner -> Stream.of(Refusal.values())
                                .map(reason -> Arguments.of(species, corner / 2, corner % 2, reason))));
    }

    @ParameterizedTest
    @MethodSource("refusedSquares")
    void refusedSquareRestoresEveryOriginalStateAndPosition(int species, int sourceX, int sourceZ, Refusal reason) {
        World f = new World(reason == Refusal.HEIGHT ? 253 : 64);
        f.square(species);
        if (reason == Refusal.OBSTRUCTION) f.put(0, f.y + 1, 0, Block.STONE, 0);
        if (reason == Refusal.SOIL) f.put(0, f.y - 1, 0, Block.STONE, 0);
        Map<Cell, State> before = Map.copyOf(f.cells);
        BlockSapling source = (BlockSapling) f.block(sourceX, f.y, sourceZ);
        Cell sourcePosition = cell(source);

        assertTrue(source.grow(), "retain the existing return value on refused generation");

        assertAll(
                () -> assertEquals(before, f.cells, "no lost, copied, displaced or re-aged saplings"),
                () -> assertEquals(sourcePosition, cell(source), "restoration must not move the triggering block object"),
                () -> assertEquals(8, f.writes.size(), "four clears followed by four restores"));
        for (int i = 0; i < 4; i++) {
            Write cleared = f.writes.get(i);
            Write restored = f.writes.get(i + 4);
            assertEquals(Block.AIR, cleared.state.id);
            assertTrue(cleared.direct);
            assertTrue(cleared.update);
            assertEquals(cleared.cell, restored.cell);
            assertEquals(before.get(restored.cell), restored.state);
            assertTrue(restored.direct);
            assertFalse(restored.update);
        }
    }

    static Stream<Arguments> successfulSquares() {
        return IntStream.of(BlockSapling.JUNGLE, BlockSapling.DARK_OAK).boxed()
                .flatMap(species -> IntStream.range(0, 4)
                        .mapToObj(corner -> Arguments.of(species, corner / 2, corner % 2)));
    }

    @ParameterizedTest
    @MethodSource("successfulSquares")
    void successfulSquareStillGrowsFromEveryTrigger(int species, int sourceX, int sourceZ) {
        World f = new World(64);
        f.square(species);
        BlockSapling source = (BlockSapling) f.block(sourceX, f.y, sourceZ);
        Cell sourcePosition = cell(source);

        assertTrue(source.grow());

        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            Block trunk = f.block(x, f.y, z);
            assertEquals(species == BlockSapling.JUNGLE ? Block.WOOD : Block.WOOD2, trunk.getId());
            assertEquals(species == BlockSapling.JUNGLE ? BlockWood.JUNGLE : BlockWood2.DARK_OAK,
                    trunk.getDamage() & 3);
        }
        assertEquals(sourcePosition, cell(source));
        assertEquals(4, f.writes.size(), "successful generators do not enter sapling restoration");
        assertTrue(f.cells.values().stream().noneMatch(state -> state.id == Block.SAPLING));
    }

    private record Cell(int x, int y, int z) {}
    private record State(int id, int data) {}
    private record Write(Cell cell, State state, boolean direct, boolean update) {}

    private static Cell cell(Vector3 pos) { return new Cell(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()); }

    private static final class World {
        final Level level = mock(Level.class);
        final Map<Cell, State> cells = new HashMap<>();
        final List<Write> writes = new ArrayList<>();
        final int y;

        World(int y) {
            this.y = y;
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
            doAnswer(c -> { put(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4)); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(c -> { put(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3), 0); return null; })
                    .when(level).setBlockAt(anyInt(), anyInt(), anyInt(), anyInt());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block value = c.getArgument(1);
                Cell target = cell(pos); State state = new State(value.getId(), value.getDamage());
                Block previous = block(target.x, target.y, target.z);
                if (previous.getId() == state.id && previous.getDamage() == state.data) return false;
                cells.put(target, state);
                writes.add(new Write(target, state, c.getArgument(2), c.getArgument(3)));
                // Level.setBlock assigns the supplied Block to its destination before notifying.
                value.setComponents(target.x, target.y, target.z);
                value.setLevel(level);
                value.layer = 0;
                return true;
            });
        }

        void square(int species) {
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                put(x, y, z, Block.SAPLING, species);
            }
        }

        Block block(int x, int y, int z) {
            State source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == this.y - 1 ? Block.GRASS : Block.AIR)
                    : Block.get(source.id, source.data);
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), new State(id, data)); }
    }
}
