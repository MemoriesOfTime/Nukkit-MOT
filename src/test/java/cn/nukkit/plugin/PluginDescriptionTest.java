package cn.nukkit.plugin;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link PluginDescription} parsing, focusing on the new {@code libraries} field.
 */
public class PluginDescriptionTest {

    /** 最小合法 plugin.yml 必填字段 / Minimal valid plugin.yml required fields. */
    private static Map<String, Object> baseMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "TestPlugin");
        map.put("main", "com.example.TestPlugin");
        map.put("version", "1.0.0");
        map.put("api", Collections.singletonList("1.0.0"));
        return map;
    }

    @Test
    public void parsesLibrariesList() {
        Map<String, Object> map = baseMap();
        map.put("libraries", Arrays.asList(
                "com.squareup.okhttp3:okhttp:4.12.0",
                "org.xerial:sqlite-jdbc:3.45.1.0"
        ));

        PluginDescription description = new PluginDescription(map);

        assertEquals(Arrays.asList(
                "com.squareup.okhttp3:okhttp:4.12.0",
                "org.xerial:sqlite-jdbc:3.45.1.0"
        ), description.getLibraries());
    }

    @Test
    public void librariesDefaultsToEmptyWhenAbsent() {
        PluginDescription description = new PluginDescription(baseMap());

        assertNotNull(description.getLibraries(), "libraries 不应为 null / should not be null");
        assertTrue(description.getLibraries().isEmpty(), "缺省 libraries 应为空 / should be empty by default");
    }

    @Test
    public void parsesRepositoriesList() {
        Map<String, Object> map = baseMap();
        map.put("libraries", Collections.singletonList("com.private:foo:1.0"));
        map.put("repositories", Arrays.asList(
                "https://maven.my-company.com/repository/public/",
                "https://jitpack.io"
        ));

        PluginDescription description = new PluginDescription(map);

        assertEquals(Arrays.asList(
                "https://maven.my-company.com/repository/public/",
                "https://jitpack.io"
        ), description.getRepositories(), "repositories 应原样保留，由 LibraryLoader 做末尾斜杠规范化");
    }

    @Test
    public void repositoriesDefaultsToEmptyWhenAbsent() {
        PluginDescription description = new PluginDescription(baseMap());

        assertNotNull(description.getRepositories(), "repositories 不应为 null");
        assertTrue(description.getRepositories().isEmpty(), "缺省 repositories 应为空");
    }

    @Test
    public void ignoresNonListRepositoriesValue() {
        Map<String, Object> map = baseMap();
        map.put("repositories", "https://single.string");

        PluginDescription description = new PluginDescription(map);

        assertTrue(description.getRepositories().isEmpty(),
                "非 List 类型的 repositories 应被忽略 / non-list repositories should be ignored");
    }

    @Test
    public void filtersNonStringLibraryElements() {
        // P7 回归：YAML 误把元素写成数字（YAML 会解析 1.0.0 为字符串，但纯数字会被解析为 Integer）
        Map<String, Object> map = baseMap();
        List<Object> mixed = new ArrayList<>();
        mixed.add("com.example:lib:1.0");
        mixed.add(12345);
        mixed.add(true);
        map.put("libraries", mixed);

        PluginDescription description = new PluginDescription(map);

        assertEquals(Collections.singletonList("com.example:lib:1.0"), description.getLibraries(),
                "非 String 元素应被过滤，只保留字符串坐标");
    }

    @Test
    public void filtersNonStringRepositoryElements() {
        Map<String, Object> map = baseMap();
        List<Object> mixed = new ArrayList<>();
        mixed.add("https://example.com/repo/");
        mixed.add(999);
        map.put("repositories", mixed);

        PluginDescription description = new PluginDescription(map);

        assertEquals(Collections.singletonList("https://example.com/repo/"), description.getRepositories(),
                "非 String 元素应被过滤");
    }

    @Test
    public void ignoresNonListLibrariesValue() {
        Map<String, Object> map = baseMap();
        // 错误类型（字符串而非列表）→ 不应抛 NPE 或 ClassCastException，保持空列表
        map.put("libraries", "com.squareup.okhttp3:okhttp:4.12.0");

        PluginDescription description = new PluginDescription(map);

        assertTrue(description.getLibraries().isEmpty(),
                "非 List 类型的 libraries 应被忽略 / non-list libraries should be ignored");
    }

    @Test
    public void parsesViaYamlString() {
        // 通过完整 YAML 字符串构造，模拟 plugin.yml 真实解析路径
        String yaml = "name: TestPlugin\n" +
                "main: com.example.TestPlugin\n" +
                "version: \"1.0.0\"\n" +
                "api: [\"1.0.0\"]\n" +
                "libraries:\n" +
                "  - \"com.google.code.gson:gson:2.10.1\"\n" +
                "  - \"org.xerial:sqlite-jdbc:3.45.1.0\"\n";

        PluginDescription description = new PluginDescription(yaml);

        assertEquals(Arrays.asList(
                "com.google.code.gson:gson:2.10.1",
                "org.xerial:sqlite-jdbc:3.45.1.0"
        ), description.getLibraries());
    }
}
