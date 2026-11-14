package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private AtomicLong unloadClock;

    @BeforeEach
    void setUp() {
        MockServer.init();
        this.server = Mockito.mock(Server.class);
        Mockito.lenient().when(this.server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        Mockito.lenient().when(this.server.getLogger()).thenReturn(Mockito.mock(MainLogger.class));
        this.provider = Mockito.mock(LevelProvider.class);
        this.unloadClock = new AtomicLong(1_000_000L);
    }

    private Level newLevel() throws Exception {
        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        setField(level, "server", this.server);
        setField(level, "provider", this.provider);
        setField(level, "blockEntities", new Long2ObjectNonBlockingMap<>());
        setField(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        setField(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        setField(level, "unloadQueue", new ChunkUnloadQueue(this.unloadClock::get));
        setField(level, "unloadTarget", new ChunkUnloadQueue.Target() {
            @Override
            public boolean isChunkInUse(long hash) {
                return level.isChunkInUse(hash);
            }

            @Override
            public boolean isChunkSaveBacklogged() {
                return level.isChunkSaveBacklogged();
            }

            @Override
            public boolean unloadChunk(long hash) {
                return level.unloadChunk(Level.getHashX(hash), Level.getHashZ(hash), true);
            }
        });
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

    private static boolean isQueued(Level level, long hash) {
        return level.isUnloadChunkRequested(hash);
    }

    private static ChunkUnloadQueue unloadQueue(Level level) throws Exception {
        Field field = Level.class.getDeclaredField("unloadQueue");
        field.setAccessible(true);
        return (ChunkUnloadQueue) field.get(level);
    }

    /** 入队并把时钟推过宽限期，避免真实等待 / queues a chunk and moves the clock past its grace period. */
    private void queueDue(Level level, long hash) throws Exception {
        unloadQueue(level).request(hash);
        this.unloadClock.addAndGet(ChunkUnloadQueue.GRACE_MILLIS + 10_000L);
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
        this.queueDue(level, hash);

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(true);
        level.unloadChunks(50, false);

        Assertions.assertTrue(isQueued(level, hash), "queue entry must be kept for the next tick");
        Mockito.verify(level, Mockito.never()).unloadChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    }

    @Test
    void unloadChunksProceedsWhenNotBacklogged() throws Exception {
        Level level = newLevel();
        long hash = Level.chunkHash(3, 5);
        this.queueDue(level, hash);

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(false);
        Mockito.when(this.provider.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        level.unloadChunks(50, false);

        Mockito.verify(level).unloadChunk(3, 5, true);
        Assertions.assertFalse(isQueued(level, hash));
    }

    @Test
    void forceUnloadBypassesBackpressure() throws Exception {
        Level level = newLevel();
        long hash = Level.chunkHash(3, 5);
        this.queueDue(level, hash);

        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(true);
        Mockito.when(this.provider.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        level.unloadChunks(50, true);

        Assertions.assertFalse(isQueued(level, hash), "force must bypass backpressure");
    }

    private AtomicInteger queueUntilBacklogged(Level level, int pending) throws Exception {
        for (int x = 0; x < 3; x++) {
            this.queueDue(level, Level.chunkHash(x * 5, 10));
        }
        AtomicInteger writes = new AtomicInteger(pending);
        Mockito.when(this.provider.isChunkSaveBacklogged()).thenAnswer(ignored -> writes.get() >= 128);
        Mockito.doAnswer(ignored -> {
            writes.incrementAndGet();
            return true;
        }).when(level).unloadChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
        return writes;
    }

    @Test
    void countLimitedUnloadStopsAsSoonAsWriterReachesCapacityAndResumes() throws Exception {
        Level level = newLevel();
        AtomicInteger writes = queueUntilBacklogged(level, 127);
        level.unloadChunks(50, false);
        Assertions.assertEquals(128, writes.get(), "only one more unload fits in the pending-write window");
        Assertions.assertEquals(2, unloadQueue(level).size(), "deferred chunks remain queued");

        writes.set(0);
        level.unloadChunks(50, false);
        Assertions.assertEquals(2, writes.get());
        Assertions.assertTrue(unloadQueue(level).isEmpty(), "deferred unloads resume after the writer drains");
    }

    @Test
    void timeLimitedUnloadStopsAsSoonAsWriterReachesCapacity() throws Exception {
        Level level = newLevel();
        AtomicInteger writes = queueUntilBacklogged(level, 127);
        level.doGarbageCollection(10000);
        Assertions.assertEquals(128, writes.get());
        Assertions.assertEquals(2, unloadQueue(level).size());
        Mockito.verify(this.provider).doGarbageCollection(Mockito.anyLong());
    }

    @Test
    void forcedUnloadDrainsWholeQueueAfterReachingCapacity() throws Exception {
        Level level = newLevel();
        AtomicInteger writes = queueUntilBacklogged(level, 127);
        level.unloadChunks(50, true);
        Assertions.assertEquals(130, writes.get());
        Assertions.assertTrue(unloadQueue(level).isEmpty(), "shutdown must not leave chunks waiting for another tick");
    }

    @Test
    void aForcedCommandUnloadIsNotCappedByTheDefaultFifty() throws Exception {
        Level level = newLevel();
        for (int x = 0; x < 120; x++) {
            this.queueDue(level, Level.chunkHash(x, 20));
        }
        AtomicInteger writes = new AtomicInteger();
        Mockito.doAnswer(ignored -> {
            writes.incrementAndGet();
            return true;
        }).when(level).unloadChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
        Mockito.when(this.provider.isChunkSaveBacklogged()).thenReturn(true);

        level.unloadChunks(true);

        Assertions.assertEquals(120, writes.get(), "/gc 的一次强制卸载必须清空队列 / a forced command pass drains the queue");
        Assertions.assertTrue(unloadQueue(level).isEmpty());
    }

    @Test
    void overloadAfterPrimarySaveDoesNotSplitChangedNeighbours() throws Exception {
        Level level = newLevel();
        setField(level, "autoSave", true);
        AtomicInteger writes = new AtomicInteger(127);
        Mockito.when(this.provider.isChunkSaveBacklogged()).thenAnswer(ignored -> writes.get() >= 128);
        // A border save and all eight dirty neighbours form the existing indivisible unload pass.
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
                Mockito.when(chunk.hasChanged()).thenReturn(true);
                Mockito.when(this.provider.isChunkLoaded(x, z)).thenReturn(true);
                Mockito.doReturn(chunk).when(level).getChunk(x, z);
                Mockito.doReturn(chunk).when(level).getChunk(x, z, false);
            }
        }
        Mockito.doAnswer(ignored -> {
            writes.incrementAndGet();
            return null;
        }).when(this.provider).saveChunk(Mockito.anyInt(), Mockito.anyInt());
        this.queueDue(level, Level.chunkHash(10, 10));

        level.unloadChunks(50, false);

        Assertions.assertEquals(136, writes.get(), "backpressure gates the next unload, never half of a border save");
        Assertions.assertTrue(unloadQueue(level).isEmpty());
        Mockito.verify(this.provider, Mockito.times(9)).saveChunk(Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(this.provider).unloadChunk(10, 10, true);
    }

}
