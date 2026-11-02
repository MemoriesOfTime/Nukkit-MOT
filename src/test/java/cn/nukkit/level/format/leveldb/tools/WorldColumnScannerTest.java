package cn.nukkit.level.format.leveldb.tools;

import cn.nukkit.level.format.leveldb.LevelDBKey;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.env.WritableFile;
import org.iq80.leveldb.impl.FileMetaData;
import org.iq80.leveldb.impl.InternalKey;
import org.iq80.leveldb.impl.InternalKeyComparator;
import org.iq80.leveldb.impl.InternalUserComparator;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.iq80.leveldb.impl.LogWriter;
import org.iq80.leveldb.impl.ValueType;
import org.iq80.leveldb.impl.VersionEdit;
import org.iq80.leveldb.table.BytewiseComparator;
import org.iq80.leveldb.table.TableBuilder;
import org.iq80.leveldb.util.DynamicSliceOutput;
import org.iq80.leveldb.util.Slice;
import org.iq80.leveldb.util.VariableLengthQuantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static cn.nukkit.level.format.leveldb.LevelDBKey.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldColumnScannerTest {
    @TempDir Path directory;

    @Test
    void closedDatabaseReportsKindsAndDimensionsWithoutChangingAnyFile() throws Exception {
        Path dbPath = directory.toRealPath().resolve("actual-db");
        try (DB db = Iq80DBFactory.factory.open(dbPath.toFile(), new Options().createIfMissing(true))) {
            db.put(VERSION.getKey(1, 2, 0), new byte[]{42});
            db.put(VERSION_OLD.getKey(2, 2, 0), new byte[]{3});
            db.put(VERSION.getKey(3, 2, 0), new byte[]{1, 2});
            db.put(VERSION_OLD.getKey(3, 2, 0), new byte[]{3});
            db.put(SUB_CHUNK_PREFIX.getKey(4, 2, 0, 0), new byte[100]);
            db.put(digest(5, 0), new byte[8]);
            db.put(digest(6, 0), new byte[8]);
            db.put(DATA_3D.getKey(6, 2, 0), new byte[100]);
            db.put(VERSION.getKey(1, 2, -8), new byte[]{43});
            db.put(VERSION.getKey(7, 2, 0), new byte[]{42});
            db.delete(VERSION.getKey(7, 2, 0));
            db.put(BIOME_IDS_TABLE, new byte[200]);
            db.put("map_xyz,".getBytes(java.nio.charset.StandardCharsets.US_ASCII), new byte[10]);
        }
        // Java backend deletes LOCK on close. Model the retained native-backend lock
        // in fixture setup; production scanner itself never creates a missing LOCK.
        Files.createFile(dbPath.resolve("LOCK"));
        Map<String, String> before = fingerprint(dbPath);
        WorldColumnScanner.Report report = WorldColumnScanner.scan(dbPath);
        assertTrue(report.complete());
        assertEquals(7, report.total().columns);
        assertEquals(1, report.total().currentVersion);
        assertEquals(1, report.total().legacyVersion);
        assertEquals(2, report.total().malformedVersion);
        assertEquals(3, report.total().versionless);
        assertEquals(2, report.total().orphanDigest);
        assertEquals(1, report.total().digestOnly);
        assertEquals(6, report.dimensions().get(0).columns);
        assertEquals(1, report.dimensions().get(-8).columns);
        assertEquals(before, fingerprint(dbPath));
    }

    @Test
    void mergesLiveTablesWithWalAndTombstonesAcrossEveryCompressionCodec() throws Exception {
        for (CompressionType codec : CompressionType.values()) {
            Path db = Files.createDirectory(directory.toRealPath().resolve(codec.name()));
            FileMetaData first = table(db, 10, codec,
                    entry(VERSION, 1, 1, new byte[]{42}),
                    entry(VERSION, 2, 2, new byte[]{42}),
                    entry(VERSION_OLD, 3, 4, new byte[]{3}),
                    entry(DATA_3D, 3, 5, new byte[16000]));
            FileMetaData second = table(db, 11, codec,
                    entry(VERSION, 1, 6, null), entry(VERSION, 2, 7, null));
            FileMetaData obsolete = table(db, 12, codec, entry(VERSION, 90, 8, new byte[]{42}));
            manifest(db, List.of(first, second, obsolete), 12L);
            wal(db, 3, 100, entry(VERSION, 1, 0, new byte[]{42}), entry(VERSION_OLD, 3, 0, null));
            // An obsolete WAL must not resurrect another column.
            wal(db, 2, 300, entry(VERSION, 99, 0, new byte[]{42}));
            Map<String, String> before = fingerprint(db);
            WorldColumnScanner.Report report = WorldColumnScanner.scan(db);
            assertEquals(2, report.liveTables(), codec.name());
            assertEquals(1, report.walFiles(), codec.name());
            assertEquals(2, report.total().columns, codec.name());
            assertEquals(1, report.total().currentVersion, codec.name());
            assertEquals(1, report.total().versionless, codec.name());
            assertEquals(before, fingerprint(db), codec.name());
        }
    }

    @Test
    void refusesInUseDatabaseAndSymlinkInputWithoutMutation() throws Exception {
        Path db = Files.createDirectory(directory.toRealPath().resolve("locked"));
        manifest(db, List.of(), null);
        wal(db, 3, 1, entry(VERSION, 1, 0, new byte[]{42}));
        Map<String, String> before = fingerprint(db);
        try (FileChannel channel = FileChannel.open(db.resolve("LOCK"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        }
        Path link = directory.toRealPath().resolve("alias");
        Files.createSymbolicLink(link, db);
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(link));
        assertEquals(before, fingerprint(db));
    }

    @Test
    void missingLockIsNotCreated() throws Exception {
        Path db = Files.createDirectory(directory.toRealPath().resolve("missing-lock"));
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        assertEquals(Map.of(), fingerprint(db));
    }

    @Test
    void rejectsCorruptTableAndTruncatedWalAsIncomplete() throws Exception {
        Path db = Files.createDirectory(directory.toRealPath().resolve("corruption"));
        FileMetaData table = table(db, 10, CompressionType.NONE, entry(VERSION, 1, 1, new byte[]{42}));
        manifest(db, List.of(table), null);
        wal(db, 3, 100, entry(VERSION, 2, 0, new byte[]{42}));
        byte[] wal = Files.readAllBytes(db.resolve("000003.log"));
        Files.write(db.resolve("000003.log"), Arrays.copyOf(wal, wal.length - 1));
        Map<String, String> truncated = fingerprint(db);
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        assertEquals(truncated, fingerprint(db));
        Files.write(db.resolve("000003.log"), wal);
        byte[] bytes = Files.readAllBytes(db.resolve("000010.ldb"));
        bytes[0] ^= 1;
        Files.write(db.resolve("000010.ldb"), bytes);
        Map<String, String> corrupt = fingerprint(db);
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        assertEquals(corrupt, fingerprint(db));
    }

    @Test
    void malformedManifestAndMissingLiveTableCannotProduceCleanReport() throws Exception {
        Path db = Files.createDirectory(directory.toRealPath().resolve("missing-table"));
        FileMetaData table = table(db, 10, CompressionType.NONE, entry(VERSION, 1, 1, new byte[]{42}));
        manifest(db, List.of(table), null);
        wal(db, 3, 1, entry(VERSION, 1, 0, new byte[]{42}));
        Files.delete(db.resolve("000010.ldb"));
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        Files.writeString(db.resolve("CURRENT"), "../MANIFEST-000001\n");
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
    }

    @Test
    void rejectsZeroByteTruncatedHeaderInsteadOfTreatingItAsBlockPadding() throws Exception {
        Path db = Files.createDirectory(directory.toRealPath().resolve("short-header"));
        manifest(db, List.of(), null);
        wal(db, 3, 1, entry(VERSION, 1, 0, new byte[]{42}));
        Files.write(db.resolve("000003.log"), new byte[]{0}, StandardOpenOption.APPEND);
        Map<String, String> before = fingerprint(db);
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        assertEquals(before, fingerprint(db));
    }

    @Test
    void requiresNextFileNumberInManifestLikeNormalRecovery() throws Exception {
        Path db = Files.createDirectory(directory.toRealPath().resolve("incomplete-manifest"));
        Files.createFile(db.resolve("LOCK"));
        Files.writeString(db.resolve("CURRENT"), "MANIFEST-000001\n");
        VersionEdit edit = new VersionEdit();
        edit.setComparatorName(new BytewiseComparator().name());
        edit.setLogNumber(3);
        edit.setLastSequenceNumber(0);
        try (LogWriter writer = LogWriter.createWriter(1, new WriteFile(db.resolve("MANIFEST-000001")))) {
            writer.addRecord(edit.encode(), true);
        }
        wal(db, 3, 1, entry(VERSION, 1, 0, new byte[]{42}));
        Map<String, String> before = fingerprint(db);
        assertThrows(IOException.class, () -> WorldColumnScanner.scan(db));
        assertEquals(before, fingerprint(db));
    }

    private static byte[] digest(int x, int dimension) {
        return LevelDBKey.getKey(DIGP_PREFIX, x, 2, dimension);
    }

    private static Entry entry(LevelDBKey kind, int x, long sequence, byte[] value) {
        return new Entry(kind.getKey(x, 2, 0), sequence, value);
    }

    private record Entry(byte[] key, long sequence, byte[] value) {
        private InternalKey internalKey() {
            return new InternalKey(new Slice(key), sequence, value == null ? ValueType.DELETION : ValueType.VALUE);
        }
    }

    private static FileMetaData table(Path db, long number, CompressionType codec, Entry... entries) throws Exception {
        InternalUserComparator comparator = new InternalUserComparator(new InternalKeyComparator(new BytewiseComparator()));
        List<Entry> sorted = new ArrayList<>(List.of(entries));
        sorted.sort((left, right) -> comparator.compare(left.internalKey().encode(), right.internalKey().encode()));
        Path path = db.resolve(String.format("%06d.ldb", number));
        try (WritableFile output = new WriteFile(path)) {
            TableBuilder builder = new TableBuilder(new Options().compressionType(codec), output, comparator);
            for (Entry entry : sorted) {
                builder.add(entry.internalKey().encode(), new Slice(entry.value == null ? new byte[0] : entry.value));
            }
            builder.finish();
        }
        return new FileMetaData(number, Files.size(path), sorted.get(0).internalKey(), sorted.get(sorted.size() - 1).internalKey());
    }

    private static void manifest(Path db, List<FileMetaData> tables, Long deleted) throws Exception {
        Files.createFile(db.resolve("LOCK"));
        Files.writeString(db.resolve("CURRENT"), "MANIFEST-000001\n");
        try (LogWriter writer = LogWriter.createWriter(1, new WriteFile(db.resolve("MANIFEST-000001")))) {
            VersionEdit initial = new VersionEdit();
            initial.setComparatorName(new BytewiseComparator().name());
            initial.setLogNumber(3);
            initial.setPreviousLogNumber(0);
            initial.setNextFileNumber(30);
            initial.setLastSequenceNumber(8); // WAL sequences above this must still be applied.
            tables.forEach(file -> initial.addFile(0, file));
            writer.addRecord(initial.encode(), true);
            if (deleted != null) {
                VersionEdit removal = new VersionEdit();
                removal.deleteFile(0, deleted);
                writer.addRecord(removal.encode(), true);
            }
        }
    }

    private static void wal(Path db, long number, long sequence, Entry... entries) throws Exception {
        DynamicSliceOutput output = new DynamicSliceOutput(100);
        output.writeLong(sequence);
        output.writeInt(entries.length);
        for (Entry entry : entries) {
            output.writeByte(entry.value == null ? 0 : 1);
            VariableLengthQuantity.writeVariableLengthInt(entry.key.length, output);
            output.writeBytes(entry.key);
            if (entry.value != null) {
                VariableLengthQuantity.writeVariableLengthInt(entry.value.length, output);
                output.writeBytes(entry.value);
            }
        }
        try (LogWriter writer = LogWriter.createWriter(number, new WriteFile(db.resolve(String.format("%06d.log", number))))) {
            writer.addRecord(output.slice(), true);
        }
    }

    private static Map<String, String> fingerprint(Path db) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var files = Files.list(db)) {
            for (Path file : files.toList()) {
                result.put(file.getFileName().toString(), Files.getLastModifiedTime(file) + ":"
                        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
        }
        return result;
    }

    // Fixture creation only. Scanner has no writable adapter.
    private static final class WriteFile implements WritableFile {
        private final FileChannel channel;

        private WriteFile(Path path) throws IOException {
            channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        public void append(Slice bytes) throws IOException {
            var buffer = bytes.toByteBuffer();
            while (buffer.hasRemaining()) channel.write(buffer);
        }

        public void force() throws IOException {
            channel.force(true);
        }

        public void close() throws IOException {
            channel.close();
        }
    }
}
