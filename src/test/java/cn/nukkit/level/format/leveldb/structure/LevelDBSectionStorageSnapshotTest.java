package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.level.format.leveldb.serializer.ChunkSectionSerializers;
import cn.nukkit.level.util.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.nbt.NbtMap;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LevelDBSectionStorageSnapshotTest {
    @org.junit.jupiter.api.Test
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
                org.junit.jupiter.api.Assertions.assertEquals(expected, actual, "palette entries: " + entries);
            } finally { expected.release(); actual.release(); }
        }
    }

    @org.junit.jupiter.api.Test
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
        LevelDBChunk owner = org.mockito.Mockito.mock(LevelDBChunk.class);
        org.junit.jupiter.api.Assertions.assertNotNull(section.captureSave(owner, 0, storages -> {
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
