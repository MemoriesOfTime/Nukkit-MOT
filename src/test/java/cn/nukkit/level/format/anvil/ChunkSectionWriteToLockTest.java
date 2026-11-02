package cn.nukkit.level.format.anvil;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Anvil {@code writeTo} 的 section 读锁契约：writeLock 持有期间序列化必须阻塞（异步子区块
 * 路径的原子快照边界——方块编辑只持写锁，列表监视器不排斥它），释放后完成。
 * <p>
 * The section read-lock contract of Anvil {@code writeTo}: serialization must block
 * while the write lock is held (the atomic-snapshot boundary for the async sub-chunk
 * path — block edits hold only the write lock; the list monitor does not exclude them)
 * and complete once it is released.
 */
class ChunkSectionWriteToLockTest {

    private static Server originalServer;

    @BeforeAll
    static void initServer() throws Exception {
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        originalServer = (Server) instanceField.get(null);
        instanceField.set(null, mock(Server.class));
    }

    @AfterAll
    static void restoreServer() throws Exception {
        Field instanceField = Server.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, originalServer);
    }

    @Test
    void writeToBlocksWhileWriteLockHeldAndCompletesAfterRelease() throws Exception {
        ChunkSection section = new ChunkSection(0);
        section.setBlockId(1, 1, 1, BlockID.STONE);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread serializer = new Thread(() -> {
            try {
                section.writeTo(GameVersion.V1_19_80, new BinaryStream(), false);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        section.sectionLock.writeLock().lock();
        try {
            serializer.start();
            assertFalse(done.await(200, TimeUnit.MILLISECONDS),
                    "writeTo must block while the section write lock is held");
        } finally {
            section.sectionLock.writeLock().unlock();
        }

        assertTrue(done.await(2, TimeUnit.SECONDS), "writeTo must complete after the write lock is released");
        serializer.join(2000);
        assertNull(failure.get());
    }
}
