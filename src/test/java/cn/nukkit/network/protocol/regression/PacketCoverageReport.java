package cn.nukkit.network.protocol.regression;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.regression.PacketCoverageRegistry.Direction;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真实交叉编解码覆盖率报告。
 * <p>
 * 数据来自 {@link PacketCoverageRegistry}：只有 {@link AbstractPacketRegressionTest} 成功完成
 * 一次 NK→CB 解码或 CB→NK 解码后才会记录。这里不再解析注解字符串来推断“是否测试过”，
 * 因此空跑、跳过、被禁用的测试不会再被算成覆盖。
 * <p>
 * 用法：
 * <pre>
 *   mvn -Pcoverage test-compile exec:exec@packet-coverage
 * </pre>
 * 报告退出码非 0 表示：没有任何实际交叉编解码记录，或有测试声明覆盖某包但实际从未执行。
 */
public final class PacketCoverageReport {

    private static final Pattern PACKET_NAME = Pattern.compile("(\\w+Packet)");

    /**
     * 这些包的回归测试只做 Nukkit 原始字节/字段校验，未走 CB 交叉编解码；
     * 保留在报告中单独列出，但不作为"声明覆盖却未执行"的失败项。
     * EventPacket / LevelEventGenericPacket 由 MiscPacketRegressionTest 直接解析 Nukkit 字节覆盖。
     */
    private static final Set<String> MANUAL_BYTE_VERIFICATION = Set.of(
            "EventPacket",
            "LevelEventGenericPacket");
    private static final Path FILE = Path.of(
            System.getProperty("packet.coverage.file",
                    Path.of(System.getProperty("user.dir", "."), "target", "packet-coverage.txt").toString()));

    private PacketCoverageReport() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, Set<Direction>> executed = readExecuted();
        Set<String> knownPackets = scanDataPacketNames();
        Map<String, Set<Direction>> claimed = scanClaimedDirections(knownPackets);

        System.out.println("=== Packet Cross-Codec Coverage (actual executions) ===");
        System.out.printf("executed: %d packets (encode=%d, decode=%d)%n",
                executed.size(), count(executed, Direction.ENCODE), count(executed, Direction.DECODE));
        System.out.printf("claimed by @ParameterizedTest names: %d packets%n", claimed.size());

        Set<String> claimedButNotExecuted = new TreeSet<>(claimed.keySet());
        claimedButNotExecuted.removeAll(executed.keySet());
        Set<String> manualOnly = new TreeSet<>(claimedButNotExecuted);
        manualOnly.retainAll(MANUAL_BYTE_VERIFICATION);
        Set<String> unexpected = new TreeSet<>(claimedButNotExecuted);
        unexpected.removeAll(MANUAL_BYTE_VERIFICATION);

        printMatrix(executed, claimed);
        if (!manualOnly.isEmpty()) {
            System.out.println("\nMANUAL BYTE VERIFICATION (not cross-codec, " + manualOnly.size() + "):");
            manualOnly.forEach(name -> System.out.println("  " + name));
        }
        if (!unexpected.isEmpty()) {
            System.out.println("\nCLAIMED BUT NEVER EXECUTED (" + unexpected.size() + "):");
            unexpected.forEach(name -> System.out.println("  " + name));
        }

        if (executed.isEmpty()) {
            throw new IllegalStateException("No cross-codec execution recorded; the regression suite did not run "
                    + "or PacketCoverageRegistry was not instrumented. File: " + FILE);
        }
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException("Tests claim coverage but never executed cross-codec for: "
                    + unexpected);
        }
        System.out.println("OK: every claimed packet was actually cross-encoded/decoded or explicitly byte-verified.");
    }

    private static int count(Map<String, Set<Direction>> coverage, Direction direction) {
        return (int) coverage.values().stream().filter(directions -> directions.contains(direction)).count();
    }

    private static void printMatrix(Map<String, Set<Direction>> executed, Map<String, Set<Direction>> claimed) {
        Set<String> names = new TreeSet<>(executed.keySet());
        names.addAll(claimed.keySet());
        System.out.printf("%n%-52s %-7s %-7s %s%n", "Packet", "Encode", "Decode", "Claimed");
        for (String name : names) {
            Set<Direction> actual = executed.getOrDefault(name, Set.of());
            System.out.printf("%-52s %-7s %-7s %s%n",
                    name,
                    actual.contains(Direction.ENCODE) ? "YES" : "-",
                    actual.contains(Direction.DECODE) ? "YES" : "-",
                    claimed.containsKey(name) ? "YES" : "-");
        }
    }

    private static Map<String, Set<Direction>> readExecuted() throws Exception {
        Map<String, Set<Direction>> executed = new LinkedHashMap<>();
        if (!Files.exists(FILE)) {
            return executed;
        }
        for (String line : Files.readAllLines(FILE)) {
            int comma = line.lastIndexOf(',');
            if (comma <= 0) {
                continue;
            }
            String name = line.substring(0, comma);
            try {
                Direction direction = Direction.valueOf(line.substring(comma + 1));
                executed.computeIfAbsent(name, ignored -> EnumSet.noneOf(Direction.class)).add(direction);
            } catch (IllegalArgumentException ignored) {
                // Ignore partial/corrupt lines from concurrent appends.
            }
        }
        return executed;
    }

    private static Map<String, Set<Direction>> scanClaimedDirections(Set<String> knownPackets) {
        Map<String, Set<Direction>> claimed = new LinkedHashMap<>();
        for (String testPackage : List.of(
                "cn.nukkit.network.protocol.regression.encode",
                "cn.nukkit.network.protocol.regression.decode")) {
            for (Class<?> testClass : scanClassesInPackage(testPackage)) {
                if (testClass == PacketCoverageReport.class) {
                    continue;
                }
                for (Method method : testClass.getDeclaredMethods()) {
                    ParameterizedTest annotation = method.getAnnotation(ParameterizedTest.class);
                    if (annotation == null) {
                        continue;
                    }
                    Matcher matcher = PACKET_NAME.matcher(annotation.name());
                    if (!matcher.find()) {
                        continue;
                    }
                    String packetName = matcher.group(1);
                    if (knownPackets.contains(packetName)) {
                        claimed.computeIfAbsent(packetName, ignored -> EnumSet.noneOf(Direction.class));
                    }
                }
            }
        }
        return claimed;
    }

    private static Set<String> scanDataPacketNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> type : scanClassesInPackage("cn.nukkit.network.protocol")) {
            if (DataPacket.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                names.add(type.getSimpleName());
            }
        }
        return names;
    }

    private static List<Class<?>> scanClassesInPackage(String basePackage) {
        List<Class<?>> result = new ArrayList<>();
        String path = basePackage.replace('.', '/');
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = loader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (!"file".equals(resource.getProtocol())) {
                    continue;
                }
                File directory = new File(resource.toURI());
                if (directory.isDirectory()) {
                    scanDirectory(directory, basePackage, result);
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to scan classes in " + basePackage, failure);
        }
        return result;
    }

    private static void scanDirectory(File directory, String packageName, List<Class<?>> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    result.add(Class.forName(className, false,
                            Thread.currentThread().getContextClassLoader()));
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // skip classes that cannot be loaded
                }
            }
        }
    }
}
