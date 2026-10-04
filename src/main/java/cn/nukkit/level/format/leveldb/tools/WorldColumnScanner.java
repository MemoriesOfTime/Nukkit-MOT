package cn.nukkit.level.format.leveldb.tools;

import com.google.gson.GsonBuilder;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.env.RandomInputFile;
import org.iq80.leveldb.env.SequentialFile;
import org.iq80.leveldb.impl.FileMetaData;
import org.iq80.leveldb.impl.InternalKey;
import org.iq80.leveldb.impl.InternalKeyComparator;
import org.iq80.leveldb.impl.InternalUserComparator;
import org.iq80.leveldb.impl.LogMonitor;
import org.iq80.leveldb.impl.LogReader;
import org.iq80.leveldb.impl.ValueType;
import org.iq80.leveldb.impl.VersionEdit;
import org.iq80.leveldb.iterator.SliceIterator;
import org.iq80.leveldb.table.BytewiseComparator;
import org.iq80.leveldb.table.Table;
import org.iq80.leveldb.util.Slice;
import org.iq80.leveldb.util.SliceInput;
import org.iq80.leveldb.util.SliceOutput;
import org.iq80.leveldb.util.VariableLengthQuantity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline inventory, not a repair tool. All source channels are opened READ-only;
 * DBFactory/Env are deliberately unused because ordinary LevelDB open runs recovery.
 * Table and log decoding reuse HiveMC LevelDB's checksum/Bedrock compression support.
 */
public final class WorldColumnScanner {
    private static final Pattern TABLE = Pattern.compile("([0-9]+)\\.(ldb|sst)");
    private static final Pattern WAL = Pattern.compile("([0-9]+)\\.log");
    private static final long MAX_SEQUENCE = (1L << 56) - 1;
    private static final int DIGEST = -1;
    private static final int EXPLICIT_OVERWORLD = 1 << 17;
    private static final String[] NON_COLUMN_PREFIXES = {"actorprefix", "map_", "player_", "~local_player",
            "BiomeIdsTable", "DimensionNameIdTable", "PosTrackDB", "PositionTrackDB", "VILLAGE_"};
    private static final InternalUserComparator COMPARATOR =
            new InternalUserComparator(new InternalKeyComparator(new BytewiseComparator()));

    private WorldColumnScanner() {
    }

    public static void main(String[] args) {
        if (args.length != 2 || !"--db".equals(args[0])) {
            System.err.println("Usage: java -cp Nukkit.jar " + WorldColumnScanner.class.getName()
                    + " --db /absolute/offline/world/db");
            System.exit(2);
        }
        try {
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(scan(Path.of(args[1]))));
        } catch (IOException | RuntimeException e) {
            System.err.println("INCOMPLETE: " + e.getMessage());
            System.exit(1);
        }
    }

    public static Report scan(Path database) throws IOException {
        Path directory = database.toAbsolutePath().normalize();
        rejectSymlinks(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a database directory: " + directory);
        }
        Path lockPath = directory.resolve("LOCK");
        if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Existing regular LOCK required; scanner never creates files");
        }
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             FileLock lock = lockChannel.tryLock(0, Long.MAX_VALUE, true)) {
            if (lock == null) {
                throw new IOException("Database is in use; stop its owner before scanning");
            }
            Map<String, Stamp> before = inventory(directory);
            Report report = scanLocked(directory, before);
            if (!before.equals(inventory(directory))) {
                throw new IOException("Database changed during scan; result is incomplete");
            }
            return report;
        } catch (OverlappingFileLockException e) {
            throw new IOException("Database is in use; stop its owner before scanning", e);
        } catch (RuntimeException e) {
            throw new IOException("Unreadable or unsupported database: " + e.getMessage(), e);
        }
    }

    private static Report scanLocked(Path directory, Map<String, Stamp> inventory) throws IOException {
        Stamp current = inventory.get("CURRENT");
        if (current == null || current.size < 2 || current.size > 256) {
            throw new IOException("Missing or invalid CURRENT");
        }
        String manifest = Files.readString(directory.resolve("CURRENT"), StandardCharsets.US_ASCII);
        if (!manifest.matches("MANIFEST-[0-9]+\\n")) {
            throw new IOException("Invalid CURRENT manifest name");
        }
        manifest = manifest.stripTrailing();
        if (!inventory.containsKey(manifest)) {
            throw new IOException("CURRENT references missing manifest");
        }
        Manifest state = new Manifest();
        readLog(directory.resolve(manifest), record -> state.apply(new VersionEdit(record)));
        if (state.logNumber == null || state.nextFileNumber == null || state.lastSequence == null || !state.comparatorSeen) {
            throw new IOException("Incomplete manifest metadata");
        }
        Map<Long, String> tables = numberedFiles(inventory, TABLE);
        Map<Long, String> logs = numberedFiles(inventory, WAL);
        if ((state.logNumber != 0 && !logs.containsKey(state.logNumber))
                || (state.previousLog != 0 && !logs.containsKey(state.previousLog))) {
            throw new IOException("Manifest references missing WAL");
        }
        Map<Column, ColumnState> columns = new HashMap<>();
        long[] records = new long[2];
        for (FileMetaData file : state.tables.values()) {
            String name = tables.get(file.getNumber());
            if (name == null || inventory.get(name).size != file.getFileSize()) {
                throw new IOException("Missing or wrong-sized live table " + file.getNumber());
            }
            try (ReadFile input = new ReadFile(directory.resolve(name));
                 Table table = new Table(input, COMPARATOR, true, null, null);
                 SliceIterator iterator = table.iterator(new ReadOptions().verifyChecksums(true).fillCache(false))) {
                for (boolean valid = iterator.seekToFirst(); valid; valid = iterator.next()) {
                    InternalKey key = new InternalKey(iterator.key());
                    accept(columns, key.getUserKey(), key.getSequenceNumber(),
                            key.getValueType(), iterator.value(), records);
                }
            }
        }
        int walFiles = 0;
        for (Map.Entry<Long, String> log : logs.entrySet()) {
            if (log.getKey() >= state.logNumber || log.getKey() == state.previousLog) {
                readLog(directory.resolve(log.getValue()), record -> readBatch(record, columns, records));
                walFiles++;
            }
        }
        Map<Integer, Counts> dimensions = new TreeMap<>();
        Counts total = new Counts();
        for (Map.Entry<Column, ColumnState> entry : columns.entrySet()) {
            ColumnState column = entry.getValue();
            if (column.keys.values().stream().noneMatch(value -> value.present)) {
                continue;
            }
            Counts counts = dimensions.computeIfAbsent(entry.getKey().dimension, ignored -> new Counts());
            counts.add(column);
            total.add(column);
        }
        return new Report(true, directory.toString(), total, dimensions, state.tables.size(), walFiles,
                records[0], records[1]);
    }

    private static void readBatch(Slice record, Map<Column, ColumnState> columns, long[] records) {
        SliceInput input = record.input();
        long sequence = input.readLong();
        int count = input.readInt();
        if (count < 0 || sequence < 0 || sequence > MAX_SEQUENCE - Math.max(0, count - 1)) {
            throw new IllegalArgumentException("Invalid WAL batch sequence/count");
        }
        for (int index = 0; index < count; index++) {
            ValueType type = ValueType.getValueTypeByPersistentId(input.readUnsignedByte());
            Slice key = input.readSlice(VariableLengthQuantity.readVariableLengthInt(input));
            int length = type == ValueType.VALUE ? VariableLengthQuantity.readVariableLengthInt(input) : 0;
            if (length < 0 || length > input.available()) {
                throw new IllegalArgumentException("Invalid WAL value length");
            }
            Slice value = input.readSlice(length);
            accept(columns, key, sequence + index, type, value, records);
        }
        if (input.isReadable()) {
            throw new IllegalArgumentException("WAL batch count does not match its contents");
        }
    }

    private static void accept(Map<Column, ColumnState> columns, Slice key, long sequence,
                               ValueType type, Slice value, long[] records) {
        records[0]++;
        if (sequence < 0 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Invalid sequence number");
        }
        // Known non-column namespaces can accidentally match a 9/10/13/14-byte shape.
        for (String prefix : NON_COLUMN_PREFIXES) {
            if (startsWith(key, prefix)) {
                records[1]++;
                return;
            }
        }
        Column column;
        int slot;
        if ((key.length() == 12 || key.length() == 16) && key.getInt(0) == 0x70676964) {
            column = new Column(key.getInt(4), key.getInt(8), key.length() == 16 ? key.getInt(12) : 0);
            slot = DIGEST;
            if (key.length() == 16 && column.dimension == 0) slot -= EXPLICIT_OVERWORLD;
        } else if (key.length() == 9 || key.length() == 10 || key.length() == 13 || key.length() == 14) {
            int offset = key.length() >= 13 ? 12 : 8;
            int tag = key.getUnsignedByte(offset);
            // Only known chunk tags: other namespaces can have identical key lengths.
            if (!((tag >= '+' && tag <= 'A') || tag == 'f' || tag == 'v' || (tag >= 232 && tag <= 234))) {
                records[1]++;
                return;
            }
            column = new Column(key.getInt(0), key.getInt(4), offset == 12 ? key.getInt(8) : 0);
            slot = key.length() == offset + 2 ? (key.getUnsignedByte(offset + 1) + 1) * 256 + tag : tag;
            // Explicit dimension zero and omitted dimension zero are different raw keys.
            if (offset == 12 && column.dimension == 0) slot += EXPLICIT_OVERWORLD;
        } else {
            records[1]++;
            return;
        }
        KeyState previous = columns.computeIfAbsent(column, ignored -> new ColumnState()).keys.get(slot);
        KeyState next = new KeyState(sequence, type == ValueType.VALUE, value.length(),
                value.length() == 1 ? value.getUnsignedByte(0) : -1);
        if (previous == null || sequence > previous.sequence) {
            columns.get(column).keys.put(slot, next);
        } else if (sequence == previous.sequence && !previous.equals(next)) {
            throw new IllegalArgumentException("Conflicting records at same sequence for " + column);
        }
    }

    private static boolean startsWith(Slice key, String prefix) {
        if (key.length() < prefix.length()) return false;
        for (int index = 0; index < prefix.length(); index++) {
            if (key.getByte(index) != prefix.charAt(index)) return false;
        }
        return true;
    }

    private static void readLog(Path file, Consumer<Slice> consumer) throws IOException {
        // LogReader intentionally tolerates incomplete crash tails. An inventory must not
        // silently label those as complete, so validate physical/logical framing first.
        validateLogFrames(file);
        try (ReadFile input = new ReadFile(file)) {
            LogReader reader = new LogReader(input, new LogMonitor() {
                public void corruption(long bytes, String reason) {
                    throw new IllegalArgumentException(file.getFileName() + ": " + reason);
                }

                public void corruption(long bytes, Throwable reason) {
                    throw new IllegalArgumentException(file.getFileName() + ": unreadable log", reason);
                }
            }, true, 0);
            for (Slice record; (record = reader.readRecord()) != null; ) {
                consumer.accept(record);
            }
        }
    }

    private static void validateLogFrames(Path file) throws IOException {
        try (ReadFile input = new ReadFile(file)) {
            boolean fragmented = false;
            for (long position = 0; position < input.size(); position += 32768) {
                ByteBuffer block = input.read(position, (int) Math.min(32768, input.size() - position));
                while (block.hasRemaining()) {
                    if (block.remaining() < 7) {
                        if (block.limit() != 32768) {
                            throw new IOException("Truncated log header: " + file.getFileName());
                        }
                        while (block.hasRemaining()) {
                            if (block.get() != 0) {
                                throw new IOException("Truncated log header: " + file.getFileName());
                            }
                        }
                        break;
                    }
                    int checksum = block.getInt();
                    int length = Byte.toUnsignedInt(block.get()) | Byte.toUnsignedInt(block.get()) << 8;
                    int type = Byte.toUnsignedInt(block.get());
                    if (checksum == 0 && length == 0 && type == 0) {
                        while (block.hasRemaining()) {
                            if (block.get() != 0) {
                                throw new IOException("Nonzero log padding: " + file.getFileName());
                            }
                        }
                        break;
                    }
                    if (length > block.remaining() || type < 1 || type > 4
                            || (fragmented ? type == 1 || type == 2 : type == 3 || type == 4)) {
                        throw new IOException("Invalid or truncated log record: " + file.getFileName());
                    }
                    fragmented = type == 2 || type == 3;
                    block.position(block.position() + length);
                }
            }
            if (fragmented) {
                throw new IOException("Unfinished fragmented log record: " + file.getFileName());
            }
        }
    }

    private static Map<Long, String> numberedFiles(Map<String, Stamp> files, Pattern pattern) throws IOException {
        Map<Long, String> result = new TreeMap<>();
        for (String file : files.keySet()) {
            Matcher matcher = pattern.matcher(file);
            if (matcher.matches() && result.put(Long.parseLong(matcher.group(1)), file) != null) {
                throw new IOException("Ambiguous numbered files: " + file);
            }
        }
        return result;
    }

    private static Map<String, Stamp> inventory(Path directory) throws IOException {
        Map<String, Stamp> result = new TreeMap<>();
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) {
                    throw new IOException("Non-regular database entry refused: " + entry.getFileName());
                }
                result.put(entry.getFileName().toString(),
                        new Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey()));
            }
        }
        return result;
    }

    private static void rejectSymlinks(Path path) throws IOException {
        for (Path component = path; component != null; component = component.getParent()) {
            if (Files.isSymbolicLink(component)) {
                throw new IOException("Symlink input refused: " + component);
            }
        }
    }

    private static final class Manifest {
        private final Map<Long, FileMetaData> tables = new TreeMap<>();
        private Long logNumber;
        private Long nextFileNumber;
        private Long lastSequence;
        private long previousLog;
        private boolean comparatorSeen;

        private void apply(VersionEdit edit) {
            if (edit.getComparatorName() != null) {
                if (!new BytewiseComparator().name().equals(edit.getComparatorName())) {
                    throw new IllegalArgumentException("Unsupported comparator: " + edit.getComparatorName());
                }
                comparatorSeen = true;
            }
            if (edit.getLogNumber() != null) logNumber = edit.getLogNumber();
            if (edit.getNextFileNumber() != null) nextFileNumber = edit.getNextFileNumber();
            if (edit.getPreviousLogNumber() != null) previousLog = edit.getPreviousLogNumber();
            if (edit.getLastSequenceNumber() != null) lastSequence = edit.getLastSequenceNumber();
            if ((logNumber != null && logNumber < 0) || (nextFileNumber != null && nextFileNumber <= 0) || previousLog < 0
                    || (lastSequence != null && (lastSequence < 0 || lastSequence > MAX_SEQUENCE))) {
                throw new IllegalArgumentException("Invalid manifest sequence/file number");
            }
            edit.getDeletedFiles().values().forEach(tables::remove);
            edit.getNewFiles().values().forEach(file -> tables.put(file.getNumber(), file));
        }
    }

    private record Stamp(long size, FileTime modified, Object fileKey) { }
    private record Column(int x, int z, int dimension) { }
    private record KeyState(long sequence, boolean present, int length, int singleByte) { }

    private static final class ColumnState {
        // Metadata only; no block/entity values retained. Slots are fixed-width chunk
        // tag/section pairs plus digest, bounded independently of value/history size.
        private final Map<Integer, KeyState> keys = new HashMap<>();

        private boolean present(int slot) {
            KeyState value = keys.get(slot);
            return value != null && value.present;
        }

        private boolean validVersion(int slot) {
            return present(slot) && keys.get(slot).length == 1
                    && keys.get(slot).singleByte >= 3 && keys.get(slot).singleByte <= 42;
        }
    }

    public static final class Counts {
        public long columns;
        public long currentVersion;
        public long legacyVersion;
        public long malformedVersion;
        public long versionless;
        public long orphanDigest;
        public long digestOnly;
        public long explicitOverworldKeys;

        private void add(ColumnState column) {
            columns++;
            boolean current = column.validVersion(',');
            boolean legacy = column.validVersion('v');
            if (current) currentVersion++;
            else if (!column.present(',') && legacy) legacyVersion++;
            if ((column.present(',') && !current) || (column.present('v') && !legacy)) malformedVersion++;
            if (!column.present(',') && !column.present('v')) versionless++;
            if (column.present(DIGEST) && !column.present(',') && !column.present('v')) orphanDigest++;
            if (column.present(DIGEST) && column.keys.entrySet().stream()
                    .noneMatch(entry -> entry.getKey() != DIGEST && entry.getValue().present)) digestOnly++;
            if (column.keys.entrySet().stream().anyMatch(entry -> entry.getValue().present
                    && (entry.getKey() >= EXPLICIT_OVERWORLD || entry.getKey() < DIGEST))) explicitOverworldKeys++;
        }
    }

    public record Report(boolean complete, String database, Counts total, Map<Integer, Counts> dimensions,
                         int liveTables, int walFiles, long physicalRecords, long ignoredPhysicalRecords) { }

    /** No writable handle, memory mapping, database recovery, directory creation or lock creation. */
    private static final class ReadFile implements RandomInputFile, SequentialFile {
        private final FileChannel channel;
        private final long size;
        private long position;

        private ReadFile(Path path) throws IOException {
            channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            size = channel.size();
        }

        public long size() {
            return size;
        }

        public ByteBuffer read(long offset, int length) throws IOException {
            if (offset < 0 || length < 0 || offset > size - length || length > 64 * 1024 * 1024) {
                throw new IOException("Invalid or unsupported read range: " + offset + "+" + length);
            }
            ByteBuffer buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer, offset + buffer.position()) < 0) {
                    throw new IOException("File truncated during scan");
                }
            }
            return buffer.flip();
        }

        public int read(int length, SliceOutput output) throws IOException {
            if (position == size) return -1;
            int count = (int) Math.min(length, size - position);
            output.writeBytes(read(position, count));
            position += count;
            return count;
        }

        public void skip(long bytes) throws IOException {
            if (bytes < 0 || bytes > size - position) throw new IOException("Invalid log skip");
            position += bytes;
        }

        public void close() throws IOException {
            channel.close();
        }
    }
}
