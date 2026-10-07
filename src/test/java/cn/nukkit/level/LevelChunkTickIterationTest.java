package cn.nukkit.level;

import it.unimi.dsi.fastutil.longs.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LevelChunkTickIterationTest {
    record Selection(long hash, int loaders) {}

    @Test void reusableEntryPreservesOrderAndRemovalsAcrossWraparoundClusters() {
        Random random = new Random(140_926L);
        for (int round = 0; round < 250; round++) {
            Long2IntMap original = new Long2IntOpenHashMap();
            original.put(0, -1);
            for (int i = 0; i < 300; i++) {
                original.put(Level.chunkHash(random.nextInt(128) - 64, random.nextInt(128) - 64),
                        random.nextInt(4) - 1);
            }
            Long2IntMap old = new Long2IntOpenHashMap(original);
            Long2IntMap fast = new Long2IntOpenHashMap(original);
            assertEquals(visit(old, false), visit(fast, true));
            assertEquals(old, fast);
        }
    }

    @Test void removingEveryEntryStillVisitsEachChunkOnce() {
        Long2IntMap map = new Long2IntOpenHashMap();
        for (int i = -100; i < 100; i++) map.put(Level.chunkHash(i * 8, -i * 8), -1);
        List<Selection> selected = visit(map, true);
        assertEquals(200, selected.size());
        assertEquals(200, new HashSet<>(selected).size());
        assertTrue(map.isEmpty());
    }

    private static List<Selection> visit(Long2IntMap map, boolean fast) {
        var iterator = fast ? Long2IntMaps.fastIterator(map) : map.long2IntEntrySet().iterator();
        List<Selection> selected = new ArrayList<>();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long hash = entry.getLongKey();
            // Mimic the unloaded-neighbor branch, which removes before reading the loader count.
            if ((hash & 7) == 2) { iterator.remove(); continue; }
            int loaders = entry.getIntValue();
            if (loaders <= 0) iterator.remove();
            selected.add(new Selection(hash, loaders));
        }
        return selected;
    }
}
