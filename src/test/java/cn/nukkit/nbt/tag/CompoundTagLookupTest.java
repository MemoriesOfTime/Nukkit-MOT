package cn.nukkit.nbt.tag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompoundTagLookupTest {
    enum Kind {
        BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, STRING;
        Object read(CompoundTag tag, String key) {
            return switch (this) {
                case BYTE -> tag.getByte(key);
                case SHORT -> tag.getShort(key);
                case INT -> tag.getInt(key);
                case LONG -> tag.getLong(key);
                case FLOAT -> tag.getFloat(key);
                case DOUBLE -> tag.getDouble(key);
                case STRING -> tag.getString(key);
            };
        }
        Object original(Map<String, Tag> map, String key) {
            if (!map.containsKey(key)) return switch (this) {
                case BYTE, SHORT, INT -> 0;
                case LONG -> 0L;
                case FLOAT -> 0f;
                case DOUBLE -> 0d;
                case STRING -> "";
            };
            Tag tag = map.get(key);
            return switch (this) {
                case BYTE, SHORT, INT -> ((NumberTag) tag).getData().intValue();
                case LONG -> ((NumberTag) tag).getData().longValue();
                case FLOAT -> ((NumberTag) tag).getData().floatValue();
                case DOUBLE -> ((NumberTag) tag).getData().doubleValue();
                case STRING -> tag instanceof NumberTag ? String.valueOf(((NumberTag) tag).getData()) : ((StringTag) tag).data;
            };
        }
        void verify(Map<String, Tag> map, String key) {
            Object expected;
            try { expected = original(map, key); }
            catch (RuntimeException failure) {
                assertThrows(failure.getClass(), () -> read(new CompoundTag(map), key));
                return;
            }
            Object actual = read(new CompoundTag(map), key);
            if (expected instanceof Float f) assertEquals(Float.floatToRawIntBits(f), Float.floatToRawIntBits((Float) actual));
            else if (expected instanceof Double d) assertEquals(Double.doubleToRawLongBits(d), Double.doubleToRawLongBits((Double) actual));
            else assertEquals(expected, actual);
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void nativeMapsKeepAllConversionsMissingNullAndWrongType(Kind kind) {
        for (Map<String, Tag> map : List.of(new HashMap<String, Tag>(), new LinkedHashMap<String, Tag>())) {
            for (Tag value : Arrays.asList(new ByteTag((byte) -3), new ShortTag((short) -1000),
                    new IntTag(Integer.MIN_VALUE), new LongTag(Long.MIN_VALUE),
                    new FloatTag(Float.intBitsToFloat(0x7fc12345)), new FloatTag(-0f),
                    new DoubleTag(Double.longBitsToDouble(0x7ff8123456789000L)), new DoubleTag(-0d),
                    new StringTag("", "value"), new StringTag(""), new ListTag<>(), new CompoundTag(), null)) {
                map.put("Aa", value);
                map.put("BB", new IntTag(987)); // Equal string hashes exercise a collision.
                map.put(null, value);
                kind.verify(map, "Aa");
                kind.verify(map, null);
                kind.verify(map, "BB");
                kind.verify(map, "missing");
            }
            assertEquals(3, map.size());
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void customMapsKeepContainsThenGetAndDoNotUseGetOrDefault(Kind kind) {
        List<String> calls = new ArrayList<>();
        HashMap<String, Tag> map = new HashMap<>() {
            @Override public boolean containsKey(Object key) { calls.add("contains:" + key); return super.containsKey(key); }
            @Override public Tag get(Object key) { calls.add("get:" + key); return super.get(key); }
            @Override public Tag getOrDefault(Object key, Tag fallback) { throw new AssertionError("unexpected getOrDefault"); }
        };
        map.put("value", new IntTag(1234));
        CompoundTag tag = new CompoundTag(map);
        kind.read(tag, "value");
        assertEquals(List.of("contains:value", "get:value"), calls);
        calls.clear();
        kind.read(tag, "missing");
        assertEquals(List.of("contains:missing"), calls);
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void nativeAccessOrderedLinkedMapRelinksOnce(Kind kind) {
        Map<String, Tag> map = new LinkedHashMap<>(16, .75f, true);
        for (String key : List.of("first", "middle", "last")) map.put(key, new IntTag(10));
        kind.read(new CompoundTag(map), "first");
        assertEquals(List.of("middle", "last", "first"), new ArrayList<>(map.keySet()));
        kind.read(new CompoundTag(map), "absent");
        assertEquals(List.of("middle", "last", "first"), new ArrayList<>(map.keySet()));
    }

    @Test
    void customNumberDataStillDispatchesAndMayMutateMap() {
        Map<String, Tag> map = new HashMap<>();
        int[] reads = {0};
        map.put("value", new IntTag(1) {
            @Override public Integer getData() { reads[0]++; map.remove("value"); return 999; }
        });
        CompoundTag tag = new CompoundTag(map);
        assertEquals(999, tag.getInt("value"));
        assertEquals(1, reads[0]);
        assertEquals(0, tag.getInt("value"));
    }

    @Test
    void nonNativeMapEqualityAndNullBackingStillMatch() {
        Map<String, Tag> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        map.put("VaLuE", new IntTag(123));
        assertEquals(123, new CompoundTag(map).getInt("value"));
        CompoundTag invalid = new CompoundTag((Map<String, Tag>) null);
        for (Kind kind : Kind.values()) assertThrows(NullPointerException.class, () -> kind.read(invalid, "value"));
    }
}
