package cn.nukkit.level;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelChunkGarbageCollectionPhaseTest {

    @Test
    void everyLevelIdGetsAPhaseBetweenOneAndNinetyNine() {
        for (int id = 0; id < 10_000; id++) {
            int phase = Level.chunkGarbageCollectionPhase(id);
            assertTrue(phase >= 1 && phase < Level.CHUNK_GC_PERIOD, "phase " + phase + " for level " + id);
            assertNotEquals(0, phase);
        }
    }

    @Test
    void levelsLoadedTogetherGetDistinctPhases() {
        Set<Integer> phases = new HashSet<>();
        for (int id = 1; id <= 24; id++) {
            phases.add(Level.chunkGarbageCollectionPhase(id));
        }
        assertEquals(24, phases.size());
    }
}
