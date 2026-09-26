package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The unload queue is time ordered: a pass takes the due chunks from its head and stops at the first
 * one that is not due. It used to walk every queued chunk on every pass - in the idle part of every
 * tick, for every level - through a snapshot iterator of a map that never shrinks.
 */
class LevelUnloadQueueTest {

    private Server server;
    private LevelProvider provider;

    @BeforeEach
    void setUp() {
        MockServer.init();
        this.server = Mockito.mock(Server.class);
        Mockito.lenient().when(this.server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        Mockito.lenient().when(this.server.getLogger()).thenReturn(Mockito.mock(MainLogger.class));
        this.provider = Mockito.mock(LevelProvider.class);
    }

    private Level newLevel() throws Exception {
        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        setField(level, "server", this.server);
        setField(level, "provider", this.provider);
        setField(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        setField(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        setField(level, "unloadQueue", new Long2LongLinkedOpenHashMap());
        setField(level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
        setField(level, "playerLoaders", new ConcurrentHashMap<>());
        setField(level, "loaders", new Int2ObjectOpenHashMap<>());
        setField(level, "loaderCounter", new Int2IntOpenHashMap());
        doReturn(false).when(level).isSpawnChunk(anyInt(), anyInt());
        doReturn(true).when(level).unloadChunk(anyInt(), anyInt(), anyBoolean());
        return level;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Long2LongLinkedOpenHashMap queue(Level level) throws Exception {
        Field field = Level.class.getDeclaredField("unloadQueue");
        field.setAccessible(true);
        return (Long2LongLinkedOpenHashMap) field.get(level);
    }

    private static long due(long secondsAgo) {
        return Level.unloadClock() - Level.CHUNK_UNLOAD_DELAY_MILLIS - secondsAgo * 1000;
    }

    @Test
    void aPassStopsAtTheFirstChunkThatIsNotDue() throws Exception {
        Level level = newLevel();
        for (int x = 0; x < 1000; x++) {
            level.unloadChunkRequest(x, 7);
        }
        Mockito.clearInvocations(level);

        level.doGarbageCollection(40);
        level.unloadChunks(50, false);

        verify(level, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
        // Only the head is looked at, never the thousand chunks behind it.
        verify(level, never()).isChunkInUse(anyLong());
        assertEquals(1000, queue(level).size());
    }

    @Test
    void dueChunksLeaveOldestFirstAndTheRestWaits() throws Exception {
        Level level = newLevel();
        queue(level).put(Level.chunkHash(1, 0), due(9));
        queue(level).put(Level.chunkHash(2, 0), due(3));
        level.unloadChunkRequest(3, 0);

        level.unloadChunks(50, false);

        InOrder order = Mockito.inOrder(level);
        order.verify(level).unloadChunk(1, 0, true);
        order.verify(level).unloadChunk(2, 0, true);
        verify(level, never()).unloadChunk(3, 0, true);
        assertTrue(level.isUnloadChunkRequested(Level.chunkHash(3, 0)));
        assertEquals(1, queue(level).size());
    }

    @Test
    void queueingAChunkAgainRestartsItsGracePeriod() throws Exception {
        Level level = newLevel();
        queue(level).put(Level.chunkHash(4, 4), due(5));
        level.unloadChunkRequest(4, 4);

        level.unloadChunks(50, false);

        verify(level, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
        assertTrue(level.isUnloadChunkRequested(Level.chunkHash(4, 4)));
    }

    @Test
    void aRefusedUnloadWaitsAFullGracePeriodInsteadOfRetryingEveryPass() throws Exception {
        Level level = newLevel();
        doReturn(false).when(level).unloadChunk(5, 5, true);
        queue(level).put(Level.chunkHash(5, 5), due(1));

        level.unloadChunks(50, false);
        level.doGarbageCollection(40);
        level.unloadChunks(50, false);

        verify(level, times(1)).unloadChunk(5, 5, true);
        assertTrue(level.isUnloadChunkRequested(Level.chunkHash(5, 5)), "the refused chunk stays queued");
    }

    @Test
    void aChunkTakenBackByALoaderLeavesTheQueue() throws Exception {
        Level level = newLevel();
        long hash = Level.chunkHash(6, 6);
        queue(level).put(hash, due(1));
        doReturn(true).when(level).isChunkInUse(hash);

        level.unloadChunks(50, false);

        verify(level, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
        assertFalse(level.isUnloadChunkRequested(hash));
    }

    @Test
    void aForcedPassUnloadsChunksBeforeTheirGracePeriodEnds() throws Exception {
        Level level = newLevel();
        level.unloadChunkRequest(8, 8);
        level.unloadChunkRequest(9, 9);

        level.unloadChunks(true);

        verify(level).unloadChunk(8, 8, true);
        verify(level).unloadChunk(9, 9, true);
        assertTrue(queue(level).isEmpty());
    }

    @Test
    void aCountLimitedPassStopsAtItsLimit() throws Exception {
        Level level = newLevel();
        for (int x = 0; x < 5; x++) {
            queue(level).put(Level.chunkHash(x, 1), due(1));
        }

        level.unloadChunks(3, false);

        verify(level, times(3)).unloadChunk(anyInt(), anyInt(), anyBoolean());
        assertEquals(2, queue(level).size());
    }
}
