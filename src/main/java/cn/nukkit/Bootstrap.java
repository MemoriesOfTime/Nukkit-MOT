package cn.nukkit;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 依赖自动下载启动引导：lib/ 缺依赖或文件损坏时按 DEPENDENCIES.txt 从 Maven 仓库下载，
 * 就绪后同进程 URLClassLoader 直启（或全部就绪时直通当前 classpath）。lib/（默认为工作目录
 * 下的 lib/）由引导独占管理，启动前清理清单外旧 jar，避免新旧版本混载。
 * <p>
 * Bootstrap that downloads missing or corrupted lib/ dependencies per DEPENDENCIES.txt and
 * launches in-process once ready (passing straight through when everything already verifies).
 * The default lib/ directory is bootstrap-managed: stale jars outside the manifest are
 * removed before launch so jar versions never mix.
 * <p>
 * 运维旋钮（系统属性）：{@code nukkit.libs.dir} 依赖目录（默认工作目录 lib/，与
 * {@link Nukkit#DATA_PATH} 同锚点；自定义目录不清理清单外文件）；{@code nukkit.libs.repos}
 * 整体替换内置镜像列表（非追加）；{@code nukkit.libs.threads} 并行下载数（默认 3，
 * 非法值回退默认）；{@code nukkit.libs.verify=false} 跳过本地 sha256 重校验，
 * 供运维自替换依赖 jar（下载本身的校验不受影响）；{@code nukkit.bootstrap.reexec.force=true}
 * 放弃同进程直启，恢复重启进程路径；{@code nukkit.bootstrap.lang} 消息语言（见 {@link BootstrapLang}）。
 * <p>
 * Operational knobs (system properties): {@code nukkit.libs.dir} (dependency dir, default
 * lib/ in the working directory, same anchor as the data dirs; custom dirs are never cleaned);
 * {@code nukkit.libs.repos} (replaces the built-in mirror list wholesale);
 * {@code nukkit.libs.threads} (parallel download count, default 3, invalid values fall back
 * to the default); {@code nukkit.libs.verify=false} (skip local sha256 re-verification for operator-swapped
 * jars; downloads are still verified); {@code nukkit.bootstrap.reexec.force=true} (revive the
 * process-restart path); {@code nukkit.bootstrap.lang} (message language).
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
    private static final String THREADS_PROPERTY = "nukkit.libs.threads";
    /** 默认并行下载数：对镜像友好，带宽受限时再高也无收益 / default parallelism: mirror-friendly; more gains nothing once bandwidth-bound */
    private static final int DEFAULT_DOWNLOAD_THREADS = 3;
    /** reexec 标记：子进程跳过就绪检查直接启动 / reexec mark: the child skips the readiness check */
    private static final String REEXEC_MARK_PROPERTY = "nukkit.bootstrap.reexec";
    /** 运维逃生口：恢复重启进程路径 / escape hatch reviving the process-restart path */
    private static final String FORCE_REEXEC_PROPERTY = "nukkit.bootstrap.reexec.force";
    /**
     * 仅作 classpath 可见性探测（无清单的 shaded jar、IDE 运行、libs.dir 指向别处时），
     * 就绪与否以 lib/ 文件齐全为准，避免清单缺该依赖导致循环重启。
     * <p>
     * Visibility probe only; readiness is decided by the lib/ files, not this class.
     */
    private static final String PROBE_CLASS = "io.netty.channel.Channel";

    /** 须剔除的启动器管理参数（取值在下一个 argv 元素）/ launcher-managed flags whose value is the next argv element */
    private static final Set<String> LAUNCHER_MANAGED_FLAGS = Set.of("-cp", "-classpath", "--class-path", "-jar");

    private Bootstrap() {
    }

    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean(REEXEC_MARK_PROPERTY)) {
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
        Path libsDir = resolveLibsDir();
        // 下载与清理放独立方法：返回后清单、仓库列表等即随栈帧销毁，不再挂在 main 栈帧上陪伴整个服务器生命周期
        // Download and cleanup live in their own frame: once returned, the manifest and repo list
        // are gone instead of lingering on main's frame for the server's whole lifetime.
        prepareDependencies(libsDir, verify);

        if (Boolean.getBoolean(FORCE_REEXEC_PROPERTY)) {
            System.out.println(BootstrapLang.getLine("reexec.start"));
            reexec(jar, libsDir, args);
        }
        System.out.println(BootstrapLang.getLine("launch.inProcess"));
        launchInProcess(jar, libsDir, args);
    }

    /**
     * 修复 lib/ 至清单就绪：下载缺失或损坏条目、清理清单外文件。失败路径直接退出进程；
     * 正常返回表示 lib/ 可用，且本方法持有的清单与仓库列表已随栈帧释放。
     * <p>
     * Repairs lib/ to manifest readiness: downloads missing or corrupted entries and removes
     * stale files. Failure paths exit the JVM; a normal return means lib/ is ready and the
     * manifest-sized state held by this frame is released.
     */
    private static void prepareDependencies(Path libsDir, boolean verify) {
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
        List<DownloadFailure> failures;
        try {
            failures = downloadAll(manifest.entries(), repos, libsDir, verify);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(BootstrapLang.get("download.interrupted"));
            System.exit(1);
            return;
        }
        if (!failures.isEmpty()) {
            for (DownloadFailure failure : failures) {
                System.err.println(BootstrapLang.getLine("download.allFailed", failure.entry().fileName()));
                for (String url : failure.attemptedUrls()) {
                    System.err.println("  " + url);
                }
            }
            System.exit(2);
            return;
        }
        cleanStaleFiles(manifest, libsDir);
    }

    /**
     * 就绪判定：有清单时逐条校验 lib/ 文件存在且 sha256 一致（{@code verifyHashes=false} 退化为
     * 仅存在性检查），再以探测类确认当前 classpath 真正可见——jar 清单的 Class-Path 按 jar 位置
     * 解析，工作目录≠jar 目录时覆盖不到，由同进程启动以显式 classpath 兜底；无清单或非 jar 运行
     * （shaded jar / IDE）退回探测类单检，维持旧语义。
     * <p>
     * Readiness: with a manifest, every lib/ file must exist with a matching sha256
     * (presence-only under verifyHashes=false) and the probe class must be visible on the
     * current classpath — the jar manifest's Class-Path resolves against the jar location,
     * so the in-process launch backs it up with an explicit classpath. Without a manifest
     * (shaded jar / IDE) the probe alone decides.
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
        return libFilesMatchManifest(manifest, resolveLibsDir(), verifyHashes) && probeClassLoadable();
    }

    /**
     * 逐条校验 lib/ 文件存在且 sha256 与清单一致；缺失、损坏或不可读都判未就绪，交给下载路径
     * 整体修复。代价是每次启动对全部依赖做一遍哈希（数十 MB 级通常亚秒）。
     * <p>
     * Every manifest entry must exist with a matching sha256; anything missing, corrupted
     * or unreadable routes to the download path for repair. Costs one hash pass over lib/
     * per start (sub-second for tens of MB).
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
     * 静态初始化失败等真实代码问题仍照常抛出。
     * <p>
     * Class-visibility probe: missing, corrupt or unresolvable classes count as not visible;
     * genuine failures (e.g. static-initializer errors) still propagate.
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
        launchFrom(Bootstrap.class.getClassLoader(), args);
    }

    /**
     * 下载完成后的同进程启动：以 URLClassLoader（自身 jar + lib/*.jar，parent 为平台加载器）加载
     * {@code cn.nukkit.Nukkit} 并反射调用其 main，JVM 参数与 JDWP 调试连接原样保留。parent 必须
     * 低于应用加载器且自身 jar 必须入 URL 列表，否则 Nukkit 类解析不到 lib/ 依赖
     * （NoClassDefFoundError）。
     * <p>
     * In-process launch after the downloads: invokes {@code cn.nukkit.Nukkit} through a
     * URLClassLoader (self jar + lib/*.jar, parented at the platform loader), preserving JVM
     * flags and the JDWP session; the parent must sit below the application loader and the
     * self jar must be on the URL list, or Nukkit cannot resolve its lib/ dependencies.
     */
    private static void launchInProcess(Path jar, Path libsDir, String[] args) throws Exception {
        URLClassLoader loader = new URLClassLoader(classpathUrls(jar, libsDir),
                ClassLoader.getPlatformClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        launchFrom(loader, args);
    }

    // 自身 jar 恒在首位，lib/ 按文件名字典序，与重启路径 lib/* 通配符展开一致
    // Self jar first, lib/ jars in name order, matching the lib/* wildcard expansion
    static URL[] classpathUrls(Path jar, Path libsDir) throws IOException {
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> files = Files.list(libsDir)) {
            files.filter(file -> Files.isRegularFile(file)
                            && file.getFileName().toString().endsWith(".jar"))
                    .forEach(jars::add);
        }
        jars.sort(Comparator.comparing(file -> file.getFileName().toString()));
        URL[] urls = new URL[jars.size() + 1];
        urls[0] = jar.toUri().toURL();
        for (int i = 0; i < jars.size(); i++) {
            urls[i + 1] = jars.get(i).toUri().toURL();
        }
        return urls;
    }

    private static void launchFrom(ClassLoader loader, String[] args) throws Exception {
        try {
            Class<?> nukkit = Class.forName("cn.nukkit.Nukkit", true, loader);
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

    /** 定位自身 jar；classpath 运行拿不到时明确报错退出 / locates the self jar, or exits with an error */
    private static Path locateSelfJar() {
        Path jar = selfJarOrNull();
        if (jar != null) {
            return jar;
        }
        System.err.println(BootstrapLang.getLine("launch.requiresJar"));
        System.exit(1);
        throw new AssertionError("unreachable");
    }

    /** 自身 jar 位置；非 jar 运行（IDE 等）时返回 null / self jar location, null when not run from a jar */
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

    /**
     * 依赖目录：默认为工作目录 lib/，与 players/ 等数据目录同锚点（{@link Nukkit#DATA_PATH}）。
     * <p>
     * Dependency directory: lib/ in the working directory by default — the same anchor as
     * the data directories ({@link Nukkit#DATA_PATH}).
     */
    private static Path resolveLibsDir() {
        String configured = System.getProperty(LIBS_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path dir = Path.of(configured);
            return dir.isAbsolute() ? dir : Path.of(System.getProperty("user.dir")).resolve(dir);
        }
        return defaultLibsDir();
    }

    private static Path defaultLibsDir() {
        return Path.of(System.getProperty("user.dir")).resolve("lib");
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

    /** 单条下载失败明细：清单条目与试过的全部仓库 URL / failure detail: the entry and every repo URL attempted */
    record DownloadFailure(DependencyManifest.Entry entry, List<String> attemptedUrls) {
    }

    /**
     * 并行下载全部条目：固定线程池（{@value #THREADS_PROPERTY}，默认 {@value #DEFAULT_DOWNLOAD_THREADS}）
     * 并发执行，进度以原子完成计数汇报。不在首个失败处中止：所有条目跑完后按清单顺序返回失败明细，
     * 由调用方统一报告退出，避免并发下提前退出留下无法解释的半成品状态。
     * <p>
     * Downloads all entries in parallel on a fixed pool ({@value #THREADS_PROPERTY}, default
     * {@value #DEFAULT_DOWNLOAD_THREADS}) with an atomic completion counter for progress. It never
     * aborts on the first failure: every entry runs, and failures come back in manifest order for
     * the caller to report and exit on.
     */
    static List<DownloadFailure> downloadAll(List<DependencyManifest.Entry> entries, List<String> repos, Path libsDir,
                                             boolean verifyExisting) throws InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        int total = entries.size();
        // 开下前先报数，网络卡时控制台不至于静默 / announce the workload before the first download
        int missing = 0;
        for (DependencyManifest.Entry entry : entries) {
            try {
                if (!entryReady(entry, libsDir, verifyExisting)) {
                    missing++;
                }
            } catch (IOException e) {
                missing++;
            }
        }
        System.out.println(BootstrapLang.getLine("download.start", missing, total));

        AtomicInteger done = new AtomicInteger();
        DownloadFailure[] results = new DownloadFailure[total];
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(downloadThreads(), Math.max(total, 1)));
        try {
            List<Future<?>> futures = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                final int index = i;
                futures.add(pool.submit(() -> results[index] =
                        downloadEntry(client, entries.get(index), done, total, repos, libsDir, verifyExisting)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            // worker 把下载失败表达为返回值，这里只剩意外错误 / workers express download failure as a return value; only unexpected errors land here
            throw new IllegalStateException(e.getCause());
        } finally {
            pool.shutdownNow();
            closeHttpClient(client);
        }
        List<DownloadFailure> failures = new ArrayList<>();
        for (DownloadFailure result : results) {
            if (result != null) {
                failures.add(result);
            }
        }
        return failures;
    }

    /** 并行下载数：{@value #THREADS_PROPERTY} 配置，缺失或非法时回退 {@value #DEFAULT_DOWNLOAD_THREADS} / configured parallelism, falling back to the default when missing or invalid */
    static int downloadThreads() {
        String configured = System.getProperty(THREADS_PROPERTY);
        if (configured != null) {
            try {
                int threads = Integer.parseInt(configured.trim());
                if (threads >= 1) {
                    return threads;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_DOWNLOAD_THREADS;
    }

    /**
     * 关闭下载用 HttpClient：编译目标 17 无 close（JDK 21 才加入），经接口反射调用，
     * 21+ 上彻底释放 selector 线程、连接池与内部 executor；17 上静默跳过，仅残留一个
     * 无害的 daemon SelectorManager 线程（实测空闲 90s 不退，也不阻止 JVM 退出）。
     * <p>
     * Closes the download HttpClient: close() only exists since JDK 21 while the compile
     * target is 17, so it is invoked through the public interface reflectively — on 21+ this
     * fully releases the selector thread, connection pool and internal executor; on 17 it is
     * skipped, leaving a single harmless daemon SelectorManager thread (observed idling for
     * 90s+ without exiting, but never blocking JVM exit either).
     */
    static void closeHttpClient(HttpClient client) {
        try {
            HttpClient.class.getMethod("close").invoke(client);
        } catch (NoSuchMethodException ignored) {
            // Java 17：无 close 可调 / nothing to call before JDK 21
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 尽力释放 / best effort
        }
    }

    private static boolean entryReady(DependencyManifest.Entry entry, Path libsDir, boolean verifyExisting)
            throws IOException {
        Path target = entry.libFile(libsDir);
        return Files.isRegularFile(target)
                && (!verifyExisting || DependencyManifest.sha256Hex(target).equals(entry.sha256()));
    }

    private static DownloadFailure downloadEntry(HttpClient client, DependencyManifest.Entry entry,
                                                 AtomicInteger done, int total, List<String> repos, Path libsDir,
                                                 boolean verifyExisting) {
        Path target = entry.libFile(libsDir);
        try {
            if (entryReady(entry, libsDir, verifyExisting)) {
                System.out.println(BootstrapLang.getLine("download.exists", done.incrementAndGet(), total, entry.fileName()));
                return null;
            }
        } catch (IOException e) {
            // 不可读按损坏处理：下方 rename 替换只需目录写权限
            // Unreadable counts as corrupted; the rename replacement only needs directory write access
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
                    System.err.println(BootstrapLang.getLine("download.shaMismatch", url, entry.sha256(), actual));
                    Files.deleteIfExists(part);
                    continue;
                }
                try {
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println(BootstrapLang.getLine("download.ok", done.incrementAndGet(), total, entry.fileName(),
                        formatSize(Files.size(target))));
                return null;
            } catch (InterruptedException e) {
                // 主线程中断（如下载期间停服）：清理半成品后按失败上交，退出交由调用方
                // Main-thread interrupt (e.g. shutdown mid-download): clean up and report as failure; exiting is the caller's job
                Thread.currentThread().interrupt();
                deleteQuietly(part);
                return new DownloadFailure(entry, attempted);
            } catch (IOException | IllegalArgumentException e) {
                // 换下一个仓库重试 / try the next repository
            }
        }
        deleteQuietly(part);
        return new DownloadFailure(entry, attempted);
    }

    /**
     * 清理 lib/ 里清单之外的旧 jar 与 .part 残留：升级服务器 jar 后旧时间戳 jar 若留下，
     * 会与新版本同加载且按字典序抢先命中。仅清理默认 lib/ 位置，自定义目录归用户管理。
     * <p>
     * Removes jars outside the manifest plus leftover .part files so stale timestamped jars
     * never load alongside the new ones; only the default lib/ location is cleaned.
     */
    private static void cleanStaleFiles(DependencyManifest manifest, Path libsDir) {
        Path defaultLibs = defaultLibsDir();
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

    private static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 尽力清理 / best effort
        }
    }

    /**
     * 重启进程启动（仅 {@code -Dnukkit.bootstrap.reexec.force=true} 时走）：子进程以显式
     * classpath（自身 jar + lib/*）与继承的 JVM 参数重启，带 reexec 标记跳过就绪检查。
     * <p>
     * Process-restarting launch (force flag only): the child restarts with an explicit
     * classpath and inherited JVM flags, skipping the readiness check via the reexec mark.
     */
    private static void reexec(Path jar, Path libsDir, String[] args) {
        String javaBin = ProcessHandle.current().info().command()
                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString());
        // lib/* 由 java launcher 自行展开，不经过 shell / expanded by the java launcher, not the shell
        String classpath = jar.toAbsolutePath() + File.pathSeparator + libsDir.toAbsolutePath() + "/*";
        List<String> command = new ArrayList<>();
        command.add(javaBin);
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
     * 过滤出可继承给重启子进程的 JVM 参数：剔除由启动器管理、与重组 classpath 冲突的
     * -cp/-classpath/-jar（含其取值；等号单 token 形态自带取值）。
     * <p>
     * Filters JVM args inheritable by the re-exec'd child: launcher-managed
     * -cp/-classpath/-jar (and their values) are dropped; single-token = forms carry
     * their value inline.
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
