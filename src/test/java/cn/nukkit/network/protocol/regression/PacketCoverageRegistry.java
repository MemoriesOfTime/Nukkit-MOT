package cn.nukkit.network.protocol.regression;

import cn.nukkit.network.protocol.DataPacket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records packets that were actually cross-encoded/cross-decoded by the regression suite.
 * <p>
 * 之前的覆盖率报告按 {@code @ParameterizedTest(name=...)} 字符串统计，空跑或测试被禁用时仍会
 * 显示“已覆盖”。这里由 {@link AbstractPacketRegressionTest} 在成功完成交叉编解码后写入事实记录，
 * 报告只读取这些事实。
 * <p>
 * 记录同时追加到 {@code target/packet-coverage.txt}，供测试结束后的报告进程读取。文件在首次写入
 * 前清空一次；默认 surefire 单 fork 运行，因此不会跨 fork 丢数据。
 */
public final class PacketCoverageRegistry {

    public enum Direction { ENCODE, DECODE }

    private static final Path FILE = Path.of(
            System.getProperty("packet.coverage.file",
                    Path.of(System.getProperty("user.dir", "."), "target", "packet-coverage.txt").toString()));
    private static final Map<String, Set<Direction>> COVERED = new ConcurrentHashMap<>();
    private static final Object FILE_LOCK = new Object();
    private static boolean filePrepared;

    private PacketCoverageRegistry() {
    }

    public static void record(DataPacket packet, Direction direction) {
        String name = packet.getClass().getSimpleName();
        COVERED.compute(name, (ignored, directions) -> {
            Set<Direction> updated = directions == null ? EnumSet.noneOf(Direction.class) : directions;
            updated.add(direction);
            return updated;
        });
        append(name, direction);
    }

    private static void append(String name, Direction direction) {
        synchronized (FILE_LOCK) {
            try {
                if (!filePrepared) {
                    Files.createDirectories(FILE.toAbsolutePath().getParent());
                    Files.deleteIfExists(FILE);
                    filePrepared = true;
                }
                Files.writeString(FILE, name + "," + direction + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot write packet coverage file " + FILE, failure);
            }
        }
    }

    /** In-memory view for tests that run in the same JVM. */
    public static Map<String, Set<Direction>> snapshot() {
        Map<String, Set<Direction>> copy = new LinkedHashMap<>();
        COVERED.forEach((name, directions) ->
                copy.put(name, Collections.unmodifiableSet(EnumSet.copyOf(directions))));
        return Collections.unmodifiableMap(copy);
    }

    public static Path file() {
        return FILE;
    }
}
