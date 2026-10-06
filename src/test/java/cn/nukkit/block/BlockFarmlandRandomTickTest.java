package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockFarmlandRandomTickTest {
    @BeforeAll
    static void init() {
        Block.init();
    }

    @Test
    void missingDiagonalDoesNotLoadAChunkOrDryTheSoil() {
        Fixture f = new Fixture(4);
        f.chunks.remove(Level.chunkHash(-1, -1));

        assertEquals(0, f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM));
        assertEquals(4, f.farmland.getDamage());
        verify(f.level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
        verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
    }

    @Test
    void waterInLoadedChunkStillHydratesWhenAnotherChunkIsMissing() {
        Fixture f = new Fixture(4);
        f.chunks.remove(Level.chunkHash(-1, -1));
        f.put(1, 64, 0, Block.WATER);

        assertEquals(Level.BLOCK_UPDATE_RANDOM, f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM));
        assertEquals(7, f.farmland.getDamage());
        verify(f.level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void waterBelowStillHydratesWhenAnotherChunkIsMissing() {
        Fixture f = new Fixture(4);
        f.chunks.remove(Level.chunkHash(-1, -1));
        f.put(0, 63, 0, Block.WATER);

        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(7, f.farmland.getDamage());
    }

    @Test
    void loadedDryAreaStillLosesOneMoistureLevel() {
        Fixture f = new Fixture(4);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(3, f.farmland.getDamage());
    }

    @Test
    void zeroMoistureStillTurnsToDirtOnlyWithTheWholeAreaLoaded() {
        Fixture f = new Fixture(0);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(Block.DIRT, f.written.getId());
    }

    @Test
    void rainStillHydratesWithoutInspectingSurroundingChunks() {
        Fixture f = new Fixture(4);
        when(f.level.isRaining()).thenReturn(true);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(7, f.farmland.getDamage());
        verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
    }

    @Test
    void cropsStillSkipFarmlandHydrationEntirely() {
        Fixture f = new Fixture(4);
        f.put(0, 65, 0, Block.WHEAT_BLOCK);
        assertEquals(0, f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM));
        assertEquals(4, f.farmland.getDamage());
        verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
    }

    @Test
    void waterInTheFirstCellStopsTheWholeScanAndSkipsTheLowerBlock() {
        Fixture f = new Fixture(4);
        f.put(-4, 64, -4, Block.WATER);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(7, f.farmland.getDamage());
        verify(f.level, times(1)).getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt());
        assertEquals(0, f.lowerBlockReads);
    }

    @Test
    void waterInTheLastCellStillHydratesAfterTheCompleteScan() {
        Fixture f = new Fixture(4);
        f.put(4, 65, 4, Block.WATER);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(7, f.farmland.getDamage());
        verify(f.level, times(161)).getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt());
        assertEquals(0, f.lowerBlockReads);
    }

    @Test
    void noWaterStillScansEveryPositionAndChecksTheLowerBlock() {
        Fixture f = new Fixture(4);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        verify(f.level, times(161)).getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt());
        assertEquals(1, f.lowerBlockReads);
        assertEquals(3, f.farmland.getDamage());
    }

    @Test
    void rainDoesNotReadTheLowerBlock() {
        Fixture f = new Fixture(4);
        when(f.level.isRaining()).thenReturn(true);
        f.farmland.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(7, f.farmland.getDamage());
        assertEquals(0, f.lowerBlockReads);
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final BlockFarmland farmland;
        final Map<Long, BaseFullChunk> chunks = new HashMap<>();
        final Map<String, Integer> blocks = new HashMap<>();
        Block written;
        int lowerBlockReads;

        Fixture(int moisture) {
            farmland = new BlockFarmland(moisture);
            farmland.x = 0;
            farmland.y = 64;
            farmland.z = 0;
            farmland.level = level;
            for (int cx = -1; cx <= 0; cx++) {
                for (int cz = -1; cz <= 0; cz++) {
                    BaseFullChunk chunk = mock(BaseFullChunk.class);
                    chunks.put(Level.chunkHash(cx, cz), chunk);
                }
            }
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(invocation ->
                    chunks.get(Level.chunkHash(invocation.getArgument(0), invocation.getArgument(1))));
            when(level.getBlockIdAt(any(FullChunk.class), anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    id(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
            // The old scalar API is a loading read. Model that side effect explicitly.
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int x = invocation.getArgument(0);
                int y = invocation.getArgument(1);
                int z = invocation.getArgument(2);
                level.getChunk(x >> 4, z >> 4, true);
                return id(x, y, z);
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                    block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            when(level.getBlock(any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                return block(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
            });
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean())).thenAnswer(invocation -> {
                written = invocation.getArgument(1);
                return true;
            });
        }

        int id(int x, int y, int z) {
            return blocks.getOrDefault(x + ":" + y + ":" + z, Block.AIR);
        }

        Block block(int x, int y, int z) {
            if (x == 0 && y == 63 && z == 0) {
                lowerBlockReads++;
            }
            return Block.get(id(x, y, z));
        }

        void put(int x, int y, int z, int id) {
            blocks.put(x + ":" + y + ":" + z, id);
        }
    }
}
