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
 * 防漂移守卫：测试源码不得继续增加对生产类私有状态的反射访问。
 * <p>
 * 反射读私有字段/方法是"钉死实现"的主要载体——字段改名、把实例字段改成常量、调整内部协作顺序都会
 * 让这类测试变红，而它们并不能发现真正的行为回归。存量已按基线冻结，只允许减少。
 * <p>
 * 清理后请把下面两个基线数字调低；一旦有人在新文件里引入反射，或给现有文件继续加倍，本测试会失败。
 * <p>
 * Drift guard: test sources must not keep growing their use of reflection into production
 * internals. The baseline is frozen and may only be lowered.
 */
class ReflectionUsageGuardTest {

    /** 存量基线（2026-11-14 复核后统计）。只允许调低。 */
    private static final int BASELINE_OFFENDING_FILES = 181;
    private static final int BASELINE_OCCURRENCES = 359;

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
