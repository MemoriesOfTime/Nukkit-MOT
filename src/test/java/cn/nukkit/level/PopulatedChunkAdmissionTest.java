package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.event.level.ChunkPopulateEvent;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.SimpleChunkManager;
import cn.nukkit.level.generator.task.LightPopulationTask;
import cn.nukkit.level.generator.task.PopulationTask;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** An active population owner must finish before loaded terrain is admitted. */
class PopulatedChunkAdmissionTest {
    private static Level level(BaseFullChunk loaded, boolean full, boolean pending) throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        if (full) queue.put(Level.chunkHash(99, 99), Boolean.TRUE);
        if (pending) queue.put(Level.chunkHash(3, 5), Boolean.TRUE);
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationQueueSize", 1);
        doReturn(loaded).when(level).getChunkIfLoaded(3, 5);
        doReturn(loaded).when(level).getChunk(3, 5, true);
        return level;
    }

    private static void field(Level level, String name, Object value) throws Exception {
        var field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
        if (name.equals("chunkPopulationQueue")) {
            field(level, "chunkLightRetries", new Long2ObjectOpenHashMap<>());
        }
    }

    private static BaseFullChunk populated() {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isPopulated()).thenReturn(true);
        return chunk;
    }

    @Test
    void populatedChunkBypassesFullQueueWithoutLoading() throws Exception {
        Level level = level(populated(), true, false);
        assertTrue(level.populateChunk(3, 5, false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void activePopulationEntryStillBlocksAfterPopulatedFlag() throws Exception {
        Level level = level(populated(), false, true);
        assertFalse(level.populateChunk(3, 5, false));
        assertFalse(level.populateChunk(3, 5, true));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void unpopulatedChunkStillWaitsAtFullQueue() throws Exception {
        Level level = level(mock(BaseFullChunk.class), true, false);
        assertFalse(level.populateChunk(3, 5, false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void unloadedChunkStillWaitsWithoutColdReadAtFullQueue() throws Exception {
        Level level = level(null, true, false);
        assertFalse(level.populateChunk(3, 5, false));
        verify(level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void ordinaryPopulatedChunkKeepsReadyResult() throws Exception {
        Level level = level(populated(), false, false);
        assertTrue(level.populateChunk(3, 5, false));
    }

    @Test
    void abandoningAStalePopulationReleasesItsEntryAndNeighbourLocks() throws Exception {
        Level level = level(populated(), false, false);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        Long2LongOpenHashMap locks = new Long2LongOpenHashMap();
        queue.put(Level.chunkHash(3, 5), Boolean.TRUE);
        for (int x = 2; x <= 4; x++) {
            for (int z = 4; z <= 6; z++) {
                locks.put(Level.chunkHash(x, z), Level.chunkHash(3, 5));
            }
        }
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationLock", locks);

        level.abandonChunkPopulation(3, 5);

        assertTrue(queue.isEmpty());
        assertTrue(locks.isEmpty());
    }

    @Test
    void staleEntryNoLongerHidesThePopulatedColumn() throws Exception {
        Level level = level(populated(), false, true);
        field(level, "chunkPopulationLock", new Long2LongOpenHashMap());

        assertFalse(level.populateChunk(3, 5, false), "a stale pending entry blocks admission");
        level.abandonChunkPopulation(3, 5);
        assertTrue(level.populateChunk(3, 5, false));
    }

    @Test
    void abortedPopulationTaskReleasesItsSlot() throws Exception {
        Level level = mock(Level.class);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.getX()).thenReturn(3);
        when(chunk.getZ()).thenReturn(5);
        when(level.getSeed()).thenReturn(1L);
        when(level.getChunk(anyInt(), anyInt(), eq(true))).thenReturn(chunk);
        Generator generator = mock(Generator.class);
        SimpleChunkManager manager = mock(SimpleChunkManager.class);
        doThrow(new IllegalStateException("boom")).when(manager).cleanChunks(anyLong());
        when(level.getGenerator()).thenReturn(generator);
        when(generator.getChunkManager()).thenReturn(manager);
        PopulationTask task = new PopulationTask(level, chunk);

        assertDoesNotThrow(task::onRun, "a throwing population task must not escape its runner");
        task.onCompletion(mock(Server.class));

        verify(level).abandonChunkPopulation(3, 5);
    }

    @Test
    void rejectedPopulationTaskDoesNotLeaveAPendingEntry() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        ServerScheduler scheduler = mock(ServerScheduler.class);
        doThrow(new RejectedExecutionException("full")).when(scheduler)
                .scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        field(level, "server", server);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        Long2LongOpenHashMap locks = new Long2LongOpenHashMap();
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationLock", locks);
        field(level, "chunkPopulationQueueSize", 8);
        doReturn(null).when(level).getChunkIfLoaded(3, 5);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isPopulated()).thenReturn(false);
        when(chunk.getX()).thenReturn(3);
        when(chunk.getZ()).thenReturn(5);
        doReturn(chunk).when(level).getChunk(anyInt(), anyInt(), anyBoolean());
        doReturn(1L).when(level).getSeed();

        assertFalse(level.populateChunk(3, 5, false));

        assertTrue(queue.isEmpty(), "a rejected population task must not leave a pending entry");
        assertTrue(locks.isEmpty());
    }

    @Test
    void failedLightPopulationRetriesLightOnlyAndStaysBlocked() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        ServerScheduler scheduler = mock(ServerScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        field(level, "server", server);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        Long2LongOpenHashMap locks = new Long2LongOpenHashMap();
        queue.put(Level.chunkHash(3, 5), Boolean.TRUE);
        for (int x = 2; x <= 4; x++) {
            for (int z = 4; z <= 6; z++) {
                locks.put(Level.chunkHash(x, z), Level.chunkHash(3, 5));
            }
        }
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationLock", locks);
        field(level, "chunkPopulationQueueSize", 8);
        doReturn(1L).when(level).getSeed();

        AtomicBoolean populated = new AtomicBoolean();
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isPopulated()).thenAnswer(call -> populated.get());
        doAnswer(call -> {
            populated.set(true);
            return null;
        }).when(chunk).setPopulated();
        doAnswer(call -> {
            populated.set((Boolean) call.getArgument(0));
            return null;
        }).when(chunk).setPopulated(anyBoolean());
        doThrow(new IllegalStateException("light boom")).when(chunk).populateSkyLight();
        when(chunk.getX()).thenReturn(3);
        when(chunk.getZ()).thenReturn(5);
        when(chunk.isGenerated()).thenReturn(true);
        doReturn(chunk).when(level).getChunk(anyInt(), anyInt(), eq(true));
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);

        Generator generator = mock(Generator.class);
        SimpleChunkManager manager = mock(SimpleChunkManager.class);
        when(generator.getChunkManager()).thenReturn(manager);
        doReturn(chunk).when(manager).getChunk(anyInt(), anyInt());
        doReturn(generator).when(level).getGenerator();

        PopulationTask task = new PopulationTask(level, chunk);
        assertDoesNotThrow(task::onRun, "a throwing population task must not escape its runner");
        task.onCompletion(server);

        assertTrue(populated.get(),
                "terrain decoration already succeeded, so the populated flag must stay published");
        assertTrue(queue.isEmpty(), "light-only recovery must return the terrain population slot");
        assertTrue(locks.containsKey(Level.chunkHash(2, 4)),
                "the 3x3 population locks must stay held while light retries run");
        assertFalse(level.populateChunk(3, 5, false),
                "the column must stay blocked even though it is published as populated");

        ArgumentCaptor<AsyncTask> retry = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), retry.capture());
        assertInstanceOf(LightPopulationTask.class, retry.getValue());
        verify(generator, times(1)).populateChunk(3, 5);
    }

    @Test
    void pendingLightRetryBlocksAdmissionAndArmsOnce() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        ServerScheduler scheduler = mock(ServerScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        field(level, "server", server);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        Long2LongOpenHashMap locks = new Long2LongOpenHashMap();
        queue.put(Level.chunkHash(3, 5), Boolean.TRUE);
        for (int x = 2; x <= 4; x++) {
            for (int z = 4; z <= 6; z++) {
                locks.put(Level.chunkHash(x, z), Level.chunkHash(3, 5));
            }
        }
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationLock", locks);
        field(level, "chunkPopulationQueueSize", 8);

        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.isPopulated()).thenReturn(true);
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);
        level.scheduleChunkLightRetry(3, 5, chunk);

        assertFalse(level.populateChunk(3, 5, false),
                "a decorated but unlit column must not be admitted");
        ArgumentCaptor<AsyncTask> armed = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), armed.capture());
        assertInstanceOf(LightPopulationTask.class, armed.getValue());

        assertFalse(level.populateChunk(3, 5, false),
                "the column stays blocked while the light retry is in flight");
        verify(scheduler, times(1)).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
    }

    @Test
    void exhaustedLightBurstKeepsTheColumnBlocked() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        ServerScheduler scheduler = mock(ServerScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        when(level.getId()).thenReturn(7);
        when(server.getLevel(7)).thenReturn(level);
        field(level, "server", server);
        Long2ObjectOpenHashMap<Boolean> queue = new Long2ObjectOpenHashMap<>();
        Long2LongOpenHashMap locks = new Long2LongOpenHashMap();
        queue.put(Level.chunkHash(3, 5), Boolean.FALSE);
        for (int x = 2; x <= 4; x++) {
            for (int z = 4; z <= 6; z++) {
                locks.put(Level.chunkHash(x, z), Level.chunkHash(3, 5));
            }
        }
        field(level, "chunkPopulationQueue", queue);
        field(level, "chunkPopulationLock", locks);
        field(level, "chunkPopulationQueueSize", 8);

        BaseFullChunk chunk = mock(BaseFullChunk.class);
        BaseFullChunk copy = mock(BaseFullChunk.class);
        when(chunk.getX()).thenReturn(3);
        when(chunk.getZ()).thenReturn(5);
        when(chunk.isPopulated()).thenReturn(true);
        when(chunk.clone()).thenReturn(copy);
        doThrow(new IllegalStateException("light boom")).when(copy).populateSkyLight();
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);

        level.scheduleChunkLightRetry(3, 5, chunk);
        ArgumentCaptor<AsyncTask> initial = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), initial.capture());
        LightPopulationTask task = (LightPopulationTask) initial.getValue();
        for (int attempt = 1; attempt <= 2; attempt++) {
            task.onRun();
            task.onCompletion(server);

            ArgumentCaptor<Runnable> delay = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(attempt)).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delay.capture(), eq(20));
            delay.getValue().run();
            ArgumentCaptor<AsyncTask> next = ArgumentCaptor.forClass(AsyncTask.class);
            verify(scheduler, times(attempt + 1)).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), next.capture());
            task = (LightPopulationTask) next.getValue();
        }
        task.onRun();
        task.onCompletion(server);

        assertTrue(queue.isEmpty(), "an exhausted light burst must not consume terrain population capacity");
        assertTrue(locks.isEmpty(), "neighbour locks are released once only light remains");
        assertFalse(level.populateChunk(3, 5, false),
                "an exhausted but unlit column must stay blocked");

        ArgumentCaptor<Runnable> backoff = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), backoff.capture(), eq(200));
        backoff.getValue().run();
        ArgumentCaptor<AsyncTask> rearmed = ArgumentCaptor.forClass(AsyncTask.class);
        verify(scheduler, times(4)).scheduleAsyncTask(eq(InternalPlugin.INSTANCE), rearmed.capture());
        assertInstanceOf(LightPopulationTask.class, rearmed.getValue());
    }

    @Test
    void pendingEntryIsReleasedAfterLightCompletes() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        ChunkLoader loader = mock(ChunkLoader.class);
        doReturn(new ChunkLoader[]{loader}).when(fixture.level).getChunkLoaders(3, 5);
        LightPopulationTask task = fixture.startLightRecovery(3, 5);
        assertFalse(fixture.level.populateChunk(3, 5));
        task.onRun();
        task.onCompletion(fixture.server);
        assertTrue(fixture.level.populateChunk(3, 5), "the now-lit column is admitted");
        task.onCompletion(fixture.server);
        verify(fixture.server.getPluginManager()).callEvent(isA(ChunkPopulateEvent.class));
        verify(loader).onChunkPopulated(fixture.chunk(3, 5));
    }

    @Test
    void completedLightRetryPreservesNeighbourPopulationLocks() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        LightPopulationTask retry = fixture.startLightRecovery(3, 5);
        fixture.level.releaseChunkPopulationLocks(3, 5);
        fixture.level.populateChunk(4, 5);
        int submitted = fixture.tasks.size();

        retry.onRun();
        retry.onCompletion(fixture.server);
        fixture.level.populateChunk(3, 4);

        assertEquals(submitted, fixture.tasks.size(),
                "finishing A's light must not remove B's locks and admit overlapping C");
    }

    @Test
    void repeatedPauseAtOriginPreservesNeighbourPopulationLocks() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        fixture.startLightRecovery(0, 0);
        fixture.level.pauseChunkLightRetry(0, 0);
        fixture.level.populateChunk(1, 0);
        int submitted = fixture.tasks.size();

        fixture.level.pauseChunkLightRetry(0, 0);
        fixture.level.populateChunk(0, 1);

        assertEquals(submitted, fixture.tasks.size(), "owner hash zero must not clear the neighbour's locks");
    }

    @Test
    void neighbourPublicationCannotCompleteAnotherColumnsLightRecovery() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        LightPopulationTask retry = fixture.startLightRecovery(3, 5);
        fixture.level.releaseChunkPopulationLocks(3, 5);
        fixture.level.populateChunk(4, 5);

        fixture.level.generateChunkCallback(3, 5, fixture.chunk(3, 5));

        assertFalse(fixture.level.populateChunk(3, 5),
                "publishing A as B's changed neighbour must not complete A's light recovery");
        fixture.level.generateChunkCallback(4, 5, fixture.chunk(4, 5), false);
        retry.onRun();
        retry.onCompletion(fixture.server);
        assertTrue(fixture.level.populateChunk(3, 5),
                "only A's successful light retry may admit the column");
    }

    @Test
    void unloadedLightRecoveryDoesNotConsumePopulationCapacity() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        field(fixture.level, "chunkPopulationQueueSize", 1);
        fixture.startLightRecovery(3, 5);
        fixture.chunks.remove(Level.chunkHash(3, 5));
        fixture.level.pauseChunkLightRetry(3, 5);
        int submitted = fixture.tasks.size();

        fixture.level.populateChunk(30, 50);

        assertEquals(submitted + 1, fixture.tasks.size(),
                "an unloaded paused recovery must leave capacity for unrelated terrain population");
        assertInstanceOf(PopulationTask.class, fixture.tasks.get(submitted));
    }

    @Test
    void unloadedLightRecoveryReloadsWithoutRepeatingDecoration() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        fixture.startLightRecovery(3, 5);
        BaseFullChunk source = fixture.chunks.remove(Level.chunkHash(3, 5));
        fixture.level.pauseChunkLightRetry(3, 5);
        doAnswer(call -> {
            fixture.chunks.put(Level.chunkHash(3, 5), source);
            return source;
        }).when(fixture.level).getChunk(3, 5, true);
        int submitted = fixture.tasks.size();

        assertFalse(fixture.level.populateChunk(3, 5));

        assertEquals(submitted + 1, fixture.tasks.size());
        assertInstanceOf(LightPopulationTask.class, fixture.tasks.get(submitted), "reload resumes light only");
        assertFalse(fixture.level.populateChunk(3, 5));
        assertEquals(submitted + 1, fixture.tasks.size(), "repeated requests must not duplicate the light task");
    }

    @Test
    void lightRecoveryDoesNotOverwriteNewerNeighbourEdits() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        LightPopulationTask retry = fixture.startLightRecovery(3, 5);
        BaseFullChunk source = fixture.chunk(3, 5);
        retry.onRun();
        when(source.getModificationVersion()).thenReturn(1L);

        retry.onCompletion(fixture.server);

        assertSame(source, fixture.chunk(3, 5), "an old light snapshot must not replace more recent terrain edits");
        assertFalse(fixture.level.populateChunk(3, 5), "the column stays blocked while fresh light is pending");
    }

    @Test
    void savingDoesNotHideChangesFromLightSnapshotValidation() {
        var chunk = LevelDBChunk.getEmptyChunk(3, 5);
        Arrays.fill(chunk.getSections(), EmptyChunkSection.bySectionY(0));
        long version = chunk.getModificationVersion();
        chunk.setChanged();
        chunk.setChanged(false);

        assertEquals(0, chunk.getChanges(), "saving clears dirty work");
        assertTrue(chunk.getModificationVersion() > version, "saving must not erase the async snapshot revision");
        BaseFullChunk copy = chunk.clone();
        long sourceVersion = chunk.getModificationVersion();
        copy.setChanged();
        assertEquals(sourceVersion, chunk.getModificationVersion(), "a light copy has its own revision counter");
    }

    @Test
    void rejectedDelayedSubmissionLeavesLightRecoveryRearmable() throws Exception {
        PopulationFixture fixture = new PopulationFixture();
        LightPopulationTask retry = fixture.startLightRecovery(3, 5);
        BaseFullChunk copy = fixture.chunk(3, 5).clone();
        doThrow(new IllegalStateException("light failure")).when(copy).populateSkyLight();
        retry.onRun();
        retry.onCompletion(fixture.server);
        ArgumentCaptor<Runnable> delayed = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).scheduleDelayedTask(eq(InternalPlugin.INSTANCE), delayed.capture(), eq(20));
        doThrow(new RejectedExecutionException("stopped")).when(fixture.scheduler)
                .scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));

        assertDoesNotThrow(() -> delayed.getValue().run());

        doAnswer(call -> {
            fixture.tasks.add(call.getArgument(1));
            return null;
        }).when(fixture.scheduler).scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class));
        int submitted = fixture.tasks.size();
        assertFalse(fixture.level.populateChunk(3, 5));
        assertEquals(submitted + 1, fixture.tasks.size(), "the next request must re-arm the rejected light task");
    }

    private static final class PopulationFixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final ServerScheduler scheduler = mock(ServerScheduler.class);
        final LevelProvider provider = mock(LevelProvider.class);
        final Map<Long, BaseFullChunk> chunks = new HashMap<>();
        final List<AsyncTask> tasks = new ArrayList<>();

        PopulationFixture() throws Exception {
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            when(server.getLevel(level.getId())).thenReturn(level);
            when(server.getPluginManager()).thenReturn(mock(cn.nukkit.plugin.PluginManager.class));
            field(level, "server", server);
            field(level, "provider", provider);
            field(level, "providerLock", new ReentrantReadWriteLock());
            field(level, "chunkPopulationQueue", new Long2ObjectOpenHashMap<Boolean>());
            field(level, "chunkPopulationLock", new Long2LongOpenHashMap());
            field(level, "chunkGenerationQueue", new Long2ObjectOpenHashMap<Boolean>());
            field(level, "chunkPopulationQueueSize", 8);
            doReturn(1L).when(level).getSeed();
            doReturn(new ChunkLoader[0]).when(level).getChunkLoaders(anyInt(), anyInt());
            doAnswer(call -> chunk(call.getArgument(0), call.getArgument(1)))
                    .when(level).getChunk(anyInt(), anyInt(), anyBoolean());
            doAnswer(call -> chunks.get(Level.chunkHash(call.getArgument(0), call.getArgument(1))))
                    .when(level).getChunkIfLoaded(anyInt(), anyInt());
            doAnswer(call -> {
                chunks.put(Level.chunkHash(call.getArgument(0), call.getArgument(1)), call.getArgument(2));
                return null;
            }).when(level).setChunk(anyInt(), anyInt(), any(BaseFullChunk.class), anyBoolean());
            when(scheduler.scheduleAsyncTask(any(Plugin.class), any(AsyncTask.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(1));
                return null;
            });
        }

        BaseFullChunk chunk(int x, int z) {
            return chunks.computeIfAbsent(Level.chunkHash(x, z), hash -> {
                BaseFullChunk chunk = mock(BaseFullChunk.class);
                when(chunk.getX()).thenReturn(x);
                when(chunk.getZ()).thenReturn(z);
                return chunk;
            });
        }

        LightPopulationTask startLightRecovery(int x, int z) {
            level.populateChunk(x, z);
            BaseFullChunk source = chunk(x, z);
            when(source.isPopulated()).thenReturn(true);
            BaseFullChunk lit = mock(BaseFullChunk.class);
            when(lit.getX()).thenReturn(x);
            when(lit.getZ()).thenReturn(z);
            when(lit.isPopulated()).thenReturn(true);
            when(lit.isLightPopulated()).thenReturn(true);
            when(lit.getProvider()).thenReturn(provider);
            when(source.clone()).thenReturn(lit);
            when(lit.clone()).thenReturn(lit);
            level.scheduleChunkLightRetry(x, z, source);
            return (LightPopulationTask) tasks.get(tasks.size() - 1);
        }
    }
}
