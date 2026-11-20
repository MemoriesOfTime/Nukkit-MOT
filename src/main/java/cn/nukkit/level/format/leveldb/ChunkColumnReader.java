package cn.nukkit.level.format.leveldb;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Range;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.Snapshot;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads LevelDB keys through one iterator instead of point {@link DB#get} lookups.
 * <p>
 * LevelDB charges a "seek" to the first table file probed by every point lookup that has to consult more
 * than one file. Once a file runs out of seeks (file size / 16 KiB, at least 100) the background thread
 * rewrites it together with every overlapping file of the next level ({@code Version::UpdateStats},
 * seek-triggered compaction). Loading one chunk column issues about thirty point lookups, most of them
 * for keys that do not exist (empty sub-chunks, extra data, ticks, entity digests), so a world with
 * players moving around never runs out of seek-triggered compactions: the compaction thread rewrites
 * "1@N + ~10@N+1" files back to back for as long as chunks keep loading, regardless of how little new
 * data is written.
 * <p>
 * Iterators do not charge per lookup; {@code DBIter} only samples one key per ~1 MiB read. This reader
 * collects a whole chunk column with a single seek and answers every column key from it, and serves any
 * other key (entity digests, actors) with an iterator seek. Values are identical to {@link DB#get}: a
 * column is contiguous in LevelDB's bytewise order, so the scan sees every key that starts with the
 * column prefix. Writes go straight to the database and drop what was read before them.
 * <p>
 * Not thread-safe; one reader belongs to one chunk read or one chunk write and must be closed.
 * {@link #close()} releases the iterator only, never the database.
 */
final class ChunkColumnReader implements DB {

    private final DB db;
    @Nullable
    private final byte[] column;

    private DBIterator cursor;
    private Map<ByteBuffer, byte[]> columnValues;

    private ChunkColumnReader(DB db, @Nullable byte[] column) {
        this.db = db;
        this.column = column;
    }

    /**
     * A reader that answers the keys of one chunk column from a single scan.
     */
    static ChunkColumnReader column(DB db, int chunkX, int chunkZ, int dimension) {
        byte[] prefix = new byte[dimension == 0 ? 8 : 12];
        putIntLE(prefix, 0, chunkX);
        putIntLE(prefix, 4, chunkZ);
        if (dimension != 0) {
            putIntLE(prefix, 8, dimension);
        }
        return new ChunkColumnReader(db, prefix);
    }

    /**
     * A reader without a column: every lookup is an iterator seek.
     */
    static ChunkColumnReader pointReads(DB db) {
        return new ChunkColumnReader(db, null);
    }

    @Override
    public byte[] get(byte[] key) throws DBException {
        if (this.isColumnKey(key)) {
            return this.columnValues().get(ByteBuffer.wrap(key));
        }
        DBIterator cursor = this.cursor();
        cursor.seek(key);
        if (!cursor.hasNext()) {
            return null;
        }
        Map.Entry<byte[], byte[]> entry = cursor.peekNext();
        return Arrays.equals(entry.getKey(), key) ? entry.getValue() : null;
    }

    /**
     * Column keys are the prefix followed by a tag byte, or a tag byte and a sub-chunk index.
     */
    private boolean isColumnKey(byte[] key) {
        if (this.column == null) {
            return false;
        }
        int extra = key.length - this.column.length;
        return (extra == 1 || extra == 2) && startsWithColumn(key);
    }

    private boolean startsWithColumn(byte[] key) {
        return key.length >= this.column.length
                && Arrays.equals(key, 0, this.column.length, this.column, 0, this.column.length);
    }

    private Map<ByteBuffer, byte[]> columnValues() {
        if (this.columnValues == null) {
            Map<ByteBuffer, byte[]> values = new HashMap<>();
            DBIterator cursor = this.cursor();
            cursor.seek(this.column);
            while (cursor.hasNext()) {
                Map.Entry<byte[], byte[]> entry = cursor.next();
                byte[] key = entry.getKey();
                if (!this.startsWithColumn(key)) {
                    break;
                }
                if (this.isColumnKey(key)) {
                    values.put(ByteBuffer.wrap(key), entry.getValue());
                }
            }
            this.columnValues = values;
        }
        return this.columnValues;
    }

    private DBIterator cursor() {
        if (this.cursor == null) {
            this.cursor = this.db.iterator();
        }
        return this.cursor;
    }

    private void forgetReads() {
        this.columnValues = null;
        if (this.cursor != null) {
            DBIterator cursor = this.cursor;
            this.cursor = null;
            cursor.close();
        }
    }

    private static void putIntLE(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    @Override
    public void close() {
        this.forgetReads();
    }

    @Override
    public byte[] get(byte[] key, ReadOptions options) throws DBException {
        return this.db.get(key, options);
    }

    @Override
    public DBIterator iterator() {
        return this.db.iterator();
    }

    @Override
    public DBIterator iterator(ReadOptions options) {
        return this.db.iterator(options);
    }

    @Override
    public void put(byte[] key, byte[] value) throws DBException {
        this.forgetReads();
        this.db.put(key, value);
    }

    @Override
    public void delete(byte[] key) throws DBException {
        this.forgetReads();
        this.db.delete(key);
    }

    @Override
    public void write(WriteBatch updates) throws DBException {
        this.forgetReads();
        this.db.write(updates);
    }

    @Override
    public WriteBatch createWriteBatch() {
        return this.db.createWriteBatch();
    }

    @Override
    public Snapshot put(byte[] key, byte[] value, WriteOptions options) throws DBException {
        this.forgetReads();
        return this.db.put(key, value, options);
    }

    @Override
    public Snapshot delete(byte[] key, WriteOptions options) throws DBException {
        this.forgetReads();
        return this.db.delete(key, options);
    }

    @Override
    public Snapshot write(WriteBatch updates, WriteOptions options) throws DBException {
        this.forgetReads();
        return this.db.write(updates, options);
    }

    @Override
    public Snapshot getSnapshot() {
        return this.db.getSnapshot();
    }

    @Override
    public long[] getApproximateSizes(Range... ranges) {
        return this.db.getApproximateSizes(ranges);
    }

    @Override
    public String getProperty(String name) {
        return this.db.getProperty(name);
    }

    @Override
    public void suspendCompactions() throws InterruptedException {
        this.db.suspendCompactions();
    }

    @Override
    public void resumeCompactions() {
        this.db.resumeCompactions();
    }

    @Override
    public void compactRange(byte[] begin, byte[] end) throws DBException {
        this.db.compactRange(begin, end);
    }
}
