package cn.nukkit.nbt;

import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.stream.NBTOutputStream;
import cn.nukkit.nbt.tag.ByteArrayTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntArrayTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.nbt.tag.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Byte, int and string payloads are copied in bulk, yet a forged length still cannot reserve
 * more memory than the stream really carries.
 */
class NBTBulkReadTest {

    private static final int[] BYTE_SIZES = {0, 1, 64, 65, 4096, 65535, 65536, 65537, 131073, 300_000};
    private static final int[] INT_SIZES = {0, 1, 16, 17, 16384, 16385, 70_000};

    private static byte[] bytes(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    private static int[] ints(int size, long seed) {
        Random random = new Random(seed);
        int[] data = new int[size];
        for (int i = 0; i < size; i++) {
            // Mix small and large magnitudes so VarInt paths see 1..5 byte encodings.
            data[i] = (i % 3 == 0) ? random.nextInt() : random.nextInt(200) - 100;
        }
        return data;
    }

    private static String text(int codePoints) {
        StringBuilder builder = new StringBuilder();
        String alphabet = "aЖ€😀";
        for (int i = 0; i < codePoints; i++) {
            builder.append(alphabet, (i % 3) == 2 ? 2 : i % 2, (i % 3) == 2 ? 3 : i % 2 + 1);
        }
        return builder.toString();
    }

    private static CompoundTag sample() {
        CompoundTag root = new CompoundTag("");
        for (int size : BYTE_SIZES) {
            root.putByteArray("b" + size, bytes(size, size));
        }
        for (int size : INT_SIZES) {
            root.putIntArray("i" + size, ints(size, size));
        }
        for (int length : new int[]{0, 1, 63, 64, 65, 1000, 20_000}) {
            root.putString("s" + length, text(length));
        }
        ListTag<ByteArrayTag> list = new ListTag<>("list");
        list.add(new ByteArrayTag("", bytes(70_000, 7)));
        list.add(new ByteArrayTag("", bytes(3, 8)));
        root.putList(list);
        root.putCompound("nested", new CompoundTag().putByteArray("skin", bytes(262_144, 9))
                .putString("geometry", text(15_000)));
        return root;
    }

    private static void assertSame(CompoundTag expected, CompoundTag actual) {
        assertEquals(expected.getAllTags().size(), actual.getAllTags().size());
        for (Tag tag : expected.getAllTags()) {
            Tag other = actual.get(tag.getName());
            assertNotNull(other, tag.getName());
            if (tag instanceof ByteArrayTag bytes) {
                assertArrayEquals(bytes.data, ((ByteArrayTag) other).data, tag.getName());
            } else if (tag instanceof IntArrayTag ints) {
                assertArrayEquals(ints.data, ((IntArrayTag) other).data, tag.getName());
            } else if (tag instanceof StringTag string) {
                assertEquals(string.data, ((StringTag) other).data, tag.getName());
            } else if (tag instanceof CompoundTag compound) {
                assertSame(compound, (CompoundTag) other);
            } else if (tag instanceof ListTag<?> list) {
                ListTag<?> otherList = (ListTag<?>) other;
                assertEquals(list.size(), otherList.size());
                for (int i = 0; i < list.size(); i++) {
                    assertArrayEquals(((ByteArrayTag) list.get(i)).data, ((ByteArrayTag) otherList.get(i)).data);
                }
            } else {
                assertEquals(tag, other, tag.getName());
            }
        }
    }

    @Test
    void bigEndianFileFormatRoundTrips() throws IOException {
        CompoundTag root = sample();
        assertSame(root, NBTIO.read(NBTIO.write(root, ByteOrder.BIG_ENDIAN), ByteOrder.BIG_ENDIAN));
    }

    @Test
    void littleEndianDiskFormatRoundTrips() throws IOException {
        CompoundTag root = sample();
        assertSame(root, NBTIO.read(NBTIO.write(root, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void networkVarIntFormatRoundTrips() throws IOException {
        CompoundTag root = sample();
        Tag read = NBTIO.readNetwork(new ByteArrayInputStream(NBTIO.writeNetwork(root)));
        assertSame(root, (CompoundTag) read);
    }

    @Test
    void gzipProfileRoundTripsThroughAStream() throws IOException {
        CompoundTag root = sample();
        byte[] compressed = NBTIO.writeGZIPCompressed(root, ByteOrder.BIG_ENDIAN);
        // A stream that hands out at most 100 bytes per call, like a slow file or socket.
        InputStream trickle = new ByteArrayInputStream(compressed) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 100));
            }
        };
        assertSame(root, NBTIO.readCompressed(trickle));
    }

    @Test
    void untypedDeserializerReadsArraysAndStrings() throws IOException {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            for (boolean network : new boolean[]{false, true}) {
                CompoundTag root = new CompoundTag("")
                        .putByteArray("b", bytes(100_000, 1))
                        .putIntArray("i", ints(40_000, 2))
                        .putString("s", text(3_000));
                byte[] encoded = NBTIO.write(root, order, network);
                try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(encoded), order, network)) {
                    Object value = in.readTag();
                    assertInstanceOf(CompoundTag.class, value);
                    assertSame(root, (CompoundTag) value);
                }
            }
        }
    }

    /** Header of a named compound holding one byte array whose declared length is {@code length}. */
    private static byte[] forgedByteArray(int length, int actualBytes) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.writeByte(Tag.TAG_Compound);
        out.writeShort(0);
        out.writeByte(Tag.TAG_Byte_Array);
        out.writeShort(1);
        out.writeByte('a');
        out.writeInt(length);
        out.write(new byte[actualBytes]);
        return raw.toByteArray();
    }

    @Test
    void forgedLengthFailsWithoutReservingIt() throws IOException {
        // Allocating Integer.MAX_VALUE - 8 bytes would throw OutOfMemoryError in a test JVM.
        byte[] forged = forgedByteArray(Integer.MAX_VALUE - 8, 200_000);
        assertThrows(EOFException.class, () -> NBTIO.read(forged, ByteOrder.BIG_ENDIAN));
    }

    /** A named compound holding one int array whose declared length is {@code length}, in {@code order}. */
    private static byte[] forgedIntArray(ByteOrder order, int length, int actualBytes) {
        ByteBuffer raw = ByteBuffer.allocate(1 + 2 + 1 + 2 + 1 + 4 + actualBytes).order(order);
        raw.put((byte) Tag.TAG_Compound).putShort((short) 0);
        raw.put((byte) Tag.TAG_Int_Array).putShort((short) 1).put((byte) 'a');
        raw.putInt(length);
        return raw.array();
    }

    @Test
    void forgedIntArrayLengthFailsWithoutReservingIt() {
        // Item NBT sent by a client is read in the little-endian disk format. A forged int array
        // length must fail on the missing payload, not reserve an int[] of that length (2 GiB here).
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (ByteOrder order : new ByteOrder[]{ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
            byte[] forged = forgedIntArray(order, Integer.MAX_VALUE / Integer.BYTES, 200_000);
            long before = threads.getCurrentThreadAllocatedBytes();
            assertThrows(EOFException.class, () -> NBTIO.read(forged, order), order.toString());
            long allocated = threads.getCurrentThreadAllocatedBytes() - before;
            assertTrue(allocated < 16L << 20, order + ": allocated " + allocated + " bytes for a 200 KB payload");
        }
    }

    @Test
    void truncatedPayloadIsAnEndOfStream() throws IOException {
        byte[] encoded = NBTIO.write(new CompoundTag("").putByteArray("a", bytes(90_000, 3)), ByteOrder.BIG_ENDIAN);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1_000);
        assertThrows(EOFException.class, () -> NBTIO.read(truncated, ByteOrder.BIG_ENDIAN));
    }

    @Test
    void negativeLengthIsRejectedAsMalformedInput() throws IOException {
        byte[] negative = forgedByteArray(-5, 0);
        assertThrows(IOException.class, () -> NBTIO.read(negative, ByteOrder.BIG_ENDIAN));
    }

    @Test
    void networkStringLengthBeyondIntRangeIsRejected() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(Tag.TAG_String);
        raw.write(0); // empty root name
        // Unsigned VarInt 2^32 + 5: truncating it to an int would silently read 5 bytes.
        long length = (1L << 32) + 5;
        while ((length & ~0x7FL) != 0) {
            raw.write((int) ((length & 0x7F) | 0x80));
            length >>>= 7;
        }
        raw.write((int) length);
        raw.write("hello".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> NBTIO.readNetwork(new ByteArrayInputStream(raw.toByteArray())));
    }

    @Test
    void littleEndianStringOfMoreThan32KiBKeepsItsLength() throws IOException {
        // 40 000 ASCII bytes: the length prefix has its high bit set in the unsigned short.
        String long32k = "x".repeat(40_000);
        CompoundTag root = new CompoundTag("").putString("json", long32k);
        CompoundTag read = NBTIO.read(NBTIO.write(root, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN);
        assertEquals(long32k, read.getString("json"));
    }

    @Test
    void intArrayWriteMatchesElementWiseEncoding() throws IOException {
        int[] data = ints(1_000, 11);
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            for (boolean network : new boolean[]{false, true}) {
                ByteArrayOutputStream bulk = new ByteArrayOutputStream();
                try (NBTOutputStream out = new NBTOutputStream(bulk, order, network)) {
                    out.writeIntArray(data);
                }
                ByteArrayOutputStream single = new ByteArrayOutputStream();
                try (NBTOutputStream out = new NBTOutputStream(single, order, network)) {
                    out.writeInt(data.length);
                    for (int value : data) {
                        out.writeInt(value);
                    }
                }
                assertArrayEquals(single.toByteArray(), bulk.toByteArray(), order + " network=" + network);
            }
        }
    }
}
