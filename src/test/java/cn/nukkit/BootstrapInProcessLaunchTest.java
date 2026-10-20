package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * classpathUrls 组装：自身 jar 恒在首位，lib/ 下 jar 按文件名字典序展开，非 jar 文件与目录排除。
 * <p>
 * classpathUrls assembly: self jar first, lib/ jars in filename order, non-jars excluded.
 */
class BootstrapInProcessLaunchTest {

    @Test
    void selfJarFirstThenLibJarsInNameOrder(@TempDir Path libs, @TempDir Path elsewhere) throws IOException {
        Files.createFile(libs.resolve("zeta.jar"));
        Files.createFile(libs.resolve("alpha.jar"));
        Files.createFile(libs.resolve("netty.jar"));
        Files.createFile(libs.resolve("leftover.part"));
        Files.createFile(libs.resolve("notes.txt"));
        Files.createDirectory(libs.resolve("nested.jar"));
        Path jar = elsewhere.resolve("server.jar");
        Files.createFile(jar);

        URL[] urls = Bootstrap.classpathUrls(jar, libs);

        assertEquals(4, urls.length);
        assertTrue(urls[0].toString().endsWith("server.jar"), "自身 jar 恒在首位 / self jar must lead");
        assertTrue(urls[1].toString().endsWith("alpha.jar"));
        assertTrue(urls[2].toString().endsWith("netty.jar"));
        assertTrue(urls[3].toString().endsWith("zeta.jar"));
    }

    @Test
    void emptyLibsYieldOnlySelfJar(@TempDir Path libs, @TempDir Path elsewhere) throws IOException {
        URL[] urls = Bootstrap.classpathUrls(elsewhere.resolve("server.jar"), libs);
        assertEquals(1, urls.length);
        assertTrue(urls[0].toString().endsWith("server.jar"));
    }
}
