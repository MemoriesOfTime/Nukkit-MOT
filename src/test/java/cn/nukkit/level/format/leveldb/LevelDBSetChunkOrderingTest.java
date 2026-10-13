package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import org.iq80.leveldb.DB;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * setChunk 替换顺序与挂起写身份测试：前驱先持久化再发布替换块（写槽不得回退一代）、
 * 槽内 batch 只按 chunk 对象身份复用（changes 计数器碰撞不得静默跳存）。读回沿用 LevelDBPendingWriteTest 的模式。
 * <p>
 * Replacement-ordering and pending-write identity: persist the predecessor before publishing the
 * replacement; reuse a staged batch only by chunk identity. Read-backs follow LevelDBPendingWriteTest's pattern.
 */
public class LevelDBSetChunkOrderingTest {

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

        LevelDBProvider.generate(this.tempDir.toString(), "setchunk-ordering-test", 404L, Flat.class);

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
        if (this.provider != null) {
            this.provider.close();
            this.provider = null;
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Long, LevelDBProvider.PendingWrite> pendingWrites() throws Exception {
        Field field = LevelDBProvider.class.getDeclaredField("pendingWrites");
        field.setAccessible(true);
        return (ConcurrentHashMap<Long, LevelDBProvider.PendingWrite>) field.get(this.provider);
    }

    private ExecutorService executor() throws Exception {
        Field field = LevelDBProvider.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(this.provider);
    }

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

    @Test
    public void setChunkPersistsPredecessorBeforePublishingReplacement() throws Exception {
        Server.getInstance().asyncChunkSending = false;
        int x = 61, z = 62;

        LevelDBChunk predecessor = this.provider.getEmptyChunk(x, z);
        predecessor.setGenerated(true);
        this.provider.setChunk(x, z, predecessor);
        predecessor.setBlock(0, 64, 0, BLOCK_A);
        Assertions.assertTrue(predecessor.hasChanged());

        LevelDBChunk replacement = this.provider.getEmptyChunk(x, z);
        replacement.setGenerated(true);
        replacement.setBlock(0, 64, 0, BLOCK_B);

        // 拦截同步落盘，记录落盘瞬间 map 中发布的仍是前驱（先持久化、后发布）
        // Intercept the sync write: the predecessor must still be published while being persisted
        AtomicReference<BaseFullChunk> seenAtSave = new AtomicReference<>();
        LevelDBProvider spied = Mockito.spy(this.provider);
        Mockito.doAnswer(invocation -> {
            seenAtSave.set(this.provider.getChunk(x, z));
            return invocation.callRealMethod();
        }).when(spied).saveChunkSync(Mockito.eq(x), Mockito.eq(z), Mockito.any());

        spied.setChunk(x, z, replacement);

        Assertions.assertSame(predecessor, seenAtSave.get(),
                "the predecessor must still be published in the map while its final state is persisted");
        Assertions.assertSame(replacement, this.provider.getChunk(x, z));
    }

    @Test
    public void unloadNeverReusesABatchStagedFromADifferentChunkObject() throws Exception {
        int x = 63, z = 64;
        long index = Level.chunkHash(x, z);

        // map 中的块与前代对象 foreign 构造出相等的 changes 计数器（测试前提）
        // The in-map chunk and the foreign object deliberately get equal changes counters
        LevelDBChunk inMap = this.provider.getEmptyChunk(x, z);
        inMap.setGenerated(true);
        this.provider.setChunk(x, z, inMap);
        inMap.setBlock(0, 64, 0, BLOCK_A);
        inMap.setBlock(0, 65, 0, BLOCK_A);

        LevelDBChunk foreign = this.provider.getEmptyChunk(x, z);
        foreign.setGenerated(true);
        foreign.setBlock(0, 64, 0, BLOCK_B);
        foreign.setBlock(0, 65, 0, BLOCK_B);
        Assertions.assertEquals(inMap.getChanges(), foreign.getChanges(),
                "test premise: equal changes counters on different chunk objects");

        CountDownLatch release = this.pauseExecutor();
        try {
            // 手工挂一个"来自 foreign"的未提交 batch 到同坐标槽位；暂停执行器防止后台提交
            // Hand-stage an uncommitted "foreign" batch at the same coordinate; the paused executor keeps it uncommitted
            Field dbField = LevelDBProvider.class.getDeclaredField("db");
            dbField.setAccessible(true);
            DB db = (DB) dbField.get(this.provider);
            LevelDBProvider.PendingWrite pw = new LevelDBProvider.PendingWrite();
            pw.batch = db.createWriteBatch();
            pw.changeSnapshot = foreign.getChanges();
            pw.chunkRef = foreign;
            this.pendingWrites().put(index, pw);

            Assertions.assertTrue(this.provider.unloadChunk(x, z));

            // 值匹配但身份不匹配：inMap 必须重新 stage（旧逻辑仅比对计数器会静默跳存，读回空/旧内容）
            // Value matches but identity does not: inMap must be re-staged, or a counter-only check silently skips
            BaseFullChunk reloaded = this.provider.getChunk(x, z);
            Assertions.assertNotNull(reloaded);
            Assertions.assertEquals(BLOCK_A, reloaded.getBlockId(0, 64, 0),
                    "the unloaded chunk's own block must be durable, not the foreign batch's content");
        } finally {
            release.countDown();
        }
    }
}
