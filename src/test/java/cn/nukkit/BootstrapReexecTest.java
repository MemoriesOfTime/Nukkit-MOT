package cn.nukkit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 重启子进程的 JVM 参数继承过滤：运行参数透传、启动器管理参数（及其取值）剔除。
 * <p>
 * Filters the JVM arguments inherited by the re-exec'd child process: runtime flags pass
 * through, launcher-managed options (and their values) are dropped.
 */
class BootstrapReexecTest {

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
