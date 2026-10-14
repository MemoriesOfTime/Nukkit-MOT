package cn.nukkit.level.util;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PalettedBlockStorageCopyIndexTest {
    @Test
    void sourceCopyAndCopyOfCopyOwnTheirCellsAndPalettesAtEveryWidth() {
        for (BitArrayVersion width : BitArrayVersion.values()) {
            int[] ids = ids(width);
            var source = populated(width, ids);
            var copy = source.copy();
            var secondCopy = copy.copy();
            assertEquivalent(source, copy);
            assertEquivalent(source, secondCopy);
            assertFalse(source.contentEquals(null));

            source.setBlock(0, 10001);
            copy.setBlock(2048, 10002);
            int added = secondCopy.setBlockReturningIndex(4095, 10003);
            secondCopy.setPaletteIndex(4094, added);
            for (int cell = 0; cell < 4096; cell++) {
                int original = ids[cell % ids.length];
                assertEquals(cell == 0 ? 10001 : original, source.getBlock(cell));
                assertEquals(cell == 2048 ? 10002 : original, copy.getBlock(cell));
                assertEquals(cell >= 4094 ? 10003 : original, secondCopy.getBlock(cell));
            }
            assertFalse(source.contentEquals(copy));
            assertFalse(copy.contentEquals(secondCopy));
        }
    }

    @Test
    void wireAndDiskBytesMatchTheFormatAndConvertersVisitEveryPaletteEntry() {
        for (BitArrayVersion width : BitArrayVersion.values()) {
            int[] ids = ids(width);
            for (var storage : List.of(populated(width, ids), populated(width, ids).copy())) {
                assertArrayEquals(encoded(width, ids, false), wire(storage, null));
                assertArrayEquals(encoded(width, ids, true), disk(storage));
                assertArrayEquals(wire(storage, null), wire(storage));
                var visits = new ArrayList<Integer>();
                int[] converted = Arrays.stream(ids).map(id -> -id - 1).toArray();
                assertArrayEquals(encoded(width, converted, false), wire(storage, id -> {
                    visits.add(id);
                    return -id - 1;
                }));
                assertEquals(Arrays.stream(ids).boxed().toList(), visits);
                assertArrayEquals(encoded(width, ids, true), disk(storage));
            }
        }
    }

    @Test
    void duplicateEntriesSurviveCopyAndDiskAndResolveToTheLastIndex() {
        int[] ids = {7, 11, 7, 11};
        var original = populated(BitArrayVersion.V2, ids);
        var copy = original.copy();
        var restored = PalettedBlockStorage.createWithDefaultState(0);
        read(restored, disk(original));
        for (var storage : List.of(original, copy, copy.copy(), restored)) {
            assertArrayEquals(encoded(BitArrayVersion.V2, ids, false), wire(storage));
            var visits = new ArrayList<Integer>();
            wire(storage, id -> { visits.add(id); return id; });
            assertEquals(List.of(7, 11, 7, 11), visits);
            assertEquals(2, storage.setBlockReturningIndex(0, 7));
            assertEquals(3, storage.setBlockReturningIndex(1, 11));
            assertEquals(4, storage.setBlockReturningIndex(2, 23));
            assertEquals(2, storage.setBlockReturningIndex(3, 7));
            assertEquals(4, storage.setBlockReturningIndex(4, 23));
        }
        assertEquivalent(original, copy);
        assertEquivalent(original, restored);
    }

    @Test
    void firstAndRepeatedMutationsOfACopyPreserveIndicesAcrossGrowth() {
        for (BitArrayVersion width : BitArrayVersion.values()) {
            int[] ids = ids(width);
            var source = populated(width, ids);
            var copy = source.copy();
            var control = populated(width, ids);
            assertEquals(ids.length - 1, copy.setBlockReturningIndex(0, ids[ids.length - 1]));
            control.setBlock(0, ids[ids.length - 1]);
            for (int i = 0; i < 260; i++) {
                int id = 20000 + i;
                int expectedIndex = ids.length + i;
                assertEquals(expectedIndex, copy.setBlockReturningIndex(i + 1, id));
                assertEquals(expectedIndex, control.setBlockReturningIndex(i + 1, id));
                assertEquals(expectedIndex, copy.setBlockReturningIndex(4095 - i, id));
                control.setPaletteIndex(4095 - i, expectedIndex);
            }
            assertEquals((BitArrayVersion.V16.getId() << 1) | 1, wire(copy)[0] & 0xff);
            assertEquivalent(control, copy);
            assertEquivalent(copy, copy.copy());
            assertArrayEquals(encoded(width, ids, false), wire(source));
        }
    }

    @Test
    void copiedStorageCanReloadMultiThenSingletonThenMultiAndGrowAgain() {
        var original = populated(BitArrayVersion.V3, new int[]{3, 5, 8, 13, 21});
        var copy = original.copy();
        var control = original.copy();
        byte[] originalBytes = disk(original);
        var singleton = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 89);
        var duplicates = populated(BitArrayVersion.V2, new int[]{17, 23, 17});
        for (byte[] input : List.of(disk(duplicates), disk(singleton), originalBytes, disk(duplicates))) {
            read(copy, input);
            read(control, input);
            assertEquivalent(control, copy);
            assertArrayEquals(input, disk(copy));
        }
        assertEquals(2, copy.setBlockReturningIndex(0, 17));
        assertEquals(3, copy.setBlockReturningIndex(1, 31));
        assertEquals(4, copy.setBlockReturningIndex(2, 37));
        assertEquals(17, copy.getBlock(0));
        assertEquals(37, copy.getBlock(2));
        assertArrayEquals(originalBytes, disk(original));
        var restored = PalettedBlockStorage.createWithDefaultState(0);
        read(restored, disk(copy));
        assertEquivalent(copy, restored);
    }

    @Test
    void malformedReadConsumesTheSameBytesAndRecoveryRestoresMutableCopies() {
        int[] ids = {7, 11, 19};
        byte[] valid = disk(populated(BitArrayVersion.V2, ids));
        byte[] tooManyEntries = valid.clone();
        int sizeOffset = 1 + BitArrayVersion.V2.getWordsForSize(4096) * Integer.BYTES;
        tooManyEntries[sizeOffset] = 5;
        List<byte[]> malformed = List.of(new byte[0], new byte[]{15},
                Arrays.copyOf(valid, 5), tooManyEntries, Arrays.copyOf(valid, valid.length - 1));
        for (byte[] input : malformed) {
            var direct = populated(BitArrayVersion.V2, ids);
            var copy = direct.copy();
            ByteBuf directBuffer = Unpooled.wrappedBuffer(input);
            ByteBuf copyBuffer = Unpooled.wrappedBuffer(input);
            try {
                RuntimeException expected = assertThrows(RuntimeException.class,
                        () -> direct.readFromStorage(directBuffer));
                RuntimeException actual = assertThrows(RuntimeException.class,
                        () -> copy.readFromStorage(copyBuffer));
                assertEquals(expected.getClass(), actual.getClass());
                assertEquals(expected.getMessage(), actual.getMessage());
                assertEquals(directBuffer.readerIndex(), copyBuffer.readerIndex());
                assertArrayEquals(wire(direct), wire(copy));
                assertArrayEquals(disk(direct), disk(copy));
            } finally {
                directBuffer.release();
                copyBuffer.release();
            }
            read(direct, valid);
            read(copy, valid);
            assertEquals(1, copy.setBlockReturningIndex(0, 11));
            direct.setBlock(0, 11);
            assertEquals(3, copy.setBlockReturningIndex(4095, 97));
            direct.setBlock(4095, 97);
            assertEquivalent(direct, copy);
        }
    }

    @Test
    void protectedConstructionVirtualHooksAndFailedWriteOrderingStayCompatible() {
        var defaults = new ApiStorage(BitArrayVersion.V2, 7);
        assertEquivalent(PalettedBlockStorage.createWithDefaultState(7), defaults);
        defaults.wireHeaderCalls = 0;
        defaults.headerCalls = 0;
        defaults.setBlock(1, 2, 3, 19);
        assertEquals(19, defaults.getBlock(new BlockVector3(1, 2, 3)));
        assertEquals(2, defaults.indexCalls);
        wire(defaults);
        assertEquals(1, defaults.wireHeaderCalls);
        assertEquals(1, defaults.headerCalls);
        disk(defaults);
        assertEquals(2, defaults.headerCalls);

        var bits = BitArrayVersion.V1.createPalette(4096);
        var palette = new IntArrayList(new int[]{7, 11});
        var source = new ApiStorage(bits, palette);
        var snapshot = source.copy();
        bits.set(0, 1);
        palette.set(0, 13);
        assertEquals(11, source.getBlock(0));
        assertEquals(13, source.getBlock(1));
        assertEquals(7, snapshot.getBlock(0));
        assertEquals(7, snapshot.getBlock(1));
        assertThrows(NullPointerException.class, () -> new ApiStorage(bits, null));

        for (var storage : List.of(populated(BitArrayVersion.V1, new int[]{7, 11}), snapshot)) {
            assertThrows(IndexOutOfBoundsException.class, () -> storage.setBlockReturningIndex(-1, 23));
            assertEquals(2, storage.setBlockReturningIndex(0, 23));
            assertEquals(3, storage.setBlockReturningIndex(1, 29));
            assertThrows(IndexOutOfBoundsException.class, () -> storage.setBlock(4096, 31));
            assertEquals(4, storage.setBlockReturningIndex(2, 31));
            assertEquals(23, storage.getBlock(0));
            assertEquals(29, storage.getBlock(1));
            assertEquals(31, storage.getBlock(2));
        }
    }

    private static int[] ids(BitArrayVersion width) {
        return Arrays.copyOf(new int[]{7, 11, 127, 128, 65536}, Math.min(5, width.getMaxEntryValue() + 1));
    }

    private static PalettedBlockStorage populated(BitArrayVersion width, int[] ids) {
        BitArray bits = width.createPalette(4096);
        for (int cell = 0; cell < 4096; cell++) bits.set(cell, cell % ids.length);
        return new PalettedBlockStorage(bits, new IntArrayList(ids));
    }

    private static void assertEquivalent(PalettedBlockStorage expected, PalettedBlockStorage actual) {
        assertTrue(expected.contentEquals(actual));
        assertTrue(actual.contentEquals(expected));
        assertEquals(expected.isEmpty(), actual.isEmpty());
        for (int cell = 0; cell < 4096; cell++) assertEquals(expected.getBlock(cell), actual.getBlock(cell));
        assertArrayEquals(wire(expected), wire(actual));
        assertArrayEquals(disk(expected), disk(actual));
    }

    private static byte[] wire(PalettedBlockStorage storage) {
        var stream = new BinaryStream();
        storage.writeTo(stream);
        return stream.getBuffer();
    }

    private static byte[] wire(PalettedBlockStorage storage, Int2IntFunction converter) {
        var stream = new BinaryStream();
        storage.writeTo(stream, converter);
        return stream.getBuffer();
    }

    private static byte[] disk(PalettedBlockStorage storage) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            storage.writeToStorage(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static void read(PalettedBlockStorage storage, byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try { storage.readFromStorage(buffer); assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
    }

    // Independent format oracle: fixed-width packed words plus LE disk IDs or signed wire varints.
    private static byte[] encoded(BitArrayVersion width, int[] ids, boolean disk) {
        int bits = disk && ids.length == 1 ? 0 : width.getId();
        var out = new ByteArrayOutputStream();
        out.write((bits << 1) | 1);
        if (bits != 0) {
            int perWord = 32 / bits;
            for (int first = 0; first < 4096; first += perWord) {
                int word = 0;
                for (int entry = 0; entry < perWord && first + entry < 4096; entry++) {
                    word |= ((first + entry) % ids.length) << (entry * bits);
                }
                littleInt(out, word);
            }
            if (disk) littleInt(out, ids.length); else signedVarInt(out, ids.length);
        }
        for (int id : ids) {
            if (disk) littleInt(out, id); else signedVarInt(out, id);
        }
        return out.toByteArray();
    }

    private static void littleInt(ByteArrayOutputStream out, int value) {
        for (int shift = 0; shift < 32; shift += 8) out.write(value >>> shift & 0xff);
    }

    private static void signedVarInt(ByteArrayOutputStream out, int value) {
        int encoded = (value << 1) ^ (value >> 31);
        while ((encoded & ~0x7f) != 0) {
            out.write((encoded & 0x7f) | 0x80);
            encoded >>>= 7;
        }
        out.write(encoded);
    }

    private static final class ApiStorage extends PalettedBlockStorage {
        private int indexCalls;
        private int wireHeaderCalls;
        private int headerCalls;

        private ApiStorage(BitArrayVersion width, int state) { super(width, state); }
        private ApiStorage(BitArray bits, IntList ids) { super(bits, ids); }
        @Override protected int getIndex(int x, int y, int z) {
            indexCalls++;
            return super.getIndex(x, y, z);
        }
        @Override protected int getPaletteHeader(BitArrayVersion width) {
            wireHeaderCalls++;
            return super.getPaletteHeader(width);
        }
        @Override protected int getPaletteHeader(BitArrayVersion width, boolean runtime) {
            headerCalls++;
            return super.getPaletteHeader(width, runtime);
        }
    }
}
