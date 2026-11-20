package cn.nukkit.nbt.tag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListTagCopyCapacityTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 11, 36, 256, 4096})
    void preservesNameTypeOrderAndDeepCopies(int size) {
        ListTag<CompoundTag> source = new ListTag<>("items-Ж");
        for (int i = 0; i < size; i++) {
            source.add(new CompoundTag().putInt("slot", i)
                    .putByteArray("bytes", new byte[]{1, 2})
                    .putList(new ListTag<IntTag>("nested").add(new IntTag(i))));
        }
        source.type = Tag.TAG_Compound;
        ListTag<?> copy = (ListTag<?>) source.copy();
        assertEquals(source, copy);
        assertEquals("items-Ж", copy.getName());
        assertEquals(Tag.TAG_Compound, copy.type);
        assertNotSame(source.getAllUnsafe(), copy.getAllUnsafe());
        for (int i = 0; i < size; i++) {
            CompoundTag child = (CompoundTag) copy.get(i);
            assertEquals(i, child.getInt("slot"));
            assertNotSame(source.get(i), child);
            assertNotSame(source.get(i).getByteArray("bytes"), child.getByteArray("bytes"));
            assertNotSame(source.get(i).getList("nested"), child.getList("nested"));
            child.getByteArray("bytes")[0] = 9;
            assertEquals(1, source.get(i).getByteArray("bytes")[0]);
        }
    }

    @Test
    void readsNameBeforeTypeAndChildren() {
        List<String> events = new ArrayList<>();
        ListTag<IntTag> source = new ListTag<>("old") {
            @Override
            public String getName() {
                events.add("name");
                type = Tag.TAG_Long;
                return "captured";
            }
        };
        source.add(new IntTag(3) {
            @Override
            public Tag copy() {
                events.add("child");
                source.type = Tag.TAG_Double;
                return super.copy();
            }
        });
        ListTag<?> result = (ListTag<?>) source.copy();
        assertEquals(List.of("name", "child"), events);
        assertEquals("captured", result.getName());
        assertEquals(Tag.TAG_Long, result.type);
        assertEquals(Tag.TAG_Double, source.type);
    }

    @Test
    void customListReceivesNoExtraSizeOrArrayCallbacks() {
        List<String> events = new ArrayList<>();
        List<IntTag> custom = new AbstractList<>() {
            @Override public IntTag get(int index) { throw new AssertionError("get"); }
            @Override public int size() { throw new AssertionError("size"); }
            @Override public Object[] toArray() { throw new AssertionError("toArray"); }
            @Override public Iterator<IntTag> iterator() {
                events.add("iterator");
                return List.<IntTag>of(new IntTag(4) {
                    @Override public Tag copy() {
                        events.add("copy");
                        return super.copy();
                    }
                }).iterator();
            }
        };
        ListTag<?> result = (ListTag<?>) new ListTag<>("custom", custom).copy();
        assertEquals(List.of("iterator", "copy"), events);
        assertEquals(4, ((IntTag) result.get(0)).data);
    }

    @Test
    void arrayListSubclassKeepsOverriddenSizeUnobserved() {
        ArrayList<IntTag> custom = new ArrayList<>() {
            @Override public int size() { throw new AssertionError("size"); }
        };
        custom.add(new IntTag(5));
        ListTag<?> result = (ListTag<?>) new ListTag<>(custom).copy();
        assertEquals(5, ((IntTag) result.get(0)).data);
    }

    @Test
    void childMutationStillFailsAtIteratorBoundary() {
        ListTag<IntTag> source = new ListTag<>();
        List<String> events = new ArrayList<>();
        source.add(new IntTag(1) {
            @Override public Tag copy() {
                events.add("first");
                source.add(new IntTag(3));
                return super.copy();
            }
        });
        source.add(new IntTag(2) {
            @Override public Tag copy() {
                events.add("second");
                return super.copy();
            }
        });
        assertThrows(ConcurrentModificationException.class, source::copy);
        assertEquals(List.of("first"), events);
    }

    @Test
    void preservesNullFailuresAndNameCallback() {
        List<String> events = new ArrayList<>();
        ListTag<IntTag> nullList = new ListTag<IntTag>("name", null) {
            @Override public String getName() { events.add("name"); return "name"; }
        };
        assertThrows(NullPointerException.class, nullList::copy);
        assertEquals(List.of("name"), events);
        ArrayList<IntTag> children = new ArrayList<>();
        children.add(null);
        assertThrows(NullPointerException.class, new ListTag<>(children)::copy);
    }
}
