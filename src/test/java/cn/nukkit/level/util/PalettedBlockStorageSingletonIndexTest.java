package cn.nukkit.level.util;

import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PalettedBlockStorageSingletonIndexTest {
    @Test
    void singletonAndItsCopyNeedNoReverseMap() throws Exception {
        for (BitArrayVersion width : BitArrayVersion.values()) {
            var storage = PalettedBlockStorage.createWithDefaultState(width, 17);
            for (int cell = 0; cell < 4096; cell++) storage.setBlock(cell, 17);
            assertNull(index(storage));
            var copy = storage.copy();
            assertNull(index(copy));
            assertArrayEquals(wire(storage), wire(copy));
            copy.setBlock(2048, 23);
            assertNotNull(index(copy));
            for (int cell = 0; cell < 4096; cell++) {
                assertEquals(17, storage.getBlock(cell));
                assertEquals(cell == 2048 ? 23 : 17, copy.getBlock(cell));
            }
        }
    }

    @Test
    void growthHasExactlyTheFormerPaletteIndicesAndBytes() throws Exception {
        var actual = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V2, 7);
        var eager = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V2, 7);
        // Install the old eager reverse index, independent of the lazy creation path.
        var map = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(16);
        map.defaultReturnValue(-1); map.put(7, 0); indexField().set(eager, map);
        for (int i = 0; i < 4096; i++) {
            int id = i % 257 == 0 ? 7 : i % 257;
            assertEquals(eager.setBlockReturningIndex(i, id), actual.setBlockReturningIndex(i, id));
        }
        assertArrayEquals(wire(eager), wire(actual));
        assertArrayEquals(disk(eager), disk(actual));
    }

    @Test
    void diskReloadReleasesOldIndexAndCanGrowAgain() throws Exception {
        var storage = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 1);
        for (int i = 0; i < 256; i++) storage.setBlock(i, i + 2);
        assertNotNull(index(storage));
        var singleton = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 13);
        read(storage, disk(singleton));
        assertNull(index(storage));
        assertArrayEquals(wire(singleton), wire(storage));
        storage.setBlock(4095, 42);
        assertEquals(13, storage.getBlock(4094));
        assertEquals(42, storage.getBlock(4095));
        var restored = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 0);
        read(restored, disk(storage));
        assertArrayEquals(wire(storage), wire(restored));
    }

    @Test
    void duplicateDiskAndCopiedEntriesStillResolveToLastIndex() {
        var bits = BitArrayVersion.V2.createPalette(4096);
        var duplicate = new PalettedBlockStorage(bits, new IntArrayList(new int[]{7, 11, 7}));
        var copy = duplicate.copy();
        var loaded = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 0);
        read(loaded, disk(duplicate));
        for (var storage : new PalettedBlockStorage[]{duplicate, copy, loaded}) {
            assertEquals(2, storage.setBlockReturningIndex(0, 7));
            assertEquals(1, storage.setBlockReturningIndex(1, 11));
        }
        assertArrayEquals(wire(duplicate), wire(copy));
        assertArrayEquals(wire(duplicate), wire(loaded));
    }

    private static Field indexField() throws Exception {
        Field f = PalettedBlockStorage.class.getDeclaredField("paletteIndexLookup");
        f.setAccessible(true); return f;
    }
    private static Object index(PalettedBlockStorage storage) throws Exception { return indexField().get(storage); }
    private static byte[] wire(PalettedBlockStorage storage) {
        var stream = new BinaryStream(); storage.writeTo(stream); return stream.getBuffer();
    }
    private static byte[] disk(PalettedBlockStorage storage) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            storage.writeToStorage(buffer);
            byte[] result = new byte[buffer.readableBytes()]; buffer.readBytes(result); return result;
        } finally { buffer.release(); }
    }
    private static void read(PalettedBlockStorage storage, byte[] data) {
        ByteBuf buffer = Unpooled.wrappedBuffer(data);
        try { storage.readFromStorage(buffer); assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
    }
}
