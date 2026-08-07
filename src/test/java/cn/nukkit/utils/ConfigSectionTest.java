package cn.nukkit.utils;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigSectionTest {

    // ==================== 构造函数 ====================

    @Test
    public void testEmptyConstructor() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.isEmpty());
    }

    @Test
    public void testKeyValueConstructor() {
        ConfigSection section = new ConfigSection("key", "value");
        assertEquals("value", section.get("key"));
    }

    @Test
    public void testKeyValueConstructorNested() {
        ConfigSection section = new ConfigSection("a.b", "value");
        assertEquals("value", section.get("a.b"));
        assertTrue(section.get("a") instanceof ConfigSection);
    }

    @Test
    public void testLinkedHashMapConstructor() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("str", "hello");
        map.put("num", 42);

        LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", "value");
        map.put("child", nested);

        map.put("list", Arrays.asList(1, 2, 3));

        ConfigSection section = new ConfigSection(map);
        assertEquals("hello", section.get("str"));
        assertEquals(42, section.get("num"));
        assertInstanceOf(ConfigSection.class, section.get("child"));
        assertEquals("value", ((ConfigSection) section.get("child")).get("inner"));
        assertEquals(Arrays.asList(1, 2, 3), section.get("list"));
    }

    @Test
    public void testLinkedHashMapConstructorNull() {
        ConfigSection section = new ConfigSection((LinkedHashMap<String, Object>) null);
        assertTrue(section.isEmpty());
    }

    @Test
    public void testMapConstructor() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", "data");
        map.put("child", nested);

        ConfigSection section = new ConfigSection(map);
        assertEquals("value", section.get("key"));
        assertInstanceOf(ConfigSection.class, section.get("child"));
    }

    @Test
    public void testMapConstructorNull() {
        ConfigSection section = new ConfigSection((Map<String, Object>) null);
        assertTrue(section.isEmpty());
    }

    @Test
    public void testLinkedHashMapInListConvertedToConfigSection() {
        LinkedHashMap<String, Object> innerMap = new LinkedHashMap<>();
        innerMap.put("name", "item1");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("items", Arrays.asList(innerMap, "plain"));

        ConfigSection section = new ConfigSection(map);
        List<?> list = (List<?>) section.get("items");
        assertInstanceOf(ConfigSection.class, list.get(0));
        assertEquals("plain", list.get(1));
    }

    // ==================== get / set 嵌套路径 ====================

    @Test
    public void testSetAndGetFlat() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        assertEquals("value", section.get("key"));
    }

    @Test
    public void testSetAndGetNested() {
        ConfigSection section = new ConfigSection();
        section.set("a.b.c", "deep");
        assertEquals("deep", section.get("a.b.c"));
    }

    @Test
    public void testSetNestedPreservesExisting() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", 1);
        section.set("a.c", 2);
        assertEquals(1, section.get("a.b"));
        assertEquals(2, section.get("a.c"));
    }

    @Test
    public void testGetDefaultValue() {
        ConfigSection section = new ConfigSection();
        assertEquals("default", section.get("missing", "default"));
    }

    @Test
    public void testGetNullKey() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        assertNull(section.get(null));
        assertEquals("default", section.get(null, "default"));
    }

    @Test
    public void testGetEmptyKey() {
        ConfigSection section = new ConfigSection();
        assertNull(section.get(""));
        assertEquals("default", section.get("", "default"));
    }

    // ==================== remove ====================

    @Test
    public void testRemoveFlat() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        section.remove("key");
        assertNull(section.get("key"));
    }

    @Test
    public void testRemoveNested() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", "value");
        section.remove("a.b");
        assertNull(section.get("a.b"));
        // 父节点 "a" 仍然存在
        assertNotNull(section.get("a"));
    }

    @Test
    public void testRemoveDeeplyNested() {
        ConfigSection section = new ConfigSection();
        section.set("a.b.c.d", "deep");
        assertEquals("deep", section.get("a.b.c.d"));
        section.remove("a.b.c.d");
        assertNull(section.get("a.b.c.d"));
        // 中间节点仍然存在
        assertNotNull(section.get("a.b.c"));
    }

    @Test
    public void testRemoveNestedPreservesSiblings() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", 1);
        section.set("a.c", 2);
        section.remove("a.b");
        assertNull(section.get("a.b"));
        assertEquals(2, section.get("a.c"));
    }

    @Test
    public void testRemoveNonExistent() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        section.remove("nonexistent");
        assertEquals("value", section.get("key"));
    }

    @Test
    public void testRemoveNullKey() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        section.remove(null);
        assertEquals("value", section.get("key"));
    }

    @Test
    public void testRemoveEmptyKey() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        section.remove("");
        assertEquals("value", section.get("key"));
    }

    @Test
    public void testRemoveNestedNonExistentParent() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", "value");
        section.remove("x.y");
        assertEquals("value", section.get("a.b"));
    }

    // ==================== getAll / getAllMap ====================

    @Test
    public void testGetAll() {
        ConfigSection section = new ConfigSection();
        section.set("a", 1);
        section.set("b", 2);
        ConfigSection copy = section.getAll();
        assertEquals(1, copy.get("a"));
        assertEquals(2, copy.get("b"));
        // 修改副本不影响原始
        copy.set("a", 99);
        assertEquals(1, section.get("a"));
    }

    @Test
    public void testGetAllMap() {
        ConfigSection section = new ConfigSection();
        section.set("key", "value");
        Map<String, Object> map = section.getAllMap();
        assertInstanceOf(LinkedHashMap.class, map);
        assertEquals("value", map.get("key"));
    }

    // ==================== isSection / getSection / getSections ====================

    @Test
    public void testIsSection() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", "value");
        section.set("flat", "string");
        assertTrue(section.isSection("a"));
        assertFalse(section.isSection("flat"));
        assertFalse(section.isSection("nonexistent"));
    }

    @Test
    public void testGetSectionReturnsEmptyForMissing() {
        ConfigSection section = new ConfigSection();
        ConfigSection result = section.getSection("missing");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetSections() {
        ConfigSection section = new ConfigSection();
        section.set("a.x", 1);
        section.set("b.y", 2);
        section.set("c", "flat");
        ConfigSection sections = section.getSections();
        assertTrue(sections.containsKey("a"));
        assertTrue(sections.containsKey("b"));
        assertFalse(sections.containsKey("c"));
    }

    @Test
    public void testGetSectionsWithPath() {
        ConfigSection section = new ConfigSection();
        section.set("parent.child1.x", 1);
        section.set("parent.child2.y", 2);
        section.set("parent.flat", "string");
        ConfigSection sections = section.getSections("parent");
        assertTrue(sections.containsKey("child1"));
        assertTrue(sections.containsKey("child2"));
        assertFalse(sections.containsKey("flat"));
    }

    // ==================== 类型化 getter ====================

    @Test
    public void testGetInt() {
        ConfigSection section = new ConfigSection();
        section.set("num", 42);
        assertEquals(42, section.getInt("num"));
        assertEquals(0, section.getInt("missing"));
        assertEquals(99, section.getInt("missing", 99));
    }

    @Test
    public void testIsInt() {
        ConfigSection section = new ConfigSection();
        section.set("num", 42);
        section.set("str", "hello");
        assertTrue(section.isInt("num"));
        assertFalse(section.isInt("str"));
    }

    @Test
    public void testGetLong() {
        ConfigSection section = new ConfigSection();
        section.set("num", 100L);
        assertEquals(100L, section.getLong("num"));
        assertEquals(0L, section.getLong("missing"));
        assertEquals(50L, section.getLong("missing", 50));
    }

    @Test
    public void testIsLong() {
        ConfigSection section = new ConfigSection();
        section.set("num", 100L);
        assertTrue(section.isLong("num"));
        assertFalse(section.isLong("missing"));
    }

    @Test
    public void testGetDouble() {
        ConfigSection section = new ConfigSection();
        section.set("num", 3.14);
        assertEquals(3.14, section.getDouble("num"), 0.001);
        assertEquals(0.0, section.getDouble("missing"), 0.001);
        assertEquals(1.5, section.getDouble("missing", 1.5), 0.001);
    }

    @Test
    public void testIsDouble() {
        ConfigSection section = new ConfigSection();
        section.set("num", 3.14);
        assertTrue(section.isDouble("num"));
        assertFalse(section.isDouble("missing"));
    }

    @Test
    public void testGetString() {
        ConfigSection section = new ConfigSection();
        section.set("str", "hello");
        assertEquals("hello", section.getString("str"));
        assertEquals("", section.getString("missing"));
        assertEquals("default", section.getString("missing", "default"));
    }

    @Test
    public void testIsString() {
        ConfigSection section = new ConfigSection();
        section.set("str", "hello");
        section.set("num", 42);
        assertTrue(section.isString("str"));
        assertFalse(section.isString("num"));
    }

    @Test
    public void testGetBoolean() {
        ConfigSection section = new ConfigSection();
        section.set("flag", true);
        assertTrue(section.getBoolean("flag"));
        assertFalse(section.getBoolean("missing"));
        assertTrue(section.getBoolean("missing", true));
    }

    @Test
    public void testIsBoolean() {
        ConfigSection section = new ConfigSection();
        section.set("flag", true);
        assertTrue(section.isBoolean("flag"));
        assertFalse(section.isBoolean("missing"));
    }

    // ==================== List getter ====================

    @Test
    public void testGetList() {
        ConfigSection section = new ConfigSection();
        List<Integer> list = Arrays.asList(1, 2, 3);
        section.set("list", list);
        assertEquals(list, section.getList("list"));
        assertNull(section.getList("missing"));

        List<String> defaultList = Arrays.asList("a");
        assertEquals(defaultList, section.getList("missing", defaultList));
    }

    @Test
    public void testIsList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(1, 2));
        section.set("str", "hello");
        assertTrue(section.isList("list"));
        assertFalse(section.isList("str"));
    }

    @Test
    public void testGetStringList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList("a", 1, true, 'c'));
        List<String> result = section.getStringList("list");
        assertEquals(Arrays.asList("a", "1", "true", "c"), result);
    }

    @Test
    public void testGetStringListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getStringList("missing").isEmpty());
    }

    @Test
    public void testGetIntegerList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(1, "2", 3.5, 'A'));
        List<Integer> result = section.getIntegerList("list");
        assertEquals(Arrays.asList(1, 2, 3, 65), result);
    }

    @Test
    public void testGetIntegerListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getIntegerList("missing").isEmpty());
    }

    @Test
    public void testGetBooleanList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(true, false, "true", "false", "invalid"));
        List<Boolean> result = section.getBooleanList("list");
        assertEquals(Arrays.asList(true, false, true, false), result);
    }

    @Test
    public void testGetBooleanListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getBooleanList("missing").isEmpty());
    }

    @Test
    public void testGetDoubleList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(1.1, "2.2", 3));
        List<Double> result = section.getDoubleList("list");
        assertEquals(3, result.size());
        assertEquals(1.1, result.get(0), 0.001);
        assertEquals(2.2, result.get(1), 0.001);
        assertEquals(3.0, result.get(2), 0.001);
    }

    @Test
    public void testGetDoubleListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getDoubleList("missing").isEmpty());
    }

    @Test
    public void testGetFloatList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(1.1f, "2.2", 3));
        List<Float> result = section.getFloatList("list");
        assertEquals(3, result.size());
        assertEquals(1.1f, result.get(0), 0.001f);
        assertEquals(2.2f, result.get(1), 0.001f);
        assertEquals(3.0f, result.get(2), 0.001f);
    }

    @Test
    public void testGetFloatListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getFloatList("missing").isEmpty());
    }

    @Test
    public void testGetLongList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(100L, "200", 300));
        List<Long> result = section.getLongList("list");
        assertEquals(Arrays.asList(100L, 200L, 300L), result);
    }

    @Test
    public void testGetLongListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getLongList("missing").isEmpty());
    }

    @Test
    public void testGetByteList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList((byte) 1, "2", 3));
        List<Byte> result = section.getByteList("list");
        assertEquals(Arrays.asList((byte) 1, (byte) 2, (byte) 3), result);
    }

    @Test
    public void testGetByteListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getByteList("missing").isEmpty());
    }

    @Test
    public void testGetShortList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList((short) 1, "2", 3));
        List<Short> result = section.getShortList("list");
        assertEquals(Arrays.asList((short) 1, (short) 2, (short) 3), result);
    }

    @Test
    public void testGetShortListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getShortList("missing").isEmpty());
    }

    @Test
    public void testGetCharacterList() {
        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList('A', "B", 67));
        List<Character> result = section.getCharacterList("list");
        assertEquals(Arrays.asList('A', 'B', 'C'), result);
    }

    @Test
    public void testGetCharacterListMissing() {
        ConfigSection section = new ConfigSection();
        assertTrue(section.getCharacterList("missing").isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetMapList() {
        LinkedHashMap<String, Object> map1 = new LinkedHashMap<>();
        map1.put("id", 1);
        LinkedHashMap<String, Object> map2 = new LinkedHashMap<>();
        map2.put("id", 2);

        ConfigSection section = new ConfigSection();
        section.set("list", Arrays.asList(map1, "not-a-map", map2));
        List<Map> result = section.getMapList("list");
        assertEquals(2, result.size());
    }

    // ==================== exists ====================

    @Test
    public void testExists() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", "value");
        assertTrue(section.exists("a"));
        assertTrue(section.exists("a.b"));
        assertFalse(section.exists("c"));
    }

    @Test
    public void testExistsIgnoreCase() {
        ConfigSection section = new ConfigSection();
        section.set("MyKey", "value");
        assertFalse(section.exists("mykey", false));
        assertTrue(section.exists("mykey", true));
        assertTrue(section.exists("MYKEY", true));
    }

    // ==================== getKeys ====================

    @Test
    public void testGetKeysFlat() {
        ConfigSection section = new ConfigSection();
        section.set("a", 1);
        section.set("b", 2);
        section.set("c", 3);
        Set<String> keys = section.getKeys(false);
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "b", "c")), keys);
    }

    @Test
    public void testGetKeysWithChildren() {
        ConfigSection section = new ConfigSection();
        section.set("a", 1);
        section.set("b.c", 2);
        section.set("b.d", 3);
        Set<String> keys = section.getKeys(true);
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
        assertTrue(keys.contains("b.c"));
        assertTrue(keys.contains("b.d"));
    }

    @Test
    public void testGetKeysDefaultIncludesChildren() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", 1);
        Set<String> keys = section.getKeys();
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("a.b"));
    }

    // ==================== 嵌套路径下的类型化 getter ====================

    @Test
    public void testGetNestedInt() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", 42);
        assertEquals(42, section.getInt("a.b"));
    }

    @Test
    public void testGetNestedString() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", "hello");
        assertEquals("hello", section.getString("a.b"));
    }

    @Test
    public void testGetNestedBoolean() {
        ConfigSection section = new ConfigSection();
        section.set("a.b", true);
        assertTrue(section.getBoolean("a.b"));
    }
}
