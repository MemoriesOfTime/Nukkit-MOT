package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Loaded chunks are copied for chunk garbage collection (every 100 ticks for every level) and for
 * {@code status}. A chunk hash puts {@code x ^ z} into {@code Long.hashCode}, so a square of loaded
 * chunks shares a few dozen hash codes, and Guava's {@code ImmutableMap.copyOf} used to throw, catch
 * and copy a second time on every such call.
 */
public class LevelDBLoadedChunksTest {

    private static final int RADIUS = 8;
    private static final int SQUARE = (2 * RADIUS + 1) * (2 * RADIUS + 1);

    @TempDir
    Path tempDir;

    private LevelDBProvider provider;
    private static Server configuredServer;
    private static int previousCache;
    private static boolean previousNative;

    @BeforeAll
    public static void setUpClass() {
        MockServer.init();
        configuredServer = Server.getInstance();
        previousCache = configuredServer.levelDbCache;
        previousNative = configuredServer.useNativeLevelDB;
        configuredServer.levelDbCache = 8;
        configuredServer.useNativeLevelDB = false;
    }

    @AfterAll
    static void restoreConfiguration() {
        configuredServer.levelDbCache = previousCache;
        configuredServer.useNativeLevelDB = previousNative;
    }

    @BeforeEach
    public void setUp() throws Exception {
        LevelDBProvider.generate(this.tempDir.toString(), "loaded-chunks-test", 404L, Flat.class);
        Level level = Mockito.mock(Level.class);
        Mockito.lenient().when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        Mockito.lenient().when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        Mockito.lenient().when(level.isAutoCompaction()).thenReturn(false);
        this.provider = new LevelDBProvider(level, this.tempDir.toString());
    }

    @AfterEach
    public void tearDown() {
        if (this.provider != null) {
            this.provider.close();
            this.provider = null;
        }
    }

    private void loadSquare() {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                LevelDBChunk chunk = this.provider.getEmptyChunk(x, z);
                this.provider.setChunk(x, z, chunk);
            }
        }
    }

    @Test
    public void guavaFallsBackAfterABucketOverflowForASquareOfChunkHashes() {
        // The defect this test pins down: the key set of an ordinary square of loaded chunks makes Guava
        // abandon its own table (after throwing BucketOverflowException) and copy into a JDK map instead.
        Map<Long, String> keys = new HashMap<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                keys.put(Level.chunkHash(x, z), "chunk");
            }
        }
        assertEquals("JdkBackedImmutableMap", ImmutableMap.copyOf(keys).getClass().getSimpleName());
    }

    @Test
    public void loadedChunksAreAnUnmodifiableSnapshotBuiltWithoutGuava() {
        this.loadSquare();

        Map<Long, BaseFullChunk> snapshot = this.provider.getLoadedChunks();

        assertEquals(SQUARE, snapshot.size());
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                assertSame(this.provider.getLoadedChunk(x, z), snapshot.get(Level.chunkHash(x, z)));
            }
        }
        assertFalse(snapshot.getClass().getName().startsWith("com.google.common."), snapshot.getClass().getName());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(Level.chunkHash(0, 0)));

        this.provider.setChunk(100, 100, this.provider.getEmptyChunk(100, 100));
        assertEquals(SQUARE, snapshot.size(), "a snapshot, not a live view");
        assertEquals(SQUARE + 1, this.provider.getLoadedChunkCount());
    }

    @Test
    public void loadedChunkHashesNameEveryLoadedChunkOnce() {
        this.loadSquare();

        long[] hashes = this.provider.getLoadedChunkHashes();

        assertEquals(SQUARE, hashes.length);
        LongOpenHashSet unique = new LongOpenHashSet(hashes);
        assertEquals(SQUARE, unique.size());
        for (Long hash : this.provider.getLoadedChunks().keySet()) {
            assertEquals(true, unique.contains(hash.longValue()));
        }
    }

    @Test
    public void scalarLoadedCountDoesNotAllocateAChunkValueSnapshot() {
        this.loadSquare();
        java.lang.management.ThreadMXBean platform = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(platform instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) platform;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        boolean enabled = allocation.isThreadAllocatedMemoryEnabled();
        allocation.setThreadAllocatedMemoryEnabled(true);
        try {
            int checksum = 0;
            for (int i = 0; i < 5000; i++) checksum += provider.getLoadedChunkCount();
            long thread = Thread.currentThread().getId();
            long before = allocation.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 1000; i++) checksum += provider.getLoadedChunkCount();
            long bytes = allocation.getThreadAllocatedBytes(thread) - before;
            assertEquals(6000 * SQUARE, checksum);
            org.junit.jupiter.api.Assertions.assertTrue(bytes < 64_000,
                    "scalar count allocated " + bytes + " bytes for1000calls; it must not copy chunk values");
        } finally {
            allocation.setThreadAllocatedMemoryEnabled(enabled);
        }
    }

}
