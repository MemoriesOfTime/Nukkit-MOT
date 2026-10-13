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
 * <p>
 * MR-JAR 例外：{@code src/main/java25} 的版本化类（虚拟线程覆盖类）在两侧构建里都以
 * release = MR_JAR_VERSION 单独编译进 META-INF/versions/25，不参与主 release 一致性；
 * 例外本身也要求 pom 与 Gradle 两侧一致并被实际编译产物核实。
 * <p>
 * MR-JAR exception: versioned classes under {@code src/main/java25} (virtual-thread overrides)
 * compile separately at release = MR_JAR_VERSION into META-INF/versions/25 in both builds;
 * the exception itself must stay consistent across pom/Gradle and match real class files.
 */
public class JavaReleaseConsistencyTest {

    /** class 文件 major 版本 = 44 + Java 特性版本号（JEP 322）：17→61、21→65, 25->69 */
    private static final int CLASS_MAJOR_OFFSET = 44;

    /** MR-JAR 版本目录（虚拟线程覆盖类），两侧构建的例外 release 必须等于此值 */
    private static final int MR_JAR_VERSION = 25;

    private static final String MR_CLASS_RESOURCE = "META-INF/versions/" + MR_JAR_VERSION + "/cn/nukkit/scheduler/VirtualThreadServiceImpl.class";
    private static final String MR_BASE_CLASS_RESOURCE = "cn/nukkit/scheduler/VirtualThreadServiceImpl.class";

    @Test
    public void testJavaReleaseConsistency() throws Exception {
        List<String> errors = new ArrayList<>();

        int mavenRelease = parseMavenRelease(errors);
        int gradleRelease = parseGradleRelease(errors);
        int mavenMrRelease = parseMavenMrRelease(errors);
        int gradleMrRelease = parseGradleMrRelease(errors);

        if (mavenRelease > 0 && gradleRelease > 0 && mavenRelease != gradleRelease) {
            errors.add(String.format(
                    "字节码版本不一致: pom.xml release=%d, build.gradle.kts options.release=%d%n" +
                    "  改了一侧必须同步另一侧（含 Dockerfile/CI 构建 JDK 与本测试期望）%n",
                    mavenRelease, gradleRelease));
        }

        if (mavenMrRelease > 0 && gradleMrRelease > 0 && mavenMrRelease != gradleMrRelease) {
            errors.add(String.format(
                "MR-JAR 版本化类 release 不一致: pom compile-java%d=%d, Gradle compileJava%d=%d (期望 %d)%n" +
                    "  一侧缺失或改动必须同步另一侧%n",
                MR_JAR_VERSION, mavenMrRelease, MR_JAR_VERSION, gradleMrRelease, MR_JAR_VERSION));
        }

        if (mavenMrRelease > 0 && mavenMrRelease != MR_JAR_VERSION) {
            errors.add(String.format("MR-JAR 版本化类 release=%d (Maven) 与约定 %d 不符%n", mavenMrRelease, MR_JAR_VERSION));
        }
        if (gradleMrRelease > 0 && gradleMrRelease != MR_JAR_VERSION) {
            errors.add(String.format("MR-JAR 版本化类 release=%d (Gradle) 与约定 %d 不符%n", gradleMrRelease, MR_JAR_VERSION));
        }

        int targetRelease = mavenRelease > 0 ? mavenRelease : gradleRelease;
        if (targetRelease > 0) {
            assertClassFileMajor("cn/nukkit/Nukkit.class", targetRelease, errors);
            assertClassFileMajor("cn/nukkit/JavaReleaseConsistencyTest.class", targetRelease, errors);
        }

       if (targetRelease > 0) {
            assertClassFileMajor(MR_CLASS_RESOURCE, MR_JAR_VERSION, errors);

            assertClassFileMajor(MR_BASE_CLASS_RESOURCE, targetRelease, errors);
        }

        System.out.println("=== Java release 一致性: Maven=" + mavenRelease
            + ", Gradle=" + gradleRelease
            + ", MR-Maven=" + mavenMrRelease + ", MR-Gradle=" + gradleMrRelease
            + " (期望 MR-JAR versions/" + MR_JAR_VERSION + ") ===");

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

        String release = findCompilerPluginRelease(document);
        if (release == null) {
            release = properties.get("maven.compiler.release");
            if (release == null) {
                errors.add("pom.xml 的 maven-compiler-plugin 缺少显式 <release> 配置%n");
                return -1;
            }
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
        }
        return null;
    }

    /**
     * 解析 pom 中 compile-java25 execution 的 release（MR-JAR 版本化类例外）；缺失返回 -1
     * <p>
     * Parses the release of the pom's compile-java25 execution (MR-JAR exception); -1 if absent
     */
    private int parseMavenMrRelease(List<String> errors) throws Exception {
        Path pom = findRepoFile("pom.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        var document = builder.parse(new org.xml.sax.InputSource(new StringReader(Files.readString(pom))));

        var plugins = document.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            var plugin = plugins.item(i);
            if (!"maven-compiler-plugin".equals(getChildText(plugin, "artifactId"))) {
                continue;
            }
            var children = plugin.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                var node = children.item(j);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && node.getNodeName().equals("executions")) {
                    var executions = ((org.w3c.dom.Element) node).getElementsByTagName("execution");
                    for (int k = 0; k < executions.getLength(); k++) {
                        var execution = executions.item(k);
                        if (("compile-java" + MR_JAR_VERSION).equals(getChildText(execution, "id"))) {
                            var releases = ((org.w3c.dom.Element) execution).getElementsByTagName("release");
                            if (releases.getLength() == 0) {
                                errors.add("pom compile-java" + MR_JAR_VERSION + " execution 缺少 <release>%n");
                                return -1;
                            }
                            return Integer.parseInt(releases.item(0).getTextContent().trim());
                        }
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 解析 build.gradle.kts 中 compileJava25 任务的 release（MR-JAR 版本化类例外）；缺失返回 -1
     * <p>
     * Parses the release of build.gradle.kts's compileJava25 task (MR-JAR exception); -1 if absent
     */
    private int parseGradleMrRelease(List<String> errors) throws IOException {
        Path kts = findRepoFile("build.gradle.kts");
        String content = Files.readString(kts);
        int split = content.indexOf("val compileJava" + MR_JAR_VERSION);
        if (split < 0) {
            return -1;
        }
        Matcher m = Pattern.compile("options\\.release\\.set\\(\\s*(\\d+)\\s*\\)").matcher(content);
        if (m.find(split)) {
            return Integer.parseInt(m.group(1));
        }
        errors.add("build.gradle.kts 的 compileJava" + MR_JAR_VERSION + " 缺少 options.release.set(...)%n");
        return -1;
    }

    /**
     * 解析 build.gradle.kts 的 options.release；必须显式声明，否则字节码会跟随 toolchain 漂移
     */
    private int parseGradleRelease(List<String> errors) throws IOException {
        Path kts = findRepoFile("build.gradle.kts");
        String content = Files.readString(kts);

        int mrSplit = content.indexOf("val compileJava" + MR_JAR_VERSION);
        Set<Integer> releases = new LinkedHashSet<>();
        Matcher releaseMatcher = Pattern.compile("options\\.release\\.set\\(\\s*(\\d+)\\s*\\)").matcher(content);
        while (releaseMatcher.find()) {
            if (mrSplit >= 0 && releaseMatcher.start() > mrSplit) {
                int lastTaskStart = content.lastIndexOf("val compileJava", releaseMatcher.start());
                if (lastTaskStart >= mrSplit) {
                    continue;
                }
            }
            releases.add(Integer.parseInt(releaseMatcher.group(1)));
        }

        if (releases.isEmpty()) {
            errors.add("build.gradle.kts 缺少显式 options.release.set(...)%n");
        } else if (releases.size() > 1) {
            errors.add(String.format("build.gradle.kts 存在多个不同的 options.release: %s%n", releases));
        }

        return releases.isEmpty() ? -1 : releases.iterator().next();
    }

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
            if (end == -1) break;
            String propValue = properties.get(value.substring(start + 2, end));
            if (propValue == null) break;
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
            if (Files.exists(file)) return file;
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 " + relative);
    }
}
