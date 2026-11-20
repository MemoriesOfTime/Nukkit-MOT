package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Binary;
import org.iq80.leveldb.DB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LevelDBStartupAuditTest {
    @TempDir Path directory;

    @BeforeAll static void setup() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
    }

    @Test void failedPrimaryReadDoesNotDestroyTheOnlyReadableMetadata() throws Exception {
        LevelDBProvider.generate(directory.toString(), "startup-recovery", 42L, Flat.class);
        byte[] good = Files.readAllBytes(directory.resolve("level.dat"));
        Files.write(directory.resolve("level.dat.bak"), good);
        Files.write(directory.resolve("level.dat"), new byte[]{0, 1, 2});
        LevelDBProvider first = new LevelDBProvider(mock(Level.class), directory.toString());
        try {
            assertArrayEquals(good, Files.readAllBytes(directory.resolve("level.dat.bak")));
        } finally { first.close(); }
        // Simulate an interrupted start before any level.dat save, then try the same recovery again.
        LevelDBProvider second = new LevelDBProvider(mock(Level.class), directory.toString());
        second.close();
    }

    @Test void successfulEmptyMigrationIsScannedOnlyOnce() throws Exception {
        LevelDBProvider.generate(directory.toString(), "startup-scan", 42L, Flat.class);
        CompoundTag data;
        try (InputStream in = Files.newInputStream(directory.resolve("level.dat"))) {
            assertEquals(8, in.skip(8));
            data = NBTIO.read(in, ByteOrder.LITTLE_ENDIAN);
        }
        data.putInt("StorageVersion", 8);
        byte[] encoded = NBTIO.write(data, ByteOrder.LITTLE_ENDIAN);
        try (var out = Files.newOutputStream(directory.resolve("level.dat"))) {
            out.write(Binary.writeLInt(8)); out.write(Binary.writeLInt(encoded.length)); out.write(encoded);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            AtomicReference<DB> database = new AtomicReference<>();
            PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> {
                DB db = spy(LevelDBProvider.openDB(path.toFile())); database.set(db); return new PreparedLevelDB.OpenedDatabase(db, data);
            });
            CompletableFuture.runAsync(() -> {
                try { token.open(); } catch (Exception e) { throw new RuntimeException(e); }
            }).get(10, TimeUnit.SECONDS);
            LevelDBProvider provider = token.activate(() -> new LevelDBProvider(mock(Level.class), directory.toString()));
            token.commit();
            try {
                verify(database.get(), times(attempt == 0 ? 2 : 0)).iterator();
                System.out.println("startup-audit migration open=" + (attempt + 1)
                        + " iterators=" + (attempt == 0 ? 2 : 0));
            } finally { provider.close(); }
        }
    }
    @Test void activationUsesPreparedMetadataEvenWhenSourceFilesAreNoLongerReadable() throws Exception {
        LevelDBProvider.generate(directory.toString(), "prepared-metadata", 42L, Flat.class);
        PreparedLevelDB token = PreparedLevelDB.reserve(directory);
        CompletableFuture.runAsync(() -> {
            try { token.open(); } catch (Exception e) { throw new RuntimeException(e); }
        }).get(10, TimeUnit.SECONDS);
        Files.delete(directory.resolve("level.dat"));
        Files.delete(directory.resolve("level.dat.bak"));
        LevelDBProvider provider;
        try {
            provider = token.activate(() -> new LevelDBProvider(mock(Level.class), directory.toString()));
            token.commit();
        } catch (Throwable failure) {
            token.cancel().toCompletableFuture().get(10, TimeUnit.SECONDS);
            token.release();
            throw failure;
        }
        try {
            assertEquals("prepared-metadata", provider.getName());
            assertFalse(Files.exists(directory.resolve("level.dat")));
            assertFalse(Files.exists(directory.resolve("level.dat.bak")));
        } finally { provider.close(); }
    }

    @Test void invalidMetadataFailsBeforeOpeningDatabaseAndReservationCanBeReleased() throws Exception {
        Files.write(directory.resolve("level.dat"), new byte[]{0, 1, 2});
        PreparedLevelDB token = PreparedLevelDB.reserve(directory);
        var opening = CompletableFuture.runAsync(() -> {
            try { token.open(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertThrows(java.util.concurrent.ExecutionException.class, () -> opening.get(10, TimeUnit.SECONDS));
        assertFalse(Files.exists(directory.resolve("db")));
        assertThrows(IllegalStateException.class, () -> token.activate(() -> null));
        token.cancel().toCompletableFuture().get(10, TimeUnit.SECONDS);
        token.release();
        assertNull(PreparedLevelDB.forConstructor(directory.toString()));
    }

}
