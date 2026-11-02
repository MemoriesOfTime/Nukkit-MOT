package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.Normal;
import cn.nukkit.level.generator.task.PopulationTask;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated("MockServer and native population schedule")
@Timeout(45)
class NativePopulationSaveAckNotificationTest {
    private static volatile Schedule active;
    @TempDir
    Path directory;

    @BeforeAll
    static void initialize() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
        Server.getInstance().asyncChunkSending = true;
    }

    private static final class Schedule {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch mutate = new CountDownLatch(1);
        final CountDownLatch mutated = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
    }

    public static final class PausingNormal extends Normal {
        public PausingNormal(Map<String, Object> options) {
            super(options);
        }
        @Override
        public void populateChunk(int x, int z) {
            Schedule schedule = active;
            schedule.entered.countDown();
            try {
                await(schedule.mutate);
                super.populateChunk(x, z);
                schedule.mutated.countDown();
                await(schedule.finish);
            } catch (Throwable failure) {
                schedule.failure.set(failure);
                throw new IllegalStateException("Native population failed", failure);
            } finally {
                schedule.mutated.countDown();
            }
        }
    }

    private static final class ObservedProvider extends LevelDBProvider {
        volatile boolean observe;
        volatile CompletableFuture<Void> targetSave;
        ObservedProvider(Level level, String path) throws java.io.IOException {
            super(level, path);
        }
        @Override
        public CompletableFuture<Void> saveChunkFuture(int x, int z, FullChunk chunk) {
            CompletableFuture<Void> future = super.saveChunkFuture(x, z, chunk);
            if (observe && x == 1 && z == 1) targetSave = future;
            return future;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "none"})
    void nativePopulationRetainsChangedNeighbourNotificationAfterRealSaveAck(String saving) throws Exception {
        Schedule schedule = new Schedule();
        active = schedule;
        var worker = Executors.newSingleThreadExecutor();
        ObservedProvider provider = null;
        try {
            Level level = mock(Level.class, CALLS_REAL_METHODS);
            Server server = mock(Server.class);
            MainLogger logger = mock(MainLogger.class);
            server.cacheChunks = true;
            server.lightUpdates = true;
            when(server.getLogger()).thenReturn(logger);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            when(server.getAutoSave()).thenReturn(true);
            when(server.getTick()).thenReturn(1);
            doReturn(true).when(level).getAutoSave();
            set(level, "server", server);
            set(level, "providerLock", new ReentrantReadWriteLock());
            set(level, "dimensionData", DimensionEnum.OVERWORLD.getDimensionData());
            set(level, "pendingChunkLoads", new ConcurrentHashMap<>());
            set(level, "generators", new WeakHashMap<Thread, Generator>());
            set(level, "generatorClass", PausingNormal.class);
            for (String name : List.of("chunkPopulationQueue", "chunkPopulationLock", "chunkGenerationQueue")) {
                set(level, name, new Long2ObjectOpenHashMap<Boolean>());
            }
            set(level, "loaders", new Int2ObjectOpenHashMap<ChunkLoader>());
            set(level, "loaderCounter", new Int2IntOpenHashMap());
            set(level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
            set(level, "playerLoaders", new Long2ObjectNonBlockingMap<>());
            set(level, "unloadQueue", new Long2ObjectNonBlockingMap<>());
            set(level, "lightQueue", new ConcurrentHashMap<>());
            set(level, "updateQueue", new BlockUpdateScheduler(level, 0L));
            set(level, "gameRules", GameRules.getDefault());
            LevelDBProvider.generate(directory.toString(), "population-ack", 5L, Flat.class);
            provider = new ObservedProvider(level, directory.toString());
            set(level, "provider", provider);
            // Prepared native forest terrain: seed 5 grows leaves into diagonal neighbour (1, 1).
            // All default Normal populators run; only their scheduling is controlled.
            for (int cx=-1;cx<=1;cx++) for (int cz=-1;cz<=1;cz++) {
                LevelDBChunk chunk = provider.getEmptyChunk(cx,cz);
                for (int x=0;x<16;x++) for (int z=0;z<16;z++) {
                    chunk.setBlock(x,62,z,BlockID.STONE);
                    chunk.setBlock(x,63,z,BlockID.DIRT);
                    chunk.setBlock(x,64,z,BlockID.GRASS);
                    chunk.setBiomeId(x,z,4);
                }
                chunk.setGenerated();
                chunk.setPopulated(cx!=0 || cz!=0);
                chunk.initChunk();
                chunk.recalculateHeightMap();
                chunk.populateSkyLight();
                chunk.populateBlockLight();
                if (cx != 0 || cz != 0) chunk.setLightPopulated();
                chunk.setNeedsLegacyConnectionFix(false);
                provider.setChunk(cx,cz,chunk);
                provider.saveChunkFuture(cx,cz,chunk).get(5,TimeUnit.SECONDS);
                assertFalse(chunk.hasChanged(), "Initial real save must ACK before the schedule");
            }
            LevelDBChunk target = (LevelDBChunk) provider.getLoadedChunk(1,1);
            LevelDBChunk center = (LevelDBChunk) provider.getLoadedChunk(0,0);
            ChunkLoader holder = mock(ChunkLoader.class);
            when(holder.getLoaderId()).thenReturn(91);
            level.registerChunkLoader(holder, 1, 1, false);
            assertTrue(target.isPopulated());
            assertFalse(center.isPopulated());
            assertEquals(0,target.getBlockId(0,68,0));
            long revision = target.getMutationRevision();
            populationQueue(level,"chunkPopulationQueue").put(Level.chunkHash(0,0),Boolean.TRUE);
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                populationQueue(level,"chunkPopulationLock").put(Level.chunkHash(x,z),Boolean.TRUE);
            }
            PopulationTask task = new PopulationTask(level,center);
            assertTrue(Arrays.stream(task.chunks).allMatch(java.util.Objects::nonNull));
            var running = worker.submit(task::onRun);
            await(schedule.entered);
            schedule.mutate.countDown();
            await(schedule.mutated);
            assertNull(schedule.failure.get());
            assertEquals(BlockID.LEAVES,target.getBlockId(0,68,0));
            assertTrue(target.getMutationRevision()>revision);
            assertTrue(target.hasChanged());
            provider.observe = true;
            if(saving.equals("direct")) {
                provider.saveChunkFuture(1,1,target).get(5,TimeUnit.SECONDS);
            }
            if(!saving.equals("none")) {
                assertFalse(target.hasChanged(),"Actual successful ACK must clear dirty while population holds monitors");
                LevelDBChunk stored = provider.readChunk(1,1);
                assertNotSame(target,stored);
                assertEquals(BlockID.LEAVES,stored.getBlockId(0,68,0),"Updated bytes must already be durable");
            }
            assertFalse(running.isDone(),"Population must still be parked before neighbour selection");
            schedule.finish.countDown();
            running.get(10,TimeUnit.SECONDS);
            assertNull(schedule.failure.get());
            boolean selected = Arrays.stream(task.chunks).anyMatch(chunk -> chunk==target);
            verify(holder, never()).onChunkChanged(target);
            task.onCompletion(server);
            verify(level).generateChunkCallback(0,0,center,false);
            long callbacks = mockingDetails(level).getInvocations().stream().filter(call ->
                    call.getMethod().getName().equals("generateChunkCallback") && call.getArguments().length==3
                    && call.getArgument(0).equals(1) && call.getArgument(1).equals(1)).count();
            assertTrue(mockingDetails(logger).getInvocations().stream().noneMatch(call ->
                    List.of("error","emergency","critical","alert").contains(call.getMethod().getName())),
                    "Real completion must not hide fixture failures in the logger");
            assertAll(() -> assertTrue(selected, "Mutated neighbour must survive an unrelated save ACK"),
                    () -> assertEquals(1L, callbacks, "Population completion must notify the mutated neighbour"),
                    () -> verify(holder).onChunkChanged(target));

        } finally {
            schedule.mutate.countDown();
            schedule.finish.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));
            if(provider!=null) provider.close();
            active=null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Long2ObjectOpenHashMap<Boolean> populationQueue(Level level,String name) throws Exception {
        var field=Level.class.getDeclaredField(name);field.setAccessible(true);
        return (Long2ObjectOpenHashMap<Boolean>)field.get(level);
    }
    private static void set(Level level,String name,Object value) throws Exception {
        var field=Level.class.getDeclaredField(name);field.setAccessible(true);field.set(level,value);
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(10,TimeUnit.SECONDS),"Bounded population schedule timed out");
    }
}
