package cn.nukkit.level;

import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelChunkLoaderIndexTest {
    private final Level level = mock(Level.class);

    @BeforeEach
    void prepareIndex() throws Exception {
        set("chunkLoaders", new Long2ObjectNonBlockingMap<>());
        set("playerLoaders", new ConcurrentHashMap<>());
        set("loaders", new Int2ObjectOpenHashMap<>());
        set("loaderCounter", new Int2IntOpenHashMap());
        doCallRealMethod().when(level).registerChunkLoader(any(), anyInt(), anyInt(), anyBoolean());
        doCallRealMethod().when(level).unregisterChunkLoader(any(), anyInt(), anyInt());
        doCallRealMethod().when(level).getChunkLoaders(anyInt(), anyInt());
        doCallRealMethod().when(level).isChunkInUse(anyLong());
    }

    @Test
    void diagonalAndZeroChunkKeysKeepIndependentLoadersAndRemoveOnlyTheLastRegistration() {
        ChunkLoader first = loader(1);
        ChunkLoader second = loader(2);
        for (int coordinate : new int[]{-128, -1, 0, 1, 128}) {
            level.registerChunkLoader(first, coordinate, coordinate, false);
            level.registerChunkLoader(first, coordinate, coordinate, false);
            level.registerChunkLoader(second, coordinate, coordinate, false);
            assertEquals(2, level.getChunkLoaders(coordinate, coordinate).length);
            assertTrue(level.isChunkInUse(Level.chunkHash(coordinate, coordinate)));
        }

        level.unregisterChunkLoader(first, 0, 0);
        assertTrue(level.isChunkInUse(Level.chunkHash(0, 0)));
        assertArrayEquals(new ChunkLoader[]{second}, level.getChunkLoaders(0, 0));
        level.unregisterChunkLoader(second, 0, 0);
        assertFalse(level.isChunkInUse(Level.chunkHash(0, 0)));
        assertEquals(0, level.getChunkLoaders(0, 0).length);
        assertTrue(level.isChunkInUse(Level.chunkHash(-1, -1)));
        assertTrue(level.isChunkInUse(Level.chunkHash(1, 1)));
    }

    @Test
    void concurrentReadsKeepFindingStableChunkWhileOtherChunkKeysAreInsertedAndRemoved() throws Exception {
        ChunkLoader stable = loader(1);
        ChunkLoader moving = loader(2);
        long stableHash = Level.chunkHash(-1, -1);
        level.registerChunkLoader(stable, -1, -1, false);
        ExecutorService reader = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try {
            Future<?> reads = reader.submit(() -> {
                started.countDown();
                for (int i = 0; i < 4096; i++) {
                    assertTrue(level.isChunkInUse(stableHash));
                }
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 256; i++) {
                level.registerChunkLoader(moving, i, i, false);
            }
            for (int i = 0; i < 256; i++) {
                level.unregisterChunkLoader(moving, i, i);
                assertFalse(level.isChunkInUse(Level.chunkHash(i, i)));
            }
            reads.get(10, TimeUnit.SECONDS);
            assertTrue(level.isChunkInUse(stableHash));
        } finally {
            reader.shutdownNow();
        }
    }

    private ChunkLoader loader(int id) {
        ChunkLoader loader = mock(ChunkLoader.class);
        when(loader.getLoaderId()).thenReturn(id);
        return loader;
    }

    private void set(String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }
}
