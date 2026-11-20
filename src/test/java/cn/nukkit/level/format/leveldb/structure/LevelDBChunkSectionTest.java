package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.leveldb.serializer.ChunkSectionSerializers;
import cn.nukkit.level.util.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

class LevelDBChunkSectionTest {
    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    // ------------------------------------------------------------------
    // Constructors (from LevelDBChunkSectionConstructorTest)
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void trimsTrailingNullLayersAndPreservesStorageAndLight(int trailingLayers) {
        StateBlockStorage first = new StateBlockStorage();
        StateBlockStorage second = new StateBlockStorage();
        StateBlockStorage[] input = new StateBlockStorage[2 + trailingLayers];
        input[0] = first;
        input[1] = second;
        byte[] blockLight = new byte[2048];
        byte[] skyLight = new byte[2048];
        byte[] compressed = new byte[]{1, 2, 3};
        Arrays.fill(blockLight, (byte) 0x47);
        Arrays.fill(skyLight, (byte) 0x9c);

        LevelDBChunkSection section = new LevelDBChunkSection(null, 7, input,
                blockLight, skyLight, compressed, true, true);

        assertEquals(7, section.getY());
        assertArrayEquals(new StateBlockStorage[]{first, second}, section.storages);
        assertNotSame(input, section.storages);
        assertSame(blockLight, section.blockLight);
        assertSame(skyLight, section.skyLight);
        assertSame(compressed, section.compressedLight);
        assertTrue(section.hasBlockLight);
        assertTrue(section.hasSkyLight);
        input[0] = null;
        assertSame(first, section.storages[0], "the array remains detached from its caller");
    }

    @Test
    void fillsInteriorHolesBeforeTrimmingOnlyTheEmptyTail() {
        StateBlockStorage first = new StateBlockStorage();
        StateBlockStorage last = new StateBlockStorage();
        StateBlockStorage[] input = {null, first, null, last, null, null};
        LevelDBChunkSection section = new LevelDBChunkSection(3, input);

        assertEquals(4, section.storages.length);
        assertNotNull(section.storages[0]);
        assertSame(first, section.storages[1]);
        assertNotNull(section.storages[2]);
        assertSame(last, section.storages[3]);
        assertNotSame(section.storages[0], section.storages[2]);
        assertNull(input[4]);
        assertNull(input[5]);
    }

    @Test
    void fullyPopulatedLayersStillUseADetachedArrayWithTheSameStorageObjects() {
        StateBlockStorage first = new StateBlockStorage();
        StateBlockStorage second = new StateBlockStorage();
        StateBlockStorage[] input = {first, second};
        LevelDBChunkSection section = new LevelDBChunkSection(2, input);

        assertNotSame(input, section.storages);
        assertArrayEquals(input, section.storages);
        input[0] = null;
        assertSame(first, section.storages[0]);
    }

    @Test
    void absentOrEntirelyEmptyStorageKeepsTheTwoAirLayerFallback() {
        for (StateBlockStorage[] input : new StateBlockStorage[][]{null, {}, {null}, {null, null, null}}) {
            LevelDBChunkSection section = new LevelDBChunkSection(0, input);
            assertEquals(2, section.storages.length);
            assertNotNull(section.storages[0]);
            assertNotNull(section.storages[1]);
            assertEquals(0, section.getBlockId(0, 0, 0, 0));
            assertEquals(0, section.getBlockId(0, 0, 0, 1));
        }
    }

    // ------------------------------------------------------------------
    // Cuboid reads (from LevelDBChunkSectionCuboidReadTest)
    // ------------------------------------------------------------------

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

    @Test
    void versionMovesWithEveryWriteAndOnlyThen() {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
        long[] out = new long[1];
        int read = section.getBlockStatePairs(0, 1, 2, 3, 1, 2, 3, out, 0, 1, 1);
        assertEquals(read, section.getBlockVersion(0));
        section.getBlockStatePair(1, 2, 3, 0);
        section.getBlockStatePairs(0, 0, 0, 0, 15, 15, 15, new long[4096], 0, 256, 16);
        assertEquals(read, section.getBlockVersion(0), "reads leave the version alone");

        storage.set(1, 2, 3, BlockStateSnapshot.builder().legacyId(BlockID.STONE).legacyData(0).build());
        int written = section.getBlockVersion(0);
        assertNotEquals(read, written, "a write moves it");
        storage.set(1, 2, 3, BlockStateSnapshot.builder().legacyId(BlockID.AIR).legacyData(0).build());
        assertNotEquals(written, section.getBlockVersion(0), "so does writing the old state back");

        int beforeCompress = section.getBlockVersion(0);
        storage.compress();
        assertNotEquals(beforeCompress, section.getBlockVersion(0), "compacting counts as a change");

        assertEquals(-1, section.getBlockVersion(1), "absent layer");
        assertEquals(-1, section.getBlockStatePairs(1, 0, 0, 0, 0, 0, 0, out, 0, 1, 1));
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

    // ------------------------------------------------------------------
    // Save snapshot (from LevelDBSectionStorageSnapshotTest)
    // ------------------------------------------------------------------

    @Test
    public void unchangedSectionsKeepExactlyTheSameStorageBytes() {
        for (int entries : new int[]{1, 4, 5, 17, 70}) {
            ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>();
            for (int i = 0; i < entries; i++) palette.add(state(i));
            StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V16.createPalette(4096), palette, null, null);
            for (int i = 0; i < 4096; i++) storage.set(i, palette.get(i % entries));
            TestSection section = new TestSection(storage);
            ByteBuf expected = Unpooled.buffer(), actual = Unpooled.buffer();
            try {
                expected.writeByte(9);
                ChunkSectionSerializers.serializer(expected, section.getStorages(), 0, 9);
                writeSnapshot(section, actual);
                assertEquals(expected, actual, "palette entries: " + entries);
            } finally { expected.release(); actual.release(); }
        }
    }

    @Test
    public void storageSerializationExcludesPaletteGrowth() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>();
        for (int i = 0; i < 4; i++) palette.add(state(i));
        StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V2.createPalette(4096), palette, null, null);
        TestSection section = new TestSection(storage);
        CountDownLatch header = new CountDownLatch(1), attempted = new CountDownLatch(1);
        AtomicBoolean mutated = new AtomicBoolean();
        ByteBuf out = new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, 4096, 100000) {
            int bytes;
            @Override public ByteBuf writeByte(int value) {
                super.writeByte(value);
                if (++bytes == 4) {
                    header.countDown();
                    await(attempted);
                }
                return this;
            }
        };
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<?> mutation = worker.submit(() -> {
            await(header);
            mutated.set(section.tryMutation(storage));
            attempted.countDown();
        });
        try {
            writeSnapshot(section, out);
            mutation.get(5, TimeUnit.SECONDS);
            int headerBits = out.getUnsignedByte(3) >> 1;
            int advertisedWords = BitArrayVersion.get(headerBits, true).getWordsForSize(4096);
            int advertisedPalette = out.getIntLE(4 + advertisedWords * 4);
            System.out.printf("mutated=%s headerBits=%d advertisedWords=%d paletteSizeAtExpectedOffset=%d payloadBytes=%d%n",
                    mutated.get(), headerBits, advertisedWords, advertisedPalette, out.readableBytes());
            if (mutated.get() || advertisedPalette != 4) throw new AssertionError("Storage serialization raced the section writer");
        } finally {
            header.countDown(); attempted.countDown(); worker.shutdownNow(); out.release();
        }
    }

    private static void writeSnapshot(LevelDBChunkSection section, ByteBuf out) {
        section.setDirty();
        LevelDBChunk owner = Mockito.mock(LevelDBChunk.class);
        assertNotNull(section.captureSave(owner, 0, storages -> {
            out.writeByte(9);
            ChunkSectionSerializers.serializer(out, storages, 0, 9);
        }));
    }

    static final class TestSection extends LevelDBChunkSection {
        TestSection(StateBlockStorage storage) { super(0, new StateBlockStorage[]{storage}, false); }
        boolean tryMutation(StateBlockStorage storage) {
            if (!writeLock.tryLock()) return false;
            try { storage.set(0, state(4)); return true; }
            finally { writeLock.unlock(); }
        }
    }

    static BlockStateSnapshot state(int id) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(0).runtimeId(id)
                .vanillaState(NbtMap.builder().putString("name", "test:block_" + id)
                        .putCompound("states", NbtMap.EMPTY).putInt("version", 1).build()).build();
    }

    static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
