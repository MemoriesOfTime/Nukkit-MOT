package cn.nukkit.level.generator.task;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.ChunkSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.plugin.Plugin;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LightPopulationTaskTest {
    private Server server;
    private Level level;
    private ChunkLoader loader;
    private ServerScheduler scheduler;
    private LevelDBProvider provider;
    private LevelDBChunk original;
    private AtomicReference<BaseFullChunk> mounted;

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        level = mock(Level.class);
        loader = mock(ChunkLoader.class);
        when(level.getChunkLoaders(3, 4)).thenReturn(new ChunkLoader[]{loader});
        scheduler = mock(ServerScheduler.class);
        provider = mock(LevelDBProvider.class);
        when(level.getId()).thenReturn(41);
        when(level.getServer()).thenReturn(server);
        when(level.getProvider()).thenReturn(provider);
        when(level.getMinBlockY()).thenReturn(0);
        when(level.getMaxBlockY()).thenReturn(15);
        when(level.getDimensionData()).thenReturn(new DimensionData(0, 0, 15));
        when(provider.getLevel()).thenReturn(level);
        when(provider.getMinBlockY()).thenReturn(0);
        when(provider.getMaxBlockY()).thenReturn(15);
        when(server.getLevel(41)).thenReturn(level);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);
        original = newChunk();
        original.setBlock(8, 8, 8, BlockID.GLOWSTONE);
        original.setChanged(false);
        mounted = new AtomicReference<>(original);
        when(level.getChunkIfLoaded(3, 4)).thenAnswer(ignored -> mounted.get());
        // Model the old callback's unconditional mount; no world/DB access is needed.
        doAnswer(invocation -> {
            mounted.set(invocation.getArgument(2));
            return null;
        }).when(level).generateChunkCallback(eq(3), eq(4), any(BaseFullChunk.class));
    }

    private LevelDBChunk newChunk() {
        LevelDBChunk chunk = LevelDBChunk.getEmptyChunk(3, 4, provider);
        chunk.setGenerated();
        chunk.setPopulated();
        return chunk;
    }

    @Test
    void unchangedCompletionKeepsChunkIdentityEntitiesAndBlocks() {
        Entity entity = mock(Entity.class);
        when(entity.getId()).thenReturn(7L);
        original.addEntity(entity);
        var entities = original.getEntities();
        LightPopulationTask task = new LightPopulationTask(level, original);
        task.onRun();
        task.onCompletion(server);
        assertSame(original, mounted.get(), "lighting must not mount a cloned chunk");
        assertSame(entities, original.getEntities());
        assertSame(entity, original.getEntities().get(7L));
        assertEquals(BlockID.GLOWSTONE, original.getBlockId(8, 8, 8));
        assertEquals(15, original.getBlockLight(8, 8, 8));
        assertTrue(original.isLightPopulated());
        verify(loader).onChunkChanged(original);
    }

    @Test
    void blockMutationAfterSnapshotIsNotOverwritten() {
        LightPopulationTask task = new LightPopulationTask(level, original);
        task.onRun();
        original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
        task.onCompletion(server);
        assertSame(original, mounted.get());
        assertEquals(BlockID.DIAMOND_BLOCK, mounted.get().getBlockId(2, 2, 2));
        assertFalse(original.isLightPopulated(), "stale results cannot mark changed light complete");
        verify(loader, never()).onChunkChanged(any());
    }

    @Test
    void unloadReloadCannotBeReplacedByOldResult() {
        LightPopulationTask task = new LightPopulationTask(level, original);
        task.onRun();
        LevelDBChunk replacement = newChunk();
        mounted.set(replacement);
        task.onCompletion(server);
        assertSame(replacement, mounted.get());
        assertFalse(replacement.isLightPopulated());
    }

    @Test
    void saveCounterResetCannotHideAnInterveningMutation() {
        original.setChanged();
        long oldChanges = original.getChanges();
        LightPopulationTask task = new LightPopulationTask(level, original);
        task.onRun();
        original.setChanged(false);
        original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
        assertEquals(oldChanges, original.getChanges(), "construct the dirty-counter ABA case");
        task.onCompletion(server);
        assertSame(original, mounted.get());
        assertEquals(BlockID.DIAMOND_BLOCK, mounted.get().getBlockId(2, 2, 2));
        assertFalse(original.isLightPopulated());
    }
    @Test
    void snapshotIsCapturedBeforeDispatchAndHasIndependentRevision() {
        long revision = original.getMutationRevision();
        LightPopulationTask task = new LightPopulationTask(level, original);
        original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
        assertEquals(BlockID.AIR, task.chunk.getBlockId(2, 2, 2));
        long afterMutation = original.getMutationRevision();
        assertTrue(afterMutation > revision);
        task.onRun();
        assertEquals(afterMutation, original.getMutationRevision(), "worker writes belong to the snapshot");
        task.onCompletion(server);
        assertFalse(original.isLightPopulated());
    }

    @Test
    void completionPreservesLiveBlockEntitiesEvenWithoutDirtyNotification() {
        LightPopulationTask task = new LightPopulationTask(level, original);
        task.onRun();
        BlockEntity tile = mock(BlockEntity.class);
        when(tile.getId()).thenReturn(9L);
        when(tile.getFloorX()).thenReturn(2);
        when(tile.getFloorY()).thenReturn(3);
        when(tile.getFloorZ()).thenReturn(4);
        original.addBlockEntity(tile);
        var tiles = original.getBlockEntities();
        task.onCompletion(server);
        assertSame(original, mounted.get());
        assertSame(tiles, original.getBlockEntities());
        assertSame(tile, original.getBlockEntities().get(9L));
        assertTrue(original.isLightPopulated());
    }

    @Test
    void staleTaskRetriesOnNextTickAndDeduplicatesConcurrentRequests() {
        LightPopulationTask.schedule(level, original);
        LightPopulationTask.schedule(level, original);
        var async = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler, times(1)).scheduleAsyncTask(any(Plugin.class), async.capture());
        AsyncTask first = async.getValue();
        first.onRun();
        original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
        first.onCompletion(server);
        var retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(any(Plugin.class), retry.capture(), eq(1));
        assertFalse(original.isLightPopulated());
        retry.getValue().run();
        LightPopulationTask.schedule(level, original);
        verify(scheduler, times(2)).scheduleAsyncTask(any(Plugin.class), async.capture());
        AsyncTask second = async.getValue();
        second.onRun();
        second.onCompletion(server);
        assertSame(original, mounted.get());
        assertEquals(BlockID.DIAMOND_BLOCK, original.getBlockId(2, 2, 2));
        assertTrue(original.isLightPopulated());
        verify(level, never()).generateChunkCallback(anyInt(), anyInt(), any(BaseFullChunk.class));
    }

    @Test
    void offThreadSnapshotIsRejectedBeforeAcquiringPendingSlot() {
        when(server.isPrimaryThread()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> new LightPopulationTask(level, original));
        when(server.isPrimaryThread()).thenReturn(true);
        LightPopulationTask.schedule(level, original);
        verify(scheduler).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
    }

    @Test
    void offThreadSchedulingCapturesSnapshotOnlyAfterMainThreadDispatch() {
        when(server.isPrimaryThread()).thenReturn(false);
        LightPopulationTask.schedule(level, original);
        var dispatch = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleTask(any(Plugin.class), dispatch.capture());
        verify(scheduler, never()).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
        when(server.isPrimaryThread()).thenReturn(true);
        dispatch.getValue().run();
        var async = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(any(Plugin.class), async.capture());
        assertEquals(BlockID.DIAMOND_BLOCK,
                ((LightPopulationTask) async.getValue()).chunk.getBlockId(2, 2, 2));
    }

    @Test
    void workerFailureLeavesLightIncompleteAndReleasesPendingSlot() {
        LightPopulationTask task = new LightPopulationTask(level, original);
        BaseFullChunk failedSnapshot = mock(BaseFullChunk.class);
        doThrow(new IllegalStateException("synthetic light failure")).when(failedSnapshot).populateSkyLight();
        task.chunk = failedSnapshot;
        task.onRun();
        task.onCompletion(server);
        assertSame(original, mounted.get());
        assertFalse(original.isLightPopulated());
        verify(scheduler, never()).scheduleDelayedTask(any(Plugin.class), any(Runnable.class), anyInt());
        LightPopulationTask.schedule(level, original);
        verify(scheduler).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
    }

    @Test
    void workerErrorReleasesPendingSlotWithoutACompletionCallback() throws Exception {
        LightPopulationTask task = new LightPopulationTask(level, original);
        BaseFullChunk failedSnapshot = mock(BaseFullChunk.class);
        AssertionError failure = new AssertionError("synthetic worker error");
        doThrow(failure).when(failedSnapshot).populateSkyLight();
        task.chunk = failedSnapshot;
        FutureTask<Throwable> worker = new FutureTask<>(() -> assertThrows(AssertionError.class, task::run));
        new Thread(worker, "light-error-test").start();
        assertSame(failure, worker.get(5, TimeUnit.SECONDS));
        assertFalse(task.isFinished(), "AsyncTask does not enqueue completion when onRun throws Error");
        assertFalse(AsyncTask.FINISHED_LIST.contains(task));
        assertFalse(original.isLightPopulated());
        LightPopulationTask.schedule(level, original);
        verify(scheduler).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        // A defensive late callback from the failed task cannot release the new task's slot.
        task.onCompletion(server);
        LightPopulationTask.schedule(level, original);
        verify(scheduler, times(1)).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        verify(loader, never()).onChunkChanged(any());
    }

    @Test
    void lightingMergeReusesASectionPublishedAfterItsInitialEmptyRead() {
        LevelDBChunk live = newChunk();
        LevelDBChunk snapshot = (LevelDBChunk) live.clone();
        snapshot.setBlockLight(2, 2, 2, 8);
        live.getSections()[0] = new EmptyChunkSection(0) {
            private boolean written;

            @Override
            public byte[] getLightArray() {
                if (!written) {
                    written = true;
                    // applyLightingFrom already captured this empty section in a local variable.
                    FutureTask<Void> writer = new FutureTask<>(() -> {
                        live.setBlock(1, 1, 1, BlockID.DIAMOND_BLOCK);
                        return null;
                    });
                    new Thread(writer, "light-section-writer-test").start();
                    try {
                        assertDoesNotThrow(() -> writer.get(5, TimeUnit.SECONDS));
                    } finally {
                        writer.cancel(true);
                    }
                    assertEquals(BlockID.DIAMOND_BLOCK, live.getBlockId(1, 1, 1));
                }
                return super.getLightArray();
            }
        };
        live.applyLightingFrom(snapshot);
        assertEquals(BlockID.DIAMOND_BLOCK, live.getBlockId(1, 1, 1), "lighting cannot replace a writer's section");
        assertEquals(8, live.getBlockLight(2, 2, 2));
    }

    private Long2ObjectOpenHashMap<Boolean> generationMap(String name) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        Long2ObjectOpenHashMap<Boolean> map = new Long2ObjectOpenHashMap<>();
        field.set(level, map);
        return map;
    }

    private Long2ObjectOpenHashMap<Boolean> useRealGenerationQueues() throws Exception {
        generationMap("chunkGenerationQueue");
        generationMap("chunkPopulationLock");
        Long2ObjectOpenHashMap<Boolean> population = generationMap("chunkPopulationQueue");
        doCallRealMethod().when(level).isChunkGenerationPending(anyInt(), anyInt());
        return population;
    }

    @Test
    void generationQueryCoversEveryPopulationNeighbourEvenAfterItsLockWasRemoved() throws Exception {
        Long2ObjectOpenHashMap<Boolean> population = useRealGenerationQueues();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                population.clear();
                population.put(Level.chunkHash(3 + dx, 4 + dz), Boolean.TRUE);
                assertTrue(level.isChunkGenerationPending(3, 4));
            }
        }
        population.clear();
        population.put(Level.chunkHash(5, 4), Boolean.TRUE);
        assertFalse(level.isChunkGenerationPending(3, 4));
        Long2ObjectOpenHashMap<Boolean> generation = generationMap("chunkGenerationQueue");
        generation.put(Level.chunkHash(3, 4), Boolean.TRUE);
        assertTrue(level.isChunkGenerationPending(3, 4));
        generation.clear();
        // Generation completion may consume the centre's population flags first.
        generation.put(Level.chunkHash(4, 4), Boolean.TRUE);
        assertTrue(level.isChunkGenerationPending(3, 4));
        generation.clear();
        assertFalse(level.isChunkGenerationPending(3, 4));
        Long2ObjectOpenHashMap<Boolean> locks = generationMap("chunkPopulationLock");
        locks.put(Level.chunkHash(3, 4), Boolean.TRUE);
        assertTrue(level.isChunkGenerationPending(3, 4));
    }

    @Test
    void busyGenerationDefersOneSnapshotWithoutWaitingForTheChunkMonitor() throws Exception {
        Long2ObjectOpenHashMap<Boolean> population = useRealGenerationQueues();
        population.put(Level.chunkHash(4, 4), Boolean.TRUE);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FutureTask<Void> worker = new FutureTask<>(() -> {
            synchronized (original) {
                held.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
            }
            return null;
        });
        new Thread(worker, "population-monitor-test").start();
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> LightPopulationTask.schedule(level, original));
            LightPopulationTask.schedule(level, original);
            assertNull(new LightPopulationTask(level, original).chunk, "direct callers cannot snapshot a busy chunk");
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        var retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(any(Plugin.class), retry.capture(), eq(1));
        verify(scheduler, never()).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        retry.getValue().run();
        verify(scheduler, times(2)).scheduleDelayedTask(any(Plugin.class), retry.capture(), eq(1));
        verify(scheduler, never()).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        population.clear(); // Main-thread population completion releases the centre queue.
        retry.getValue().run();
        var async = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(any(Plugin.class), async.capture());
        assertEquals(BlockID.DIAMOND_BLOCK, ((LightPopulationTask) async.getValue()).chunk.getBlockId(2, 2, 2));
    }

    @Test
    void populationStartingAfterSnapshotDefersCompletionUntilItsCallback() throws Exception {
        Long2ObjectOpenHashMap<Boolean> population = useRealGenerationQueues();
        LightPopulationTask task = new LightPopulationTask(level, original);
        task.onRun();
        population.put(Level.chunkHash(4, 4), Boolean.TRUE);
        task.onCompletion(server);
        assertFalse(original.isLightPopulated());
        verify(loader, never()).onChunkChanged(any());
        original.setBlock(2, 2, 2, BlockID.DIAMOND_BLOCK);
        population.clear();
        var retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(any(Plugin.class), retry.capture(), eq(1));
        retry.getValue().run();
        var async = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(any(Plugin.class), async.capture());
        async.getValue().onRun();
        async.getValue().onCompletion(server);
        assertTrue(original.isLightPopulated());
        assertEquals(BlockID.DIAMOND_BLOCK, original.getBlockId(2, 2, 2));
    }

    @Test
    void lightMergeReadsEachSectionArrayOnceInsteadOfCloningItForEveryNibble() {
        LevelDBChunk snapshot = (LevelDBChunk) original.clone();
        snapshot.setBlockSkyLight(2, 2, 2, 3);
        snapshot.setBlockSkyLight(3, 2, 2, 11);
        snapshot.setBlockLight(2, 2, 2, 7);
        snapshot.setBlockLight(3, 2, 2, 13);
        ChunkSection source = spy(snapshot.getSections()[0]);
        ChunkSection target = spy(original.getSections()[0]);
        snapshot.getSections()[0] = source;
        original.getSections()[0] = target;
        original.applyLightingFrom(snapshot);
        verify(source).getSkyLightArray();
        verify(target).getSkyLightArray();
        verify(source).getLightArray();
        verify(target).getLightArray();
        verify(source, never()).getBlockSkyLight(anyInt(), anyInt(), anyInt());
        verify(target, never()).getBlockSkyLight(anyInt(), anyInt(), anyInt());
        assertEquals(3, original.getBlockSkyLight(2, 2, 2));
        assertEquals(11, original.getBlockSkyLight(3, 2, 2));
        assertEquals(7, original.getBlockLight(2, 2, 2));
        assertEquals(13, original.getBlockLight(3, 2, 2));
    }

    @Test
    void asymmetricLightCoordinatesRemainUnchangedInAnvilAndLevelDB() {
        cn.nukkit.level.format.anvil.Anvil anvil = mock(cn.nukkit.level.format.anvil.Anvil.class);
        when(anvil.getLevel()).thenReturn(level);
        when(anvil.getMinBlockY()).thenReturn(0);
        when(anvil.getMaxBlockY()).thenReturn(255);
        BaseFullChunk[] chunks = {newChunk(), cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(3, 4, anvil)};
        for (BaseFullChunk live : chunks) {
            live.setBlock(9, 6, 1, BlockID.DIAMOND_BLOCK);
            BaseFullChunk snapshot = live.clone();
            snapshot.setBlockSkyLight(2, 7, 11, 3);
            snapshot.setBlockSkyLight(3, 7, 11, 12);
            snapshot.setBlockLight(12, 4, 9, 5);
            snapshot.setBlockLight(13, 4, 9, 14);
            live.applyLightingFrom(snapshot);
            assertEquals(3, live.getBlockSkyLight(2, 7, 11));
            assertEquals(12, live.getBlockSkyLight(3, 7, 11));
            assertEquals(15, live.getBlockSkyLight(7, 2, 11));
            assertEquals(5, live.getBlockLight(12, 4, 9));
            assertEquals(14, live.getBlockLight(13, 4, 9));
            assertEquals(0, live.getBlockLight(4, 12, 9));
            assertEquals(BlockID.DIAMOND_BLOCK, live.getBlockId(9, 6, 1));
        }
    }

    private static final class CountingChunk extends LevelDBChunk {
        int heightScans;

        CountingChunk(LevelDBProvider provider) {
            super(provider, 3, 4);
        }

        @Override
        public int getHighestBlockAt(int x, int z) {
            heightScans++;
            return super.getHighestBlockAt(x, z);
        }
    }

    private CountingChunk tallChunk() {
        when(level.getDimensionData()).thenReturn(new DimensionData(0, -64, 319));
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(provider.getMinBlockY()).thenReturn(-64);
        when(provider.getMaxBlockY()).thenReturn(319);
        return new CountingChunk(provider);
    }

    @Test
    void equal384HeightLightMergeNeverScansColumnHeight() {
        CountingChunk live = tallChunk();
        CountingChunk snapshot = (CountingChunk) live.clone();
        live.applyLightingFrom(snapshot);
        assertEquals(0, live.heightScans);
        assertEquals(0, snapshot.heightScans);
        assertTrue(live.isLightPopulated());
    }

    @Test
    void changed384HeightLightMergeUsesRawSectionsAndPreservesBlocks() {
        CountingChunk live = tallChunk();
        live.setBlock(1, 0, 1, BlockID.DIAMOND_BLOCK);
        CountingChunk snapshot = (CountingChunk) live.clone();
        snapshot.setBlockSkyLight(2, -32, 2, 0);
        snapshot.setBlockLight(3, 300, 4, 9);
        snapshot.setHeightMap(3, 4, 302);
        live.applyLightingFrom(snapshot);
        assertEquals(0, live.heightScans);
        assertEquals(0, snapshot.heightScans);
        assertEquals(0, live.getSection(-2).getBlockSkyLight(2, 0, 2));
        assertEquals(9, live.getSection(18).getBlockLight(3, 12, 4));
        assertEquals(302, live.getHeightMap(3, 4));
        assertEquals(BlockID.DIAMOND_BLOCK, live.getBlockId(1, 0, 1));
        assertTrue(live.isLightPopulated());
    }

    @Test
    void snapshotOwnsExistingLightArraysBeforeWorkerStarts() {
        original.setBlockLight(8, 8, 8, 1);
        original.setBlockSkyLight(8, 8, 8, 2);
        LightPopulationTask task = new LightPopulationTask(level, original);
        long revision = original.getMutationRevision();
        task.onRun();
        assertEquals(1, original.getBlockLight(8, 8, 8), "worker cannot mutate live block-light arrays");
        assertEquals(2, original.getBlockSkyLight(8, 8, 8), "worker cannot mutate live sky-light arrays");
        assertEquals(revision, original.getMutationRevision());
        task.onCompletion(server);
        assertEquals(15, original.getBlockLight(8, 8, 8));
    }

}
