package cn.nukkit.nbt.tag;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.stream.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class LongArrayTagTest {

    private static final long[] VALUES = {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE};

    @Test
    void tagFactoryAndNames() {
        Tag tag = Tag.newTag(Tag.TAG_Long_Array, "longs");
        assertInstanceOf(LongArrayTag.class, tag);
        assertEquals(Tag.TAG_Long_Array, tag.getId());
        assertEquals("TAG_Long_Array", Tag.getTagName(Tag.TAG_Long_Array));
    }

    @Test
    void compoundRoundTripsAllEncodingsWithoutTruncatingFollowingFields() throws IOException {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            for (boolean network : new boolean[]{false, true}) {
                CompoundTag root = new CompoundTag()
                        .putString("before", "kept")
                        .putLongArray("longs", VALUES)
                        .putInt("after", 42);

                CompoundTag read = NBTIO.read(NBTIO.write(root, order, network), order, network);

                assertEquals(3, read.getTags().size(), order + " network=" + network);
                assertEquals("kept", read.getString("before"));
                assertArrayEquals(VALUES, read.getLongArray("longs"));
                assertEquals(42, read.getInt("after"));
            }
        }
    }

    @Test
    void networkEncodingMatchesCloudburstNbt() throws IOException {
        CompoundTag mot = new CompoundTag().putLongArray("longs", VALUES);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try (org.cloudburstmc.nbt.NBTOutputStream writer = NbtUtils.createNetworkWriter(expected)) {
            writer.writeTag(NbtMap.builder().putLongArray("longs", VALUES).build());
        }

        assertArrayEquals(expected.toByteArray(), NBTIO.write(mot, ByteOrder.LITTLE_ENDIAN, true));
        assertArrayEquals(VALUES, NBTIO.read(expected.toByteArray(), ByteOrder.LITTLE_ENDIAN, true).getLongArray("longs"));
    }

    @Test
    void diskEncodingMatchesCloudburstNbt() throws IOException {
        CompoundTag mot = new CompoundTag().putLongArray("longs", VALUES);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try (org.cloudburstmc.nbt.NBTOutputStream writer = NbtUtils.createWriterLE(expected)) {
            writer.writeTag(NbtMap.builder().putLongArray("longs", VALUES).build());
        }

        assertArrayEquals(expected.toByteArray(), NBTIO.write(mot, ByteOrder.LITTLE_ENDIAN));
        assertArrayEquals(VALUES, NBTIO.read(expected.toByteArray(), ByteOrder.LITTLE_ENDIAN).getLongArray("longs"));
    }

    @Test
    void serializerPathWritesBothArrayTypes() throws IOException {
        CompoundTag root = new CompoundTag()
                .putIntArray("ints", new int[]{1, -2})
                .putLongArray("longs", VALUES);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (NBTOutputStream stream = new NBTOutputStream(out, ByteOrder.LITTLE_ENDIAN, true)) {
            stream.writeTag(root);
        }

        CompoundTag read = NBTIO.read(out.toByteArray(), ByteOrder.LITTLE_ENDIAN, true);
        assertArrayEquals(new int[]{1, -2}, read.getIntArray("ints"));
        assertArrayEquals(VALUES, read.getLongArray("longs"));
    }

    @Test
    void longArrayIsALegalListElement() throws IOException {
        ListTag<LongArrayTag> list = new ListTag<>("list");
        list.add(new LongArrayTag(new long[]{1, 2}));
        list.add(new LongArrayTag(new long[]{3}));
        CompoundTag root = new CompoundTag().putList(list);

        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            for (boolean network : new boolean[]{false, true}) {
                ListTag<LongArrayTag> read = NBTIO.read(NBTIO.write(root, order, network), order, network)
                        .getList("list", LongArrayTag.class);
                assertEquals(2, read.size());
                assertArrayEquals(new long[]{1, 2}, read.get(0).data);
                assertArrayEquals(new long[]{3}, read.get(1).data);
            }
        }
    }

    @Test
    void compoundAccessorsAndCopy() {
        CompoundTag root = new CompoundTag();
        assertFalse(root.containsLongArray("longs"));
        assertArrayEquals(new long[0], root.getLongArray("longs"));

        root.putLongArray("longs", new long[]{7, 8});
        assertTrue(root.containsLongArray("longs"));
        assertArrayEquals(new long[]{7, 8}, root.getLongArray("longs"));

        LongArrayTag tag = (LongArrayTag) root.get("longs");
        LongArrayTag copy = (LongArrayTag) tag.copy();
        assertEquals(tag, copy);
        assertEquals(tag.hashCode(), copy.hashCode());
        assertNotSame(tag.data, copy.data);
        assertArrayEquals(new long[]{7, 8}, copy.data);

        assertNull(root.putLongArrayIfAbsent("absent", new long[]{1}));
        assertArrayEquals(new long[]{1}, root.getLongArray("absent"));
        assertNotNull(root.putLongArrayIfAbsent("absent", new long[]{2}));
        assertArrayEquals(new long[]{1}, root.getLongArray("absent"));
    }

    @Test
    void snbtUsesLongArrayLiteral() {
        LongArrayTag tag = new LongArrayTag("longs", new long[]{1, -2});
        assertEquals("[L;1L,-2L]", tag.toSNBT());
        assertEquals("[L; 1L, -2L]", tag.toSNBT(4));
    }
}
