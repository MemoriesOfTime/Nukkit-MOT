package cn.nukkit.nbt.tag;

import cn.nukkit.nbt.NBTIO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkedCompoundTagCopyTest {

    @Test
    void namedCopyPreservesNameAndLinkedSubtype() {
        CompoundTag source = new LinkedCompoundTag("root-Ж-😀");
        source.putInt("value", 17);
        CompoundTag copy = source.copy();

        assertEquals("root-Ж-😀", copy.getName(), "COPY_NAME");
        assertEquals(LinkedCompoundTag.class, copy.getClass());
        assertNotSame(source, copy);
        assertEquals(17, copy.getInt("value"));
        assertEquals("root-Ж-😀", source.getName());

        source.setName("renamed-根");
        assertEquals("renamed-根", source.copy().getName());
        assertEquals("root-Ж-😀", copy.getName());
    }

    @Test
    void unnamedCopyPreservesOrderAndDeepIndependence() {
        assertEquals("", new LinkedCompoundTag().copy().getName());
        assertEquals("", new LinkedCompoundTag((String) null).copy().getName());
        LinkedCompoundTag reset = new LinkedCompoundTag("discarded");
        reset.setName(null);
        assertEquals("", reset.copy().getName());

        LinkedCompoundTag source = fixture("");
        LinkedCompoundTag copy = source.copy();
        assertEquals("", copy.getName());
        assertEquals(LinkedCompoundTag.class, copy.getClass());
        assertNotSame(source, copy);
        List<String> order = List.of("z", "bytes", "ints", "nested", "list", "a");
        assertEquals(order, new ArrayList<>(source.getTags().keySet()));
        assertEquals(order, new ArrayList<>(copy.getTags().keySet()));
        assertPayload(copy);
        assertNotSame(source.get("bytes"), copy.get("bytes"));
        assertNotSame(source.getByteArray("bytes"), copy.getByteArray("bytes"));
        assertNotSame(source.getIntArray("ints"), copy.getIntArray("ints"));
        assertNotSame(source.getCompound("nested"), copy.getCompound("nested"));
        assertEquals(LinkedCompoundTag.class, copy.getCompound("nested").getClass());
        assertEquals(List.of("b", "a"), new ArrayList<>(copy.getCompound("nested").getTags().keySet()));
        ListTag<ByteArrayTag> sourceList = source.getList("list", ByteArrayTag.class);
        ListTag<ByteArrayTag> copyList = copy.getList("list", ByteArrayTag.class);
        assertNotSame(sourceList, copyList);
        assertNotSame(sourceList.get(0), copyList.get(0));
        assertNotSame(sourceList.get(0).data, copyList.get(0).data);

        copy.getByteArray("bytes")[0] = 99;
        copy.getIntArray("ints")[0] = 99;
        copy.getCompound("nested").putString("b", "changed");
        copyList.get(0).data[0] = 99;
        copy.putInt("copy-only", 1);
        assertPayload(source);
        assertFalse(source.contains("copy-only"));

        source.getByteArray("bytes")[1] = 88;
        source.getIntArray("ints")[1] = 88;
        source.getCompound("nested").putInt("a", 88);
        sourceList.add(new ByteArrayTag("", new byte[]{88}));
        source.putInt("source-only", 2);
        assertEquals(2, copy.getByteArray("bytes")[1]);
        assertEquals(20, copy.getIntArray("ints")[1]);
        assertEquals(7, copy.getCompound("nested").getInt("a"));
        assertEquals(1, copyList.size());
        assertFalse(copy.contains("source-only"));
    }

    @Test
    void namedCopyKeepsNativeSerializedBytes() throws IOException {
        LinkedCompoundTag source = fixture("root-Ж-😀");
        LinkedCompoundTag copy = source.copy();
        for (ByteOrder order : List.of(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            byte[] expected = NBTIO.write(source, order);
            byte[] actual = NBTIO.write(copy, order);
            assertArrayEquals(expected, actual, "COPY_NBT_BYTES " + order);
            CompoundTag decoded = NBTIO.read(actual, order);
            assertEquals("root-Ж-😀", decoded.getName());
            assertPayload(decoded);
        }
        byte[] expected = NBTIO.writeNetwork(source);
        byte[] actual = NBTIO.writeNetwork(copy);
        assertArrayEquals(expected, actual, "COPY_NBT_BYTES network");
        CompoundTag decoded = (CompoundTag) NBTIO.readNetwork(new ByteArrayInputStream(actual));
        assertEquals("root-Ж-😀", decoded.getName());
        assertPayload(decoded);
    }

    private static LinkedCompoundTag fixture(String name) {
        LinkedCompoundTag root = new LinkedCompoundTag(name);
        root.putInt("z", 17);
        root.putByteArray("bytes", new byte[]{1, 2, 3});
        root.putIntArray("ints", new int[]{10, 20, 30});
        LinkedCompoundTag nested = new LinkedCompoundTag();
        nested.putString("b", "original");
        nested.putInt("a", 7);
        root.putCompound("nested", nested);
        ListTag<ByteArrayTag> list = new ListTag<>("list");
        list.add(new ByteArrayTag("", new byte[]{4, 5}));
        root.putList(list);
        root.putString("a", "tail");
        return root;
    }

    private static void assertPayload(CompoundTag root) {
        assertEquals(17, root.getInt("z"));
        assertArrayEquals(new byte[]{1, 2, 3}, root.getByteArray("bytes"));
        assertArrayEquals(new int[]{10, 20, 30}, root.getIntArray("ints"));
        assertEquals("original", root.getCompound("nested").getString("b"));
        assertEquals(7, root.getCompound("nested").getInt("a"));
        assertEquals(1, root.getList("list", ByteArrayTag.class).size());
        assertArrayEquals(new byte[]{4, 5}, root.getList("list", ByteArrayTag.class).get(0).data);
        assertEquals("tail", root.getString("a"));
    }
}
