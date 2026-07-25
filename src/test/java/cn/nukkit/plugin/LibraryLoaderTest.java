package cn.nukkit.plugin;

import cn.nukkit.utils.MainLogger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link LibraryLoader}'s offline-parseable logic (coordinate parsing, pom parsing, scope filtering,
 * cycle handling). Network downloads are not exercised here.
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
