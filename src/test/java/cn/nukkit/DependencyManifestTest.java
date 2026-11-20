package cn.nukkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEPENDENCIES.txt 解析、路径构建与 sha256 工具的行为锁定。
 * <p>
 * Locks DEPENDENCIES.txt parsing, path building and the sha256 helper.
 */
class DependencyManifestTest {

    private static final String VALID_SHA = "a".repeat(64);
    private static final String OTHER_SHA = "b".repeat(64);

    @Test
    void parsesReposLineEntriesCommentsAndBlanks() {
        String content = ""
                + "# 生成于构建期 / generated at build time\n"
                + "\n"
                + "repos=https://repo.lanink.cn/repository/maven-public/|https://repo1.maven.org/maven2/\n"
                + "# 中间注释 / inline comment\n"
                + "it.unimi.dsi:fastutil:8.5.15:fastutil-8.5.15.jar:" + VALID_SHA + "\r\n"
                + "org.cloudburstmc.protocol:common:3.0.0.Beta13-SNAPSHOT:common-3.0.0.Beta13-20260915.235937-24.jar:" + OTHER_SHA + "\n";
        DependencyManifest manifest = DependencyManifest.parse(content);

        assertEquals(2, manifest.repos().size());
        assertEquals("https://repo.lanink.cn/repository/maven-public/", manifest.repos().get(0));
        assertEquals("https://repo1.maven.org/maven2/", manifest.repos().get(1));

        assertEquals(2, manifest.entries().size());
        DependencyManifest.Entry fastutil = manifest.entries().get(0);
        assertEquals("it.unimi.dsi", fastutil.groupId());
        assertEquals("fastutil", fastutil.artifactId());
        assertEquals("8.5.15", fastutil.dirVersion());
        assertEquals("fastutil-8.5.15.jar", fastutil.fileName());
        assertEquals(VALID_SHA, fastutil.sha256());
        assertEquals("org.cloudburstmc.protocol:common:3.0.0.Beta13-SNAPSHOT:common-3.0.0.Beta13-20260915.235937-24.jar",
                manifest.entries().get(1).toString());
    }

    @Test
    void corruptedEntryThrows() {
        String header = "repos=https://repo1.maven.org/maven2/\n";

        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "it.unimi.dsi:fastutil:8.5.15:fastutil-8.5.15.jar\n"));
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "g:a:1.0:a-1.0.jar:" + VALID_SHA + ":extra\n"));
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "g:a:1.0:a-1.0.jar:abc123\n"));
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse(header + "g:a:1.0:a-1.0.jar:" + "A".repeat(64) + "\n"));
    }

    @Test
    void missingOrMalformedReposLineThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse("g:a:1.0:a-1.0.jar:" + VALID_SHA + "\n"));
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse("repos=\ng:a:1.0:a-1.0.jar:" + VALID_SHA + "\n"));
        assertThrows(IllegalArgumentException.class, () ->
                DependencyManifest.parse("repos=https://repo1.maven.org/maven2/\n"));
    }

    @Test
    void duplicateFileNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> DependencyManifest.parse(""
                + "repos=https://repo1.maven.org/maven2/\n"
                + "g.one:a:1.0:a-1.0.jar:" + VALID_SHA + "\n"
                + "g.two:b:2.0:a-1.0.jar:" + OTHER_SHA + "\n"));
    }

    @Test
    void buildsLibFileNameAndDownloadUrl() {
        DependencyManifest manifest = DependencyManifest.parse(""
                + "repos=https://repo.example.com/releases\n"
                + "org.cloudburstmc.protocol:common:3.0.0.Beta13-SNAPSHOT:common-3.0.0.Beta13-20260915.235937-24.jar:" + VALID_SHA + "\n");
        DependencyManifest.Entry entry = manifest.entries().get(0);

        assertEquals(Path.of("somewhere/lib/common-3.0.0.Beta13-20260915.235937-24.jar"), entry.libFile(Path.of("somewhere/lib")));

        assertEquals("org/cloudburstmc/protocol/common/3.0.0.Beta13-SNAPSHOT/common-3.0.0.Beta13-20260915.235937-24.jar",
                entry.repositoryPath());
        assertEquals("https://repo.example.com/releases/org/cloudburstmc/protocol/common/3.0.0.Beta13-SNAPSHOT/common-3.0.0.Beta13-20260915.235937-24.jar",
                entry.downloadUrl("https://repo.example.com/releases"));
        assertEquals("https://repo.example.com/releases/org/cloudburstmc/protocol/common/3.0.0.Beta13-SNAPSHOT/common-3.0.0.Beta13-20260915.235937-24.jar",
                entry.downloadUrl("https://repo.example.com/releases/"));
    }

    @Test
    void sha256OfTempFileMatchesKnownVector() throws IOException {
        Path file = Files.createTempFile("dependency-manifest-sha", ".bin");
        try {
            Files.writeString(file, "abc");
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    DependencyManifest.sha256Hex(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /*
     * 防漂移守护：下载镜像列表双写于 GenerateDependencyManifest.DEFAULT_REPOS（Maven 管线）
     * 与 build.gradle.kts 的 dependencyDownloadRepos（Gradle 管线），须逐项一致
     * （顺序即下载失败回退优先级）。
     * / Anti-drift guard: the mirror list is written in both pipelines and must match item by item.
     */
    private static final Pattern QUOTED_URL = Pattern.compile("\"(https?://[^\"]+)\"");

    @Test
    public void repoListsMatch() throws Exception {
        String javaSource = Files.readString(
                findProjectFile("src/build-tools/java/cn/nukkit/buildtools/GenerateDependencyManifest.java"));
        String gradleScript = Files.readString(findProjectFile("build.gradle.kts"));

        List<String> mavenRepos = extractQuotedUrls(javaSource, "DEFAULT_REPOS = List.of(");
        List<String> gradleRepos = extractQuotedUrls(gradleScript, "dependencyDownloadRepos = listOf(");

        assertFalse(mavenRepos.isEmpty(), "未从 GenerateDependencyManifest 解析到镜像 / no repos parsed");
        assertFalse(gradleRepos.isEmpty(), "未从 build.gradle.kts 解析到镜像 / no repos parsed");
        assertEquals(mavenRepos, gradleRepos,
                "两侧下载镜像列表不一致（顺序即回退优先级）/ mirror lists diverged (order is failover priority)");
    }

    /** 取 opener 到其后首个右括号之间的引号 URL，保持出现顺序 / quoted URLs up to the next ')', in order */
    private static List<String> extractQuotedUrls(String text, String opener) {
        int start = text.indexOf(opener);
        if (start < 0) {
            throw new AssertionError("找不到代码块 / cannot locate block: " + opener);
        }
        int end = text.indexOf(')', start);
        if (end < 0) {
            throw new AssertionError("代码块未闭合 / unterminated block: " + opener);
        }
        List<String> urls = new ArrayList<>();
        Matcher matcher = QUOTED_URL.matcher(text.substring(start, end));
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return urls;
    }

    private static Path findProjectFile(String relative) {
        Path current = Path.of(".");
        while (current != null) {
            Path file = current.resolve(relative);
            if (Files.exists(file)) {
                return file;
            }
            current = current.getParent();
        }
        throw new AssertionError("找不到 / cannot find: " + relative);
    }
}
