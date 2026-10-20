package cn.nukkit.level;

import cn.nukkit.level.format.generic.LightingFixture;
import cn.nukkit.utils.Hash;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockLightPrimitiveTraversalTest {
    /** Fail if a primitive set is accidentally called through java.util.Set again. */
    static final class PrimitiveOnlySet extends LongOpenHashSet {
        @Override public boolean contains(Object value) { throw new AssertionError("boxed contains"); }
        @Override public boolean add(Long value) { throw new AssertionError("boxed add"); }
    }

    private static Method helper(String name) {
        for (Method method : Level.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) { method.setAccessible(true); return method; }
        }
        throw new AssertionError(name);
    }

    @Test void spreadPreservesLightAndFifoWithoutBoxingOrDuplicateNodes() throws Exception {
        Level level = mock(Level.class);
        LightingFixture chunk = new LightingFixture();
        chunk.setGenerated(true); chunk.setPopulated(true); chunk.setLightPopulated(true);
        when(level.isYInRange(64)).thenReturn(true);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        PrimitiveOnlySet visited = new PrimitiveOnlySet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        byte[] expected = chunk.emitted.clone();
        expected[LightingFixture.index(1, 64, 2)] = 12;
        expected[LightingFixture.index(2, 64, 2)] = 11;
        Method spread = helper("computeSpreadBlockLight");
        spread.invoke(level, 1, 64, 2, 12, queue, visited, List.of(), chunk);
        spread.invoke(level, 2, 64, 2, 11, queue, visited, List.of(), chunk);
        spread.invoke(level, 1, 64, 2, 12, queue, visited, List.of(), chunk);
        assertArrayEquals(expected, chunk.emitted);
        assertEquals(Hash.hashBlock(1, 64, 2), queue.dequeueLong());
        assertEquals(Hash.hashBlock(2, 64, 2), queue.dequeueLong());
        assertTrue(queue.isEmpty());
    }

    @Test void removalPreservesLevelsSpreadAndFifoWithoutBoxing() throws Exception {
        Level level = mock(Level.class);
        LightingFixture chunk = new LightingFixture();
        chunk.setGenerated(true); chunk.setPopulated(true); chunk.setLightPopulated(true);
        when(level.isYInRange(64)).thenReturn(true);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
        chunk.setBlockLight(1, 64, 2, 9);
        chunk.setBlockLight(2, 64, 2, 12);
        byte[] expected = chunk.emitted.clone();
        expected[LightingFixture.index(1, 64, 2)] = 0;
        LongArrayFIFOQueue removal = new LongArrayFIFOQueue(), spread = new LongArrayFIFOQueue();
        IntArrayFIFOQueue levels = new IntArrayFIFOQueue();
        PrimitiveOnlySet removed = new PrimitiveOnlySet(), visited = new PrimitiveOnlySet();
        Method remove = helper("computeRemoveBlockLight");
        remove.invoke(level, 1, 64, 2, 12, removal, levels, spread, removed, visited, List.of(), 0L);
        remove.invoke(level, 2, 64, 2, 12, removal, levels, spread, removed, visited, List.of(), 0L);
        remove.invoke(level, 2, 64, 2, 12, removal, levels, spread, removed, visited, List.of(), 0L);
        assertArrayEquals(expected, chunk.emitted);
        assertEquals(Hash.hashBlock(1, 64, 2), removal.dequeueLong());
        assertEquals(9, levels.dequeueInt());
        assertEquals(Hash.hashBlock(2, 64, 2), spread.dequeueLong());
        assertTrue(removal.isEmpty()); assertTrue(levels.isEmpty()); assertTrue(spread.isEmpty());
    }
}
