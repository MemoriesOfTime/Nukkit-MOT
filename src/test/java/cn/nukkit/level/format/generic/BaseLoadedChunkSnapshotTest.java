package cn.nukkit.level.format.generic;

import cn.nukkit.level.Level;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseLoadedChunkSnapshotTest {
    @Test
    void baseProviderSnapshotIsDetachedAndReadOnly() throws Exception {
        BaseLevelProvider provider = mock(BaseLevelProvider.class, CALLS_REAL_METHODS);
        Map<Long, BaseFullChunk> chunks = new ConcurrentHashMap<>();
        BaseFullChunk first = mock(BaseFullChunk.class);
        chunks.put(Level.chunkHash(-2, 4), first);
        Field field = BaseLevelProvider.class.getDeclaredField("chunks");
        field.setAccessible(true);
        field.set(provider, chunks);
        Map<Long, BaseFullChunk> snapshot = provider.getLoadedChunks();
        assertSame(first, snapshot.get(Level.chunkHash(-2, 4)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        chunks.put(Level.chunkHash(7, -9), mock(BaseFullChunk.class));
        assertEquals(1, snapshot.size());
        assertEquals(2, provider.getLoadedChunkCount());
        assertEquals(new LongOpenHashSet(chunks.keySet()), new LongOpenHashSet(provider.getLoadedChunkHashes()));
    }

    @Test
    void scalarCountAndCoordinateSnapshotDoNotCopyChunkValues() throws Exception {
        BaseLevelProvider provider = mock(BaseLevelProvider.class, CALLS_REAL_METHODS);
        Map<Long, BaseFullChunk> chunks = new ConcurrentHashMap<>();
        chunks.put(Level.chunkHash(1, 2), mock(BaseFullChunk.class));
        Field field = BaseLevelProvider.class.getDeclaredField("chunks");
        field.setAccessible(true);
        field.set(provider, chunks);
        doThrow(new AssertionError("copied complete chunk map")).when(provider).getLoadedChunks();
        assertEquals(1, provider.getLoadedChunkCount());
        assertArrayEquals(new long[]{Level.chunkHash(1, 2)}, provider.getLoadedChunkHashes());
    }
}
