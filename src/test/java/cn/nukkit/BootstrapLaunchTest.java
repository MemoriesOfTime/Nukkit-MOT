package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapLaunchTest {

    /*
     * classpathUrls 组装：自身 jar 恒在首位，lib/ 下 jar 按文件名字典序展开，非 jar 文件与目录排除。
     * / classpathUrls assembly: self jar first, lib/ jars in filename order, non-jars excluded.
     */
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

    /*
     * 重启子进程的 JVM 参数继承过滤：运行参数透传，启动器管理参数（及其取值）剔除。
     * / Filters the JVM args inherited by the re-exec'd child: runtime flags pass through,
     * launcher-managed options (and their values) are dropped.
     */
    @Test
    void runtimeFlagsPassThroughInOrder() {
        List<String> input = List.of("-Xmx2G", "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-XX:+UseG1GC", "-javaagent:agent.jar", "-Dnukkit.libs.dir=lib", "-verbose:gc");
        assertEquals(input, Bootstrap.inheritableJvmArgs(input));
    }

    @Test
    void launcherManagedFlagsAndTheirValuesAreDropped() {
        assertEquals(List.of("-Xmx2G"),
                Bootstrap.inheritableJvmArgs(List.of("-cp", "foo:bar", "-Xmx2G")));
        assertEquals(List.of("-Xmx2G"),
                Bootstrap.inheritableJvmArgs(List.of("-classpath", "foo", "--class-path", "bar", "-Xmx2G")));
        assertEquals(List.of("-Xmx2G"),
                Bootstrap.inheritableJvmArgs(List.of("-jar", "server.jar", "-Xmx2G")));
        // 等号单 token 形态自带取值，不能吞掉下一个参数 / single-token forms carry no trailing value
        assertEquals(List.of("-Xmx2G", "-Dkey=-cp"),
                Bootstrap.inheritableJvmArgs(List.of("-classpath=foo:bar", "-Xmx2G", "-Dkey=-cp")));
    }

    @Test
    void trailingFlagWithoutValueIsHarmless() {
        assertEquals(List.of(), Bootstrap.inheritableJvmArgs(List.of("-jar")));
        assertEquals(List.of(), Bootstrap.inheritableJvmArgs(List.of()));
    }
}
