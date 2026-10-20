package cn.nukkit.level;

import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import org.junit.jupiter.api.Test;
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
}
