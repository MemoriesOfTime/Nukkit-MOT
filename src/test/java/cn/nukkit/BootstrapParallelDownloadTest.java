package cn.nukkit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * downloadAll 并行下载行为：固定线程池并发（服务端观测到请求重叠）、已就绪文件跳过网络、
 * 失败明细按清单顺序携带全部尝试过的 URL 返回而非直接退出进程。
 * <p>
 * downloadAll parallel behavior: requests overlap on a fixed pool (observed server-side),
 * ready files skip the network, and failures come back in manifest order with every attempted
 * URL instead of exiting the JVM.
 */
class BootstrapParallelDownloadTest {

    /** 服务端人为延迟：拉长请求在服务端的存活窗口，使并发下载的重叠可被可靠观测 / server-side delay per artifact, widening the in-flight window so overlap is reliably observable */
    private static final long SLOW_RESPONSE_MILLIS = 300;
    private static final String THREADS_PROPERTY = "nukkit.libs.threads";

    @TempDir
    Path libs;

    private HttpServer server;
    private ExecutorService serverPool;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();

    @AfterEach
    void tearDown() {
        System.clearProperty(THREADS_PROPERTY);
        if (server != null) {
            server.stop(0);
        }
        if (serverPool != null) {
            serverPool.shutdownNow();
        }
    }

    @Test
    void downloadsOverlapAndSucceed() throws Exception {
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        artifacts.put(artifactPath("a"), bytes("a"));
        artifacts.put(artifactPath("b"), bytes("b"));
        artifacts.put(artifactPath("c"), bytes("c"));
        startServer(artifacts);

        // 不做墙钟断言：绝对/相对耗时在机器负载下都会假失败，重叠证据由 peakInFlight 断言承担
        // No wall-clock assertion: absolute or relative timing false-fails under machine load; peakInFlight carries the overlap proof
        List<Bootstrap.DownloadFailure> failures = Bootstrap.downloadAll(
                manifest("a", "b", "c").entries(), repos(), libs, true);

        assertTrue(failures.isEmpty(), "应无下载失败 / expected no failures: " + failures);
        for (String name : new String[]{"a", "b", "c"}) {
            Path file = libs.resolve(name + "-1.0.jar");
            assertTrue(Files.isRegularFile(file), "应落盘 / expected on disk: " + file);
            assertArrayEquals(bytes(name), Files.readAllBytes(file), "内容应与清单 sha 对应 / content must match the manifest sha");
        }
        assertEquals(3, requests.get(), "每条依赖恰好一个请求 / exactly one request per entry");
        assertTrue(peakInFlight.get() >= 2, "服务端应观测到请求重叠，实际峰值 " + peakInFlight.get() + " / requests must overlap, peak=" + peakInFlight.get());
    }

    @Test
    void failuresComeBackInManifestOrderWithAttemptedUrls() throws Exception {
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        artifacts.put(artifactPath("a"), bytes("a"));
        // b 在两个仓库都 404 / b is absent from both repositories
        artifacts.put(artifactPath("c"), bytes("c"));
        startServer(artifacts);

        List<Bootstrap.DownloadFailure> failures = Bootstrap.downloadAll(
                manifest("a", "b", "c").entries(), repos(), libs, true);

        assertEquals(1, failures.size(), "仅 b 失败 / only b should fail");
        assertEquals("b-1.0.jar", failures.get(0).entry().fileName());
        assertEquals(2, failures.get(0).attemptedUrls().size(), "两个仓库都应被尝试 / both repos must be attempted");
        for (String url : failures.get(0).attemptedUrls()) {
            assertTrue(url.endsWith("test/b/1.0/b-1.0.jar"), "URL 应指向 b / URL must address b: " + url);
        }
        assertTrue(Files.isRegularFile(libs.resolve("a-1.0.jar")), "失败不应拖垮其他条目 / a must still land");
        assertTrue(Files.isRegularFile(libs.resolve("c-1.0.jar")), "失败不应拖垮其他条目 / c must still land");
    }

    @Test
    void existingFilesSkipNetwork() throws Exception {
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        artifacts.put(artifactPath("c"), bytes("c"));
        startServer(artifacts);
        Files.write(libs.resolve("a-1.0.jar"), bytes("a"));
        Files.write(libs.resolve("b-1.0.jar"), bytes("b"));

        List<Bootstrap.DownloadFailure> failures = Bootstrap.downloadAll(
                manifest("a", "b", "c").entries(), repos(), libs, true);

        assertTrue(failures.isEmpty());
        assertEquals(1, requests.get(), "已就绪文件不应发请求 / ready files must not hit the network");
        assertTrue(Files.isRegularFile(libs.resolve("c-1.0.jar")));
    }

    @Test
    void singleThreadStillCompletesWithoutOverlap() throws Exception {
        System.setProperty(THREADS_PROPERTY, "1");
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        for (String name : new String[]{"a", "b", "c"}) {
            artifacts.put(artifactPath(name), bytes(name));
        }
        startServer(artifacts);

        List<Bootstrap.DownloadFailure> failures = Bootstrap.downloadAll(
                manifest("a", "b", "c").entries(), repos(), libs, true);

        assertTrue(failures.isEmpty());
        assertEquals(1, peakInFlight.get(), "threads=1 时请求应逐个发出 / a single thread must not overlap requests");
    }

    @Test
    void threadsPropertyFallsBackToDefaultWhenInvalid() {
        assertEquals(3, Bootstrap.downloadThreads(), "未配置取默认 / default applies when unset");
        for (String invalid : new String[]{"abc", "0", "-1", " ", "1.5"}) {
            System.setProperty(THREADS_PROPERTY, invalid);
            assertEquals(3, Bootstrap.downloadThreads(), "非法值回退默认 / invalid value falls back: " + invalid);
        }
        System.setProperty(THREADS_PROPERTY, "7");
        assertEquals(7, Bootstrap.downloadThreads());
    }

    @Test
    void httpClientThreadsReleasedAfterDownload() throws Exception {
        // JDK 21+ 才有 HttpClient.close()；17 上引导无法释放 selector 线程（已知无害残留）
        // HttpClient.close() exists only on JDK 21+; on 17 the bootstrap cannot release the selector thread
        assumeTrue(closeAvailable());
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        artifacts.put(artifactPath("a"), bytes("a"));
        startServer(artifacts);

        Set<String> before = httpClientThreadNames();
        Bootstrap.downloadAll(manifest("a").entries(), repos(), libs, true);
        Thread.sleep(3_000); // close 异步生效 / close() takes effect asynchronously

        assertEquals(before, httpClientThreadNames(),
                "下载完成后不应残留 HttpClient 线程 / no HttpClient threads may linger after the download");
    }

    private static boolean closeAvailable() {
        try {
            java.net.http.HttpClient.class.getMethod("close");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Set<String> httpClientThreadNames() {
        Set<String> names = new HashSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().contains("HttpClient")) {
                names.add(thread.getName());
            }
        }
        return names;
    }

    private void startServer(Map<String, byte[]> artifacts) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPool = Executors.newFixedThreadPool(8);
        server.setExecutor(serverPool);
        server.createContext("/", exchange -> {
            // Count at handler entry: a finally-position increment races the client — the body is
            // already flushed once the stream closes, so downloadAll can return before the counter
            // catches up
            requests.incrementAndGet();
            int now = inFlight.incrementAndGet();
            peakInFlight.accumulateAndGet(now, Math::max);
            try {
                byte[] body = artifacts.get(exchange.getRequestURI().getPath());
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                Thread.sleep(SLOW_RESPONSE_MILLIS);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
                exchange.close();
            }
        });
        server.start();
    }

    private List<String> repos() {
        return List.of("http://127.0.0.1:" + server.getAddress().getPort() + "/repoA",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/repoA-backup");
    }

    private DependencyManifest manifest(String... names) {
        StringBuilder content = new StringBuilder("repos=").append(repos().get(0)).append('|').append(repos().get(1));
        for (String name : names) {
            content.append('\n').append("test:").append(name).append(":1.0:")
                    .append(name).append("-1.0.jar:").append(sha256Hex(bytes(name)));
        }
        return DependencyManifest.parse(content.toString());
    }

    private static String artifactPath(String name) {
        return "/repoA/test/" + name + "/1.0/" + name + "-1.0.jar";
    }

    private static byte[] bytes(String name) {
        return ("payload-of-" + name).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] bytes) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
