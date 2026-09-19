package cn.nukkit;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/**
 * 依赖自动下载启动引导：lib/ 缺依赖时按 DEPENDENCIES.txt 从 Maven 仓库下载并重启进程，
 * 依赖已就位时零开销直通 {@code cn.nukkit.Nukkit}。lib/（默认位置）由引导独占管理，
 * 重启前清理清单外的旧 jar，避免通配符 classpath 同时加载新旧版本。
 * <p>
 * Bootstrap that downloads missing lib/ dependencies per DEPENDENCIES.txt and re-execs,
 * or passes straight through to {@code cn.nukkit.Nukkit} when everything is in place.
 * The default lib/ directory is exclusively bootstrap-managed: stale jars outside the
 * manifest are removed before re-exec so the wildcard classpath never mixes jar versions.
 * <p>
 * 运维旋钮（系统属性）：{@code -Dnukkit.libs.dir=<目录>} 覆盖依赖目录，相对路径按工作目录解析，
 * 默认为 jar 同级 lib/，自定义目录视为用户管理、不清理旧文件；
 * {@code -Dnukkit.libs.repos=<逗号分隔仓库URL>} 整体替换清单内置镜像列表（非追加）。
 * <p>
 * Operational knobs (system properties): {@code -Dnukkit.libs.dir=<dir>} overrides the
 * dependency directory (relative paths resolve against the working directory; the default
 * is lib/ next to the jar; a custom directory is user-managed and never cleaned), and
 * {@code -Dnukkit.libs.repos=<comma-separated repository URLs>} replaces the manifest's
 * built-in mirror list wholesale rather than appending to it.
 * <p>
 * 只允许使用 JDK：下载完成前 classpath 上没有任何第三方库（含日志），输出走 System.out/err。
 * <p>
 * JDK-only by design: no third-party library (logging included) is available before the
 * downloads finish, so all output goes to System.out/System.err.
 */
public final class Bootstrap {

    private static final String LIBS_DIR_PROPERTY = "nukkit.libs.dir";
    private static final String REPOS_PROPERTY = "nukkit.libs.repos";
    /** reexec 标记：子进程跳过就绪检查直接启动（父进程已下载并校验完毕）。 */
    private static final String REEXEC_MARK_PROPERTY = "nukkit.bootstrap.reexec";
    /**
     * 仅作 classpath 可见性探测（无清单的 shaded jar、IDE 运行，以及 libs.dir 指向别处时）；
     * 有清单时以「lib/ 文件齐全」为准，避免清单缺该依赖导致重启循环。
     */
    private static final String PROBE_CLASS = "io.netty.channel.Channel";

    /** 启动器管理、重组 classpath 时必须丢弃的参数形态（取值在下一个 argv 元素）。 */
    private static final Set<String> LAUNCHER_MANAGED_FLAGS = Set.of("-cp", "-classpath", "--class-path", "-jar");

    private Bootstrap() {
    }

    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean(REEXEC_MARK_PROPERTY)) {
            // 只有父进程下载并逐条校验成功后才会带此标记重启 / set only by a parent that verified every entry
            launchNukkit(args);
            return;
        }
        if (dependenciesReady()) {
            System.out.println("[Bootstrap] 依赖已就绪，直接启动 / dependencies ready");
            launchNukkit(args);
            return;
        }

        Path jar = locateSelfJar();
        Path libsDir = resolveLibsDir(jar);
        DependencyManifest manifest;
        try {
            manifest = DependencyManifest.loadFromClasspath();
        } catch (Exception e) {
            System.err.println("[Bootstrap] 错误: 读取或解析 DEPENDENCIES.txt 失败 / cannot load DEPENDENCIES.txt: " + e.getMessage());
            System.exit(1);
            return;
        }
        List<String> repos = configuredRepos(manifest);

        try {
            Files.createDirectories(libsDir);
        } catch (IOException e) {
            System.err.println("[Bootstrap] 错误: 无法创建依赖目录 / cannot create " + libsDir + ": " + e.getMessage());
            System.exit(1);
            return;
        }
        downloadAll(manifest.entries(), repos, libsDir);
        cleanStaleFiles(manifest, jar, libsDir);

        System.out.println("[Bootstrap] 依赖就绪，重启进程启动服务器 / restarting");
        reexec(jar, libsDir, args);
    }

    /**
     * 就绪判定：有清单时逐条核对 lib/ 文件齐全，再用探测类确认当前 classpath 真正可见
     * （-Dnukkit.libs.dir 可指向别处，jar 清单的 Class-Path 未必覆盖）；
     * 无清单或非 jar 运行（shaded jar / IDE）退回探测类单检，维持旧语义。
     */
    private static boolean dependenciesReady() {
        DependencyManifest manifest;
        try {
            manifest = DependencyManifest.loadFromClasspath();
        } catch (Exception e) {
            return probeClassLoadable();
        }
        Path jar = selfJarOrNull();
        if (jar == null) {
            return probeClassLoadable();
        }
        Path libsDir = resolveLibsDir(jar);
        for (DependencyManifest.Entry entry : manifest.entries()) {
            if (!Files.isRegularFile(entry.libFile(libsDir))) {
                return false;
            }
        }
        return probeClassLoadable();
    }

    private static boolean probeClassLoadable() {
        try {
            Class.forName(PROBE_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void launchNukkit(String[] args) throws Exception {
        try {
            Class<?> nukkit = Class.forName("cn.nukkit.Nukkit");
            Method main = nukkit.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("启动 Nukkit 失败 / failed to start Nukkit", cause);
        }
    }

    /** 定位自身 jar；IDE/classpath 运行拿不到 jar 文件时明确报错退出。 */
    private static Path locateSelfJar() {
        Path jar = selfJarOrNull();
        if (jar != null) {
            return jar;
        }
        System.err.println("[Bootstrap] 错误: 引导只支持 jar 运行（IDE 或目录运行请用 java -jar）"
                + " / bootstrap requires java -jar");
        System.exit(1);
        throw new AssertionError("unreachable");
    }

    /** 自身 jar 位置；不是 jar 运行（IDE 目录 classpath 等）时返回 null。 */
    private static Path selfJarOrNull() {
        try {
            Path path = Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                return path;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path resolveLibsDir(Path jar) {
        String configured = System.getProperty(LIBS_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path dir = Path.of(configured);
            return dir.isAbsolute() ? dir : Path.of(System.getProperty("user.dir")).resolve(dir);
        }
        return jar.toAbsolutePath().getParent().resolve("lib");
    }

    private static List<String> configuredRepos(DependencyManifest manifest) {
        String configured = System.getProperty(REPOS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return manifest.repos();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void downloadAll(List<DependencyManifest.Entry> entries, List<String> repos, Path libsDir) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        int total = entries.size();
        for (int i = 0; i < entries.size(); i++) {
            downloadEntry(client, entries.get(i), i + 1, total, repos, libsDir);
        }
    }

    private static void downloadEntry(HttpClient client, DependencyManifest.Entry entry,
                                      int index, int total, List<String> repos, Path libsDir) {
        Path target = entry.libFile(libsDir);
        try {
            if (Files.isRegularFile(target) && DependencyManifest.sha256Hex(target).equals(entry.sha256())) {
                System.out.println("[Bootstrap] 下载 (" + index + "/" + total + ") " + entry.fileName() + " ... 已存在，跳过");
                return;
            }
        } catch (IOException e) {
            System.err.println("[Bootstrap] 错误: 校验既有文件失败 / cannot verify " + target + ": " + e);
            System.exit(2);
        }

        Path part = libsDir.resolve(entry.fileName() + ".part");
        List<String> attempted = new ArrayList<>();
        for (String repo : repos) {
            String url = entry.downloadUrl(repo);
            attempted.add(url);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(120))
                        .GET()
                        .build();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(part,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
                if (response.statusCode() != 200) {
                    Files.deleteIfExists(part);
                    continue;
                }
                String actual = DependencyManifest.sha256Hex(part);
                if (!actual.equals(entry.sha256())) {
                    // 200 但内容不对（镜像缓存损坏、同时间戳重新发布）：换下一个仓库，全部失败才退出
                    // 200 with wrong body (corrupted mirror cache etc.): try the next repository
                    System.err.println("[Bootstrap] 警告: sha256 校验失败，换下一仓库 / sha256 mismatch for " + url
                            + " (expected " + entry.sha256() + ", got " + actual + ")");
                    Files.deleteIfExists(part);
                    continue;
                }
                try {
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.printf("[Bootstrap] 下载 (%d/%d) %s ... OK (%.1f MB)%n",
                        index, total, entry.fileName(), Files.size(target) / 1048576.0);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[Bootstrap] 错误: 下载被中断 / download interrupted: " + entry.fileName());
                deleteQuietly(part);
                System.exit(1);
                throw new AssertionError("unreachable");
            } catch (IOException | IllegalArgumentException e) {
                // 换下一个仓库重试 / try the next repository
            }
        }
        deleteQuietly(part);
        System.err.println("[Bootstrap] 错误: 全部仓库下载失败 / all repositories failed for " + entry.fileName()
                + ", 尝试过 / attempted:");
        for (String url : attempted) {
            System.err.println("  " + url);
        }
        System.exit(2);
        throw new AssertionError("unreachable");
    }

    /**
     * 清理 lib/ 里清单之外的旧 jar 与 .part 残留：升级服务器 jar（新时间戳 pin）后旧文件若留下，
     * 通配符 classpath 按字典序展开会让旧版本类先命中。仅清理默认 lib/ 位置，自定义目录归用户管理。
     * <p>
     * Removes jars outside the manifest plus leftover .part files: after upgrading the server jar,
     * stale timestamped jars would otherwise be loaded alongside (and shadow) the new ones under the
     * lexicographic wildcard expansion. Only the default lib/ location is cleaned; a custom
     * -Dnukkit.libs.dir stays user-managed.
     */
    private static void cleanStaleFiles(DependencyManifest manifest, Path jar, Path libsDir) {
        Path defaultLibs = jar.toAbsolutePath().getParent().resolve("lib");
        try {
            if (!Files.isSameFile(libsDir, defaultLibs)) {
                return;
            }
        } catch (IOException e) {
            return;
        }
        Set<String> keep = new HashSet<>();
        for (DependencyManifest.Entry entry : manifest.entries()) {
            keep.add(entry.fileName());
        }
        List<Path> stale = new ArrayList<>();
        try (Stream<Path> files = Files.list(libsDir)) {
            files.forEach(file -> {
                String name = file.getFileName().toString();
                if (name.endsWith(".part") || (name.endsWith(".jar") && !keep.contains(name))) {
                    stale.add(file);
                }
            });
        } catch (IOException e) {
            System.err.println("[Bootstrap] 警告: 无法扫描 lib/ 清理旧文件 / cannot scan " + libsDir + ": " + e.getMessage());
            return;
        }
        for (Path file : stale) {
            try {
                Files.deleteIfExists(file);
                System.out.println("[Bootstrap] 清理旧文件 / removed stale " + file.getFileName());
            } catch (IOException e) {
                System.err.println("[Bootstrap] 警告: 清理失败，旧版本可能与新版本同时加载 / cannot delete " + file + ": " + e.getMessage());
            }
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 尽力清理 / best effort
        }
    }

    private static void reexec(Path jar, Path libsDir, String[] args) {
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString());
        // lib/* 由 java launcher 自行展开，不经过 shell / the lib/* wildcard is expanded by the java launcher itself
        String classpath = jar.toAbsolutePath() + File.pathSeparator + libsDir.toAbsolutePath() + "/*";
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        // 继承本进程 JVM 参数（-Xmx/-XX/-javaagent/-D 等），否则子进程会以默认配置（如默认堆）启动
        // Inherit this JVM's flags, otherwise the child would boot with defaults (e.g. default heap)
        command.addAll(inheritableJvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        command.add("-D" + REEXEC_MARK_PROPERTY + "=true");
        command.add("-cp");
        command.add(classpath);
        command.add("cn.nukkit.Bootstrap");
        command.addAll(Arrays.asList(args));
        try {
            Process process = new ProcessBuilder(command).inheritIO().start();
            System.exit(process.waitFor());
        } catch (IOException e) {
            System.err.println("[Bootstrap] 错误: 重启进程失败 / cannot re-exec: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.exit(1);
        }
        throw new AssertionError("unreachable");
    }

    /**
     * 从 {@code getInputArguments()} 过滤出可继承给重启子进程的参数：运行参数原样透传，
     * 剔除由启动器管理、与重组 classpath 冲突的 -cp/-classpath/-jar（含其取值；等号单 token 形态无后续取值）。
     * <p>
     * Filters getInputArguments() into flags inheritable by the re-exec'd child: runtime flags pass
     * through verbatim in order, while launcher-managed -cp/-classpath/-jar (and their values) are
     * dropped; the single-token {@code =} forms carry no trailing value element.
     */
    static List<String> inheritableJvmArgs(List<String> inputArguments) {
        List<String> inheritable = new ArrayList<>();
        boolean skipValue = false;
        for (String arg : inputArguments) {
            if (skipValue) {
                skipValue = false;
                continue;
            }
            if (arg.startsWith("-cp=") || arg.startsWith("-classpath=") || arg.startsWith("--class-path=")) {
                continue;
            }
            if (LAUNCHER_MANAGED_FLAGS.contains(arg)) {
                skipValue = true;
                continue;
            }
            inheritable.add(arg);
        }
        return inheritable;
    }
}
