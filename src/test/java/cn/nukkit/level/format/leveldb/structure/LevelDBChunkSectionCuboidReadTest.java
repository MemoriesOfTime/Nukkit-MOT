package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.BlockID;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

class LevelDBChunkSectionCuboidReadTest {

    @Test
    void cuboidMatchesSingleCellReadsInScanOrder() {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
        Random random = new Random(42);
        for (int i = 0; i < 4096; i++) {
            int id = random.nextInt(6) == 0 ? BlockID.AIR : random.nextInt(700);
            storage.set(i, BlockStateSnapshot.builder().legacyId(id).legacyData(random.nextInt(16)).build());
        }
        // Extended values survive the cuboid read exactly like getBlockStatePair.
        storage.set(StateBlockStorage.elementIndex(3, 4, 5),
                BlockStateSnapshot.builder().legacyId(Integer.MIN_VALUE).legacyData(-2).build());

        for (int round = 0; round < 200; round++) {
            int x0 = random.nextInt(16), x1 = x0 + random.nextInt(16 - x0);
            int y0 = random.nextInt(16), y1 = y0 + random.nextInt(16 - y0);
            int z0 = random.nextInt(16), z1 = z0 + random.nextInt(16 - z0);
            int sizeY = y1 - y0 + 1, sizeZ = z1 - z0 + 1, sizeX = x1 - x0 + 1;
            int base = random.nextInt(7);
            long[] out = new long[base + sizeX * sizeZ * sizeY];
            section.getBlockStatePairs(0, x0, y0, z0, x1, y1, z1, out, base, sizeZ * sizeY, sizeY);
            int index = base;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        assertEquals(section.getBlockStatePair(x, y, z, 0), out[index++], x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void absentLayerReadsAsAir() {
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{new StateBlockStorage()}, false);
        long[] out = new long[8];
        java.util.Arrays.fill(out, -1L);
        section.getBlockStatePairs(1, 0, 0, 0, 1, 1, 1, out, 0, 4, 2);
        for (long pair : out) {
            assertEquals(0L, pair);
        }
    }

    @Test
    void wholeCuboidTakesTheReadLockOnce() {
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{new StateBlockStorage()}, false);
        CountingLock counting = new CountingLock(section.readLock);
        section.readLock = counting;
        long[] out = new long[4 * 5 * 4];
        section.getBlockStatePairs(0, 2, 3, 4, 5, 7, 7, out, 0, 20, 5);
        assertEquals(1, counting.locks);
        assertEquals(1, counting.unlocks);
    }

    private static final class CountingLock implements Lock {
        private final Lock delegate;
        int locks;
        int unlocks;

        CountingLock(Lock delegate) {
            this.delegate = delegate;
        }

        @Override public void lock() { locks++; delegate.lock(); }
        @Override public void lockInterruptibly() throws InterruptedException { locks++; delegate.lockInterruptibly(); }
        @Override public boolean tryLock() { locks++; return delegate.tryLock(); }
        @Override public boolean tryLock(long time, TimeUnit unit) throws InterruptedException { locks++; return delegate.tryLock(time, unit); }
        @Override public void unlock() { unlocks++; delegate.unlock(); }
        @Override public Condition newCondition() { return delegate.newCondition(); }
    }
}
