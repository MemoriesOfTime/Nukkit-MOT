package cn.nukkit.level;

import cn.nukkit.level.format.generic.BaseFullChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** An active population owner must finish before loaded terrain is admitted. */
class PopulatedChunkAdmissionTest {
    private static Level level(BaseFullChunk loaded, boolean full, boolean pending) throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        if (full) queue.put(Level.chunkHash(99, 99), Boolean.TRUE);
        if (pending) queue.put(Level.chunkHash(3, 5), Boolean.TRUE);
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationQueueSize", 1);
        doReturn(loaded).when(level).getChunkIfLoaded(3, 5);
        doReturn(loaded).when(level).getChunk(3, 5, true);
        return level;
    }

    private static void field(Level level, String name, Object value) throws Exception {
        var field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static BaseFullChunk populated() {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isPopulated()).thenReturn(true);
        return chunk;
    }

    @Test
    void populatedChunkBypassesFullQueueWithoutLoading() throws Exception {
        Level level = level(populated(), true, false);
        assertTrue(level.populateChunk(3, 5, false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void activePopulationEntryStillBlocksAfterPopulatedFlag() throws Exception {
        Level level = level(populated(), false, true);
        assertFalse(level.populateChunk(3, 5, false));
        assertFalse(level.populateChunk(3, 5, true));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void unpopulatedChunkStillWaitsAtFullQueue() throws Exception {
        Level level = level(mock(BaseFullChunk.class), true, false);
        assertFalse(level.populateChunk(3, 5, false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void unloadedChunkStillWaitsWithoutColdReadAtFullQueue() throws Exception {
        Level level = level(null, true, false);
        assertFalse(level.populateChunk(3, 5, false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void ordinaryPopulatedChunkKeepsReadyResult() throws Exception {
        Level level = level(populated(), false, false);
        assertTrue(level.populateChunk(3, 5, false));
    }
}
