package cn.nukkit.level;

import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LevelFullLightHeightTest {
    @Test
    void readsValidNegativeWorldYWithoutWrapping() {
        Level level = levelWithHeight(-64, 319);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        doReturn(chunk).when(level).getChunk(0, 0, false);
        when(chunk.getBlockSkyLight(1, -1, 2)).thenReturn(9);
        when(chunk.getBlockLight(1, -1, 2)).thenReturn(3);

        assertEquals(9, level.getFullLight(new Vector3(1, -1, 2)));

        verify(chunk).getBlockSkyLight(1, -1, 2);
        verify(chunk).getBlockLight(1, -1, 2);
    }

    @Test
    void readsValidYAboveLegacyHeightWithoutWrapping() {
        Level level = levelWithHeight(-64, 319);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        doReturn(chunk).when(level).getChunk(0, 0, false);
        when(chunk.getBlockSkyLight(1, 256, 2)).thenReturn(0);
        when(chunk.getBlockLight(1, 256, 2)).thenReturn(12);

        assertEquals(12, level.getFullLight(new Vector3(1, 256, 2)));

        verify(chunk).getBlockSkyLight(1, 256, 2);
        verify(chunk).getBlockLight(1, 256, 2);
    }

    @Test
    void returnsDarkOutsideWorldHeightWithoutReadingChunk() {
        Level level = levelWithHeight(-64, 319);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        doReturn(chunk).when(level).getChunk(0, 0, false);

        assertEquals(0, level.getFullLight(new Vector3(1, 320, 2)));

        verifyNoInteractions(chunk);
        verify(level, org.mockito.Mockito.never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    private static Level levelWithHeight(int minY, int maxY) {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        doReturn(minY).when(level).getMinBlockY();
        doReturn(maxY).when(level).getMaxBlockY();
        return level;
    }
}
