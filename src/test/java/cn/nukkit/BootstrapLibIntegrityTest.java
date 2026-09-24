package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 就绪判定的完整性核对：lib/ 文件齐全且 sha256 与清单一致才判就绪，
 * 缺失与损坏（篡改、截断）都必须转入下载路径修复；
 * verifyHashes=false（-Dnukkit.libs.verify=false）退化为仅存在性检查，但缺失仍判未就绪。
 * <p>
 * Locks the integrity half of the readiness check: lib/ passes only when every file is
 * present and sha256-intact; missing and corrupted (tampered, truncated) files must
 * route to the download path for repair; verifyHashes=false (-Dnukkit.libs.verify=false)
 * degrades to presence-only while a missing file still counts as not ready.
 */
class BootstrapLibIntegrityTest {

    @TempDir
    Path libsDir;

    @Test
    void intactLibPasses() throws IOException {
        writeLibFile("a-1.0.jar", "content-a");
        writeLibFile("b-2.0.jar", "content-b");
        assertTrue(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a", "b-2.0.jar", "content-b"), libsDir, true));
    }

    @Test
    void missingFileFails() throws IOException {
        writeLibFile("a-1.0.jar", "content-a");
        assertFalse(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a", "b-2.0.jar", "content-b"), libsDir, true));
    }

    @Test
    void tamperedContentFails() throws IOException {
        writeLibFile("a-1.0.jar", "tampered");
        assertFalse(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a"), libsDir, true));
    }

    @Test
    void truncatedEmptyFileFails() throws IOException {
        // 0 字节截断产物 / truncated to zero bytes
        Files.writeString(libsDir.resolve("a-1.0.jar"), "");
        assertFalse(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a"), libsDir, true));
    }

    @Test
    void directoryInPlaceOfJarFails() throws IOException {
        Files.createDirectory(libsDir.resolve("a-1.0.jar"));
        assertFalse(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a"), libsDir, true));
    }

    @Test
    void verifyDisabledToleratesTamperedContent() throws IOException {
        // -Dnukkit.libs.verify=false：运维自替换/修补的 jar 不因 sha 不符被回滚
        // -Dnukkit.libs.verify=false: operator-swapped jars must not be reverted over a sha mismatch
        writeLibFile("a-1.0.jar", "operator-patched");
        writeLibFile("b-2.0.jar", "anything");
        assertTrue(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a", "b-2.0.jar", "content-b"), libsDir, false));
    }

    @Test
    void verifyDisabledStillRequiresEveryFile() throws IOException {
        writeLibFile("a-1.0.jar", "operator-patched");
        assertFalse(Bootstrap.libFilesMatchManifest(
                manifestOf("a-1.0.jar", "content-a", "b-2.0.jar", "content-b"), libsDir, false));
    }

    @Test
    void corruptClassFileCountsAsNotVisible() throws Exception {
        // CAFEBABE 魔数 + 截断的常量池 → ClassFormatError，探测须按未就绪处理而非裸崩
        // CAFEBABE magic + truncated constant pool -> ClassFormatError; the probe must treat
        // it as not ready instead of crashing with a raw Error
        Path root = Files.createDirectory(libsDir.resolve("classes"));
        Files.createDirectories(root.resolve("corrupt"));
        Files.write(root.resolve("corrupt/Probe.class"), new byte[]{
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 52, 1, 2});
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            assertFalse(Bootstrap.classLoadable(loader, "corrupt.Probe"));
            assertFalse(Bootstrap.classLoadable(loader, "no.such.Class"));
            assertTrue(Bootstrap.classLoadable(loader, "java.lang.String"));
        }
    }

    private void writeLibFile(String fileName, String content) throws IOException {
        Files.writeString(libsDir.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    /** 期望内容与磁盘解耦：sha256 来自期望内容而非 lib/ 里的实际文件。 */
    private DependencyManifest manifestOf(String... fileNameAndExpectedContent) throws IOException {
        StringBuilder text = new StringBuilder("repos=https://repo1.maven.org/maven2/\n");
        for (int i = 0; i < fileNameAndExpectedContent.length; i += 2) {
            Path scratch = Files.createTempFile(libsDir, "sha-scratch", ".bin");
            try {
                Files.writeString(scratch, fileNameAndExpectedContent[i + 1], StandardCharsets.UTF_8);
                text.append("g:a:1.0:").append(fileNameAndExpectedContent[i])
                        .append(':').append(DependencyManifest.sha256Hex(scratch)).append('\n');
            } finally {
                Files.deleteIfExists(scratch);
            }
        }
        return DependencyManifest.parse(text.toString());
    }
}
