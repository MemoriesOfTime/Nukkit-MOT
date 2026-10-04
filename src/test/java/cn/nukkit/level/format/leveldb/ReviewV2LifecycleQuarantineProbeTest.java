package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.level.ChunkLoadEvent;
import cn.nukkit.level.ChunkLoadResult;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Review v2 probe: a REAL decoded chunk quarantined after a lifecycle failure
 * (Level.forceLoadChunkSynchronously / mountChunk catch -> markChunkLoadFailure)
 * loses its generated flag and every later in-memory change is discarded on unload.
 */
class ReviewV2LifecycleQuarantineProbeTest {
    private static final int X = 3, Z = 7;
    @TempDir Path directory;
    private LevelDBProvider provider;
    private Level level;
    private PluginManager plugins;
    private ExecutorService reader;
    private boolean previousAsync, previousCompletion;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        Server server = Server.getInstance();
        previousAsync = server.asyncChunkSending;
        previousCompletion = server.asyncChunkLoadCompletion;
        server.asyncChunkSending = true;
        server.asyncChunkLoadCompletion = true;
        server.levelDbCache = 8;
        server.useNativeLevelDB = false;
        server.maxPendingChunkWrites = 128;
        LevelDBProvider.generate(directory.toString(), "lifecycle-quarantine-probe", 404L, Flat.class);
        level = mock(Level.class, CALLS_REAL_METHODS);
        Server lifecycleServer = mock(Server.class);
        lifecycleServer.asyncChunkSending = true;
        lifecycleServer.asyncChunkLoadCompletion = true;
        Thread main = Thread.currentThread();
        when(lifecycleServer.isPrimaryThread()).thenAnswer(ignored -> Thread.currentThread() == main);
        plugins = mock(PluginManager.class);
        when(lifecycleServer.getPluginManager()).thenReturn(plugins);
        when(lifecycleServer.getLogger()).thenReturn(mock(MainLogger.class));
        field("server", lifecycleServer);
        reader = Executors.newSingleThreadExecutor();
        field("asyncChunkLoadExecutor", reader);
        field("pendingChunkLoads", new ConcurrentHashMap<>());
        field("completedChunkLoads", new ConcurrentLinkedQueue<>());
        field("chunkLoadFailures", Caffeine.newBuilder().build());
        field("unloadQueue", new Long2ObjectNonBlockingMap<Long>());
        field("chunkLoaders", new Long2ObjectNonBlockingMap<>());
        field("playerLoaders", new Long2ObjectNonBlockingMap<>());
        field("loaders", new Int2ObjectOpenHashMap<>());
        field("loaderCounter", new Int2IntOpenHashMap());
        doReturn(DimensionEnum.OVERWORLD.getDimensionData()).when(level).getDimensionData();
        doReturn(Level.DIMENSION_OVERWORLD).when(level).getDimension();
        doReturn(java.util.Set.of()).when(level).getPendingBlockUpdates(any(cn.nukkit.level.format.FullChunk.class));
        provider = new LevelDBProvider(level, directory.toString());
        field("provider", provider);
        LevelDBChunk seed = provider.getEmptyChunk(X, Z);
        seed.setGenerated(true);
        seed.setPopulated(true);
        seed.setBlockId(1, 64, 1, BlockID.STONE);
        provider.saveChunkSync(X, Z, seed);
    }

    @AfterEach
    void tearDown() {
        if (reader != null) reader.shutdownNow();
        if (provider != null) provider.close();
        Server.getInstance().asyncChunkSending = previousAsync;
        Server.getInstance().asyncChunkLoadCompletion = previousCompletion;
    }

    @Test
    void lifecycleDiagnosticKeepsTerrainAndActorRemovalDurable() throws Exception {
        BaseFullChunk real = provider.getChunk(X, Z, false);
        assertTrue(real.isGenerated());
        assertEquals(BlockID.STONE, real.getBlockId(1, 64, 1));

        Entity entity = mock(Entity.class);
        when(entity.getId()).thenReturn(801L);
        when(entity.canBeSavedWithChunk()).thenReturn(true);
        entity.namedTag = Entity.getDefaultNBT(new Vector3((X << 4) + 1, 64, (Z << 4) + 1))
                .putString("id", "Cow").putLong("UniqueID", 918273645L);
        real.addEntity(entity);
        provider.saveChunkSync(X, Z, real);
        byte[] digestKey = LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, X, Z, 0);
        byte[] digest = provider.getDatabase().get(digestKey);
        assertNotNull(digest);
        assertEquals(8, digest.length);
        byte[] actorKey = LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, digest);
        assertNotNull(provider.getDatabase().get(actorKey));

        real.markChunkLoadFailure(new RuntimeException("loader callback threw after initChunk"));
        assertTrue(real.isGenerated(), "decoded terrain must stay generated after lifecycle failure");
        assertFalse(real.isReadFailurePlaceholder());

        real.removeEntity(entity);                    // Picked up or moved to a different chunk.
        real.setBlockId(1, 64, 1, BlockID.AIR);
        assertTrue(real.hasChanged());
        assertDoesNotThrow(() -> provider.saveChunkFuture(X, Z, real).join());
        assertTrue(provider.unloadChunk(X, Z, false));

        BaseFullChunk reread = provider.getChunk(X, Z, false);
        assertNull(reread.getChunkLoadFailure());
        assertEquals(BlockID.AIR, reread.getBlockId(1, 64, 1), "in-memory removal must reach disk");
        assertNull(provider.getDatabase().get(digestKey), "old chunk membership must not replay the removed actor");
        assertNull(provider.getDatabase().get(actorKey), "stale actor payload must be deleted with its membership");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realLoaderFailureAfterInitializationKeepsMutationsDurable(boolean asynchronous) throws Exception {
        ChunkLoader loader = mock(ChunkLoader.class);
        when(loader.getLoaderId()).thenReturn(19);
        level.registerChunkLoader(loader, X, Z, false);
        IllegalStateException failure = new IllegalStateException("real loader callback failed after init");
        Thread main = Thread.currentThread();
        doAnswer(call -> {
            BaseFullChunk loaded = call.getArgument(0);
            assertSame(main, Thread.currentThread());
            assertTrue(loaded.isInitialized(), "failure must happen after the real initialization");
            assertTrue(loaded.isGenerated());
            throw failure;
        }).when(loader).onChunkLoaded(any());

        if (asynchronous) {
            CompletionStage<ChunkLoadResult> request = level.requestChunkLoadAsyncResult(X, Z);
            reader.submit(() -> { }).get(5, TimeUnit.SECONDS);
            Queue<?> completed = (Queue<?>) field("completedChunkLoads");
            Object pending = completed.remove();
            Method mount = Level.class.getDeclaredMethod("mountChunk", pending.getClass());
            mount.setAccessible(true);
            mount.invoke(level, pending);
            ChunkLoadResult result = request.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(ChunkLoadResult.Status.FAILED, result.status());
            assertSame(failure, result.failure());
        } else {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> level.getChunk(X, Z, true)));
        }

        BaseFullChunk real = provider.getLoadedChunk(Level.chunkHash(X, Z));
        assertNotNull(real);
        assertTrue(real.isInitialized());
        assertTrue(real.isGenerated());
        assertFalse(real.isReadFailurePlaceholder());
        assertSame(failure, real.getChunkLoadFailure());
        assertSame(real, level.getChunk(X, Z, true));
        assertEquals(ChunkLoadResult.Status.FAILED,
                level.requestChunkLoadAsyncResult(X, Z).toCompletableFuture().join().status());
        verify(loader, times(1)).onChunkLoaded(real);
        verify(plugins, times(1)).callEvent(any(ChunkLoadEvent.class));

        real.setBlockId(1, 64, 1, BlockID.AIR);
        assertDoesNotThrow(() -> provider.saveChunkFuture(X, Z, real).join());
        assertTrue(provider.unloadChunk(X, Z, false));
        BaseFullChunk reread = provider.getChunk(X, Z, false);
        assertNotSame(real, reread);
        assertTrue(reread.isGenerated());
        assertNull(reread.getChunkLoadFailure());
        assertEquals(BlockID.AIR, reread.getBlockId(1, 64, 1), "post-lifecycle removal must not replay from disk");
    }

    private Object field(String name) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(level);
    }

    private void field(String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }
}
