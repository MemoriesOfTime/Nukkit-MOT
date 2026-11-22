package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RandomTickPaletteEpochTest {

    @Test
    void unchangedOwnedPaletteIsScannedOncePerVersionAndPolicyEpoch() {
        int id = BlockID.STONE;
        boolean previous = Level.isRandomTickBlock(id);
        try (var level = mockStatic(Level.class, CALLS_REAL_METHODS)) {
            Level.setCanRandomTick(id, false);
            BlockStateSnapshot stone = state(id);
            StateBlockStorage storage = owned(stone);
            for (int i = 0; i < 20; i++) assertFalse(storage.mayHaveRandomTickBlocks());
            level.verify(() -> Level.isRandomTickBlock(id), times(1));

            storage.set(0, stone);
            for (int i = 0; i < 20; i++) assertFalse(storage.mayHaveRandomTickBlocks());
            level.verify(() -> Level.isRandomTickBlock(id), times(2));

            long epoch = Level.getRandomTickPaletteEpoch();
            Level.setCanRandomTick(id, false);
            assertEquals(epoch + 1, Level.getRandomTickPaletteEpoch());
            for (int i = 0; i < 20; i++) assertFalse(storage.mayHaveRandomTickBlocks());
            level.verify(() -> Level.isRandomTickBlock(id), times(3));

            Level.setCanRandomTick(id, true);
            for (int i = 0; i < 20; i++) assertTrue(storage.mayHaveRandomTickBlocks());
            level.verify(() -> Level.isRandomTickBlock(id), times(4));
        } finally {
            Level.setCanRandomTick(id, previous);
        }
    }

    @Test
    void policyChangesInvalidateBothStorageAndSectionResults() {
        int id = BlockID.DIAMOND_ORE;
        boolean previous = Level.isRandomTickBlock(id);
        try {
            Level.setCanRandomTick(id, false);
            StateBlockStorage storage = owned(state(id));
            LevelDBChunkSection section = section(storage);
            assertFalse(section.mayHaveRandomTickBlocks());
            Level.setCanRandomTick(id, true);
            assertTrue(storage.mayHaveRandomTickBlocks());
            assertTrue(section.mayHaveRandomTickBlocks());
            Level.setCanRandomTick(id, false);
            assertFalse(storage.mayHaveRandomTickBlocks());
            assertFalse(section.mayHaveRandomTickBlocks());
        } finally {
            Level.setCanRandomTick(id, previous);
        }
    }

    @Test
    void policyChangeDuringScanCannotPublishANegativeUnderTheNewEpoch() {
        int id = BlockID.STONE;
        boolean previous = Level.isRandomTickBlock(id);
        try {
            Level.setCanRandomTick(id, false);
            StateBlockStorage storage = owned(state(id), new DynamicSnapshot(() -> {
                Level.setCanRandomTick(id, true);
                return BlockID.DIRT;
            }));
            assertTrue(storage.mayHaveRandomTickBlocks());
            assertTrue(storage.mayHaveRandomTickBlocks());
        } finally {
            Level.setCanRandomTick(id, previous);
        }
    }

    @Test
    void writeLockBeforeVersionIncrementInvalidatesNegativeMemoWithoutWaiting() throws Exception {
        CountDownLatch writerEntered = new CountDownLatch(1);
        CountDownLatch writerReleased = new CountDownLatch(1);
        BitArray cells = new BlockingCells(BitArrayVersion.V2.createPalette(), writerEntered, writerReleased);
        StateBlockStorage storage = new StateBlockStorage(cells,
                new ArrayList<>(List.of(state(BlockID.STONE))), null, null).copy();
        LevelDBChunkSection section = section(storage);
        assertFalse(section.mayHaveRandomTickBlocks());
        int version = storage.getVersion();
        FutureTask<Void> write = new FutureTask<>(() -> {
            storage.set(0, state(BlockID.STONE));
            return null;
        });
        Thread writer = new Thread(write, "random-tick-palette-writer");
        writer.start();
        try {
            assertTrue(writerEntered.await(5, TimeUnit.SECONDS));
            assertEquals(version, storage.getVersion(), "write begins before version changes");
            assertTrue(storage.mayHaveRandomTickBlocks());
            assertTrue(section.mayHaveRandomTickBlocks());
        } finally {
            writerReleased.countDown();
            write.get(5, TimeUnit.SECONDS);
        }
        assertNotEquals(version, storage.getVersion());
        assertFalse(section.mayHaveRandomTickBlocks());
    }

    @Test
    void virtualSnapshotNeverSuppliesACachedNegativeEvenAfterCopy() {
        AtomicInteger id = new AtomicInteger(BlockID.STONE);
        StateBlockStorage storage = owned(new DynamicSnapshot(id::get));
        assertFalse(storage.mayHaveRandomTickBlocks());
        id.set(BlockID.GRASS);
        assertTrue(storage.mayHaveRandomTickBlocks());
        assertTrue(new StateBlockStorage() {}.mayHaveRandomTickBlocks());
        StateBlockStorage subclassWithOverride = new StateBlockStorage() {
            @Override public boolean mayHaveRandomTickBlocks() { return false; }
        };
        assertTrue(section(subclassWithOverride).mayHaveRandomTickBlocks());
    }

    @Test
    void unresolvedForeignSnapshotNeverSuppliesACachedNegativeAfterCopy() {
        int runtimeId = 42;
        var mapping = mock(BlockStateMapping.class);
        AtomicInteger id = new AtomicInteger(BlockID.STONE);
        when(mapping.getVersion()).thenReturn(GameVersion.getFeatureVersion());
        when(mapping.getLegacyId(runtimeId)).thenAnswer(invocation -> id.get());
        StateBlockStorage storage = owned(BlockStateSnapshot.builder().runtimeId(runtimeId).build());
        try (var mappings = mockStatic(BlockStateMapping.class)) {
            mappings.when(BlockStateMapping::get).thenReturn(mapping);
            assertFalse(storage.mayHaveRandomTickBlocks());
            id.set(BlockID.GRASS);
            assertTrue(storage.mayHaveRandomTickBlocks());
        }
    }

    @Test
    void racingLegacyResolutionCannotReplaceTheIdUsedByAStoredNegative() throws Exception {
        int runtimeId = 42;
        GameVersion version = GameVersion.getFeatureVersion();
        BlockStateSnapshot snapshot = BlockStateSnapshot.builder().version(version).runtimeId(runtimeId).build();
        StateBlockStorage storage = owned(snapshot);
        CountDownLatch resolverEntered = new CountDownLatch(1);
        CountDownLatch resolverReleased = new CountDownLatch(1);
        var delayed = mock(BlockStateMapping.class);
        when(delayed.getVersion()).thenReturn(version);
        when(delayed.getLegacyId(runtimeId)).thenAnswer(invocation -> {
            resolverEntered.countDown();
            assertTrue(resolverReleased.await(5, TimeUnit.SECONDS));
            return BlockID.GRASS;
        });
        var immediate = mock(BlockStateMapping.class);
        when(immediate.getVersion()).thenReturn(version);
        when(immediate.getLegacyId(runtimeId)).thenReturn(BlockID.STONE);

        FutureTask<Integer> resolve = new FutureTask<>(() -> {
            try (var mappings = mockStatic(BlockStateMapping.class)) {
                mappings.when(BlockStateMapping::get).thenReturn(delayed);
                return snapshot.getLegacyId();
            }
        });
        new Thread(resolve, "random-tick-legacy-resolver").start();
        try (var mappings = mockStatic(BlockStateMapping.class)) {
            assertTrue(resolverEntered.await(5, TimeUnit.SECONDS));
            mappings.when(BlockStateMapping::get).thenReturn(immediate);
            assertFalse(storage.mayHaveRandomTickBlocks());
        } finally {
            resolverReleased.countDown();
        }
        assertEquals(BlockID.STONE, resolve.get(5, TimeUnit.SECONDS));
        assertEquals(BlockID.STONE, snapshot.getLegacyId());
        assertFalse(storage.mayHaveRandomTickBlocks());
    }

    private static BlockStateSnapshot state(int id) {
        return BlockStateSnapshot.builder().legacyId(id).build();
    }

    private static StateBlockStorage owned(BlockStateSnapshot... states) {
        return new StateBlockStorage(BitArrayVersion.V2.createPalette(),
                new ArrayList<>(List.of(states)), null, null).copy();
    }

    private static LevelDBChunkSection section(StateBlockStorage storage) {
        return new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
    }

    private static final class DynamicSnapshot extends BlockStateSnapshot {
        private final IntSupplier id;

        DynamicSnapshot(IntSupplier id) {
            super(NbtMap.EMPTY, 0, null, false, -1, -1);
            this.id = id;
        }

        @Override public int getLegacyId() { return id.getAsInt(); }
    }

    private record BlockingCells(BitArray cells, CountDownLatch entered, CountDownLatch released) implements BitArray {
        @Override
        public void set(int index, int value) {
            entered.countDown();
            try {
                assertTrue(released.await(5, TimeUnit.SECONDS), "writer deadline expired");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            cells.set(index, value);
        }

        @Override public int get(int index) { return cells.get(index); }
        @Override public int size() { return cells.size(); }
        @Override public int[] getWords() { return cells.getWords(); }
        @Override public BitArrayVersion getVersion() { return cells.getVersion(); }
        @Override public BitArray copy() { return new BlockingCells(cells.copy(), entered, released); }
    }
}
