package cn.nukkit;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 验证 Maven (pom.xml) 和 Gradle (libs.versions.toml) 依赖版本一致性
 * <p>
 * 自动发现机制：
 * - Maven 依赖：从 pom.xml 顶级 dependencies 解析 groupId:artifactId -> version
 * - Gradle 依赖：从 libs.versions.toml 的 [libraries] 提取 module (groupId:artifactId) -> version
 * - 自动匹配并比较版本，完全一致
 */
public class DependencyVersionConsistencyTest {

    @Test
    public void testDependencyVersionConsistency() throws Exception {
        Path pomPath = findPomXml();
        Path tomlPath = findLibsVersionsToml();

        String pomContent = Files.readString(pomPath);
        String tomlContent = Files.readString(tomlPath);

        // 解析 Maven 依赖: groupId:artifactId -> version
        Map<String, DepInfo> mavenDeps = parseMavenDependencies(pomContent);

        // 解析 Gradle 依赖: groupId:artifactId -> version
        Map<String, DepInfo> gradleDeps = parseGradleDependencies(tomlContent);

        // 自动匹配并比较
        StringBuilder errors = new StringBuilder();
        int matchCount = 0;

        for (Map.Entry<String, DepInfo> entry : new TreeMap<>(mavenDeps).entrySet()) {
            String key = entry.getKey();
            DepInfo mavenInfo = entry.getValue();

            DepInfo gradleInfo = gradleDeps.get(key);
            if (gradleInfo == null) {
                errors.append(String.format(
                    "Maven 依赖在 Gradle 中未找到: %s%n  Maven: %s%n%n",
                    key, mavenInfo.version
                ));
                continue;
            }

            if (!mavenInfo.version.equals(gradleInfo.version)) {
                errors.append(String.format(
                    "版本不一致: %s%n  Maven: %s%n  Gradle: %s%n%n",
                    key, mavenInfo.version, gradleInfo.version
                ));
            } else {
                matchCount++;
            }
        }

        // 检查 Gradle 独有但 Maven 没有的依赖
        for (String key : new TreeMap<>(gradleDeps).keySet()) {
            if (!mavenDeps.containsKey(key)) {
                DepInfo info = gradleDeps.get(key);
                errors.append(String.format(
                    "Gradle 依赖在 Maven 中未找到: %s%n  Gradle: %s%n%n",
                    key, info.version
                ));
            }
        }

        // 输出摘要
        System.out.println("\n=== 依赖版本一致性检查 ===");
        System.out.println("匹配数量: " + matchCount);
        System.out.println("Maven 依赖数: " + mavenDeps.size());
        System.out.println("Gradle 依赖数: " + gradleDeps.size());

        if (!errors.isEmpty()) {
            System.out.println("\n错误:");
            System.out.print(errors);
            Assertions.fail("Maven 和 Gradle 依赖版本不一致:\n\n" + errors);
        }
    }

    /**
     * 解析 Maven 顶级 dependencies 下的依赖
     */
    private Map<String, DepInfo> parseMavenDependencies(String pomContent) throws Exception {
        Map<String, DepInfo> deps = new HashMap<>();
        Map<String, String> properties = new HashMap<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        var document = builder.parse(new org.xml.sax.InputSource(new StringReader(pomContent)));

        // 解析 properties
        var propertiesNodes = document.getElementsByTagName("properties");
        if (propertiesNodes.getLength() > 0) {
            var propertiesElement = propertiesNodes.item(0);
            var propertyChildren = propertiesElement.getChildNodes();
            for (int i = 0; i < propertyChildren.getLength(); i++) {
                var node = propertyChildren.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String name = node.getNodeName();
                    String value = node.getTextContent().trim();
                    properties.put(name, value);
                }
            }
        }

        // 解析顶级 <project>/<dependencies> 下的 <dependency>
        // 避免解析插件配置内的依赖
        var projectChildren = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < projectChildren.getLength(); i++) {
            var node = projectChildren.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE ||
                !node.getNodeName().equals("dependencies")) {
                continue;
            }
            var dependencyNodes = node.getChildNodes();
            for (int j = 0; j < dependencyNodes.getLength(); j++) {
                var depNode = dependencyNodes.item(j);
                if (depNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE ||
                    !depNode.getNodeName().equals("dependency")) {
                    continue;
                }

                String groupId = getChildText(depNode, "groupId");
                String artifactId = getChildText(depNode, "artifactId");
                String version = getChildText(depNode, "version");

                if (groupId == null || artifactId == null || version == null) {
                    continue;
                }

                // 展开属性占位符
                version = expandProperty(version, properties);

                String key = groupId + ":" + artifactId;
                deps.put(key, new DepInfo(version, "maven"));
            }
        }

        return deps;
    }

    /**
     * 从 libs.versions.toml 解析 Gradle 依赖
     * 自动提取 [libraries] 中的 module 信息
     */
    private Map<String, DepInfo> parseGradleDependencies(String tomlContent) throws Exception {
        Map<String, DepInfo> deps = new HashMap<>();

        TomlMapper mapper = new TomlMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = mapper.readValue(tomlContent, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> versions = (Map<String, String>) root.get("versions");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> libraries = (Map<String, Map<String, Object>>) root.get("libraries");

        if (versions == null || libraries == null) {
            return deps;
        }

        for (Map.Entry<String, Map<String, Object>> entry : libraries.entrySet()) {
            Map<String, Object> libConfig = entry.getValue();
            String module = (String) libConfig.get("module");
            String versionRef = null;
            Object versionObj = libConfig.get("version");
            if (versionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> versionMap = (Map<String, Object>) versionObj;
                versionRef = (String) versionMap.get("ref");
            }

            if (module == null || versionRef == null) {
                continue;
            }

            String version = versions.get(versionRef);
            if (version != null) {
                deps.put(module, new DepInfo(version, "gradle:" + entry.getKey()));
            }
        }

        return deps;
    }

    private String getChildText(org.w3c.dom.Node parent, String tagName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE &&
                child.getNodeName().equals(tagName)) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    private String expandProperty(String value, Map<String, String> properties) {
        while (value.contains("${")) {
            int start = value.indexOf("${");
            int end = value.indexOf("}", start);
            if (end == -1) break;

            String propName = value.substring(start + 2, end);
            String propValue = properties.get(propName);
            if (propValue == null) {
                propValue = System.getProperty(propName);
            }
            if (propValue == null) {
                break;
            }
            value = value.substring(0, start) + propValue + value.substring(end + 1);
        }
        return value;
    }

    /**
     * 依赖信息
     */
    private record DepInfo(String version, String source) {}

    /**
     * 打印依赖详情
     */
    @Test
    public void printDependencyDetails() throws Exception {
        Path pomPath = findPomXml();
        Path tomlPath = findLibsVersionsToml();

        String pomContent = Files.readString(pomPath);
        String tomlContent = Files.readString(tomlPath);

        Map<String, DepInfo> mavenDeps = parseMavenDependencies(pomContent);
        Map<String, DepInfo> gradleDeps = parseGradleDependencies(tomlContent);

        System.out.println("=== Maven 依赖 ===");
        for (Map.Entry<String, DepInfo> entry : new TreeMap<>(mavenDeps).entrySet()) {
            DepInfo info = gradleDeps.get(entry.getKey());
            String status = info != null ? (info.version.equals(entry.getValue().version) ? "✓" : "✗") : "?";
            System.out.println(status + " " + entry.getKey() + " = " + entry.getValue().version);
        }

        System.out.println("\n=== Gradle 依赖 ===");
        for (Map.Entry<String, DepInfo> entry : new TreeMap<>(gradleDeps).entrySet()) {
            DepInfo info = mavenDeps.get(entry.getKey());
            String status = info != null ? (info.version.equals(entry.getValue().version) ? "✓" : "✗") : "?";
            System.out.println(status + " " + entry.getKey() + " = " + entry.getValue().version + " (" + entry.getValue().source + ")");
        }
    }

    private Path findPomXml() throws Exception {
        Path current = Path.of(".");
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.exists(pom)) {
                return pom;
            }
            current = current.getParent();
        }
        throw new Exception("找不到 pom.xml");
    }

    private Path findLibsVersionsToml() throws Exception {
        Path current = Path.of(".");
        while (current != null) {
            Path toml = current.resolve("gradle/libs.versions.toml");
            if (Files.exists(toml)) {
                return toml;
            }
            current = current.getParent();
        }
        throw new Exception("找不到 gradle/libs.versions.toml");
    }
}
