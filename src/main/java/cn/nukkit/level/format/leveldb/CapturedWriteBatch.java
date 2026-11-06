package cn.nukkit.level.format.leveldb;

import org.iq80.leveldb.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable byte snapshots that can be replayed into the provider's own native or Java batch. */
final class CapturedWriteBatch implements WriteBatch {
    private record Operation(byte[] key, byte[] value) {}
    private final List<Operation> operations = new ArrayList<>();
    private long bytes = 12;
    private boolean actorPut;

    @Override
    public WriteBatch put(byte[] key, byte[] value) {
        operations.add(new Operation(key.clone(), value.clone()));
        bytes += 9L + key.length + value.length;
        if (key.length >= LevelDBKey.ACTOR_PREFIX.length
                && Arrays.equals(key, 0, LevelDBKey.ACTOR_PREFIX.length,
                LevelDBKey.ACTOR_PREFIX, 0, LevelDBKey.ACTOR_PREFIX.length)) actorPut = true;
        return this;
    }

    @Override
    public WriteBatch delete(byte[] key) {
        operations.add(new Operation(key.clone(), null));
        bytes += 5L + key.length;
        return this;
    }

    void replay(WriteBatch target) {
        for (Operation operation : operations) {
            if (operation.value == null) target.delete(operation.key);
            else target.put(operation.key, operation.value);
        }
    }

    // Actor keys are shared between columns. Keep entity-bearing snapshots on the original
    // per-column path; grouping their stale-actor cleanup needs a separate ownership protocol.
    boolean canGroup() { return !actorPut; }

    @Override public int getApproximateSize() { return (int) Math.min(Integer.MAX_VALUE, bytes); }
    @Override public int size() { return operations.size(); }
    @Override public void close() { operations.clear(); }
}
