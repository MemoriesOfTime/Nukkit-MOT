package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.event.level.ChunkLoadEvent;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.google.common.util.concurrent.MoreExecutors;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 异步区块加载管线测试:pending 去重、失效丢弃、挂载判定
 * <p>
 * Async chunk load pipeline tests: pending dedup, invalidation drops, mount decisions
 *
 * @author LT_Name
 */
public class AsyncChunkLoadTest {

    private Server server;
    private PluginManager pluginManager;
    private LevelProvider provider;

    @BeforeEach
    public void setUp() {
        MockServer.init();

        this.pluginManager = Mockito.mock(PluginManager.class);
        this.server = Mockito.mock(Server.class);
        this.server.asyncChunkSending = true;
        this.server.lightUpdates = false;
        Mockito.lenient().when(this.server.isPrimaryThread()).thenReturn(true);
        Mockito.lenient().when(this.server.getPluginManager()).thenReturn(this.pluginManager);
        Mockito.lenient().when(this.server.getLogger()).thenReturn(Mockito.mock(MainLogger.class));

        this.provider = Mockito.mock(LevelProvider.class);
        Mockito.lenient().when(this.provider.isOffThreadChunkReadSupported()).thenReturn(true);
        // mountChunk 守卫要求 provider 处于存活状态（getLevel() 非 null 表示未关闭）
        // The mountChunk guard requires a live provider (non-null getLevel() means not closed)
        Mockito.lenient().when(this.provider.getLevel()).thenReturn(Mockito.mock(Level.class));
    }

    private Level newLevel(ExecutorService executor) throws Exception {
        return newLevel(executor, 16);
    }

    private Level newLevel(ExecutorService executor, int inFlightCap) throws Exception {
        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        // CALLS_REAL_METHODS 不执行构造器,手动初始化本测试路径依赖的字段
        // CALLS_REAL_METHODS skips constructors; init the fields this test path depends on
        setField(level, "server", this.server);
        setField(level, "provider", this.provider);
        setField(level, "asyncChuckExecutor", executor);
        setField(level, "asyncChunkLoadExecutor", executor);
        setField(level, "asyncChunkLoadSlots", new Semaphore(inFlightCap));
        setField(level, "runningAsyncChunkLoads", new AtomicInteger());
        // mock 不跑字段初始化器,排空超时须显式给值否则为 0 立即超时
        // The mock skips field initializers; the drain timeout needs an explicit value or it is 0
        setField(level, "asyncChunkLoadDrainTimeoutMillis", 10_000L);
        setField(level, "pendingChunkLoads", new ConcurrentHashMap<>());
        setField(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
        setField(level, "unloadQueue", new Long2ObjectNonBlockingMap<>());
        setField(level, "chunkLoaders", new Long2ObjectNonBlockingMap<>());
        setField(level, "playerLoaders", new Long2ObjectNonBlockingMap<>());
        setField(level, "loaders", new Int2ObjectOpenHashMap<>());
        setField(level, "loaderCounter", new Int2IntOpenHashMap());
        return level;
    }

    /** 捕获任务而不执行的 executor,用于精确控制任务完成时机 / Captures tasks without running them, for precise completion control */
    private static ExecutorService capturingExecutor(List<Runnable> sink) {
        ExecutorService executor = Mockito.mock(ExecutorService.class);
        Mockito.doAnswer(invocation -> {
            sink.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(Mockito.any(Runnable.class));
        return executor;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private BaseFullChunk newChunk() {
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.lenient().when(chunk.getProvider()).thenReturn(this.provider);
        return chunk;
    }

    @Test
    public void requestChunkLoadAsyncDeduplicatesSameChunk() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        Mockito.when(this.provider.readChunkOffThread(3, 5)).thenReturn(chunk);

        Assertions.assertTrue(level.requestChunkLoadAsync(3, 5));
        Assertions.assertTrue(level.requestChunkLoadAsync(3, 5));

        Mockito.verify(this.provider, Mockito.times(1)).readChunkOffThread(3, 5);
        Assertions.assertEquals(1, level.completedChunkLoads.size());
        Assertions.assertSame(chunk, level.completedChunkLoads.peek().chunk);
        Assertions.assertTrue(level.pendingChunkLoads.containsKey(Level.chunkHash(3, 5)));
    }

    @Test
    public void requestChunkLoadAsyncRejectsWhenDisabledOrUnsupported() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());

        this.server.asyncChunkSending = false;
        Assertions.assertFalse(level.requestChunkLoadAsync(0, 0));

        this.server.asyncChunkSending = true;
        Mockito.when(this.provider.isOffThreadChunkReadSupported()).thenReturn(false);
        Assertions.assertFalse(level.requestChunkLoadAsync(0, 0));

        Assertions.assertTrue(level.pendingChunkLoads.isEmpty());
        Mockito.verify(this.provider, Mockito.never()).readChunkOffThread(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void requestChunkLoadAsyncRejectsWhenInFlightCapReached() throws Exception {
        List<Runnable> tasks = new CopyOnWriteArrayList<>();
        Level level = newLevel(capturingExecutor(tasks), 2);
        try {
            Assertions.assertTrue(level.requestChunkLoadAsync(1, 1));
            Assertions.assertTrue(level.requestChunkLoadAsync(2, 2));
            // 在飞上限 2 已满:第三笔被拒,回退同步路径且不留 pending 条目
            // The in-flight cap of 2 is full: the third request is rejected,
            // falls back to the sync path and leaves no pending entry
            Assertions.assertFalse(level.requestChunkLoadAsync(3, 3));
            Assertions.assertFalse(level.pendingChunkLoads.containsKey(Level.chunkHash(3, 3)));

            // 一笔任务完成归还许可后恢复受理
            // One completed task releases its permit, so submissions resume
            tasks.get(0).run();
            Assertions.assertTrue(level.requestChunkLoadAsync(3, 3));
        } finally {
            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    @Test
    public void requestChunkLoadAsyncReleasesSlotOnRejection() throws Exception {
        ExecutorService executor = Mockito.mock(ExecutorService.class);
        List<Runnable> sink = new CopyOnWriteArrayList<>();
        Mockito.doThrow(new RejectedExecutionException())
                .doAnswer(invocation -> {
                    sink.add(invocation.getArgument(0));
                    return null;
                })
                .when(executor).execute(Mockito.any(Runnable.class));
        Level level = newLevel(executor, 1);

        // 池拒绝(关停)时受理失败,但许可必须归还,否则该世界加载通道永久卡死
        // A pool rejection (shutdown) fails the request, but the permit must be
        // released or this level's load lane wedges forever
        Assertions.assertFalse(level.requestChunkLoadAsync(1, 1));
        Assertions.assertTrue(level.pendingChunkLoads.isEmpty());

        Assertions.assertTrue(level.requestChunkLoadAsync(1, 1));
        Assertions.assertEquals(1, sink.size());
    }

    @Test
    public void requestChunkLoadAsyncSkipsAlreadyLoadedChunk() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Mockito.when(this.provider.isChunkLoaded(Level.chunkHash(1, 2))).thenReturn(true);

        Assertions.assertTrue(level.requestChunkLoadAsync(1, 2));

        Assertions.assertTrue(level.pendingChunkLoads.isEmpty());
        Mockito.verify(this.provider, Mockito.never()).readChunkOffThread(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void loadChunkReplaysDeferredUpdatesForProviderCachedChunk() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        long hash = Level.chunkHash(3, 5);
        Mockito.when(this.provider.isChunkLoaded(hash)).thenReturn(true);
        Mockito.when(this.provider.getLoadedChunk(hash)).thenReturn(chunk);

        Assertions.assertTrue(level.loadChunk(3, 5, false));

        Mockito.verify(chunk).replayDeferredBlockUpdates();
        Mockito.verify(chunk, Mockito.never()).initChunk();
        Mockito.verify(this.pluginManager, Mockito.never()).callEvent(Mockito.any());
    }

    @Test
    public void loadChunkDoesNotReplayProviderCachedChunkOffMainThread() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        long hash = Level.chunkHash(3, 5);
        Mockito.when(this.server.isPrimaryThread()).thenReturn(false);
        Mockito.when(this.provider.isChunkLoaded(hash)).thenReturn(true);
        Mockito.when(this.provider.getLoadedChunk(hash)).thenReturn(chunk);

        Assertions.assertTrue(level.loadChunk(3, 5, false));

        Mockito.verify(chunk, Mockito.never()).replayDeferredBlockUpdates();
        Mockito.verify(this.pluginManager, Mockito.never()).callEvent(Mockito.any());
    }

    @Test
    public void getChunkReplaysDeferredUpdatesForProviderCachedChunkOnMainThread() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        long hash = Level.chunkHash(3, 5);
        Mockito.when(this.provider.getLoadedChunk(hash)).thenReturn(chunk);

        Assertions.assertSame(chunk, level.getChunk(3, 5, false));

        Mockito.verify(chunk).replayDeferredBlockUpdates();
        Mockito.verify(chunk, Mockito.never()).initChunk();
        Mockito.verify(this.pluginManager, Mockito.never()).callEvent(Mockito.any());
    }

    @Test
    public void getChunkDoesNotReplayProviderCachedChunkOffMainThread() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        long hash = Level.chunkHash(3, 5);
        Mockito.when(this.server.isPrimaryThread()).thenReturn(false);
        Mockito.when(this.provider.getLoadedChunk(hash)).thenReturn(chunk);

        Assertions.assertSame(chunk, level.getChunk(3, 5, false));

        Mockito.verify(chunk, Mockito.never()).replayDeferredBlockUpdates();
        Mockito.verify(this.pluginManager, Mockito.never()).callEvent(Mockito.any());
    }

    @Test
    public void readFailureIsMountedAsEmptyChunk() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Mockito.when(this.provider.readChunkOffThread(7, 8)).thenThrow(new RuntimeException("corrupted"));
        BaseFullChunk emptyChunk = newChunk();
        Mockito.when(this.provider.getEmptyChunk(7, 8)).thenReturn(emptyChunk);

        Assertions.assertTrue(level.requestChunkLoadAsync(7, 8));

        Assertions.assertEquals(1, level.completedChunkLoads.size());
        Assertions.assertNull(level.completedChunkLoads.peek().chunk);
        Assertions.assertNotNull(level.completedChunkLoads.peek().failure);

        level.pendingChunkLoads.remove(Level.chunkHash(7, 8));
        level.mountChunk(level.completedChunkLoads.poll());

        // 失败挂载空区块,与同步 readOrCreateChunk(create=true) 一致,避免玩家每 tick 重读坏区块
        // Failure mounts an empty chunk (matching sync readOrCreateChunk(create=true)) so the player does not re-read the broken chunk every tick
        Mockito.verify(this.provider).getEmptyChunk(7, 8);
        Mockito.verify(this.provider).putChunkIfAbsent(7, 8, emptyChunk);
    }

    @Test
    public void mountChunkMountsDecodedChunk() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        pending.chunk = chunk;

        level.mountChunk(pending);

        Mockito.verify(this.provider).putChunkIfAbsent(3, 5, chunk);
        org.mockito.InOrder order = Mockito.inOrder(chunk, this.pluginManager);
        order.verify(chunk).replayDeferredBlockUpdates();
        order.verify(this.pluginManager).callEvent(Mockito.any(ChunkLoadEvent.class));
        order.verify(chunk).initChunk();
    }

    @Test
    public void mountChunkUsesEmptyChunkWhenAbsentOnDisk() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk emptyChunk = newChunk();
        Mockito.when(this.provider.getEmptyChunk(3, 5)).thenReturn(emptyChunk);
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);

        level.mountChunk(pending);

        Mockito.verify(this.provider).putChunkIfAbsent(3, 5, emptyChunk);
        Mockito.verify(emptyChunk).initChunk();
    }

    @Test
    public void mountChunkDropsInvalidatedResult() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        pending.chunk = newChunk();
        pending.invalidated = true;

        level.mountChunk(pending);

        Mockito.verify(this.provider, Mockito.never()).putChunkIfAbsent(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
        Mockito.verify(this.pluginManager, Mockito.never()).callEvent(Mockito.any());
    }

    @Test
    public void mountChunkDropsWhenSyncLoadWonTheRace() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        pending.chunk = newChunk();
        Mockito.when(this.provider.isChunkLoaded(pending.hash)).thenReturn(true);

        level.mountChunk(pending);

        Mockito.verify(this.provider, Mockito.never()).putChunkIfAbsent(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
    }

    @Test
    public void mountChunkDropsWhenProviderChanged() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        LevelProvider oldProvider = Mockito.mock(LevelProvider.class);
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), oldProvider);
        pending.chunk = newChunk();

        level.mountChunk(pending);

        Mockito.verify(this.provider, Mockito.never()).putChunkIfAbsent(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
        Mockito.verify(oldProvider, Mockito.never()).putChunkIfAbsent(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
    }

    @Test
    public void mountChunkDropsWhenPutIfAbsentRaces() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        BaseFullChunk chunk = newChunk();
        BaseFullChunk existing = newChunk();
        Mockito.when(this.provider.putChunkIfAbsent(3, 5, chunk)).thenReturn(existing);
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        pending.chunk = chunk;

        level.mountChunk(pending);

        Mockito.verify(chunk, Mockito.never()).initChunk();
        Mockito.verify(this.pluginManager, Mockito.never()).callEvent(Mockito.any());
    }

    @Test
    public void invalidatePendingChunkLoadMarksPendingEntry() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        level.pendingChunkLoads.put(pending.hash, pending);

        level.invalidatePendingChunkLoad(pending.hash);

        Assertions.assertTrue(pending.invalidated);
    }

    @Test
    public void invalidatedPendingLoadSkipsProviderRead() throws Exception {
        ExecutorService executor = Mockito.mock(ExecutorService.class);
        final Runnable[] task = new Runnable[1];
        Mockito.doAnswer(invocation -> {
            task[0] = invocation.getArgument(0);
            return null;
        }).when(executor).execute(Mockito.any(Runnable.class));
        Level level = newLevel(executor);

        Assertions.assertTrue(level.requestChunkLoadAsync(3, 5));
        level.invalidatePendingChunkLoad(Level.chunkHash(3, 5));
        task[0].run();

        Mockito.verify(this.provider, Mockito.never()).readChunkOffThread(3, 5);
        Assertions.assertEquals(1, level.completedChunkLoads.size());
    }

    @Test
    public void unloadingNotYetMountedChunkInvalidatesPendingLoad() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        level.pendingChunkLoads.put(pending.hash, pending);
        Mockito.when(this.provider.isChunkLoaded(3, 5)).thenReturn(false);

        Assertions.assertTrue(level.unloadChunk(3, 5, false, false));

        Assertions.assertTrue(pending.invalidated);
    }

    @Test
    public void removingLastChunkLoaderInvalidatesPendingLoad() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        Mockito.doReturn(true).when(level).unloadChunkRequest(3, 5, true);
        ChunkLoader loader = Mockito.mock(ChunkLoader.class);
        Mockito.when(loader.getLoaderId()).thenReturn(11);
        level.registerChunkLoader(loader, 3, 5, false);
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        level.pendingChunkLoads.put(pending.hash, pending);

        level.unregisterChunkLoader(loader, 3, 5);

        Assertions.assertTrue(pending.invalidated);
    }

    @Test
    public void removingOneOfMultipleChunkLoadersKeepsSharedPendingLoad() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        ChunkLoader first = Mockito.mock(ChunkLoader.class);
        ChunkLoader second = Mockito.mock(ChunkLoader.class);
        Mockito.when(first.getLoaderId()).thenReturn(11);
        Mockito.when(second.getLoaderId()).thenReturn(12);
        level.registerChunkLoader(first, 3, 5, false);
        level.registerChunkLoader(second, 3, 5, false);
        Level.PendingChunkLoad pending = new Level.PendingChunkLoad(3, 5, Level.chunkHash(3, 5), this.provider);
        level.pendingChunkLoads.put(pending.hash, pending);

        level.unregisterChunkLoader(first, 3, 5);

        Assertions.assertFalse(pending.invalidated);
    }

    @Test
    public void drainAsyncChunkLoadsWaitsForRunningReads() throws Exception {
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        Mockito.when(this.provider.readChunkOffThread(1, 1)).thenAnswer(invocation -> {
            readStarted.countDown();
            releaseRead.await();
            return newChunk();
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Level level = newLevel(executor);
        try {
            Assertions.assertTrue(level.requestChunkLoadAsync(1, 1));
            Assertions.assertTrue(readStarted.await(5, TimeUnit.SECONDS), "the read task should start");

            CountDownLatch finished = new CountDownLatch(1);
            Thread drainer = new Thread(() -> {
                level.drainAsyncChunkLoads();
                finished.countDown();
            });
            drainer.start();

            // 原生读仍在跑,排空不得提前返回
            // The native read is still running; the drain must not return early
            Assertions.assertFalse(finished.await(200, TimeUnit.MILLISECONDS), "drain must wait while a read is running");

            releaseRead.countDown();
            Assertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "drain should return once the running read finishes");
        } finally {
            releaseRead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void drainAsyncChunkLoadsAbandonsAfterTimeout() throws Exception {
        CountDownLatch readStarted = new CountDownLatch(1);
        Mockito.when(this.provider.readChunkOffThread(1, 1)).thenAnswer(invocation -> {
            readStarted.countDown();
            try {
                Thread.sleep(60_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return newChunk();
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Level level = newLevel(executor);
        try {
            Assertions.assertTrue(level.requestChunkLoadAsync(1, 1));
            Assertions.assertTrue(readStarted.await(5, TimeUnit.SECONDS), "the read task should start");
            setField(level, "asyncChunkLoadDrainTimeoutMillis", 200L);

            long start = System.currentTimeMillis();
            Assertions.assertFalse(level.drainAsyncChunkLoads());
            Assertions.assertTrue(System.currentTimeMillis() - start >= 200L, "drain should respect its timeout");
        } finally {
            executor.shutdownNow();
            // 被中断的读必须真正退出，否则线程会残留到后续用例
            // The interrupted read must actually exit, or its thread leaks into later tests
            Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "the wedged read should be released");
        }
    }

    /**
     * close() 必须走排空路径：在飞上限与等待逻辑都挂在它上面，漏接等于关世界不等在飞读取。
     * <p>
     * close() must go through the drain: the in-flight wait hangs off it, so a missing call would
     * close a world without waiting for running reads.
     */
    @Test
    public void closeDrainsAsyncChunkLoads() throws Exception {
        Level level = newLevel(MoreExecutors.newDirectExecutorService());
        setField(level, "autoSave", false);
        setField(level, "providerLock", new ReentrantReadWriteLock());
        setField(level, "generators", new HashMap<>());
        Mockito.when(this.server.getLevels()).thenReturn(new HashMap<>());
        Mockito.doReturn(false).when(level).drainAsyncChunkLoads();

        level.close();

        Mockito.verify(level).drainAsyncChunkLoads();
    }

    @Test
    public void drainAsyncChunkLoadsSkipsQueuedTasksForDyingWorld() throws Exception {
        List<Runnable> tasks = new CopyOnWriteArrayList<>();
        Level level = newLevel(capturingExecutor(tasks));
        Assertions.assertTrue(level.requestChunkLoadAsync(1, 1));
        Assertions.assertTrue(level.requestChunkLoadAsync(2, 2));
        // 超时给足余量:若排空误等排队任务,elapsed 会顶满超时而失败
        // A generous timeout: if the drain wrongly waited on queued tasks,
        // the elapsed time would hit the full timeout and fail
        setField(level, "asyncChunkLoadDrainTimeoutMillis", 2_000L);

        long start = System.currentTimeMillis();
        Assertions.assertFalse(level.drainAsyncChunkLoads());
        Assertions.assertTrue(System.currentTimeMillis() - start < 2_000L,
                "queued (unstarted) tasks must not delay the drain");

        // 排队任务已被批量失效:close 后执行也不触碰 provider
        // Queued tasks were bulk-invalidated: running them post-close touches no provider
        for (Level.PendingChunkLoad pending : level.pendingChunkLoads.values()) {
            Assertions.assertTrue(pending.invalidated);
        }
        for (Runnable task : tasks) {
            task.run();
        }
        Mockito.verify(this.provider, Mockito.never()).readChunkOffThread(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void initChunkReplaysPendingBlockUpdatesOnMainThread() throws Exception {
        // 验证延迟回放:解码阶段暂存的 pendingBlockUpdates 在 initChunk()(主线程)才触发 scheduleUpdate
        // Verify deferred replay: pendingBlockUpdates stashed at decode time only fire scheduleUpdate in initChunk() (main thread)
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class, Mockito.CALLS_REAL_METHODS);
        setFieldOn(chunk, "provider", this.provider);
        setFieldOn(chunk, "isInit", false);
        setFieldOn(chunk, "changes", new java.util.concurrent.atomic.AtomicLong());

        // provider.getLevel() 返回可验证的 Level mock
        // provider.getLevel() returns a verifiable Level mock
        Level chunkLevel = Mockito.mock(Level.class);
        Mockito.lenient().when(this.provider.getLevel()).thenReturn(chunkLevel);

        cn.nukkit.block.Block block = new cn.nukkit.block.BlockStone();
        BaseFullChunk.PendingBlockUpdate update = new BaseFullChunk.PendingBlockUpdate(block, 16, 64, 32, 5, 0);
        java.util.List<BaseFullChunk.PendingBlockUpdate> updates = new java.util.ArrayList<>();
        updates.add(update);
        chunk.setPendingBlockUpdates(updates);

        chunk.initChunk();

        // scheduleUpdate 应在 initChunk 中被调用一次,证明延迟回放到主线程
        // scheduleUpdate must be called exactly once in initChunk, proving deferred replay onto the main thread
        Mockito.verify(chunkLevel, Mockito.times(1))
                .scheduleUpdate(Mockito.any(), Mockito.any(), Mockito.eq(5), Mockito.eq(0), Mockito.anyBoolean());

        // 回放后 pendingBlockUpdates 应被清空
        // pendingBlockUpdates must be cleared after replay
        Assertions.assertNull(getField(chunk, "pendingBlockUpdates"));
    }

    private static void setFieldOn(Object target, String name, Object value) throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object getField(Object target, String name) throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
