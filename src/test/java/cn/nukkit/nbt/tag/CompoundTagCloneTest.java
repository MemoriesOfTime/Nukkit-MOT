package cn.nukkit.nbt.tag;

import cn.nukkit.nbt.NBTIO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompoundTagCloneTest {

    @Test
    void namedClonePreservesName() {
        CompoundTag source = new CompoundTag("root-Ж-😀").putInt("value", 17);
        CompoundTag clone = source.clone();

        assertEquals("root-Ж-😀", clone.getName(), "CLONE_NAME");
        assertNotSame(source, clone);
        assertEquals(17, clone.getInt("value"));
        assertEquals("root-Ж-😀", source.getName());
        assertEquals(source, clone);

        source.setName("renamed-根");
        assertEquals("renamed-根", source.clone().getName());
        assertEquals("root-Ж-😀", clone.getName());
    }

    @Test
    void unnamedClonePreservesNormalizationAndDeepIndependence() {
        assertEquals("", new CompoundTag().clone().getName());
        assertEquals("", new CompoundTag("").clone().getName());
        assertEquals("", new CompoundTag((String) null).clone().getName());
        CompoundTag reset = new CompoundTag("discarded");
        reset.setName(null);
        assertEquals("", reset.clone().getName());

        CompoundTag source = fixture("");
        CompoundTag clone = source.clone();
        assertEquals("", clone.getName());
        assertNotSame(source, clone);
        assertEquals(source, clone);
        assertPayload(clone);
        assertNotSame(source.get("bytes"), clone.get("bytes"));
        assertNotSame(source.getByteArray("bytes"), clone.getByteArray("bytes"));
        assertNotSame(source.getIntArray("ints"), clone.getIntArray("ints"));
        assertNotSame(source.getCompound("nested"), clone.getCompound("nested"));
        ListTag<ByteArrayTag> sourceList = source.getList("list", ByteArrayTag.class);
        ListTag<ByteArrayTag> cloneList = clone.getList("list", ByteArrayTag.class);
        assertNotSame(sourceList, cloneList);
        assertNotSame(sourceList.get(0), cloneList.get(0));
        assertNotSame(sourceList.get(0).data, cloneList.get(0).data);

        clone.getByteArray("bytes")[0] = 99;
        clone.getIntArray("ints")[0] = 99;
        clone.getCompound("nested").putString("b", "changed");
        cloneList.get(0).data[0] = 99;
        clone.putInt("clone-only", 1);
        assertPayload(source);
        assertFalse(source.contains("clone-only"));

        source.getByteArray("bytes")[1] = 88;
        source.getIntArray("ints")[1] = 88;
        source.getCompound("nested").putInt("a", 88);
        sourceList.add(new ByteArrayTag("", new byte[]{88}));
        source.putInt("source-only", 2);
        assertEquals(2, clone.getByteArray("bytes")[1]);
        assertEquals(20, clone.getIntArray("ints")[1]);
        assertEquals(7, clone.getCompound("nested").getInt("a"));
        assertEquals(1, cloneList.size());
        assertFalse(clone.contains("source-only"));
    }

    @Test
    void namedCloneKeepsNativeSerializedNameAndPayload() throws IOException {
        CompoundTag source = new CompoundTag("root-Ж-😀").putInt("value", 17);
        CompoundTag clone = source.clone();
        CompoundTag richSource = fixture("root-Ж-😀");
        CompoundTag richClone = richSource.clone();

        for (ByteOrder order : List.of(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            byte[] expected = NBTIO.write(source, order);
            byte[] actual = NBTIO.write(clone, order);
            assertArrayEquals(expected, actual, "CLONE_NBT_BYTES " + order);
            CompoundTag decodedSource = NBTIO.read(expected, order);
            CompoundTag decodedClone = NBTIO.read(actual, order);
            assertEquals("root-Ж-😀", decodedClone.getName());
            assertEquals(17, decodedClone.getInt("value"));
            assertEquals(source, decodedSource);
            assertEquals(source, decodedClone);

            CompoundTag richDecodedSource = NBTIO.read(NBTIO.write(richSource, order), order);
            CompoundTag richDecodedClone = NBTIO.read(NBTIO.write(richClone, order), order);
            assertEquals("root-Ж-😀", richDecodedClone.getName());
            assertEquals(richSource, richDecodedSource);
            assertEquals(richSource, richDecodedClone);
            assertPayload(richDecodedSource);
            assertPayload(richDecodedClone);
        }

        byte[] expected = NBTIO.writeNetwork(source);
        byte[] actual = NBTIO.writeNetwork(clone);
        assertArrayEquals(expected, actual, "CLONE_NBT_BYTES network");
        CompoundTag decodedSource = (CompoundTag) NBTIO.readNetwork(new ByteArrayInputStream(expected));
        CompoundTag decodedClone = (CompoundTag) NBTIO.readNetwork(new ByteArrayInputStream(actual));
        assertEquals("root-Ж-😀", decodedClone.getName());
        assertEquals(17, decodedClone.getInt("value"));
        assertEquals(source, decodedSource);
        assertEquals(source, decodedClone);

        CompoundTag richDecodedSource = (CompoundTag) NBTIO.readNetwork(
                new ByteArrayInputStream(NBTIO.writeNetwork(richSource)));
        CompoundTag richDecodedClone = (CompoundTag) NBTIO.readNetwork(
                new ByteArrayInputStream(NBTIO.writeNetwork(richClone)));
        assertEquals("root-Ж-😀", richDecodedClone.getName());
        assertEquals(richSource, richDecodedSource);
        assertEquals(richSource, richDecodedClone);
        assertPayload(richDecodedSource);
        assertPayload(richDecodedClone);
    }

    private static CompoundTag fixture(String name) {
        CompoundTag root = new CompoundTag(name);
        root.putInt("z", 17);
        root.putByteArray("bytes", new byte[]{1, 2, 3});
        root.putIntArray("ints", new int[]{10, 20, 30});
        CompoundTag nested = new CompoundTag();
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
