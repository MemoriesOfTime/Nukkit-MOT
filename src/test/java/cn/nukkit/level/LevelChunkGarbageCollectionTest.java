package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Chunk garbage collection needs only the coordinates of the loaded chunks, and takes them from the
 * primitive hashes instead of a boxed copy of the whole chunk map.
 */
class LevelChunkGarbageCollectionTest {

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
        setField(level, "blockEntities", new Long2ObjectNonBlockingMap<>());
        setField(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        setField(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        setField(level, "unloadQueue", new Long2ObjectNonBlockingMap<>());
        setField(level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
        setField(level, "playerLoaders", new Long2ObjectNonBlockingMap<>());
        setField(level, "loaders", new Int2ObjectOpenHashMap<>());
        setField(level, "loaderCounter", new Int2IntOpenHashMap());
        doReturn(false).when(level).isSpawnChunk(anyInt(), anyInt());
        return level;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean isQueued(Level level, long hash) throws Exception {
        return unloadQueue(level).containsKey(hash);
    }

    @SuppressWarnings("unchecked")
    private static Long2ObjectNonBlockingMap<Long> unloadQueue(Level level) throws Exception {
        Field field = Level.class.getDeclaredField("unloadQueue");
        field.setAccessible(true);
        return (Long2ObjectNonBlockingMap<Long>) field.get(level);
    }

    @Test
    void unloadIsRequestedFromChunkHashesWithoutCopyingTheChunkMap() throws Exception {
        Level level = this.newLevel();
        doReturn(true).when(level).isSpawnChunk(0, 0);
        Mockito.when(this.provider.getLoadedChunkHashes()).thenReturn(new long[]{
                Level.chunkHash(-3, 7), Level.chunkHash(12, -40), Level.chunkHash(0, 0)});

        level.doChunkGarbageCollection();

        assertTrue(isQueued(level, Level.chunkHash(-3, 7)));
        assertTrue(isQueued(level, Level.chunkHash(12, -40)));
        assertFalse(isQueued(level, Level.chunkHash(0, 0)), "spawn chunks stay loaded");
        verify(this.provider, never()).getLoadedChunks();
        verify(this.provider).doGarbageCollection();
    }

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

    /**
     * 区块写积压背压测试:积压时本 tick 暂停卸载并保留队列,force 与非积压路径不受影响
     * <p>
     * Chunk-write backpressure tests: unloading pauses this tick while backlogged with the queue kept;
     * force and non-backlogged paths are unaffected
     *
     * @author LT_Name
     */
    @Test
    void isChunkSaveBackloggedDelegatesToProvider() throws Exception {
        Level level = newLevel();

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(true);
        Assertions.assertTrue(level.isChunkSaveBacklogged());

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(false);
        Assertions.assertFalse(level.isChunkSaveBacklogged());

        setField(level, "provider", null);
        Assertions.assertFalse(level.isChunkSaveBacklogged());
    }

    @Test
    void unloadChunksPausesAndKeepsQueueWhileBacklogged() throws Exception {
        Level level = newLevel();
        long hash = Level.chunkHash(3, 5);
        unloadQueue(level).put(hash, (Long) (System.currentTimeMillis() - 30000));

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(true);
        level.unloadChunks(50, false);

        Assertions.assertTrue(unloadQueue(level).containsKey(hash), "queue entry must be kept for the next tick");
        Mockito.verify(level, Mockito.never()).unloadChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    }

    @Test
    void unloadChunksProceedsWhenNotBacklogged() throws Exception {
        Level level = newLevel();
        long hash = Level.chunkHash(3, 5);
        unloadQueue(level).put(hash, (Long) (System.currentTimeMillis() - 30000));

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(false);
        Mockito.when(this.provider.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        level.unloadChunks(50, false);

        Assertions.assertFalse(unloadQueue(level).containsKey(hash));
    }

    @Test
    void forceUnloadBypassesBackpressure() throws Exception {
        Level level = newLevel();
        long hash = Level.chunkHash(3, 5);
        unloadQueue(level).put(hash, (Long) (System.currentTimeMillis() - 30000));

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(true);
        Mockito.when(this.provider.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        level.unloadChunks(50, true);

        Assertions.assertFalse(unloadQueue(level).containsKey(hash), "force must bypass backpressure");
    }
}
