package cn.nukkit.level;

import cn.nukkit.utils.Hash;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BlockLightPendingCellsTest {
    @Test void matchesGlobalOccupancyThroughSparseDenseAndDecreasingRevisits() throws Exception {
        BlockLightPendingCells candidate = new BlockLightPendingCells();
        LongOpenHashSet baseline = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        Random random = new Random(319324);
        for (int i = 0; i < 150_000; i++) {
            int x = i % 3 == 0 ? random.nextInt(40000) - 20000 : random.nextInt(32) - 16;
            int z = i % 3 == 0 ? random.nextInt(40000) - 20000 : random.nextInt(32) - 16;
            long cell = Hash.hashBlock(x, random.nextInt(384) - 64, z);
            boolean expected = baseline.add(cell);
            assertEquals(expected, candidate.add(cell), "duplicate admission at " + i);
            if (expected) queue.enqueue(cell);
            if (i % 3 == 0 && !queue.isEmpty()) {
                long next = queue.dequeueLong();
                assertTrue(baseline.remove(next));
                candidate.remove(next);
                // A decrease can immediately visit the same coordinate again.
                assertTrue(candidate.add(next));
                assertTrue(baseline.add(next));
                queue.enqueue(next);
            }
        }
        while (!queue.isEmpty()) {
            long cell = queue.dequeueLong();
            candidate.remove(cell);
            assertTrue(baseline.remove(cell));
        }
        assertTrue(baseline.isEmpty());
        Field sections = BlockLightPendingCells.class.getDeclaredField("sections");
        sections.setAccessible(true);
        assertTrue(((Map<?, ?>) sections.get(candidate)).isEmpty(), "idle sections release their occupancy");
        Field overflow = BlockLightPendingCells.class.getDeclaredField("sparseOverflow");
        overflow.setAccessible(true);
        assertTrue(((LongOpenHashSet) overflow.get(candidate)).isEmpty());
        Field buckets = BlockLightPendingCells.class.getDeclaredField("activeBuckets");
        buckets.setAccessible(true);
        for (short count : (short[]) buckets.get(candidate)) assertEquals(0, count);
    }

    @Test void pendingCoordinatesSurviveUnloadReplacementAndPackedAliases() {
        BlockLightPendingCells cells = new BlockLightPendingCells();
        long cell = Hash.hashBlock(-17, -1, -33);
        assertTrue(cells.add(cell));
        assertFalse(cells.add(Hash.hashBlock(-17 + (1 << 26), -1, -33)));
        // Occupancy belongs to FIFO coordinates, not a retained chunk generation.
        assertFalse(cells.add(cell));
        cells.remove(cell);
        assertTrue(cells.add(cell));
        cells.remove(cell);
    }
}
