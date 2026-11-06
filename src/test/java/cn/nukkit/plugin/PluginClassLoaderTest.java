package cn.nukkit.plugin;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link PluginClassLoader}：类加载隔离语义（方案 C：自己 URL[] 优先于全局共享），以及
 * 并发安全（缓存条目丢失后的 LinkageError 自愈、并发 define 只产生一个 Class、全局缓存
 * first-wins、getClassByName 遍历与 loader 注册并发无 CME）。
 * / Verifies {@link PluginClassLoader}: class-loading isolation semantics (Strategy C: own URL[]
 * before global sharing) and concurrency safety (LinkageError self-heal after cache entry loss,
 * a single Class from concurrent defines, first-wins global cache, and CME-free getClassByName
 * iteration while loaders are being registered).
 */
public class PluginClassLoaderTest {

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

    /** 清空 PluginClassLoader 私有 classes 缓存，模拟 HashMap 并发 put 丢条目后的状态。 / Clears the private per-loader cache to simulate a lost HashMap entry. */
    private static void clearLoaderCache(PluginClassLoader loader) throws Exception {
        Field field = PluginClassLoader.class.getDeclaredField("classes");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(loader)).clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PluginClassLoader> classLoadersMap(JavaPluginLoader loader) throws Exception {
        Field field = JavaPluginLoader.class.getDeclaredField("classLoaders");
        field.setAccessible(true);
        return (Map<String, PluginClassLoader>) field.get(loader);
    }

    private static ClassLoader getSystemParent() {
        return PluginClassLoaderTest.class.getClassLoader();
    }

    /**
     * 缓存条目丢失后重查（单线程即可确定复现）：JVM 已定义过该类，super.findClass 会抛
     * LinkageError（attempted duplicate class definition），findClass 必须自愈返回同一实例。
     * / Refind after a lost cache entry (single-threaded, deterministic): the JVM already defined the
     * class, so super.findClass throws LinkageError (attempted duplicate class definition); findClass
     * must self-heal and return the same instance.
     */
    @Test
    public void selfHealsAfterCacheEntryLoss(@TempDir Path tempDir) throws Exception {
        File jar = buildClassJar(tempDir, "heal", "HealTarget");

        JavaPluginLoader jpl = new JavaPluginLoader(null);
        try (PluginClassLoader loader = new PluginClassLoader(jpl, getClass().getClassLoader(), jar)) {
            Class<?> first = loader.findClass("com.test.HealTarget");
            assertSame(loader, first.getClassLoader());

            clearLoaderCache(loader);
            Class<?> healed = loader.findClass("com.test.HealTarget");
            assertSame(first, healed, "缓存丢失后重查应自愈返回同一 Class，而非 LinkageError");

            // 缓存已被补写，第三次走正常命中路径
            assertSame(first, loader.findClass("com.test.HealTarget"));
        }
    }

    /**
     * 并发 define 风暴：每轮清空缓存后多线程同时 findClass，任何线程不得抛异常且必须拿到同一 Class。
     * / Concurrent define storm: cache cleared each round, then simultaneous findClass from several
     * threads — no thread may throw, and all must observe the same Class instance.
     */
    @Test
    public void concurrentFindClassYieldsSingleDefinition(@TempDir Path tempDir) throws Exception {
        File jar = buildClassJar(tempDir, "storm", "StormTarget");
        String name = "com.test.StormTarget";

        JavaPluginLoader jpl = new JavaPluginLoader(null);
        try (PluginClassLoader loader = new PluginClassLoader(jpl, getClass().getClassLoader(), jar)) {
            Class<?> reference = loader.findClass(name);

            int threads = 4;
            int rounds = 32;
            Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                for (int round = 0; round < rounds; round++) {
                    clearLoaderCache(loader);
                    CyclicBarrier barrier = new CyclicBarrier(threads);
                    List<Future<Class<?>>> futures = new java.util.ArrayList<>();
                    for (int t = 0; t < threads; t++) {
                        futures.add(pool.submit((Callable<Class<?>>) () -> {
                            barrier.await();
                            return loader.findClass(name);
                        }));
                    }
                    for (Future<Class<?>> future : futures) {
                        try {
                            assertSame(reference, future.get(), "并发加载必须收敛到同一 Class 实例");
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            errors.add(cause);
                        }
                    }
                }
            } finally {
                pool.shutdownNow();
            }
            assertTrue(errors.isEmpty(), "并发 findClass 不得抛出: " + errors);
        }
    }

    /**
     * 全局缓存 first-wins：同名类第二个注册不得覆盖第一个（多插件 shade 同库场景）。
     * / Global cache first-wins: a second registration of the same class name must not overwrite the
     * first (multiple plugins shading the same library).
     */
    @Test
    public void setClassFirstRegistrationWins(@TempDir Path tempDir) throws Exception {
        File jarA = buildClassJar(tempDir, "a", "Duplicated");
        File jarB = buildClassJar(tempDir, "b", "Duplicated");

        JavaPluginLoader jpl = new JavaPluginLoader(null);
        try (PluginClassLoader loaderA = new PluginClassLoader(jpl, getClass().getClassLoader(), jarA);
             PluginClassLoader loaderB = new PluginClassLoader(jpl, getClass().getClassLoader(), jarB)) {

            Class<?> fromA = loaderA.findClass("com.test.Duplicated");
            Class<?> fromB = loaderB.findClass("com.test.Duplicated");

            jpl.setClass("com.test.Duplicated", fromA);
            jpl.setClass("com.test.Duplicated", fromB);
            assertSame(fromA, jpl.getClassByName("com.test.Duplicated"), "先注册者胜，不得被覆盖");
        }
    }

    /**
     * getClassByName 遍历 classLoaders 与新 loader 注册并发：不得抛 ConcurrentModificationException。
     * / getClassByName iterating classLoaders while new loaders are registered concurrently must not
     * throw ConcurrentModificationException.
     */
    @Test
    public void getClassByNameSurvivesConcurrentLoaderRegistration(@TempDir Path tempDir) throws Exception {
        File jar = buildClassJar(tempDir, "cme", "CmeProbe");

        JavaPluginLoader jpl = new JavaPluginLoader(null);
        Map<String, PluginClassLoader> classLoaders = classLoadersMap(jpl);
        try (PluginClassLoader loader = new PluginClassLoader(jpl, getClass().getClassLoader(), jar)) {
            int registrations = 200;
            int lookups = 200;
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<?> registrant = pool.submit(() -> {
                    try {
                        for (int i = 0; i < registrations; i++) {
                            classLoaders.put("plugin" + i, loader);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                });
                Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
                for (int i = 0; i < lookups; i++) {
                    try {
                        assertNull(jpl.getClassByName("com.test.DoesNotExist"), "不存在的类应返回 null");
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                }
                registrant.get();
                assertTrue(errors.isEmpty(), "遍历期间注册 loader 不得抛出: " + errors);
            } finally {
                pool.shutdownNow();
            }
        }
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
