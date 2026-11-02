package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual random-tick/generator path; only tick selection and world storage are controlled. */
@Timeout(20)
class AcaciaRandomGrowthConservationTest {
    private static final int AGED_ACACIA = BlockSapling.ACACIA | BlockSapling.AGED_BIT;

    @BeforeAll
    static void initialize() { Block.init(); }

    @ParameterizedTest
    @ValueSource(strings = {"obstruction", "soil", "ceiling"})
    void rejectedNaturalGrowthPreservesSourceAndEntireWorld(String reason) {
        World f = new World(reason.equals("ceiling") ? 253 : 64);
        f.put(0, f.sourceY, 0, Block.SAPLING, AGED_ACACIA);
        if (reason.equals("obstruction")) f.put(0, f.sourceY + 1, 0, Block.STONE, 0);
        if (reason.equals("soil")) f.put(0, f.sourceY - 1, 0, Block.STONE, 0);
        Map<Cell, String> before = snapshot(f);

        randomTick(f, 1);

        assertEquals(Block.SAPLING, f.block(0, f.sourceY, 0).getId());
        assertEquals(AGED_ACACIA, f.block(0, f.sourceY, 0).getDamage());
        assertEquals(before, snapshot(f), "generator refusal must not remove the sapling or change other cells");
        assertEquals(2, f.writes, "only remove and restore the source; rejected generator must not write");
        ArgumentCaptor<Vector3> positions = ArgumentCaptor.forClass(Vector3.class);
        ArgumentCaptor<Block> states = ArgumentCaptor.forClass(Block.class);
        verify(f.level, times(2)).setBlock(positions.capture(), states.capture(), eq(true), eq(false));
        assertEquals(Block.AIR, states.getAllValues().get(0).getId());
        assertSame(positions.getAllValues().get(0), positions.getAllValues().get(1));
        assertSame(positions.getAllValues().get(0), states.getAllValues().get(1), "restore the original sapling instance");
    }

    @Test
    void blockedSaplingCanGrowOnALaterTickAfterObstacleIsRemoved() {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, AGED_ACACIA);
        f.put(0, 65, 0, Block.STONE, 0);
        randomTick(f, 1);
        assertEquals(Block.SAPLING, f.block(0, 64, 0).getId());
        f.put(0, 65, 0, Block.AIR, 0);
        randomTick(f, 1);
        assertAcaciaTrunk(f);
    }

    @Test
    void successfulNaturalGrowthKeepsItsAcaciaTrunk() {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, AGED_ACACIA);
        randomTick(f, 1);
        assertAcaciaTrunk(f);
    }

    @Test
    void youngAcaciaOnlyAgesOnItsFirstSelectedTick() {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, BlockSapling.ACACIA);
        randomTick(f, 1);
        assertEquals(Block.SAPLING, f.block(0, 64, 0).getId());
        assertEquals(AGED_ACACIA, f.block(0, 64, 0).getDamage());
        assertEquals(1, f.writes);
    }

    @Test
    void unselectedTickDoesNotTouchTheWorld() {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, AGED_ACACIA);
        Map<Cell, String> before = snapshot(f);
        randomTick(f, 2);
        assertEquals(before, snapshot(f));
        assertEquals(0, f.writes);
    }

    @Test
    void otherSpeciesKeepTheirExistingNaturalGrowthPath() {
        World f = new World();
        f.put(0, 64, 0, Block.SAPLING, BlockSapling.OAK | BlockSapling.AGED_BIT);
        randomTick(f, 1);
        assertEquals(Block.WOOD, f.block(0, 64, 0).getId());
        assertEquals(BlockSapling.OAK, f.block(0, 64, 0).getDamage() & 3);
    }

    private static void randomTick(World f, int selectedRoll) {
        try (MockedStatic<Utils> random = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            random.when(() -> Utils.rand(1, 7)).thenReturn(selectedRoll);
            f.sapling().onUpdate(Level.BLOCK_UPDATE_RANDOM);
        }
    }

    private static void assertAcaciaTrunk(World f) {
        assertEquals(Block.WOOD2, f.block(0, 64, 0).getId());
        assertEquals(BlockSapling.ACACIA - 4, f.block(0, 64, 0).getDamage() & 3);
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
        final int sourceY;
        int writes;

        World() { this(64); }

        World(int sourceY) {
            this.sourceY = sourceY;
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
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(c -> {
                Vector3 pos = c.getArgument(0); Block block = c.getArgument(1);
                write(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), block.getId(), block.getDamage()); return true;
            });
        }

        BlockSapling sapling() { return (BlockSapling) block(0, sourceY, 0); }

        Block block(int x, int y, int z) {
            Block source = cells.get(new Cell(x, y, z));
            Block block = source == null ? Block.get(y == sourceY - 1 ? Block.GRASS : Block.AIR) : source.clone();
            block.setLevel(level); block.setComponents(x, y, z); return block;
        }

        void put(int x, int y, int z, int id, int data) { cells.put(new Cell(x, y, z), Block.get(id, data)); }
        void write(int x, int y, int z, int id, int data) { writes++; put(x, y, z, id, data); }
    }
}
