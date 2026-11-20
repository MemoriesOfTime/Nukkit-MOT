package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单格读的一致性由 {@link StateBlockStorage} 乐观读承担;验证调色板/单元被整体重塑时
 * 读到的仍是某一时刻成立的状态,且不抛异常。
 * <p>
 * Single-cell read consistency rests on the optimistic read in {@link StateBlockStorage}; these
 * tests verify a reader mid-reshape still gets a state that held at some point and never throws.
 */
class LevelDBSectionLockFreeReadTest {
    private static final int CELLS = 16 * 16 * 16;
    /** Enough distinct states to walk V1 -> V2 -> V4 -> V8 cell widths, still under the V8 palette cap. */
    private static final int STATES = 192;

    @BeforeAll
    static void setup() {
        MockServer.init();
    }

    @Test
    void aPaletteRemapPreservesEveryCell() {
        StateBlockStorage storage = new StateBlockStorage();
        BlockStateSnapshot[] states = states();
        int[] expected = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            BlockStateSnapshot state = states[(i * 7) % states.length];
            storage.set(i, state);
            expected[i] = fullId(state);
        }

        // A compaction rebuilds every cell word against a trimmed palette and a re-created bit array.
        assertTrue(storage.compress());

        for (int i = 0; i < CELLS; i++) {
            assertEquals(expected[i], storage.get(i), "cell " + i);
        }
    }

    @Test
    void concurrentReadsObserveOnlyStatesThatWereWritten() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
        BlockStateSnapshot[] states = states();
        // Id -> the only data value that id is ever written with; a mixed-up palette entry shows up here.
        int[] expectedData = new int[STATES + 1];
        for (int i = 0; i < STATES; i++) {
            expectedData[i + 1] = i & 0xF;
        }
        AtomicIntegerArray lastWritten = new AtomicIntegerArray(CELLS);
        AtomicBoolean writerDone = new AtomicBoolean();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            pool.execute(() -> {
                awaitUnchecked(start);
                Random random = new Random(1234);
                try {
                    for (int round = 0; round < 300; round++) {
                        // A handful of states per round makes every compaction renumber almost the whole
                        // palette, so a read that mixes the new cells with the old palette is wrong.
                        int[] subset = new int[4 + random.nextInt(4)];
                        for (int i = 0; i < subset.length; i++) {
                            subset[i] = random.nextInt(states.length);
                        }
                        for (int cell = 0; cell < CELLS; cell++) {
                            BlockStateSnapshot state = states[subset[random.nextInt(subset.length)]];
                            storage.set(cell, state);
                            lastWritten.set(cell, fullId(state));
                        }
                        storage.compress();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    writerDone.set(true);
                }
            });
            for (int r = 0; r < 3; r++) {
                pool.execute(() -> {
                    awaitUnchecked(start);
                    Random random = new Random();
                    try {
                        while (!writerDone.get()) {
                            for (int i = 0; i < 256; i++) {
                                int cell = random.nextInt(CELLS);
                                int value = section.getFullBlock(cell >> 8, cell & 0xF, (cell >> 4) & 0xF);
                                int id = value >> Block.DATA_BITS;
                                if (id < 0 || id >= expectedData.length || (value & Block.DATA_MASK) != expectedData[id]) {
                                    throw new AssertionError("read a state that was never written: " + value);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "readers/writer did not finish");
        } finally {
            pool.shutdownNow();
        }
        if (failure.get() != null) {
            throw new AssertionError("concurrent access failed", failure.get());
        }

        // The optimistic reads must agree with the writer's own record once everything is quiescent.
        for (int cell = 0; cell < CELLS; cell++) {
            assertEquals(lastWritten.get(cell), section.getFullBlock(cell >> 8, cell & 0xF, (cell >> 4) & 0xF),
                    "cell " + cell);
        }
    }

    @Test
    void singleCellReadsDoNotTakeTheSectionReadLock() {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
        BlockStateSnapshot state = states()[0];
        storage.set(StateBlockStorage.elementIndex(1, 2, 3), state);

        CountingLock readLock = new CountingLock(section.readLock);
        section.readLock = readLock;

        assertEquals(fullId(state), section.getFullBlock(1, 2, 3));
        assertEquals(fullId(state), section.getFullBlock(1, 2, 3, 0));
        assertEquals(state.getLegacyId(), section.getBlockId(1, 2, 3, 0));
        assertEquals(state.getLegacyData(), section.getBlockData(1, 2, 3, 0));
        assertEquals(((long) state.getLegacyId() << 32) | (state.getLegacyData() & 0xffffffffL),
                section.getBlockStatePair(1, 2, 3, 0));
        assertArrayEquals(new int[]{state.getLegacyId(), state.getLegacyData()}, section.getBlockState(1, 2, 3, 0));
        // A per-cell lock acquisition was ~70% of the read cost under load; readers must not re-add it.
        assertEquals(0, readLock.lockCount);
    }

    @Test
    void aLayerAddedThroughTheSectionIsReadableWithoutTheSectionLock() {
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{new StateBlockStorage()}, false);
        // A write only needs the parent for its change callback; the section itself stays detached.
        section.setParent(Mockito.mock(LevelDBChunk.class));
        assertFalse(section.hasLayer(1));

        section.setBlockId(3, 4, 5, 1, BlockID.DIRT);

        int dirt = BlockStateMapping.get().getState(BlockID.DIRT, 0).getLegacyId();
        assertTrue(section.hasLayer(1));
        assertNotNull(section.storages[1]);
        assertEquals(dirt, section.getBlockId(3, 4, 5, 1));
        assertEquals(dirt, section.getFullBlock(3, 4, 5, 1) >> Block.DATA_BITS);
        assertEquals(BlockID.AIR, section.getBlockId(3, 4, 5, 0));
    }

    private static BlockStateSnapshot[] states() {
        BlockStateSnapshot[] states = new BlockStateSnapshot[STATES];
        for (int i = 0; i < STATES; i++) {
            states[i] = BlockStateSnapshot.builder()
                    .legacyId(i + 1)
                    .legacyData(i & 0xF)
                    .build();
        }
        return states;
    }

    private static int fullId(BlockStateSnapshot state) {
        return state.getLegacyId() << Block.DATA_BITS | state.getLegacyData();
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CountingLock implements Lock {
        private final Lock delegate;
        private int lockCount;

        private CountingLock(Lock delegate) {
            this.delegate = delegate;
        }

        @Override
        public void lock() {
            this.lockCount++;
            this.delegate.lock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            this.lockCount++;
            this.delegate.lockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            boolean locked = this.delegate.tryLock();
            if (locked) {
                this.lockCount++;
            }
            return locked;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            boolean locked = this.delegate.tryLock(time, unit);
            if (locked) {
                this.lockCount++;
            }
            return locked;
        }

        @Override
        public void unlock() {
            this.delegate.unlock();
        }

        @Override
        public Condition newCondition() {
            return this.delegate.newCondition();
        }
    }
}
