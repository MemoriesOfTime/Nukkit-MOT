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
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Chunk garbage collection needs only the coordinates of the loaded chunks, and takes them from the
 * primitive hashes instead of a boxed copy of the whole chunk map.
 */
class LevelChunkGarbageCollectionTest {

    private LevelProvider provider;

    @BeforeEach
    void setUp() {
        MockServer.init();
        this.provider = Mockito.mock(LevelProvider.class);
    }

    private Level newLevel() throws Exception {
        Server server = Mockito.mock(Server.class);
        Mockito.lenient().when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        Mockito.lenient().when(server.getLogger()).thenReturn(Mockito.mock(MainLogger.class));
        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        setField(level, "server", server);
        setField(level, "provider", this.provider);
        setField(level, "blockEntities", new Long2ObjectNonBlockingMap<>());
        setField(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        setField(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        setField(level, "unloadQueue", Level.class.getDeclaredField("unloadQueue").getType().getDeclaredConstructor().newInstance());
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
        Field field = Level.class.getDeclaredField("unloadQueue");
        field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(level)).containsKey(hash);
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
}
