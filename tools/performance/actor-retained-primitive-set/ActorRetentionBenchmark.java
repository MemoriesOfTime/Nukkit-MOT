package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import com.sun.management.ThreadMXBean;
import org.iq80.leveldb.*;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Actual actor key generation, digest reads and snapshot+cleanup, without disk timing. */
public final class ActorRetentionBenchmark {
    private static final ThreadMXBean BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final Object[] RETAINED = new Object[64];
    private static volatile long consumed;
    private static volatile Object fixtureOwner;

    public static void main(String[] args) throws Exception {
        BEAN.setThreadAllocatedMemoryEnabled(true);
        System.out.println("classes=" + EntityStorageV1_18_30.class.getProtectionDomain().getCodeSource().getLocation());
        Fixture fixture = new Fixture();
        fixtureOwner = fixture;
        measure("generate-key", 200000, count -> {
            for (int i = 0; i < count; i++) RETAINED[i & 63] = EntityStorageV1_18_30.generateStorageKeyForEntity(0x8765432100000000L + i);
            long sum = 0;
            for (Object key : RETAINED) sum += ((byte[]) key)[7];
            consumed = sum;
        });
        for (int size : new int[]{0, 1, 16, 64, 256}) {
            fixture.configure(size);
            int operations = Math.max(1000, 100000 / Math.max(1, size));
            measure("read-missing-" + size, operations, count -> {
                for (int i = 0; i < count; i++) RETAINED[i & 63] = EntityStorageV1_18_30.INSTANCE.readEntities(fixture.db, 0, 0, 0);
                consumed = fixture.db.reads;
            });
            measure("snapshot-cleanup-" + size, operations, count -> {
                for (int i = 0; i < count; i++) {
                    EntitySerializer.Cleanup cleanup = EntityStorageV1_18_30.INSTANCE.snapshotEntities(fixture.batch, fixture.chunk);
                    RETAINED[i & 63] = cleanup;
                    cleanup.apply(fixture.db, fixture.batch);
                }
                consumed = fixture.batch.checksum;
            });
        }
        if (consumed == Long.MIN_VALUE) throw new AssertionError();
    }

    private static void measure(String workload, int operations, Work work) {
        for (int i = 0; i < 8; i++) work.run(operations);
        long tid = Thread.currentThread().getId();
        for (int round = 0; round < 5; round++) {
            long bytes = BEAN.getThreadAllocatedBytes(tid), start = System.nanoTime();
            work.run(operations);
            long elapsed = System.nanoTime() - start, allocated = BEAN.getThreadAllocatedBytes(tid) - bytes;
            System.out.printf(Locale.ROOT,
                    "{\"workload\":\"%s\",\"round\":%d,\"ns_op\":%.3f,\"bytes_op\":%.3f,\"count\":%d,\"consumed\":%d}%n",
                    workload, round, (double) elapsed / operations, (double) allocated / operations, operations, consumed);
        }
    }

    private interface Work { void run(int count); }

    private static final class Fixture {
        final MemoryDb db = new MemoryDb();
        final CountingBatch batch = new CountingBatch();
        final LevelDBChunk chunk;

        Fixture() throws Exception {
            // Only dimension getters are needed; no world/server constructor or mocked hot calls.
            Level level = new ObjenesisStd().newInstance(Level.class);
            Field dimension = Level.class.getDeclaredField("dimensionData");
            dimension.setAccessible(true);
            dimension.set(level, DimensionData.LEGACY_DIMENSION);
            LevelProvider provider = (LevelProvider) Proxy.newProxyInstance(LevelProvider.class.getClassLoader(),
                    new Class<?>[]{LevelProvider.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getLevel")) return level;
                        throw new UnsupportedOperationException(method.getName());
                    });
            chunk = new LevelDBChunk(provider, 0, 0);
        }

        void configure(int count) {
            List<LevelDBChunk.PreservedEntityActor> actors = new ArrayList<>();
            ByteBuffer old = ByteBuffer.allocate((count + 2) * 8);
            for (int i = 0; i < count; i++) {
                long id = Long.MIN_VALUE + i * 257L;
                byte[] key = ByteBuffer.allocate(8).putLong(id).array();
                actors.add(new LevelDBChunk.PreservedEntityActor(key, new byte[]{1, 2, 3}));
                if ((i & 3) == 0) actors.add(new LevelDBChunk.PreservedEntityActor(key, new byte[]{4}));
                old.putLong(id);
            }
            old.putLong(1).putLong(2);
            chunk.setPreservedEntityActors(actors);
            db.digest = old.array();
        }
    }

    private static final class CountingBatch implements WriteBatch {
        long checksum;
        final byte[][] keys = new byte[64][];
        final byte[][] values = new byte[64][];
        @Override public WriteBatch put(byte[] key, byte[] value) {
            int slot = (int) checksum & 63;
            keys[slot] = key;
            values[slot] = value;
            checksum += key[key.length - 1] + value.length;
            return this;
        }
        @Override public WriteBatch delete(byte[] key) {
            keys[(int) checksum & 63] = key;
            checksum += key[key.length - 1];
            return this;
        }
        @Override public void close() {}
        @Override public int getApproximateSize() { return 0; }
        @Override public int size() { return 0; }
    }

    private static final class MemoryDb implements DB {
        byte[] digest;
        long reads;
        final byte[][] keys = new byte[64][];
        @Override public byte[] get(byte[] key) {
            keys[(int) reads++ & 63] = key;
            return key[0] == 'd' ? digest : null;
        }
        @Override public byte[] get(byte[] key, ReadOptions options) { return get(key); }
        @Override public void close() {}
        @Override public DBIterator iterator() { throw new UnsupportedOperationException(); }
        @Override public DBIterator iterator(ReadOptions options) { throw new UnsupportedOperationException(); }
        @Override public void put(byte[] key, byte[] value) { throw new UnsupportedOperationException(); }
        @Override public void delete(byte[] key) { throw new UnsupportedOperationException(); }
        @Override public void write(WriteBatch batch) { throw new UnsupportedOperationException(); }
        @Override public WriteBatch createWriteBatch() { throw new UnsupportedOperationException(); }
        @Override public Snapshot put(byte[] key, byte[] value, WriteOptions options) { throw new UnsupportedOperationException(); }
        @Override public Snapshot delete(byte[] key, WriteOptions options) { throw new UnsupportedOperationException(); }
        @Override public Snapshot write(WriteBatch batch, WriteOptions options) { throw new UnsupportedOperationException(); }
        @Override public Snapshot getSnapshot() { throw new UnsupportedOperationException(); }
        @Override public long[] getApproximateSizes(Range... ranges) { throw new UnsupportedOperationException(); }
        @Override public String getProperty(String name) { throw new UnsupportedOperationException(); }
        @Override public void suspendCompactions() { throw new UnsupportedOperationException(); }
        @Override public void resumeCompactions() { throw new UnsupportedOperationException(); }
        @Override public void compactRange(byte[] start, byte[] end) { throw new UnsupportedOperationException(); }
    }
}
