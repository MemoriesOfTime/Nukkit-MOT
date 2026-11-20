package cn.nukkit.level.util;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PalettedBlockStorageCowTest {
    @ParameterizedTest
    @EnumSource(BitArrayVersion.class)
    void everyWriterKeepsSnapshotsAndOldLogicEquivalent(BitArrayVersion width) {
        List<Consumer<PalettedBlockStorage>> writers = List.of(
                storage -> storage.setBlock(0, 23),
                storage -> storage.setBlock(0, 0, 0, 23),
                storage -> storage.setBlock(new BlockVector3(), 23),
                storage -> assertEquals(1, storage.setBlockReturningIndex(0, 23)),
                storage -> storage.setPaletteIndex(0, 1),
                storage -> {
                    for (int i = 0; i < 300; i++) storage.setBlock(i, 1000 + i);
                },
                storage -> read(storage, disk(populated(BitArrayVersion.V3))),
                storage -> read(storage, disk(PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 71)))
        );
        for (Consumer<PalettedBlockStorage> writer : writers) {
            var live = PalettedBlockStorage.createWithDefaultState(width, 7);
            live.setBlock(4095, 23);
            var eager = eager(live);
            var first = live.copy();
            var second = first.copy();
            byte[] before = wire(live), diskBefore = disk(live);
            writer.accept(live);
            writer.accept(eager);
            assertEquivalent(eager, live);
            assertArrayEquals(before, wire(first));
            assertArrayEquals(before, wire(second));
            assertArrayEquals(diskBefore, disk(first));
            assertFalse(java.util.Arrays.equals(before, wire(live)));
            var third = live.copy();
            byte[] after = wire(third);
            first.setBlock(2048, 97);
            assertEquals(97, first.getBlock(2048));
            assertArrayEquals(before, wire(second));
            assertArrayEquals(after, wire(live));
            assertArrayEquals(after, wire(third));
        }
    }

    @Test
    void singletonGrowthAndFailedReloadCannotChangeSharedOwners() {
        var live = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 7);
        var snapshot = live.copy();
        live.setBlock(0, 11);
        assertEquals(7, snapshot.getBlock(0));
        assertEquals(11, live.getBlock(0));
        for (byte[] malformed : List.of(new byte[0], new byte[]{15}, new byte[]{5, 0})) {
            var source = populated(BitArrayVersion.V2);
            var copy = source.copy();
            byte[] before = wire(source);
            assertThrows(RuntimeException.class, () -> read(copy, malformed));
            assertArrayEquals(before, wire(source));
        }
        var original = populated(BitArrayVersion.V1);
        var copy = original.copy();
        byte[] before = wire(original);
        assertThrows(IndexOutOfBoundsException.class, () -> copy.setBlock(-1, 97));
        assertArrayEquals(before, wire(original));
    }

    @Test
    void firstWriteDetachesOnceAndExternalAliasesKeepEagerCopies() {
        var live = populated(BitArrayVersion.V3);
        var first = live.copy();
        assertSame(field(live, "bitArray"), field(first, "bitArray"));
        assertSame(field(live, "palette"), field(first, "palette"));
        live.setBlock(0, 23);
        Object detached = field(live, "bitArray"), palette = field(live, "palette");
        assertNotSame(detached, field(first, "bitArray"));
        assertNotSame(palette, field(first, "palette"));
        live.setBlock(1, 23);
        assertSame(detached, field(live, "bitArray"));
        assertSame(palette, field(live, "palette"));
        var second = live.copy();
        live.setBlock(2, 23);
        assertNotSame(detached, field(live, "bitArray"));
        assertSame(detached, field(second, "bitArray"));

        BitArray bits = BitArrayVersion.V2.createPalette();
        var ids = new IntArrayList(new int[]{7, 23});
        var external = new PalettedBlockStorage(bits, ids);
        var snapshot = external.copy();
        bits.getWords()[0] = 1;
        ids.set(0, 71);
        assertEquals(23, external.getBlock(0));
        assertEquals(71, external.getBlock(1));
        assertEquals(7, snapshot.getBlock(0));
        assertEquals(7, snapshot.getBlock(1));
    }

    private static PalettedBlockStorage populated(BitArrayVersion width) {
        var storage = PalettedBlockStorage.createWithDefaultState(width, 7);
        for (int i = 0; i < 4096; i++) storage.setBlock(i, (i & 1) == 0 ? 7 : 23);
        return storage;
    }

    // Original copy logic, independent of the new shared-copy path.
    private static PalettedBlockStorage eager(PalettedBlockStorage source) {
        return new PalettedBlockStorage(source.bitArray.copy(), new IntArrayList(source.palette));
    }

    private static void assertEquivalent(PalettedBlockStorage expected, PalettedBlockStorage actual) {
        for (int i = 0; i < 4096; i++) assertEquals(expected.getBlock(i), actual.getBlock(i));
        assertEquals(expected.isEmpty(), actual.isEmpty());
        assertArrayEquals(wire(expected), wire(actual));
        assertArrayEquals(disk(expected), disk(actual));
    }

    private static byte[] wire(PalettedBlockStorage storage) {
        var stream = new BinaryStream();
        storage.writeTo(stream);
        return stream.getBuffer();
    }

    private static byte[] disk(PalettedBlockStorage storage) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            storage.writeToStorage(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void read(PalettedBlockStorage storage, byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            storage.readFromStorage(buffer);
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    private static Object field(PalettedBlockStorage storage, String name) {
        try {
            Field field = PalettedBlockStorage.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(storage);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
