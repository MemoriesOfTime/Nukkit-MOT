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
 * 依赖自动下载启动引导：lib/ 缺依赖或文件损坏时按 DEPENDENCIES.txt 从 Maven 仓库下载并重启进程，
 * 全部就位且 sha256 校验通过时直通 {@code cn.nukkit.Nukkit}（启动多一次 lib/ 全量哈希，防损坏依赖带病启动）。
 * lib/（默认位置）由引导独占管理，重启前清理清单外的旧 jar，避免通配符 classpath 同时加载新旧版本。
 * <p>
 * Bootstrap that downloads missing or corrupted lib/ dependencies per DEPENDENCIES.txt and re-execs,
 * or passes straight through {@code cn.nukkit.Nukkit} once every entry passes a full sha256 pass over
 * lib/ (so a corrupted jar is re-downloaded instead of crashing the server mid-startup).
 * The default lib/ directory is exclusively bootstrap-managed: stale jars outside the
 * manifest are removed before re-exec so the wildcard classpath never mixes jar versions.
 * <p>
 * 运维旋钮（系统属性）：{@code -Dnukkit.libs.dir=<目录>} 覆盖依赖目录，相对路径按工作目录解析，
 * 默认为 jar 同级 lib/；自定义目录不清理清单外旧文件，但内容仍按清单 sha256 校验（与默认目录一致）；
 * {@code -Dnukkit.libs.repos=<逗号分隔仓库URL>} 整体替换清单内置镜像列表（非追加）；
 * {@code -Dnukkit.libs.verify=false} 跳过本地 sha256 重校验，文件存在即判就绪——供自行替换/修补
 * 依赖 jar 的运维使用，下载本身的 sha256 校验不受此开关影响；
 * {@code -Dnukkit.bootstrap.lang=<语言>} 覆盖消息语言（见 {@link BootstrapLang}）。
 * <p>
 * Operational knobs (system properties): {@code -Dnukkit.libs.dir=<dir>} overrides the
 * dependency directory (relative paths resolve against the working directory; the default
 * is lib/ next to the jar; a custom directory is never cleaned but its contents are still
 * sha256-verified against the manifest, same as the default location);
 * {@code -Dnukkit.libs.repos=<comma-separated repository URLs>} replaces the manifest's
 * built-in mirror list wholesale rather than appending to it;
 * {@code -Dnukkit.libs.verify=false} skips the local sha256 re-verification so presence
 * alone counts as ready — for operators who swap in their own patched jars; downloaded
 * files are still sha256-checked regardless; and
 * {@code -Dnukkit.bootstrap.lang=<language>} overrides the message language (see {@link BootstrapLang}).
 * <p>
 * 控制台消息经 {@link BootstrapLang} 多语言化（六语言，英文兜底）。
 * <p>
 * Console messages are localized through {@link BootstrapLang} (six languages, English fallback).
 * <p>
 * 只允许使用 JDK：下载完成前 classpath 上没有任何第三方库（含日志），输出走 System.out/err。
 * <p>
 * JDK-only by design: no third-party library (logging included) is available before the
 * downloads finish, so all output goes to System.out/System.err.
 */
public final class Bootstrap {

    private static final String LIBS_DIR_PROPERTY = "nukkit.libs.dir";
    private static final String REPOS_PROPERTY = "nukkit.libs.repos";
    private static final String VERIFY_PROPERTY = "nukkit.libs.verify";
    /** reexec 标记：子进程跳过就绪检查直接启动（父进程已跑完就绪/修复路径）。 */
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
            // 只有父进程跑完就绪/修复路径后才会带此标记重启 / set only by a parent that
            // finished the readiness/repair path
            launchNukkit(args);
            return;
        }
        boolean verify = Boolean.parseBoolean(System.getProperty(VERIFY_PROPERTY, "true"));
        if (dependenciesReady(verify)) {
            System.out.println(BootstrapLang.getLine(verify ? "lib.verified" : "lib.skippedVerify"));
            launchNukkit(args);
            return;
        }

        Path jar = locateSelfJar();
        Path libsDir = resolveLibsDir(jar);
        DependencyManifest manifest;
        try {
            manifest = DependencyManifest.loadFromClasspath();
        } catch (Exception e) {
            System.err.println(BootstrapLang.getLine("manifest.loadFailed", e.getMessage()));
            System.exit(1);
            return;
        }
        List<String> repos = configuredRepos(manifest);

        try {
            Files.createDirectories(libsDir);
        } catch (IOException e) {
            System.err.println(BootstrapLang.getLine("libsDir.createFailed", libsDir, e.getMessage()));
            System.exit(1);
            return;
        }
        downloadAll(manifest.entries(), repos, libsDir, verify);
        cleanStaleFiles(manifest, jar, libsDir);

        System.out.println(BootstrapLang.getLine("reexec.start"));
        reexec(jar, libsDir, args);
    }

    /**
     * 就绪判定：有清单时逐条校验 lib/ 文件存在且 sha256 与清单一致（损坏即转入下载路径修复；
     * {@code verifyHashes=false} 时退化为仅存在性检查，即 {@code -Dnukkit.libs.verify=false} 语义），
     * 再用探测类确认当前 classpath 真正可见（-Dnukkit.libs.dir 可指向别处，jar 清单的 Class-Path 未必覆盖）；
     * 无清单或非 jar 运行（shaded jar / IDE）退回探测类单检，维持旧语义。
     */
    private static boolean dependenciesReady(boolean verifyHashes) {
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
        return libFilesMatchManifest(manifest, resolveLibsDir(jar), verifyHashes) && probeClassLoadable();
    }

    /**
     * 就绪判定的完整性核对：逐条校验 lib/ 文件存在且 sha256 与清单一致，缺失、损坏
     * （截断、坏镜像缓存、被篡改）或不可读的文件都判未就绪，交给下载路径整体修复后重启。
     * 代价是每次启动对全部依赖做一遍哈希（数十 MB 级通常亚秒）。
     * {@code verifyHashes=false} 跳过哈希只看存在，供自行替换依赖的运维使用。
     * <p>
     * Integrity half of the readiness check: every manifest entry must exist with a matching sha256;
     * missing, corrupted (truncation, bad mirror cache, tampering) or unreadable files count as not
     * ready so the download path repairs them before launch. Cost is one hash pass over lib/ per
     * start (sub-second for tens of MB). {@code verifyHashes=false} checks presence only, for
     * operators who swap in their own jars.
     */
    static boolean libFilesMatchManifest(DependencyManifest manifest, Path libsDir, boolean verifyHashes) {
        for (DependencyManifest.Entry entry : manifest.entries()) {
            Path file = entry.libFile(libsDir);
            if (!Files.isRegularFile(file)) {
                return false;
            }
            if (!verifyHashes) {
                continue;
            }
            try {
                if (!DependencyManifest.sha256Hex(file).equals(entry.sha256())) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean probeClassLoadable() {
        return classLoadable(Bootstrap.class.getClassLoader(), PROBE_CLASS);
    }

    /**
     * 类可见性探测：类缺失、class 文件损坏或缺引用类都按「不可见」处理（转入下载修复路径），
     * 不让引导在依赖未就绪前以裸 Error 崩溃；静态初始化失败等真实代码问题仍照常抛出。
     * <p>
     * Class-visibility probe: a missing class, corrupt class file or missing referenced class
     * all count as not visible (routing to the repair path) instead of crashing bootstrap with
     * a raw Error; genuine failures such as static-initializer errors still propagate.
     */
    static boolean classLoadable(ClassLoader loader, String className) {
        try {
            Class.forName(className, true, loader);
            return true;
        } catch (ClassNotFoundException | ClassFormatError | NoClassDefFoundError e) {
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
            throw new IllegalStateException(BootstrapLang.get("launch.failed"), cause);
        }
    }

    /** 定位自身 jar；IDE/classpath 运行拿不到 jar 文件时明确报错退出。 */
    private static Path locateSelfJar() {
        Path jar = selfJarOrNull();
        if (jar != null) {
            return jar;
        }
        System.err.println(BootstrapLang.getLine("launch.requiresJar"));
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

    private static void downloadAll(List<DependencyManifest.Entry> entries, List<String> repos, Path libsDir,
                                    boolean verifyExisting) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        int total = entries.size();
        for (int i = 0; i < entries.size(); i++) {
            downloadEntry(client, entries.get(i), i + 1, total, repos, libsDir, verifyExisting);
        }
    }

    private static void downloadEntry(HttpClient client, DependencyManifest.Entry entry,
                                      int index, int total, List<String> repos, Path libsDir,
                                      boolean verifyExisting) {
        Path target = entry.libFile(libsDir);
        try {
            // verifyExisting=false（-Dnukkit.libs.verify=false）时存在即跳过，尊重运维自替换的 jar
            // With verifyExisting=false (-Dnukkit.libs.verify=false) presence alone skips the
            // download, respecting operator-swapped jars
            if (Files.isRegularFile(target)
                    && (!verifyExisting || DependencyManifest.sha256Hex(target).equals(entry.sha256()))) {
                System.out.println(BootstrapLang.getLine("download.exists", index, total, entry.fileName()));
                return;
            }
        } catch (IOException e) {
            // 不可读（权限/磁盘错误）按损坏处理：走下方下载替换自愈，rename 只需目录写权限
            // Unreadable (permissions/disk error) counts as corrupted: the rename-based
            // replacement below only needs write permission on the directory
            System.err.println(BootstrapLang.getLine("download.verifyExistingFailed", target, e.getMessage()));
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
                    System.err.println(BootstrapLang.getLine("download.shaMismatch", url, entry.sha256(), actual));
                    Files.deleteIfExists(part);
                    continue;
                }
                try {
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println(BootstrapLang.getLine("download.ok", index, total, entry.fileName(),
                        String.format(Locale.ROOT, "%.1f", Files.size(target) / 1048576.0)));
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println(BootstrapLang.getLine("download.interrupted", entry.fileName()));
                deleteQuietly(part);
                System.exit(1);
                throw new AssertionError("unreachable");
            } catch (IOException | IllegalArgumentException e) {
                // 换下一个仓库重试 / try the next repository
            }
        }
        deleteQuietly(part);
        System.err.println(BootstrapLang.getLine("download.allFailed", entry.fileName()));
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
            System.err.println(BootstrapLang.getLine("clean.scanFailed", libsDir, e.getMessage()));
            return;
        }
        for (Path file : stale) {
            try {
                Files.deleteIfExists(file);
                System.out.println(BootstrapLang.getLine("clean.removed", file.getFileName()));
            } catch (IOException e) {
                System.err.println(BootstrapLang.getLine("clean.deleteFailed", file, e.getMessage()));
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
            System.err.println(BootstrapLang.getLine("reexec.failed", e.getMessage()));
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
