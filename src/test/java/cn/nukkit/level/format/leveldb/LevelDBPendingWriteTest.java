package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 区块挂起写槽位协议测试:卸载异步落盘、读取认领、批次替换全序、失败重试、close 排空与背压
 * <p>
 * Pending-write slot protocol tests: async unload persistence, claim-on-load, batch supersession ordering,
 * write retry, close-time drain and backpressure
 *
 * @author LT_Name
 */
public class LevelDBPendingWriteTest {

    private static final int BLOCK_A = 1;  // stone
    private static final int BLOCK_B = 3;  // dirt

    @TempDir
    Path tempDir;

    private Level level;
    private LevelDBProvider provider;

    @BeforeAll
    public static void setUpClass() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
    }

    @BeforeEach
    public void setUp() throws Exception {
        Server.getInstance().asyncChunkSending = true;
        Server.getInstance().maxPendingChunkWrites = 128;

        LevelDBProvider.generate(this.tempDir.toString(), "pending-write-test", 404L, Flat.class);

        this.level = Mockito.mock(Level.class);
        Mockito.lenient().when(this.level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        Mockito.lenient().when(this.level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        Mockito.lenient().when(this.level.isAutoCompaction()).thenReturn(false);
        Mockito.lenient().when(this.level.getCurrentTick()).thenReturn(0L);

        this.provider = new LevelDBProvider(this.level, this.tempDir.toString());
    }

    @AfterEach
    public void tearDown() {
        Server.getInstance().asyncChunkSending = false;
        Server.getInstance().maxPendingChunkWrites = 128;
        if (this.provider != null) {
            this.provider.close();
            this.provider = null;
        }
    }

    private LevelDBChunk newDirtyChunk(int x, int z, int blockId) {
        LevelDBChunk chunk = this.provider.getEmptyChunk(x, z);
        chunk.setGenerated(true);
        this.provider.setChunk(x, z, chunk);
        chunk.setBlock(0, 64, 0, blockId);
        Assertions.assertTrue(chunk.hasChanged());
        return chunk;
    }

    private ExecutorService executor() throws Exception {
        Field field = LevelDBProvider.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(this.provider);
    }

    /**
     * 占住单线程 executor,模拟 compaction/写延迟导致挂起写迟迟不落盘
     * <p>
     * Occupy the single-thread executor, simulating compaction/write latency delaying pending writes
     */
    private CountDownLatch pauseExecutor() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(1);
        this.executor().execute(() -> {
            started.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Assertions.assertTrue(started.await(5, TimeUnit.SECONDS), "executor pause task should start");
        return latch;
    }

    private void awaitLockQueued(ReentrantLock lock, Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!lock.hasQueuedThread(thread) && System.nanoTime() < deadline) {
            Thread.yield();
        }
        Assertions.assertTrue(lock.hasQueuedThread(thread), "reader should have claimed the stale slot reference");
    }

    private void drainExecutor() throws Exception {
        CompletableFuture.runAsync(() -> {
        }, this.executor()).get(10, TimeUnit.SECONDS);
    }

    private int readBlockFromDisk(int x, int z) {
        LevelDBChunk chunk = this.provider.readChunk(x, z);
        Assertions.assertNotNull(chunk, "chunk should exist on disk");
        return chunk.getBlockId(0, 64, 0);
    }

    @Test
    public void unloadDirtyChunkPersistsWithoutBlockingAndDrains() throws Exception {
        this.newDirtyChunk(5, 7, BLOCK_A);

        Assertions.assertTrue(this.provider.unloadChunk(5, 7, false));
        Assertions.assertFalse(this.provider.isChunkLoaded(5, 7));

        this.drainExecutor();
        Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        Assertions.assertEquals(BLOCK_A, this.readBlockFromDisk(5, 7));
    }

    @Test
    public void reloadBeforeWriteLandsClaimsPendingWrite() throws Exception {
        this.newDirtyChunk(9, 2, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        try {
            Assertions.assertTrue(this.provider.unloadChunk(9, 2, false));
            Assertions.assertEquals(1, this.provider.getPendingWriteCount());

            // 写尚未落盘即同步重载:读取入口认领槽位、inline 落盘,必须读到卸载前的数据
            // Reload before the write lands: the read entry claims the slot, commits inline, and must see pre-unload data
            BaseFullChunk reloaded = this.provider.getChunk(9, 2);
            Assertions.assertNotNull(reloaded);
            Assertions.assertEquals(BLOCK_A, reloaded.getBlockId(0, 64, 0));
            Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        } finally {
            release.countDown();
        }
        this.drainExecutor();
    }

    @Test
    public void claimDoesNotReadBeforeAConcurrentReplacementBatch() throws Exception {
        int chunkX = 14;
        int chunkZ = 4;
        this.newDirtyChunk(chunkX, chunkZ, BLOCK_A);
        this.provider.saveChunkFuture(chunkX, chunkZ, this.provider.getChunk(chunkX, chunkZ)).get(10, TimeUnit.SECONDS);

        long hash = Level.chunkHash(chunkX, chunkZ);
        LevelDBProvider.PendingWrite stale = new LevelDBProvider.PendingWrite();
        ConcurrentHashMap<Long, LevelDBProvider.PendingWrite> pendingWrites = this.pendingWrites();
        pendingWrites.put(hash, stale);

        CountDownLatch release = this.pauseExecutor();
        AtomicReference<BaseFullChunk> loaded = new AtomicReference<>();
        Thread reader = new Thread(() -> loaded.set(this.provider.readChunkOffThread(chunkX, chunkZ)), "stale-claim-reader");
        try {
            stale.lock.lock();
            reader.start();
            try {
                this.awaitLockQueued(stale.lock, reader);
                Assertions.assertTrue(pendingWrites.remove(hash, stale));

                LevelDBChunk replacement = this.provider.getEmptyChunk(chunkX, chunkZ);
                replacement.setGenerated(true);
                replacement.setBlock(0, 64, 0, BLOCK_B);
                this.provider.saveChunkFuture(chunkX, chunkZ, replacement);
            } finally {
                stale.lock.unlock();
            }

            reader.join(10_000);
            Assertions.assertFalse(reader.isAlive(), "reader should finish");
            Assertions.assertNotNull(loaded.get());
            Assertions.assertEquals(BLOCK_B, loaded.get().getBlockId(0, 64, 0),
                    "claim must commit a replacement batch before reading the database");
        } finally {
            release.countDown();
            reader.join(10_000);
            this.drainExecutor();
        }
    }

    @Test
    public void newerSaveSupersedesOlderPendingBatch() throws Exception {
        LevelDBChunk chunk = this.newDirtyChunk(3, 3, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        try {
            this.provider.saveChunk(3, 3, chunk);
            chunk.setBlock(0, 64, 0, BLOCK_B);
            this.provider.saveChunk(3, 3, chunk);
            Assertions.assertEquals(1, this.provider.getPendingWriteCount());
        } finally {
            release.countDown();
        }
        this.drainExecutor();

        Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        Assertions.assertEquals(BLOCK_B, this.readBlockFromDisk(3, 3));
    }

    @Test
    public void syncSaveSupersedesPendingAsyncBatchAndOldBatchNeverLands() throws Exception {
        LevelDBChunk chunk = this.newDirtyChunk(4, 8, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        try {
            this.provider.saveChunk(4, 8, chunk);
            chunk.setBlock(0, 64, 0, BLOCK_B);
            // 同步保存接管槽位 inline 落盘;稍后运行的旧异步任务必须空转,不得用旧内容覆盖(回滚缺陷回归)
            // The sync save takes over the slot and commits inline; the older async task must no-op instead of overwriting with stale content (rollback-defect regression)
            this.provider.saveChunkSync(4, 8, chunk);
            Assertions.assertEquals(0, this.provider.getPendingWriteCount());
            Assertions.assertEquals(BLOCK_B, this.readBlockFromDisk(4, 8));
        } finally {
            release.countDown();
        }
        this.drainExecutor();
        Assertions.assertEquals(BLOCK_B, this.readBlockFromDisk(4, 8));
    }

    @Test
    public void unloadRestagesWhenPendingBatchIsStale() throws Exception {
        LevelDBChunk chunk = this.newDirtyChunk(6, 1, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        try {
            this.provider.saveChunk(6, 1, chunk);
            // 快照过期:入槽后区块又被修改,卸载必须重新序列化,否则改动丢失
            // Stale snapshot: the chunk changed after staging, so unload must re-serialize or the change is lost
            chunk.setBlock(0, 64, 0, BLOCK_B);
            Assertions.assertTrue(this.provider.unloadChunk(6, 1, false));
        } finally {
            release.countDown();
        }
        this.drainExecutor();
        Assertions.assertEquals(BLOCK_B, this.readBlockFromDisk(6, 1));
    }

    @Test
    public void unloadSkipsRestagingWhenPendingBatchIsCurrent() throws Exception {
        LevelDBChunk chunk = this.newDirtyChunk(2, 6, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        try {
            this.provider.saveChunk(2, 6, chunk);
            // 快照仍最新:卸载复用已序列化批次(不重复序列化),数据仍须完整落盘
            // Snapshot still current: unload reuses the staged batch (no re-serialization); data must still land intact
            Assertions.assertTrue(this.provider.unloadChunk(2, 6, false));
            Assertions.assertEquals(1, this.provider.getPendingWriteCount());
        } finally {
            release.countDown();
        }
        this.drainExecutor();
        Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        Assertions.assertEquals(BLOCK_A, this.readBlockFromDisk(2, 6));
    }

    @Test
    public void closeFlushesPendingWritesBeforeClosingDatabase() throws Exception {
        this.newDirtyChunk(1, 4, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        Assertions.assertTrue(this.provider.unloadChunk(1, 4, false));
        Assertions.assertEquals(1, this.provider.getPendingWriteCount());
        release.countDown();

        this.provider.close();
        Assertions.assertEquals(0, this.provider.getPendingWriteCount());

        // 重开数据库验证挂起写在关库前已全部落盘
        // Reopen the database to verify the pending write landed before the db closed
        this.provider = new LevelDBProvider(this.level, this.tempDir.toString());
        Assertions.assertEquals(BLOCK_A, this.readBlockFromDisk(1, 4));
    }

    @Test
    public void writeFailureRetriesUntilSuccess() throws Exception {
        Field dbField = LevelDBProvider.class.getDeclaredField("db");
        dbField.setAccessible(true);
        DB realDb = (DB) dbField.get(this.provider);
        AtomicInteger failuresLeft = new AtomicInteger(1);
        DB flakyDb = Mockito.mock(DB.class, AdditionalAnswers.delegatesTo(realDb));
        Mockito.doAnswer(invocation -> {
            if (failuresLeft.getAndDecrement() > 0) {
                throw new RuntimeException("injected write failure");
            }
            realDb.write(invocation.getArgument(0, WriteBatch.class));
            return null;
        }).when(flakyDb).write(Mockito.any(WriteBatch.class));
        dbField.set(this.provider, flakyDb);

        try {
            this.newDirtyChunk(8, 8, BLOCK_A);
            Assertions.assertTrue(this.provider.unloadChunk(8, 8, false));
            // 重试任务在失败的 commit 内重新入队,单次 fence 追不上,循环排空至槽位清零
            // The retry task re-enqueues from inside the failed commit, so one fence isn't enough; drain in a loop until the slot clears
            long deadline = System.currentTimeMillis() + 10_000;
            while (this.provider.getPendingWriteCount() > 0 && System.currentTimeMillis() < deadline) {
                this.drainExecutor();
            }

            Assertions.assertEquals(0, this.provider.getPendingWriteCount());
            Assertions.assertEquals(BLOCK_A, this.readBlockFromDisk(8, 8));
        } finally {
            dbField.set(this.provider, realDb);
        }
    }

    @Test
    public void saveChunkFutureCompletionMeansDurable() throws Exception {
        LevelDBChunk chunk = this.newDirtyChunk(7, 5, BLOCK_B);

        this.provider.saveChunkFuture(7, 5, chunk).get(10, TimeUnit.SECONDS);

        Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        Assertions.assertEquals(BLOCK_B, this.readBlockFromDisk(7, 5));
        Assertions.assertFalse(chunk.hasChanged(), "changes should be cleared after the write lands");
    }

    @Test
    public void backpressureFlagFollowsPendingWriteCount() throws Exception {
        Server.getInstance().maxPendingChunkWrites = 1;
        this.newDirtyChunk(0, 9, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        try {
            Assertions.assertFalse(this.provider.isChunkSaveBacklogged());
            Assertions.assertTrue(this.provider.unloadChunk(0, 9, false));
            Assertions.assertTrue(this.provider.isChunkSaveBacklogged());
        } finally {
            release.countDown();
        }
        this.drainExecutor();
        Assertions.assertFalse(this.provider.isChunkSaveBacklogged());
    }

    @Test
    public void asyncSavingDisabledFallsBackToSynchronousUnloadSave() throws Exception {
        Server.getInstance().asyncChunkSending = false;
        this.newDirtyChunk(11, 3, BLOCK_B);

        Assertions.assertTrue(this.provider.unloadChunk(11, 3, false));

        // 回退路径同步落盘:无需排空 executor 即可读到
        // Fallback path writes synchronously: readable without draining the executor
        Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        Assertions.assertEquals(BLOCK_B, this.readBlockFromDisk(11, 3));
    }

    @Test
    public void closeFlushesPendingWritesWhenDrainTimesOut() throws Exception {
        this.newDirtyChunk(12, 5, BLOCK_A);
        // 占住 executor 模拟在跑的 AutoCompaction(与挂起写共用单线程 executor,close 不打断在跑任务)
        // Occupy the executor to simulate an in-flight AutoCompaction (shares the single-thread executor; close doesn't interrupt running tasks)
        CountDownLatch release = this.pauseExecutor();
        try {
            Assertions.assertTrue(this.provider.unloadChunk(12, 5, false));
            Assertions.assertEquals(1, this.provider.getPendingWriteCount());

            // 排空超时进入清扫分支:槽内批次是已卸载区块数据的唯一副本,须在关库前 inline 落盘而非丢弃
            // Drain times out into the sweep branch: the staged batch is the only copy of the unloaded chunk's data and must be committed inline before the db closes, not dropped
            this.provider.closeDrainTimeoutMillis = 100;
            this.provider.close();
            Assertions.assertEquals(0, this.provider.getPendingWriteCount());
        } finally {
            release.countDown();
        }

        this.provider = new LevelDBProvider(this.level, this.tempDir.toString());
        Assertions.assertEquals(BLOCK_A, this.readBlockFromDisk(12, 5));
    }

    @Test
    public void drainTimeoutSweepSkipsSlotHeldByWedgedWriter() throws Exception {
        this.newDirtyChunk(13, 6, BLOCK_A);
        CountDownLatch release = this.pauseExecutor();
        Assertions.assertTrue(this.provider.unloadChunk(13, 6, false));

        // 辅助线程持住槽锁,模拟残余线程卡在不可中断写中;清扫须 tryLock 超时跳过而非无限等待
        // A helper thread holds the slot lock, simulating a leftover thread wedged in an uninterruptible write; the sweep must skip via tryLock timeout instead of waiting forever
        LevelDBProvider.PendingWrite pw = this.pendingWrites().get(Level.chunkHash(13, 6));
        Assertions.assertNotNull(pw);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch releaseSlotLock = new CountDownLatch(1);
        Thread wedgedWriter = new Thread(() -> {
            pw.lock.lock();
            try {
                held.countDown();
                releaseSlotLock.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pw.lock.unlock();
            }
        }, "wedged-writer");
        wedgedWriter.start();
        Assertions.assertTrue(held.await(5, TimeUnit.SECONDS));

        try {
            this.provider.closeDrainTimeoutMillis = 100;
            this.provider.closeSweepLockTimeoutMillis = 200;
            long start = System.currentTimeMillis();
            this.provider.close();
            Assertions.assertTrue(System.currentTimeMillis() - start < 30_000, "close must return bounded");
            // 被持有的槽放弃保留(数据注定丢失,批次由 GC 兜底)
            // The held slot is abandoned in place (its data is already lost; GC reclaims the batch)
            Assertions.assertEquals(1, this.provider.getPendingWriteCount());
        } finally {
            releaseSlotLock.countDown();
            release.countDown();
            wedgedWriter.join(5_000);
        }

        this.provider = new LevelDBProvider(this.level, this.tempDir.toString());
        Assertions.assertNull(this.provider.readChunk(13, 6), "abandoned batch must never have landed");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Long, LevelDBProvider.PendingWrite> pendingWrites() throws Exception {
        Field field = LevelDBProvider.class.getDeclaredField("pendingWrites");
        field.setAccessible(true);
        return (ConcurrentHashMap<Long, LevelDBProvider.PendingWrite>) field.get(this.provider);
    }
}
