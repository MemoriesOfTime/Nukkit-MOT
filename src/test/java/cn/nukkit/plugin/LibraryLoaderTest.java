package cn.nukkit.plugin;

import cn.nukkit.utils.MainLogger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link LibraryLoader}'s coordinate parsing, pom parsing, scope filtering, cycle handling,
 * path-traversal rejection, XXE rejection, and nearest-wins transitive conflict resolution.
 * The nearest-wins test spins up a local HTTP server; other tests stay offline.
 */
public class LibraryLoaderTest {

    @Test
    public void parsesValidCoordinate() {
        LibraryLoader.Coordinate c = LibraryLoader.parse("com.squareup.okhttp3:okhttp:4.12.0");

        assertEquals("com.squareup.okhttp3", c.groupId());
        assertEquals("okhttp", c.artifactId());
        assertEquals("4.12.0", c.version());
        assertEquals("com/squareup/okhttp3/okhttp/4.12.0", c.repositoryPath());
        assertEquals("okhttp-4.12.0.jar", c.fileName(".jar"));
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", c.key());
    }

    @Test
    public void rejectsMalformedCoordinate() {
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("only:two-parts"));
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("a:b:c:d"));
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse(":missing:groups"));
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse(""));
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse(null));
    }

    /**
     * 回归：路径穿越防护。{@code libraries/} 之外的目标必须被拒。
     * / Regression: path traversal — targets outside {@code libraries/} must be rejected.
     */
    @Test
    public void rejectsPathTraversalInCoordinates() {
        // 各类典型穿越 payload / typical traversal payloads
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("..:lib:1.0"), "纯点段 groupId");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("com.example:..:1.0"), "纯点段 artifactId");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("a:b:c:d"), "多余段（已覆盖）");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("../evil:lib:1.0"), "groupId 含路径分隔");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("com.example:lib/../../evil:1.0"), "artifactId 含 /");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("a:b:c/../../evil"), "version 含 /");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("a\\b:lib:1.0"), "groupId 含反斜杠");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse(".hidden:lib:1.0"), "groupId 首字符为点");
        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parse("com.example:lib:1.0."), "version 尾字符为点");
        // 合法坐标应仍然通过 / legitimate coordinates must still pass
        LibraryLoader.Coordinate ok = LibraryLoader.parse("com.squareup.okhttp3:okhttp:4.12.0");
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", ok.key());
        LibraryLoader.Coordinate dotted = LibraryLoader.parse("org.apache.commons:commons-lang3:3.12.0");
        assertEquals("org/apache/commons/commons-lang3/3.12.0", dotted.repositoryPath());
    }

    @Test
    public void trimsWhitespaceInCoordinate() {
        // P5 回归：plugin.yml 误写空格时应自动 trim，而不是拼出带空格的错误 URL
        LibraryLoader.Coordinate c = LibraryLoader.parse("  com.example : lib : 1.0  ");

        assertEquals("com.example", c.groupId());
        assertEquals("lib", c.artifactId());
        assertEquals("1.0", c.version());
        assertEquals("com/example/lib/1.0", c.repositoryPath(), "trim 后路径不应含空格");
        assertEquals("lib-1.0.jar", c.fileName(".jar"));
    }

    @Test
    public void parsesPomCompileAndRuntimeDependencies(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("test.pom");
        Files.writeString(pom, ""
                + "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>root</artifactId>\n"
                + "  <version>1.0.0</version>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>g.compile</groupId>\n"
                + "      <artifactId>a-compile</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>g.runtime</groupId>\n"
                + "      <artifactId>a-runtime</artifactId>\n"
                + "      <version>2.0</version>\n"
                + "      <scope>runtime</scope>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>g.test</groupId>\n"
                + "      <artifactId>a-test</artifactId>\n"
                + "      <version>3.0</version>\n"
                + "      <scope>test</scope>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>g.provided</groupId>\n"
                + "      <artifactId>a-provided</artifactId>\n"
                + "      <version>4.0</version>\n"
                + "      <scope>provided</scope>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>g.optional</groupId>\n"
                + "      <artifactId>a-optional</artifactId>\n"
                + "      <version>5.0</version>\n"
                + "      <optional>true</optional>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n", StandardCharsets.UTF_8);

        List<LibraryLoader.Coordinate> deps = LibraryLoader.parsePom(pom.toFile());

        assertEquals(2, deps.size(), "应只保留 compile + runtime 范围的依赖 / should keep only compile+runtime");
        assertEquals("g.compile:a-compile:1.0", deps.get(0).key());
        assertEquals("g.runtime:a-runtime:2.0", deps.get(1).key());
    }

    @Test
    public void parsesDependencyManagementSectionWithoutCrashing(@TempDir Path tempDir) throws Exception {
        // P1 回归：dependencyManagement 里的依赖（任意 scope，含 compile）都不应被当作实际依赖
        Path pom = tempDir.resolve("dm.pom");
        Files.writeString(pom, ""
                + "<project>\n"
                + "  <dependencyManagement>\n"
                + "    <dependencies>\n"
                + "      <dependency>\n"
                + "        <groupId>g.dm.import</groupId>\n"
                + "        <artifactId>a-dm-import</artifactId>\n"
                + "        <version>1.0</version>\n"
                + "        <scope>import</scope>\n"
                + "      </dependency>\n"
                + "      <dependency>\n"
                + "        <groupId>g.dm.compile</groupId>\n"
                + "        <artifactId>a-dm-compile</artifactId>\n"
                + "        <version>1.5</version>\n"
                + "      </dependency>\n"
                + "    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>g.real</groupId>\n"
                + "      <artifactId>a-real</artifactId>\n"
                + "      <version>2.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n", StandardCharsets.UTF_8);

        List<LibraryLoader.Coordinate> deps = LibraryLoader.parsePom(pom.toFile());

        // 只保留根 <dependencies> 里的 g.real，dependencyManagement 里的两条都应被忽略
        assertEquals(1, deps.size(), "dependencyManagement 内的依赖（无论 scope）都应被忽略");
        assertEquals("g.real:a-real:2.0", deps.get(0).key());
    }

    @Test
    public void skipsVersionPlaceholdersInPom(@TempDir Path tempDir) throws Exception {
        // P3 回归：含 ${...} 占位符的依赖（version 或继承）无法解析，应跳过而不是当作字面量坐标递归下载
        Path pom = tempDir.resolve("placeholder.pom");
        Files.writeString(pom, ""
                + "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>root</artifactId>\n"
                + "  <version>1.0.0</version>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>g.placeholder</groupId>\n"
                + "      <artifactId>a-placeholder</artifactId>\n"
                + "      <version>${project.version}</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>g.parent</groupId>\n"
                + "      <artifactId>a-parent</artifactId>\n"
                + "      <version>${project.parent.version}</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>g.literal</groupId>\n"
                + "      <artifactId>a-literal</artifactId>\n"
                + "      <version>3.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n", StandardCharsets.UTF_8);

        List<LibraryLoader.Coordinate> deps = LibraryLoader.parsePom(pom.toFile());

        assertEquals(1, deps.size(), "${...} 占位符依赖应被跳过，只保留字面量版本");
        assertEquals("g.literal:a-literal:3.0", deps.get(0).key());
    }

    @Test
    public void parsePomHandlesNoDependencies(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("bare.pom");
        Files.writeString(pom, ""
                + "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>bare</artifactId>\n"
                + "  <version>1.0.0</version>\n"
                + "</project>\n", StandardCharsets.UTF_8);

        List<LibraryLoader.Coordinate> deps = LibraryLoader.parsePom(pom.toFile());

        assertTrue(deps.isEmpty(), "无 dependencies 的 pom 应返回空列表 / pom with no deps should return empty");
    }

    @Test
    public void parsePomRejectsMalformedXml(@TempDir Path tempDir) throws Exception {
        Path pom = tempDir.resolve("bad.pom");
        Files.writeString(pom, "not xml at all", StandardCharsets.UTF_8);

        assertThrows(LibraryLoadException.class, () -> LibraryLoader.parsePom(pom.toFile()));
    }

    /**
     * 回归：XXE 防护。POM 声明外部实体指向本地敏感文件时，敏感内容不得泄漏进任何解析出的坐标。
     * <br>设计要点：sentinel 用纯字母数字（能通过 {@code validateComponent} 的 {@code [A-Za-z0-9.\-_]} 校验），
     * 这样一旦 XXE 防护失效，泄漏的内容会作为合法坐标出现在结果列表里，测试即可捕获。
     * 若 sentinel 含 {@code _} 之外的非法字符，{@code parse()} 会因路径穿越校验丢弃该坐标，
     * 反而掩盖 XXE 泄漏（测试假阴性）。
     * <br>双层断言：
     * <ol>
     *   <li>JDK 自带 Xerces 上 {@code disallow-doctype-decl} 直接拒 DOCTYPE → 解析抛异常（通过）。</li>
     *   <li>若 parser 不支持 {@code disallow-doctype-decl} 而 DOCTYPE 通过，{@code external-general-entities=false}
     *       + {@code FEATURE_SECURE_PROCESSING} 应阻断实体展开——坐标字段不得含 sentinel。</li>
     * </ol>
     * / Regression: XXE protection. When a POM declares an external entity pointing at a local file,
     * the file's content must not leak into any parsed coordinate. The sentinel is alphanumeric so
     * it passes {@code validateComponent}'s charset — if XXE protection regresses, the leaked content
     * shows up as a valid coordinate and the test catches it.
     */
    @Test
    public void parsePomRejectsDoctypeAndExternalEntities(@TempDir Path tempDir) throws Exception {
        // 全字母数字 sentinel：能通过 parse() 的字符集校验，确保泄漏不会被 validateComponent 顺手拦截。
        // / Alphanumeric sentinel: passes parse()'s charset check so a leak isn't masked by
        // validateComponent's path-traversal validation.
        final String SENTINEL = "XXELEAK7c3f9a";
        Path sensitive = tempDir.resolve("secret.txt");
        Files.writeString(sensitive, SENTINEL, StandardCharsets.UTF_8);

        Path pom = tempDir.resolve("xxe.pom");
        Files.writeString(pom, ""
                + "<!DOCTYPE project [\n"
                + "  <!ENTITY xxe SYSTEM \"file:" + sensitive.toUri().getRawPath() + "\">\n"
                + "]>\n"
                + "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>xxe-test</artifactId>\n"
                + "  <version>1.0.0</version>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>&xxe;</groupId>\n"
                + "      <artifactId>a</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n", StandardCharsets.UTF_8);

        List<LibraryLoader.Coordinate> deps;
        try {
            deps = LibraryLoader.parsePom(pom.toFile());
        } catch (LibraryLoadException e) {
            // 主断言：DOCTYPE 被拒是期望行为（JDK 自带 Xerces 路径），通过。
            // / Primary assertion: DOCTYPE rejection is the expected (Xerces) path — pass.
            return;
        }
        // 兜底断言：解析侥幸通过时，外部文件内容绝不能出现在任何坐标字段。
        // / Defense-in-depth: if parsing somehow succeeded, external content must not appear.
        for (LibraryLoader.Coordinate c : deps) {
            assertFalse(c.groupId().contains(SENTINEL), "XXE 泄漏到 groupId / leak into groupId: " + c.groupId());
            assertFalse(c.artifactId().contains(SENTINEL), "XXE 泄漏到 artifactId / leak into artifactId: " + c.artifactId());
            assertFalse(c.version().contains(SENTINEL), "XXE 泄漏到 version / leak into version: " + c.version());
        }
    }

    /**
     * 回归：nearest-wins 语义。直接声明胜过传递依赖：A 传递依赖 X:1.0，同时直接声明 X:2.0 时，
     * 最终下载列表中 X 应为 2.0（路径上包含 {@code /x/2.0/}），而非 1.0。
     * / Regression: nearest-wins. Direct declaration of X:2.0 must override the transitive X:1.0
     * pulled in by another coordinate.
     */
    @Test
    public void directDependencyWinsOverTransitiveConflict(@TempDir Path tempDir) throws Exception {
        // 确保 Server.getInstance() 可用（getBaseFolder 依赖它）。/ Ensure a Server instance exists.
        cn.nukkit.MockServer.init();
        // 将 libraries 根目录重定向到本测试的 @TempDir，避免污染共享 tmpdir。
        // / Redirect the libraries root to this test's @TempDir to avoid polluting the shared tmpdir.
        cn.nukkit.Server server = cn.nukkit.Server.getInstance();
        // 通过 Mockito 临时把 dataPath 指向 @TempDir，让 getBaseFolder() 返回 tempDir/libraries。
        // / Use Mockito to point dataPath at @TempDir so getBaseFolder() returns tempDir/libraries.
        org.mockito.Mockito.when(server.getDataPath()).thenReturn(tempDir.toString());

        // 准备本地 HTTP 仓库，提供 A、X 两个 jar 与对应 POM / Set up a local HTTP repo serving A and X
        byte[] jarBytes = minimalJarBytes();
        com.sun.net.httpserver.HttpServer httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if (path.endsWith(".jar")) {
                body = jarBytes;
            } else if (path.endsWith(".pom")) {
                body = pomForPath(path).getBytes(StandardCharsets.UTF_8);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.start();
        try {
            int port = httpServer.getAddress().getPort();
            String repo = "http://127.0.0.1:" + port + "/";

            // 直接声明 A 与 X:2.0。A 的 POM 声明对 X:1.0 的传递依赖。
            // 旧实现（深度优先 + 入口即 visited）会把 X:1.0 写进 resolved，X:2.0 直接被吞掉。
            // 修复后 Pass 1 先注册 X:2.0，Pass 2 处理 A 的传递依赖时遇到 X 已注册 → 跳过。
            // / Declare A and X:2.0 directly. A's POM declares a transitive dep on X:1.0.
            // The old DFS-with-early-visited impl would let X:1.0 claim X, silently dropping X:2.0.
            // After the fix, Pass 1 registers X:2.0 first; Pass 2 sees X already registered and skips.
            List<String> coords = Arrays.asList("com.example:a:1.0", "com.example:x:2.0");
            URL[] urls = LibraryLoader.resolve(coords, Arrays.asList(repo), MainLogger.getLogger());

            // 找到 X 的 URL，必须是 2.0 版本 / find X's URL; it must be the 2.0 version
            URL xUrl = null;
            for (URL u : urls) {
                if (u.getPath().contains("/x/")) {
                    xUrl = u;
                    break;
                }
            }
            assertNotNull(xUrl, "resolved URL 列表中应包含 X");
            assertTrue(xUrl.getPath().contains("/x/2.0/"),
                    "nearest-wins: 直接声明的 X:2.0 应胜出，实际为 " + xUrl);
        } finally {
            httpServer.stop(0);
            // 恢复 MockServer 默认 dataPath（避免影响后续测试）。/ Restore default dataPath.
            org.mockito.Mockito.when(server.getDataPath()).thenReturn(System.getProperty("java.io.tmpdir"));
        }
    }

    /** 构造一个最小的合法 jar 字节序列（仅含 manifest）。 / Minimal valid jar bytes (manifest only). */
    private static byte[] minimalJarBytes() throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(baos)) {
            jos.putNextEntry(new java.util.jar.JarEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** 为 HTTP 仓库路径生成对应 POM 内容（仅 A 声明对 X:1.0 的传递依赖；其他返回空 POM）。 / Generates POM content for a given repo path. */
    private static String pomForPath(String path) {
        if (path.contains("/a/")) {
            return "<project>\n"
                    + "  <groupId>com.example</groupId>\n"
                    + "  <artifactId>a</artifactId>\n"
                    + "  <version>1.0</version>\n"
                    + "  <dependencies>\n"
                    + "    <dependency>\n"
                    + "      <groupId>com.example</groupId>\n"
                    + "      <artifactId>x</artifactId>\n"
                    + "      <version>1.0</version>\n"
                    + "    </dependency>\n"
                    + "  </dependencies>\n"
                    + "</project>\n";
        }
        // X 的 POM：version 由 URL 路径决定（X:1.0 与 X:2.0 共用同一模板）。
        // / X's POM: version comes from the URL path (both X:1.0 and X:2.0 use this template).
        String version = path.contains("/x/2.0/") ? "2.0" : "1.0";
        return "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>x</artifactId>\n"
                + "  <version>" + version + "</version>\n"
                + "</project>\n";
    }

    @Test
    public void resolveEmptyListReturnsEmptyArray() {
        assertEquals(0, LibraryLoader.resolve(null, null, null).length);
        assertEquals(0, LibraryLoader.resolve(Arrays.asList(), null, null).length);
    }

    @Test
    public void mergeRepositoriesPutsUserFirstThenFallbacksDedup() {
        List<String> merged = LibraryLoader.mergeRepositories(Arrays.asList(
                "https://maven.my-company.com/repository/public/",
                "https://repo.maven.apache.org/maven2/",   // 与兜底重复，应去重保留首次
                "https://jitpack.io"                        // 缺少末尾斜杠，应自动补齐
        ));

        // 用户仓库在前，其中与兜底重复的 Maven Central 只保留一份
        assertEquals("https://maven.my-company.com/repository/public/", merged.get(0));
        assertEquals("https://repo.maven.apache.org/maven2/", merged.get(1));
        assertEquals("https://jitpack.io/", merged.get(2), "缺末尾斜杠应自动补齐");
        // 兜底的 lanink 仓库仍在末尾（用户没声明过）
        assertEquals("https://repo.lanink.cn/repository/maven-public/", merged.get(3));
        // 共 4 条（用户 3 条 - 1 重复 + 兜底 2 条 - 1 已包含 = 4）
        assertEquals(4, merged.size());
    }

    @Test
    public void mergeRepositoriesEmptyUserReturnsOnlyFallbacks() {
        List<String> merged = LibraryLoader.mergeRepositories(null);

        assertEquals(2, merged.size());
        assertEquals("https://repo.maven.apache.org/maven2/", merged.get(0));
        assertEquals("https://repo.lanink.cn/repository/maven-public/", merged.get(1));
    }

    /**
     * 回归：downloadToTemp 必须创建中间父目录（{@code HttpClient.BodyHandlers.ofFile} 不自动创建）。
     * / Regression: downloadToTemp must create intermediate parent dirs (HttpClient.BodyHandlers.ofFile does not).
     */
    @Test
    public void downloadToTempCreatesMissingParentDirectories(@TempDir Path tempDir) throws Exception {
        // 启动一个本地 HTTP 服务器返回固定字节，避免任何真实网络依赖。
        byte[] payload = "hello-maven".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repo", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            try (var os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/repo/lib-1.0.jar";

            // 目标父目录故意不存在 —— 模拟 libraries/<groupPath>/<artifact>/<version>/ 首次下载
            File target = tempDir.resolve("nested/deep/path/lib-1.0.jar").toFile();
            assertFalse(target.getParentFile().exists(),
                    "前置条件：父目录不应存在 / pre: parent should not exist");

            LibraryLoader.downloadToTemp(url, target, MainLogger.getLogger());

            assertTrue(target.isFile(), "下载后目标文件应存在 / target file should exist");
            assertArrayEquals(payload, Files.readAllBytes(target.toPath()));
            // 清理残留 .tmp（理论上 ATOMIC_MOVE 后已无）
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            assertFalse(tmp.exists(), ".tmp 临时文件应已移除 / .tmp should be gone");
        } finally {
            server.stop(0);
        }
    }
}
