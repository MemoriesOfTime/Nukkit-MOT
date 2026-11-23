package cn.nukkit.level;

import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LevelThunderSelectionTest {
    @Test void losingRollDoesNotInspectChunkOrNeighbors() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        doReturn(provider).when(level).requireProvider();
        level.performThunder(42, chunk, 1);
        level.performThunder(42, chunk, 99999);
        verifyNoInteractions(provider);
        verifyNoInteractions(chunk);
    }

    @Test void winningRollRetainsTheExistingInteriorChunkGuard() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        doReturn(provider).when(level).requireProvider();
        when(provider.isChunkLoaded(anyLong())).thenReturn(true);
        level.performThunder(42, chunk, 0);
        verify(provider, atLeastOnce()).isChunkLoaded(anyLong());
        verifyNoInteractions(chunk);
    }

    @Test void winningBoundaryRollEntersTheUnchangedLightningPath() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        FullChunk chunk = mock(FullChunk.class);
        LevelProvider provider = mock(LevelProvider.class);
        doReturn(provider).when(level).requireProvider();
        when(provider.isChunkLoaded(anyLong())).thenReturn(false);
        RuntimeException reachedLightningBody = new RuntimeException("reached chunk coordinates");
        doThrow(reachedLightningBody).when(chunk).getX();
        assertSame(reachedLightningBody,
                assertThrows(RuntimeException.class, () -> level.performThunder(42, chunk, 0)));
        verify(provider, atLeastOnce()).isChunkLoaded(anyLong());
    }

    @Test void geometricThunderSelectionChoosesTheExpectedGapAndStopsAtEnd() {
        assertEquals(7, Level.nextThunderChunkIndex(5, 10, 0.2, 0.4));
        assertEquals(10, Level.nextThunderChunkIndex(5, 10, 0.2, 0.96));
    }

    @Test void geometricSkipsMatchIndependentPerChunkThunderProbability() {
        int chunks = 8;
        int trials = 100_000;
        double probability = 0.2;
        int[] observed = new int[chunks];
        Random random = new Random(0x4b4dL);

        for (int trial = 0; trial < trials; trial++) {
            int next = 0;
            while (next < chunks) {
                int selected = Level.nextThunderChunkIndex(next, chunks, probability, random.nextDouble());
                if (selected == chunks) break;
                observed[selected]++;
                next = selected + 1;
            }
        }

        double expected = trials * probability;
        double chiSquared = 0;
        for (int count : observed) {
            double delta = count - expected;
            chiSquared += delta * delta / expected;
        }
        assertTrue(chiSquared < 30.0,
                "per-chunk hit counts differ from Bernoulli(p): chi-square=" + chiSquared);
    }

    @Test void loadedThunderSelectionUsesHashSnapshotInsteadOfChunkMapCopy() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        LevelProvider provider = mock(LevelProvider.class);
        doReturn(provider).when(level).requireProvider();
        when(provider.getLoadedChunkHashes()).thenReturn(new long[0]);

        level.performThunderForLoadedChunks();

        verify(provider).getLoadedChunkHashes();
        verify(provider, never()).getLoadedChunks();
        verify(level, never()).getChunks();
    }
}
