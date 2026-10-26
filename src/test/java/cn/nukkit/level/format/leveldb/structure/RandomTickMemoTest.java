package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RandomTickMemoTest {
    @Test
    void replacementWithSameVersionDropsPositiveAndNewGrassIsVisibleImmediately() {
        StateBlockStorage grass = storage(BlockID.GRASS);
        StateBlockStorage stone = storage(BlockID.STONE);
        assertEquals(grass.getVersion(), stone.getVersion(), "identity matters even when versions match");
        LevelDBChunkSection section = section(grass);
        assertTrue(section.mayHaveRandomTickBlocks());
        section.getStorages()[0] = stone;
        assertFalse(section.mayHaveRandomTickBlocks(), "replacing active storage must discard its old positive");
        stone.set(123, state(BlockID.GRASS));
        assertTrue(section.mayHaveRandomTickBlocks(), "the new plant must invalidate an inert probe immediately");
    }

    @Test
    void compactionRemovesUnusedTickingPaletteEntryWithoutCopyingSection() {
        StateBlockStorage data = storage(BlockID.STONE, BlockID.GRASS);
        LevelDBChunkSection section = section(data);
        assertTrue(section.mayHaveRandomTickBlocks(), "unused entries are initially conservative positives");
        int before = data.getVersion();
        assertTrue(data.compress());
        assertNotEquals(before, data.getVersion());
        assertFalse(section.mayHaveRandomTickBlocks(), "compaction makes this same section provably inert");
    }

    @Test
    void largeInertPaletteFallsBackThenCompactionRestoresSkippingAndInsertionRestoresTicks() {
        StateBlockStorage data = largeStorage(false);
        LevelDBChunkSection section = section(data);
        assertTrue(section.mayHaveRandomTickBlocks(), "seventeen entries retain bounded-probe fallback");
        assertTrue(data.compress());
        assertFalse(section.mayHaveRandomTickBlocks(), "collapsed inert palette must no longer stay active forever");
        data.set(123, state(BlockID.GRASS));
        assertTrue(section.mayHaveRandomTickBlocks());
    }

    @Test
    void largePaletteWithLiveGrassStillTicksAfterCompaction() {
        StateBlockStorage data = largeStorage(true);
        LevelDBChunkSection section = section(data);
        assertTrue(section.mayHaveRandomTickBlocks());
        assertTrue(data.compress());
        assertEquals(BlockID.GRASS << Block.DATA_BITS, data.get(123));
        assertTrue(section.mayHaveRandomTickBlocks(), "a live plant survives palette compaction");
    }

    @Test
    void compactionRetainsUnusedEntryZeroAndItsConservativePositive() {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        palette.add(state(BlockID.GRASS));
        palette.add(state(BlockID.STONE));
        BitArray cells = BitArrayVersion.V2.createPalette(4096);
        for (int i = 0; i < 4096; i++) cells.set(i, 1);
        StateBlockStorage data = new StateBlockStorage(cells, palette, null, null);
        LevelDBChunkSection section = section(data);
        assertEquals(BlockID.STONE << Block.DATA_BITS, data.get(123));
        assertTrue(section.mayHaveRandomTickBlocks());
        assertTrue(data.compress());
        assertFalse(data.isEmpty(), "compress intentionally preserves palette entry zero beside stone");
        assertTrue(section.mayHaveRandomTickBlocks(), "retained unused entry zero remains a safe false positive");
    }

    @Test
    void borrowedPaletteAliasCanAddTickingEntryWithoutVersionAndNegativeIsNeverCached() {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        palette.add(state(BlockID.STONE));
        StateBlockStorage data = new StateBlockStorage(BitArrayVersion.V2.createPalette(4096), palette, null, null);
        LevelDBChunkSection section = section(data);
        assertFalse(section.mayHaveRandomTickBlocks());
        int version = data.getVersion();
        palette.add(state(BlockID.GRASS));
        assertEquals(version, data.getVersion(), "borrowed constructor aliases bypass the mutation counter");
        assertTrue(section.mayHaveRandomTickBlocks(), "an unchanged version cannot justify a cached negative");
    }

    @Test
    void liveLegacyMappingChangesDoNotLeaveAStaleNegative() {
        AtomicInteger id = new AtomicInteger(BlockID.STONE);
        BlockStateMapping mapping = mock(BlockStateMapping.class);
        when(mapping.getVersion()).thenReturn(GameVersion.getFeatureVersion());
        when(mapping.getLegacyId(42)).thenAnswer(invocation -> id.get());
        BlockStateSnapshot dynamic = BlockStateSnapshot.builder().runtimeId(42).legacyId(-1)
                .legacyData(0).version(null).vanillaState(tag("test:dynamic")).build();
        StateBlockStorage data = new StateBlockStorage(BitArrayVersion.V2.createPalette(4096),
                new ArrayList<>(List.of(dynamic)), null, null);
        LevelDBChunkSection section = section(data);
        try (MockedStatic<BlockStateMapping> statics = mockStatic(BlockStateMapping.class)) {
            statics.when(BlockStateMapping::get).thenReturn(mapping);
            assertFalse(section.mayHaveRandomTickBlocks());
            id.set(BlockID.GRASS);
            assertTrue(section.mayHaveRandomTickBlocks(), "mapping changes cannot be hidden by a negative memo");
            id.set(BlockID.STONE);
            assertTrue(section.mayHaveRandomTickBlocks(), "positive memo stays conservatively true without storage edits");
        }
    }

    @Test
    void changedVersionWithBusyWriterFallsBackWithoutWaitingThenBecomesInert() throws Exception {
        StateBlockStorage data = storage(BlockID.STONE, BlockID.GRASS);
        LevelDBChunkSection section = section(data);
        assertTrue(section.mayHaveRandomTickBlocks());
        assertTrue(data.compress());
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            section.writeLock.lock();
            try {
                held.countDown();
                if (!release.await(2, TimeUnit.SECONDS)) {
                    failure.set(new AssertionError("writer deadline expired"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.set(e);
            } finally {
                section.writeLock.unlock();
            }
        }, "random-tick-memo-bounded-writer");
        try {
            writer.start();
            assertTrue(held.await(1, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(100), () -> assertTrue(section.mayHaveRandomTickBlocks()));
        } finally {
            release.countDown();
            writer.join(2500);
        }
        assertFalse(writer.isAlive());
        assertNull(failure.get());
        assertFalse(section.mayHaveRandomTickBlocks(), "after writer release, the compacted inert palette is discoverable");
    }

    @Test
    void unchangedPositiveRetainsLockFreeFastPath() {
        LevelDBChunkSection section = section(storage(BlockID.GRASS));
        assertTrue(section.mayHaveRandomTickBlocks());
        CountingLock reads = new CountingLock(section.readLock);
        section.readLock = reads;
        for (int i = 0; i < 100; i++) assertTrue(section.mayHaveRandomTickBlocks());
        assertEquals(0, reads.acquisitions, "unchanged active sections must not add a palette lock each tick");
        assertEquals(0, reads.releases);
    }

    private static StateBlockStorage largeStorage(boolean active) {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        for (int i = 0; i < 17; i++) palette.add(state(active && i == 16 ? BlockID.GRASS : BlockID.STONE));
        BitArray cells = BitArrayVersion.V5.createPalette(4096);
        if (active) cells.set(123, 16);
        return new StateBlockStorage(cells, palette, null, null);
    }

    private static LevelDBChunkSection section(StateBlockStorage data) {
        return new LevelDBChunkSection(0, new StateBlockStorage[]{data}, false);
    }

    private static StateBlockStorage storage(int... ids) {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        for (int id : ids) palette.add(state(id));
        return new StateBlockStorage(BitArrayVersion.V2.createPalette(4096), palette, null, null);
    }

    private static BlockStateSnapshot state(int id) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(0).runtimeId(id)
                .vanillaState(tag("test:block_" + id)).build();
    }

    private static NbtMap tag(String name) {
        return NbtMap.builder().putString("name", name).putCompound("states", NbtMap.EMPTY)
                .putInt("version", 1).build();
    }

    private static final class CountingLock implements Lock {
        final Lock delegate;
        int acquisitions, releases;
        CountingLock(Lock delegate) { this.delegate = delegate; }
        @Override public void lock() { delegate.lock(); acquisitions++; }
        @Override public void lockInterruptibly() throws InterruptedException {
            delegate.lockInterruptibly(); acquisitions++;
        }
        @Override public boolean tryLock() {
            boolean acquired = delegate.tryLock();
            if (acquired) acquisitions++;
            return acquired;
        }
        @Override public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            boolean acquired = delegate.tryLock(time, unit);
            if (acquired) acquisitions++;
            return acquired;
        }
        @Override public void unlock() { delegate.unlock(); releases++; }
        @Override public Condition newCondition() { return delegate.newCondition(); }
    }
}
