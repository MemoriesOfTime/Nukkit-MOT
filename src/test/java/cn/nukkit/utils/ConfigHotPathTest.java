package cn.nukkit.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 等价性测试：验证 Config/ConfigSection 热路径优化后行为与优化前一致。
 * 覆盖扁平 key、嵌套 key、点开头/结尾/连续点、null/空串、类型转换、子类等边界。
 */
public class ConfigHotPathTest {

    // ---------- get 边界 ----------

    @Test
    void getFlatHit() {
        ConfigSection s = new ConfigSection();
        s.set("key", "value");
        assertEquals("value", s.get("key"));
        assertEquals("value", s.get("key", "def"));
    }

    @Test
    void getFlatMissReturnsDefault() {
        ConfigSection s = new ConfigSection();
        assertEquals("def", s.get("missing", "def"));
        assertNull(s.get("missing"));
    }

    @Test
    void getNullAndEmptyKey() {
        ConfigSection s = new ConfigSection();
        s.set("key", "value");
        assertNull(s.get(null));
        assertNull(s.get(""));
        assertEquals("def", s.get(null, "def"));
        assertEquals("def", s.get("", "def"));
    }

    @Test
    void getNestedHit() {
        ConfigSection s = new ConfigSection();
        s.set("a.b", 1);
        assertEquals(1, s.get("a.b"));
        assertEquals(1, s.get("a.b", 99));
    }

    @Test
    void getDeepNested() {
        ConfigSection s = new ConfigSection();
        s.set("a.b.c.d", "deep");
        assertEquals("deep", s.get("a.b.c.d"));
        assertEquals("deep", s.get("a.b.c.d", "def"));
    }

    @Test
    void getDottedKeyMiss() {
        ConfigSection s = new ConfigSection();
        s.set("a", 1);
        // a 存在但不是 section -> 返回 default
        assertEquals("def", s.get("a.b", "def"));
        assertNull(s.get("a.b"));
    }

    @Test
    void getLeadingDot() {
        ConfigSection s = new ConfigSection();
        s.set("key", 1);
        // ".key" -> split 为 ["", "key"]，"" 段不存在
        assertEquals("def", s.get(".key", "def"));
    }

    @Test
    void getTrailingDot() {
        ConfigSection s = new ConfigSection();
        s.set("a.b", 1);
        // "a." -> 递归到 "" key
        assertEquals("def", s.get("a.", "def"));
    }

    @Test
    void getConsecutiveDots() {
        ConfigSection s = new ConfigSection();
        s.set("a.b", 1);
        // "a..b" -> 第一段 "a" 存在，剩余 ".b" 无此 key
        assertEquals("def", s.get("a..b", "def"));
    }

    @Test
    void getMapValueWithConfigSectionDefault() {
        ConfigSection s = new ConfigSection();
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("x", 1);
        s.set("sec", m);
        // value 是 Map 而 default 是 ConfigSection -> 包装转换
        Object v = s.get("sec", new ConfigSection());
        assertTrue(v instanceof ConfigSection);
        assertEquals(1, ((ConfigSection) v).getInt("x"));
    }

    @Test
    void getValueNullKeepsNull() {
        ConfigSection s = new ConfigSection();
        s.set("key", null);
        // key 存在但值为 null -> 返回 null，而不是 default
        assertNull(s.get("key", "def"));
    }

    @Test
    void getSubclassInstanceNotCopied() {
        // 与 Server.ServerProperties extends ConfigSection 同构：default 是 ConfigSection、value 是子类实例
        SubConfigSection subclass = new SubConfigSection();
        subclass.set("x", 1);
        ConfigSection root = new ConfigSection();
        root.set("sub", subclass);
        // 原实现 isInstance 命中直接返回原实例（不拷贝）
        ConfigSection result = root.get("sub", new ConfigSection());
        assertSame(subclass, result, "子类实例应原样返回，不应被拷贝");
    }

    /** 真实 ConfigSection 子类，用于验证 isInstance 语义。 */
    private static class SubConfigSection extends ConfigSection {
    }

    // ---------- set 边界 ----------

    @Test
    void setFlat() {
        ConfigSection s = new ConfigSection();
        s.set("k", "v");
        assertEquals("v", s.get("k"));
    }

    @Test
    void setNestedPreservesSiblings() {
        ConfigSection s = new ConfigSection();
        s.set("a.b", 1);
        s.set("a.c", 2);
        s.set("a.b.d", 3); // 深入已有 section：a.b 从标量 1 变为 section {d=3}
        assertEquals(3, s.get("a.b.d"));
        assertEquals(2, s.get("a.c"));
        assertTrue(s.get("a.b") instanceof ConfigSection);
    }

    @Test
    void setOverwritesScalarWithSection() {
        ConfigSection s = new ConfigSection();
        s.set("a", 1);
        s.set("a.b", 2); // a 从标量变 section
        assertEquals(2, s.get("a.b"));
        assertTrue(s.get("a") instanceof ConfigSection); // 顶层 a 变成 section（旧语义同样如此）
    }

    @Test
    void setLeadingDot() {
        ConfigSection s = new ConfigSection();
        s.set(".k", 1);
        // 与 split 语义一致："" 段为 key
        assertTrue(s.containsKey(""));
        assertEquals(1, s.get(".k"));
    }

    @Test
    void setConsecutiveDots() {
        ConfigSection s = new ConfigSection();
        s.set("a..b", 1);
        // split 语义: ["a", ".b"] -> section ".b"
        ConfigSection a = (ConfigSection) s.get("a");
        assertNotNull(a);
        assertEquals(1, a.get(".b"));
    }

    @Test
    void setTrailingDotStoresEmptyLeafKey() {
        ConfigSection s = new ConfigSection();
        s.set("a.", 1);
        // 旧语义同样如此：a 变成 section，含空串叶键；get("a.") 递归空 key 返回默认
        ConfigSection a = (ConfigSection) s.get("a");
        assertNotNull(a);
        assertTrue(a.containsKey("")); // 空串叶键存在（空 key 走 get 会提前返回，需 map 视角读取）
        assertEquals(1, rawGet(a, ""));
        assertEquals("def", s.get("a.", "def"));
    }

    @Test
    void setBareDot() {
        ConfigSection s = new ConfigSection();
        s.set(".", 1);
        // 旧语义："" -> {"" -> 1}
        assertTrue(s.containsKey(""));
        Object v = rawGet(s, "");
        assertTrue(v instanceof ConfigSection);
        assertEquals(1, rawGet((ConfigSection) v, ""));
        assertEquals("def", s.get(".", "def"));
    }

    /** 从 Map 视角读取原始值，绕开 ConfigSection.get(String) 的空 key 提前返回。 */
    @SuppressWarnings("unchecked")
    private static Object rawGet(ConfigSection s, String key) {
        return ((Map<String, Object>) s).get(key);
    }

    @Test
    void removeTrailingDotAndBareDot() {
        ConfigSection s = new ConfigSection();
        s.set("a.b", 1);
        s.remove("a."); // 无异常，空 key 提前返回
        assertEquals(1, s.get("a.b"));
        s.remove(".");
        s.remove("a..b");
        s.remove(null);
        s.remove("");
    }

    // ---------- remove 边界 ----------

    @Test
    void removeFlat() {
        ConfigSection s = new ConfigSection();
        s.set("k", "v");
        s.remove("k");
        assertNull(s.get("k"));
    }

    @Test
    void removeNested() {
        ConfigSection s = new ConfigSection();
        s.set("a.b", 1);
        s.set("a.c", 2);
        s.remove("a.b");
        assertNull(s.get("a.b"));
        assertEquals(2, s.get("a.c"));
    }

    @Test
    void removeMissingAndInvalid() {
        ConfigSection s = new ConfigSection();
        s.remove("missing");
        s.remove(null);
        s.remove("");
        s.remove("a.b.c"); // 无异常
    }

    // ---------- 操作序列一致性（新旧实现对比） ----------

    @Test
    void operationSequenceMatchesLegacy() {
        // 旧实现（优化前的 split 版本）
        LegacyModel legacy = new LegacyModel();
        ConfigSection current = new ConfigSection();

        String[] keys = {"a", "b", "a.b", "a.c", "x.y.z", ".lead", "trail.", "a..z", "deep.er.still", "plain"};
        int[] vals = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        // 交替 set/get/remove
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            Object v = vals[i];
            legacy.set(k, v);
            current.set(k, v);
            assertSameValue(legacy.get(k, "def"), current.get(k, "def"), "set+get " + k);
        }
        for (String k : keys) {
            assertSameValue(legacy.get(k, "def"), current.get(k, "def"), "read " + k);
            legacy.remove(k);
            current.remove(k);
            assertSameValue(legacy.get(k, "def"), current.get(k, "def"), "after remove " + k);
        }
        // remove 后重新 set 嵌套
        legacy.set("a.b", "again");
        current.set("a.b", "again");
        assertSameValue(legacy.get("a.b", "def"), current.get("a.b", "def"), "re-set nested");
    }

    private static void assertSameValue(Object expected, Object actual, String msg) {
        if (expected == null) {
            assertNull(actual, msg);
        } else if (expected instanceof Map && actual instanceof Map) {
            Map<?, ?> em = (Map<?, ?>) expected;
            Map<?, ?> am = (Map<?, ?>) actual;
            assertEquals(em.size(), am.size(), msg + " (map size)");
            for (Object ek : em.keySet()) {
                assertTrue(am.containsKey(ek), msg + " (missing key " + ek + ")");
                assertSameValue(em.get(ek), am.get(ek), msg + "." + ek);
            }
        } else {
            assertEquals(expected, actual, msg);
        }
    }

    /** 优化前的 ConfigSection 语义（split 版本），用于对比。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class LegacyModel {
        final LinkedHashMap<String, Object> root = new LinkedHashMap<>();

        Object get(String key, Object def) {
            if (key == null || key.isEmpty()) return def;
            if (root.containsKey(key)) return root.get(key);
            String[] keys = key.split("\\.", 2);
            if (!root.containsKey(keys[0])) return def;
            Object value = root.get(keys[0]);
            if (value instanceof LinkedHashMap m) {
                LegacyModel sub = new LegacyModel();
                sub.root.putAll(m);
                return sub.get(keys[1], def);
            }
            return def;
        }

        void set(String key, Object value) {
            String[] subKeys = key.split("\\.", 2);
            if (subKeys.length > 1) {
                LinkedHashMap<String, Object> child = new LinkedHashMap<>();
                if (root.get(subKeys[0]) instanceof LinkedHashMap m) child = m;
                LegacyModel sub = new LegacyModel();
                sub.root.putAll(child);
                sub.set(subKeys[1], value);
                root.put(subKeys[0], sub.root);
            } else root.put(subKeys[0], value);
        }

        void remove(String key) {
            if (key == null || key.isEmpty()) return;
            if (root.remove(key) != null) return;
            if (key.contains(".")) {
                String[] keys = key.split("\\.", 2);
                if (root.get(keys[0]) instanceof LinkedHashMap m) {
                    LegacyModel sub = new LegacyModel();
                    sub.root.putAll(m);
                    sub.remove(keys[1]);
                    root.put(keys[0], sub.root);
                }
            }
        }
    }

    // ---------- Config properties 解析 / 保存 ----------

    @Test
    void propertiesRoundTrip(@TempDir Path dir) {
        File f = dir.resolve("test.properties").toFile();
        Config c = new Config(f, Config.PROPERTIES);
        c.set("name", "server");
        c.set("max-players", 100);
        c.set("pvp", true);
        c.set("motd", "Hello World");
        assertTrue(c.save());
        assertTrue(f.exists());

        Config reloaded = new Config(f, Config.PROPERTIES);
        assertEquals("server", reloaded.getString("name"));
        // properties 格式的值均为 String（parseProperties 语义），数字用 getString 读取
        assertEquals("100", reloaded.getString("max-players"));
        assertTrue(reloaded.getBoolean("pvp"));
        assertEquals("Hello World", reloaded.getString("motd"));
    }

    @Test
    void propertiesParseCommentAndBlankLines(@TempDir Path dir) throws java.io.IOException {
        File f = dir.resolve("comment.properties").toFile();
        Config c = new Config(f, Config.PROPERTIES);
        c.set("key-a", "va");
        c.set("key-b", true);
        c.set("key-c", false);
        assertTrue(c.save());

        // 追加注释和空行后仍能正确解析
        String content = "# comment line\n\n" + java.nio.file.Files.readString(f.toPath());
        java.nio.file.Files.writeString(f.toPath(), content);

        Config reloaded = new Config(f, Config.PROPERTIES);
        assertEquals("va", reloaded.getString("key-a"));
        assertTrue(reloaded.getBoolean("key-b"));
        assertFalse(reloaded.getBoolean("key-c"));
    }

    // ---------- Utils.writeFile ----------

    @Test
    void writeFileCreatesAndOverwrites(@TempDir Path dir) {
        File f = dir.resolve("out.txt").toFile();
        try {
            Utils.writeFile(f, "hello");
            assertEquals("hello", java.nio.file.Files.readString(f.toPath()));
            Utils.writeFile(f, "world");
            assertEquals("world", java.nio.file.Files.readString(f.toPath()));
        } catch (java.io.IOException e) {
            fail("unexpected IOException", e);
        }
    }

    @Test
    void writeFileToDirectoryThrows(@TempDir Path dir) {
        assertThrows(java.io.IOException.class, () -> Utils.writeFile(dir.toFile(), "x"));
    }
}
