package cn.nukkit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 防漂移守护：下载镜像列表双写于 GenerateDependencyManifest.DEFAULT_REPOS（Maven 管线）
 * 与 build.gradle.kts 的 dependencyDownloadRepos（Gradle 管线），须逐项一致
 * （顺序即下载失败回退优先级）。
 * <p>
 * Anti-drift guard: the mirror list is written in both pipelines and must match item by item.
 */
class DependencyManifestReposConsistencyTest {

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
