package cn.nukkit.plugin;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link PluginClassLoader} 的类加载隔离语义（方案 C：自己 URL[] 优先于全局共享）。 / Verifies the
 * class-loading isolation semantics of {@link PluginClassLoader} (Strategy C: own URL[] before global sharing).
 */
public class PluginClassLoaderIsolationTest {

    /** 编译一个空类到 jar 里。无 JDK 编译器（纯 JRE 环境）时跳过整个测试类。 / Compiles an empty class into a jar; skips when no JDK compiler is available (JRE-only env). */
    private static File buildClassJar(Path dir, String tag, String className) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assumptions.assumeTrue(compiler != null,
                "跳过：当前 JRE 无 javax.tools.JavaCompiler（需 JDK 而非 JRE 跑此测试）/ Skipped: no JDK compiler available");

        String internalName = "com/test/" + className;
        String fqcn = internalName.replace('/', '.');
        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
        String src = "package " + pkg + "; class " + className + " {}";

        Path compileDir = Files.createTempDirectory("compile-" + tag + "-");
        File srcFile = compileDir.resolve(className + ".java").toFile();
        Files.writeString(srcFile.toPath(), src);
        int code = compiler.run(null, null, null, "-d", compileDir.toString(), srcFile.toString());
        assertEquals(0, code, "stub 类编译失败 / stub class compile failed: " + className);

        File classFile = compileDir.resolve(internalName + ".class").toFile();
        byte[] bytecode = Files.readAllBytes(classFile.toPath());

        File jar = dir.resolve("cls-" + tag + "-" + className + ".jar").toFile();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry(internalName + ".class"));
            out.write(bytecode);
            out.closeEntry();
        }
        return jar;
    }

    private static ClassLoader getSystemParent() {
        return PluginClassLoaderIsolationTest.class.getClassLoader();
    }

    /**
     * 场景 1：depend 回退机制保留 —— 自己 URL[] 找不到的类，应能从全局其他 loader 命中。
     * / Scenario 1: depend fallback — a class absent from own URL[] should be found via the global scan.
     */
    @Test
    public void missingClassFallsBackToGlobalScan(@TempDir Path tempDir) throws Exception {
        File jarA = buildClassJar(tempDir, "a", "ClassOnlyInA");
        File jarB = buildClassJar(tempDir, "b", "ClassOnlyInB");

        // JavaPluginLoader.getClassByName/setClass 不依赖 server，构造时传 null
        JavaPluginLoader sharedLoader = new JavaPluginLoader(null);

        try (PluginClassLoader loaderA = new PluginClassLoader(sharedLoader, getSystemParent(), jarA);
             PluginClassLoader loaderB = new PluginClassLoader(sharedLoader, getSystemParent(), jarB)) {

            Class<?> classInB = loaderB.loadClass("com.test.ClassOnlyInB");
            assertNotNull(classInB, "loaderB 应能加载自己的 ClassOnlyInB");

            // A 自己没有 ClassOnlyInB，应回退到全局共享命中 B 的版本（保留 depend/softdepend 机制）
            Class<?> foundFromGlobal = loaderA.loadClass("com.test.ClassOnlyInB");
            assertSame(classInB, foundFromGlobal,
                    "A 加载 ClassOnlyInB 应回退到全局共享（depend 机制保留）");
        }
    }

    /**
     * 场景 2：同名类自己优先（方案 C 核心）—— A、B 都有 com.test.Shared，A 加载时应得 A 的版本，
     * 即使 B 的版本已进全局缓存。 / Scenario 2: own version wins — A and B both ship com.test.Shared;
     * A must get its own version even when B's is already in the global cache. Core goal of Strategy C.
     */
    @Test
    public void ownVersionPreferredOverGlobalCache(@TempDir Path tempDir) throws Exception {
        File jarA = buildClassJar(tempDir, "a", "Shared");
        File jarB = buildClassJar(tempDir, "b", "Shared");

        JavaPluginLoader sharedLoader = new JavaPluginLoader(null);

        try (PluginClassLoader loaderA = new PluginClassLoader(sharedLoader, getSystemParent(), jarA);
             PluginClassLoader loaderB = new PluginClassLoader(sharedLoader, getSystemParent(), jarB)) {

            // B 先加载 Shared → 全局缓存里是 B 的 ClassLoader 加载的版本
            Class<?> sharedFromB = loaderB.loadClass("com.test.Shared");

            // A 再加载同名 Shared：方案 C 要求优先自己的 URL[]，得到 A 的版本
            Class<?> sharedFromA = loaderA.loadClass("com.test.Shared");

            assertNotSame(sharedFromB, sharedFromA,
                    "同名类各自加载应得到不同 Class 实例（自己 URL[] 优先，不共享 B 的版本）");
            assertSame(loaderA, sharedFromA.getClassLoader(),
                    "A 加载的 Shared 应由 A 的 ClassLoader 加载");
            assertSame(loaderB, sharedFromB.getClassLoader(),
                    "B 加载的 Shared 应由 B 的 ClassLoader 加载");
        }
    }

    /**
     * 场景 3：服务端内部包禁止加载 —— cn.nukkit.* 和 net.minecraft.* 永远 ClassNotFoundException。
     * / Scenario 3: server-internal packages are blocked — cn.nukkit.* and net.minecraft.* always throw.
     */
    @Test
    public void blocksServerInternalPackages(@TempDir Path tempDir) throws Exception {
        File jar = buildClassJar(tempDir, "x", "SomeClass");

        JavaPluginLoader sharedLoader = new JavaPluginLoader(null);
        try (PluginClassLoader loader = new PluginClassLoader(sharedLoader, getSystemParent(), jar)) {
            assertThrows(ClassNotFoundException.class, () -> loader.findClass("cn.nukkit.Foo"));
            assertThrows(ClassNotFoundException.class, () -> loader.findClass("net.minecraft.Bar"));
        }
    }
}
