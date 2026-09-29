package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** level.dat is serialized by the caller and written by the provider's writer thread, atomically. */
public class LevelDBLevelDatWriteTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void setUpClass() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
    }

    private static long storedTime(Path dir) throws Exception {
        try (InputStream in = Files.newInputStream(dir.resolve("level.dat"))) {
            assertEquals(8, in.skip(8));
            CompoundTag tag = NBTIO.read(in, ByteOrder.LITTLE_ENDIAN);
            return tag.getLong("Time");
        }
    }

    @Test
    public void saveReturnsBeforeTheWriteAndCloseDrainsIt() throws Exception {
        LevelDBProvider.generate(this.tempDir.toString(), "level-dat-write", 3L, Flat.class);
        Level level = Mockito.mock(Level.class);
        Mockito.lenient().when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        Mockito.lenient().when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        LevelDBProvider provider = new LevelDBProvider(level, this.tempDir.toString());

        Field executorField = LevelDBProvider.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(provider);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch busy = new CountDownLatch(1);
        executor.execute(() -> {
            busy.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        assertTrue(busy.await(5, TimeUnit.SECONDS));

        long before = storedTime(this.tempDir);
        provider.setTime(before + 12345);
        provider.saveLevelData();
        // The writer is busy: the caller returned without touching the file.
        assertEquals(before, storedTime(this.tempDir));

        release.countDown();
        provider.close();
        assertEquals(before + 12345, storedTime(this.tempDir));
        assertFalse(Files.exists(this.tempDir.resolve("level.dat.tmp")));
    }
}
