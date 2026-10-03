package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.level.format.generic.BaseFullChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Quarantine must not enter population/generation queues, including through a neighbour. */
class QuarantinedChunkAdmissionTest {
    private Level level(BaseFullChunk centre) throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        server.asyncChunkLoadCompletion = true;
        field(level, "server", server);
        field(level, "chunkPopulationQueue", new Long2ObjectOpenHashMap<>());
        field(level, "chunkPopulationLock", new Long2ObjectOpenHashMap<>());
        field(level, "chunkGenerationQueue", new Long2ObjectOpenHashMap<>());
        field(level, "chunkPopulationQueueSize", 100);
        field(level, "chunkGenerationQueueSize", 100);
        doReturn(centre).when(level).getChunk(anyInt(), anyInt(), eq(true));
        return level;
    }

    private static BaseFullChunk failed() {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isReadFailurePlaceholder()).thenReturn(true);
        return chunk;
    }

    private static void field(Level level, String name, Object value) throws Exception {
        var field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    @Test
    void failedCentreCannotBePopulatedOrGeneratedEvenWhenForced() throws Exception {
        Level level = level(failed());
        assertFalse(level.populateChunk(3, 5, true));
        assertDoesNotThrow(() -> level.generateChunk(3, 5, true));
        verify(level.getServer(), never()).getScheduler();
    }

    @Test
    void failedNeighbourCannotBeGeneratedByPopulation() throws Exception {
        BaseFullChunk centre = mock(BaseFullChunk.class);
        Level level = level(centre);
        doReturn(failed()).when(level).getChunk(4, 6, true);
        assertFalse(level.populateChunk(3, 5, true));
        verify(level.getServer(), never()).getScheduler();
    }
}
