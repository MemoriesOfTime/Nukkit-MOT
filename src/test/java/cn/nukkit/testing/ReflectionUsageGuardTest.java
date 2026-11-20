package cn.nukkit.testing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Drift guard: reflection into private production state locks tests to implementation details and
 * often misses behavioral regressions. Routine cleanup may lower the baseline. A regression test
 * that needs reflective setup may raise it only with explicit review and an exact count update;
 * unexplained additions fail this test.
 * <p>
 * After cleanup, lower the baseline counts below. A new reflective access must otherwise fail.
 */
class ReflectionUsageGuardTest {

    /** Reviewed baseline (2026-11-20); includes one new regression-test setup access. */
    private static final int BASELINE_OFFENDING_FILES = 181;
    private static final int BASELINE_OCCURRENCES = 360;

    private static final Pattern PRIVATE_STATE_ACCESS = Pattern.compile(
            "getDeclaredField\\(|getDeclaredMethod\\(|privateLookupIn\\(");

    @Test
    void testSourcesDoNotGrowTheirRelianceOnReflectionIntoProductionInternals() throws IOException {
        Path sourceRoot = locateTestSourceRoot();
        Assumptions.assumeTrue(sourceRoot != null, "找不到 src/test/java，跳过守卫");

        Map<String, Integer> perFile = new TreeMap<>();
        int total = 0;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals(getClass().getSimpleName() + ".java")) {
                    continue;
                }
                int hits = countMatches(Files.readString(file));
                if (hits > 0) {
                    perFile.put(sourceRoot.relativize(file).toString(), hits);
                    total += hits;
                }
            }
        }

        assertFalse(perFile.isEmpty(), "未扫描到任何测试源码，守卫已失效");

        String summary = describe(perFile);
        // 棘轮：必须与基线完全相等。清理后请下调两个常量；这样"删一处、别处加一处"不能互相抵消。
        assertEquals(BASELINE_OFFENDING_FILES, perFile.size(),
                "通过反射访问生产类私有成员的测试文件数应为 " + BASELINE_OFFENDING_FILES
                        + "；若这是清理，请下调 BASELINE_OFFENDING_FILES。当前清单：\n" + summary);
        assertEquals(BASELINE_OCCURRENCES, total,
                "反射访问私有成员应为 " + BASELINE_OCCURRENCES
                        + " 处；若这是清理，请下调 BASELINE_OCCURRENCES。请改用可观察行为断言；当前清单：\n"
                        + summary);
    }

    private static int countMatches(String source) {
        Matcher matcher = PRIVATE_STATE_ACCESS.matcher(source);
        int hits = 0;
        while (matcher.find()) {
            hits++;
        }
        return hits;
    }

    private static String describe(Map<String, Integer> perFile) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(perFile.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : entries.stream().limit(15).toList()) {
            text.append("  ").append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        if (entries.size() > 15) {
            text.append("  ... 另有 ").append(entries.size() - 15).append(" 个文件\n");
        }
        return text.toString();
    }

    private static Path locateTestSourceRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int depth = 0; depth < 6 && cursor != null; depth++) {
            Path candidate = cursor.resolve("src/test/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }
}
