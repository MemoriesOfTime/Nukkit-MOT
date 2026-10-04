package cn.nukkit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证 Maven 与 Gradle 的字节码输出版本（release）一致，且与实际编译产物相符
 * <p>
 * Guards bytecode compatibility across the two build systems: pom.xml must declare an explicit
 * compiler {@code <release>}, build.gradle.kts must pin {@code options.release} to the same value,
 * and the compiled classes on the test classpath must match. Fails when one build file is bumped
 * without the other, or when the Gradle pin is dropped so bytecode silently follows the toolchain.
 */
public class JavaReleaseConsistencyTest {

    /** class 文件 major 版本 = 44 + Java 特性版本号（JEP 322）：17→61、21→65 */
    private static final int CLASS_MAJOR_OFFSET = 44;

    @Test
    public void testJavaReleaseConsistency() throws Exception {
        List<String> errors = new ArrayList<>();

        int mavenRelease = parseMavenRelease(errors);
        int gradleRelease = parseGradleRelease(errors);

        if (mavenRelease > 0 && gradleRelease > 0 && mavenRelease != gradleRelease) {
            errors.add(String.format(
                    "字节码版本不一致: pom.xml release=%d, build.gradle.kts options.release=%d%n" +
                    "  改了一侧必须同步另一侧（含 Dockerfile/CI 构建 JDK 与本测试期望）%n",
                    mavenRelease, gradleRelease));
        }

        // 与实际编译产物核对；本测试在哪个构建系统下运行，就在验证哪个系统的输出
        // Cross-checks the real compiled output; whichever build runs this test validates itself
        if (mavenRelease > 0 && gradleRelease > 0 && mavenRelease == gradleRelease) {
            assertClassFileMajor("cn/nukkit/Nukkit.class", mavenRelease, errors);
            assertClassFileMajor("cn/nukkit/JavaReleaseConsistencyTest.class", mavenRelease, errors);
        }

        System.out.println("=== Java release 一致性: Maven=" + mavenRelease
                + ", Gradle=" + gradleRelease + " (期望字节码 major=" + (Math.max(mavenRelease, 0) + CLASS_MAJOR_OFFSET) + ") ===");

        if (!errors.isEmpty()) {
            Assertions.fail(String.join("", errors));
        }
    }

    /**
     * 解析 pom.xml 的有效 release：maven-compiler-plugin 的 <release>（展开 ${...} 占位符）
     */
    private int parseMavenRelease(List<String> errors) throws Exception {
        Path pom = findRepoFile("pom.xml");
        String content = Files.readString(pom);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        var document = builder.parse(new org.xml.sax.InputSource(new StringReader(content)));

        Map<String, String> properties = new HashMap<>();
        var propertiesNodes = document.getElementsByTagName("properties");
        if (propertiesNodes.getLength() > 0) {
            var children = propertiesNodes.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                var node = children.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    properties.put(node.getNodeName(), node.getTextContent().trim());
                }
            }
        }

        String source = properties.get("maven.compiler.source");
        String target = properties.get("maven.compiler.target");
        if (source != null && target != null && !source.equals(target)) {
            errors.add(String.format("pom.xml maven.compiler.source=%s 与 target=%s 不一致%n", source, target));
        }

        String release = findCompilerPluginRelease(document);
        if (release == null) {
            errors.add(String.format("pom.xml 的 maven-compiler-plugin 缺少显式 <release> 配置%n"));
            return -1;
        }
        release = expandProperty(release.trim(), properties);
        try {
            return Integer.parseInt(release);
        } catch (NumberFormatException e) {
            errors.add(String.format("pom.xml release 无法解析为数字: %s%n", release));
            return -1;
        }
    }

    private String findCompilerPluginRelease(org.w3c.dom.Document document) {
        var plugins = document.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            var plugin = plugins.item(i);
            if (!"maven-compiler-plugin".equals(getChildText(plugin, "artifactId"))) {
                continue;
            }
            var releases = ((org.w3c.dom.Element) plugin).getElementsByTagName("release");
            if (releases.getLength() > 0) {
                return releases.item(0).getTextContent();
            }
            return null;
        }
        return null;
    }

    /**
     * 解析 build.gradle.kts 的 options.release；必须显式声明，否则字节码会跟随 toolchain 漂移
     */
    private int parseGradleRelease(List<String> errors) throws IOException {
        Path kts = findRepoFile("build.gradle.kts");
        String content = Files.readString(kts);

        Set<Integer> releases = new LinkedHashSet<>();
        Matcher releaseMatcher = Pattern.compile("options\\.release\\.set\\(\\s*(\\d+)\\s*\\)").matcher(content);
        while (releaseMatcher.find()) {
            releases.add(Integer.parseInt(releaseMatcher.group(1)));
        }
        if (releases.isEmpty()) {
            errors.add(String.format(
                    "build.gradle.kts 缺少显式 options.release.set(...)%n" +
                    "  没有它字节码版本会直接跟随 toolchain（约定必须显式钉住输出）%n"));
        } else if (releases.size() > 1) {
            errors.add(String.format("build.gradle.kts 存在多个不同的 options.release: %s%n", releases));
        }

        // toolchain 必须 ≥ release，否则 javac 直接拒绝 --release
        // The toolchain must be >= release, or javac rejects --release outright
        int toolchain = -1;
        Matcher toolchainMatcher = Pattern.compile("JavaLanguageVersion\\.of\\(\\s*(\\d+)\\s*\\)").matcher(content);
        while (toolchainMatcher.find()) {
            toolchain = Integer.parseInt(toolchainMatcher.group(1));
        }
        int release = releases.isEmpty() ? -1 : releases.iterator().next();
        if (release > 0 && toolchain > 0 && toolchain < release) {
            errors.add(String.format("Gradle toolchain=%d 低于 options.release=%d，无法编译%n", toolchain, release));
        }
        return release;
    }

    /**
     * 核对 classpath 上实际 class 文件的 major 版本（本测试自身即当前构建的产物）
     */
    private void assertClassFileMajor(String classResource, int release, List<String> errors) throws IOException {
        try (InputStream in = JavaReleaseConsistencyTest.class.getClassLoader().getResourceAsStream(classResource)) {
            if (in == null) {
                errors.add(String.format("classpath 上找不到 %s%n", classResource));
                return;
            }
            byte[] header = in.readNBytes(8);
            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            if (major != release + CLASS_MAJOR_OFFSET) {
                errors.add(String.format(
                        "%s major=%d（Java %d）与 release=%d（期望 major=%d）不符，构建产物字节码版本漂移%n",
                        classResource, major, major - CLASS_MAJOR_OFFSET, release, release + CLASS_MAJOR_OFFSET));
            }
        }
    }

    private String expandProperty(String value, Map<String, String> properties) {
        while (value.contains("${")) {
            int start = value.indexOf("${");
            int end = value.indexOf("}", start);
            if (end == -1) {
                break;
            }
            String propValue = properties.get(value.substring(start + 2, end));
            if (propValue == null) {
                break;
            }
            value = value.substring(0, start) + propValue + value.substring(end + 1);
        }
        return value;
    }

    private String getChildText(org.w3c.dom.Node parent, String tagName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    private Path findRepoFile(String relative) {
        Path current = Path.of(".");
        while (current != null) {
            Path file = current.resolve(relative);
            if (Files.exists(file)) {
                return file;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 " + relative);
    }
}
