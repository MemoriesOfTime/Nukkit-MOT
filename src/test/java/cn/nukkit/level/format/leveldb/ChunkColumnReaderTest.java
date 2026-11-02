package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import net.daporkchop.ldbjni.LevelDB;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chunk IO must not use point lookups: LevelDB charges them as seeks and turns chunk loading into
 * back-to-back seek-triggered compactions. Reads through {@link ChunkColumnReader} must still return
 * exactly what {@link DB#get} returns.
 */
public class ChunkColumnReaderTest {

    private static final int X = 5;
    private static final int Z = -3;

    @TempDir
    Path tempDir;

    private LevelDBProvider provider;

    @BeforeAll
    public static void setUpClass() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
    }

    @AfterEach
    public void tearDown() {
        if (this.provider != null) {
            this.provider.close();
            this.provider = null;
        }
    }

    @Test
    public void javaDatabaseAnswersExactlyLikePointLookups() throws Exception {
        try (DB db = Iq80DBFactory.factory.open(this.tempDir.resolve("java").toFile(), new Options().createIfMissing(true))) {
            assertSameAsPointLookups(db);
        }
    }

    @Test
    public void nativeDatabaseAnswersExactlyLikePointLookups() throws Exception {
        Assumptions.assumeTrue(LevelDB.PROVIDER.isNative(), "native LevelDB is not available on this platform");
        File dir = this.tempDir.resolve("native").toFile();
        try (DB db = LevelDB.PROVIDER.open(dir, new Options().createIfMissing(true))) {
            assertSameAsPointLookups(db);
        }
    }

    private static void assertSameAsPointLookups(DB db) {
        List<byte[]> keys = new ArrayList<>();
        // The column under test, its byte-order neighbours and the same x/z in other dimensions.
        for (int[] column : new int[][]{{X, Z}, {X + 1, Z}, {X, Z + 1}, {X + 256, Z}, {X - 256, Z}, {-X, -Z}}) {
            for (int dimension : new int[]{0, 1, 2}) {
                keys.add(LevelDBKey.VERSION.getKey(column[0], column[1], dimension));
                keys.add(LevelDBKey.STATE_FINALIZATION.getKey(column[0], column[1], dimension));
                keys.add(LevelDBKey.DATA_3D.getKey(column[0], column[1], dimension));
                keys.add(LevelDBKey.BLOCK_ENTITIES.getKey(column[0], column[1], dimension));
                keys.add(LevelDBKey.NUKKIT_CONN_FIX_DONE.getKey(column[0], column[1], dimension));
                for (int y = -4; y < 20; y++) {
                    keys.add(LevelDBKey.SUB_CHUNK_PREFIX.getKey(column[0], column[1], y, dimension));
                }
                keys.add(LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, column[0], column[1], dimension));
            }
        }
        keys.add("BiomeData".getBytes(StandardCharsets.UTF_8));
        keys.add(LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, new byte[]{0, 0, 0, 1, 0, 0, 0, 7}));
        keys.add(LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, new byte[]{0, 0, 0, 1, 0, 0, 0, 8}));

        // Store every third key so that each group mixes present and missing keys.
        for (int i = 0; i < keys.size(); i += 3) {
            db.put(keys.get(i), ("value-" + i).getBytes(StandardCharsets.UTF_8));
        }

        for (int dimension : new int[]{0, 1, 2}) {
            try (ChunkColumnReader reader = ChunkColumnReader.column(db, X, Z, dimension)) {
                for (byte[] key : keys) {
                    Assertions.assertArrayEquals(db.get(key), reader.get(key), "dimension " + dimension);
                }
            }
        }
        try (ChunkColumnReader reader = ChunkColumnReader.pointReads(db)) {
            for (byte[] key : keys) {
                Assertions.assertArrayEquals(db.get(key), reader.get(key));
            }
        }
    }

    @Test
    public void writesThroughTheReaderAreSeenByLaterReads() throws Exception {
        try (DB db = Iq80DBFactory.factory.open(this.tempDir.resolve("writes").toFile(), new Options().createIfMissing(true));
             ChunkColumnReader reader = ChunkColumnReader.column(db, X, Z, 0)) {
            byte[] version = LevelDBKey.VERSION.getKey(X, Z, 0);
            byte[] digp = LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, X, Z, 0);
            Assertions.assertNull(reader.get(version));
            Assertions.assertNull(reader.get(digp));
            reader.put(version, new byte[]{41});
            reader.put(digp, new byte[]{1});
            Assertions.assertArrayEquals(new byte[]{41}, reader.get(version));
            Assertions.assertArrayEquals(new byte[]{1}, reader.get(digp));
            reader.delete(version);
            Assertions.assertNull(reader.get(version));
            Assertions.assertNull(db.get(version));
        }
    }

    @Test
    public void chunkLoadsAndSavesNeverUsePointLookups() throws Exception {
        LevelDBProvider.generate(this.tempDir.toString(), "column-read-test", 404L, Flat.class);
        Level level = Mockito.mock(Level.class);
        Mockito.lenient().when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        Mockito.lenient().when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        Mockito.lenient().when(level.isAutoCompaction()).thenReturn(false);
        Mockito.lenient().when(level.getCurrentTick()).thenReturn(0L);
        this.provider = new LevelDBProvider(level, this.tempDir.toString());

        LevelDBChunk chunk = this.provider.getEmptyChunk(X, Z);
        chunk.setGenerated(true);
        this.provider.setChunk(X, Z, chunk);
        chunk.setBlock(0, 64, 0, 1);
        chunk.setBlock(15, -60, 15, 3);
        this.provider.saveChunkFuture(X, Z, chunk).get(10, TimeUnit.SECONDS);

        Field field = LevelDBProvider.class.getDeclaredField("db");
        field.setAccessible(true);
        DB realDb = (DB) field.get(this.provider);
        DB counted = Mockito.mock(DB.class, AdditionalAnswers.delegatesTo(realDb));
        field.set(this.provider, counted);
        try {
            LevelDBChunk read = this.provider.readChunk(X, Z);
            Assertions.assertNotNull(read);
            Assertions.assertEquals(1, read.getBlockId(0, 64, 0));
            Assertions.assertEquals(3, read.getBlockId(15, -60, 15));
            Assertions.assertNull(this.provider.readChunk(X + 1, Z), "missing column stays missing");

            BaseFullChunk deferred = this.provider.readChunkOffThread(X, Z);
            Assertions.assertNotNull(deferred);
            Assertions.assertEquals(3, deferred.getBlockId(15, -60, 15));

            chunk.setBlock(1, 64, 1, 3);
            this.provider.saveChunkFuture(X, Z, chunk).get(10, TimeUnit.SECONDS);

            Mockito.verify(counted, Mockito.never()).get(Mockito.any(byte[].class));
            Mockito.verify(counted, Mockito.never()).get(Mockito.any(byte[].class), Mockito.any(ReadOptions.class));
        } finally {
            field.set(this.provider, realDb);
        }
        Assertions.assertEquals(3, this.provider.readChunk(X, Z).getBlockId(1, 64, 1));
    }
}
