package cn.nukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 MR-JAR 语义端到端生效：构造一个含 versions/21 覆盖类的最小 Multi-Release jar，
 * 经 URLClassLoader（Bootstrap/Lite jar 的加载方式）加载后，
 * JVM 21+ 应选中虚拟线程实现、JVM 17-20 应选中基线 stub。
 * 同时守卫 shaded jar 的 Multi-Release manifest。
 * <p>
 * End-to-end MR-JAR semantics: builds a minimal Multi-Release jar with the versions/21
 * override, loads it through a URLClassLoader (the Bootstrap/Lite jar path) and asserts the
 * JVM selects the virtual-thread implementation on 21+ and the base stub on 17-20.
 * Also guards the Multi-Release manifest of the shaded jar when present.
 */
public class MultiReleaseJarTest {

    private static final String SERVICE_CLASS = "cn.nukkit.scheduler.VirtualThreadService";
    private static final String IMPL_CLASS = "cn.nukkit.scheduler.VirtualThreadServiceImpl";
    private static final String VERSIONED_IMPL_RESOURCE =
            "META-INF/versions/21/cn/nukkit/scheduler/VirtualThreadServiceImpl.class";

    @TempDir
    Path tempDir;

    @Test
    public void testMrJarSelectionThroughUrlClassLoader() throws Exception {
        Path jar = buildMiniMrJar();
        // parent=null（bootstrap 委托）：确保三个类都从 mini jar 解析，隔离测试类路径
        // parent=null (bootstrap delegation): all three classes resolve from the mini jar
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
            Class<?> service = loader.loadClass(SERVICE_CLASS);
            Object instance = service.getMethod("getInstance").invoke(null);
            boolean supported = (boolean) service.getMethod("isSupported").invoke(instance);

            boolean expectSupported = Runtime.version().feature() >= 21;
            assertEquals(expectSupported, supported,
                    "JVM " + Runtime.version().feature() + " 应" + (expectSupported ? "" : "不") + "选中 versions/21 覆盖类");

            Object executor = service.getMethod("newExecutor", String.class, Thread.UncaughtExceptionHandler.class)
                    .invoke(instance, "Nukkit MR Test #", (Thread.UncaughtExceptionHandler) (t, e) -> { });
            if (expectSupported) {
                assertNotNull(executor, "JVM 21+ 上 newExecutor 应返回虚拟线程执行器");
                runOnExecutorAndAssertVirtualThread(service.getClassLoader(), executor);
                ((ExecutorService) executor).shutdown();
                assertTrue(((ExecutorService) executor).awaitTermination(5, TimeUnit.SECONDS));
            } else {
                assertEquals(null, executor, "JVM 17-20 上 newExecutor 应返回 null");
            }
        }
    }

    /**
     * 在执行器上跑一个任务，断言运行线程是名为指定前缀的虚拟线程。
     * isVirtual() 是 Java 21+ API，测试字节码为 17，经反射调用。
     * <p>
     * Runs a task on the executor and asserts the executing thread is a virtual thread
     * with the expected name prefix. Thread.isVirtual() is a 21+ API, invoked reflectively
     * since test bytecode stays at 17.
     */
    private void runOnExecutorAndAssertVirtualThread(ClassLoader loader, Object executor) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        ((ExecutorService) executor).execute(() -> {
            worker.set(Thread.currentThread());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "虚拟线程任务未在 5s 内执行");
        Thread thread = worker.get();
        assertTrue(thread.getName().startsWith("Nukkit MR Test #"), "线程名应为前缀+序号: " + thread.getName());
        assertEquals(true, Thread.class.getMethod("isVirtual").invoke(thread), "任务应运行在虚拟线程上");
    }

    @Test
    public void testVersionedClassLayoutOnClasspath() throws Exception {
        // 两个实现类都存在，版本化类 major=65、基线类 major=61（接线在 JavaReleaseConsistencyTest 也有守卫）
        // Both impl classes exist; versioned major=65, base major=61
        assertClassMajor(IMPL_CLASS.replace('.', '/') + ".class", 61);
        assertClassMajor(VERSIONED_IMPL_RESOURCE, 65);
    }

    @Test
    public void testShadedJarMultiReleaseManifestWhenPresent() throws Exception {
        // 只在 shaded jar 已构建时检查（mvn/gradle test 阶段 jar 通常尚未产出，跳过不算失败）
        // Checked only when the shaded jar exists (usually absent during the test phase)
        Path repoRoot = findRepoRoot();
        Path fatJar = repoRoot.resolve("target/Nukkit-MOT-SNAPSHOT.jar");
        if (!Files.exists(fatJar)) {
            System.out.println("=== shaded jar 不存在，跳过 manifest 检查 (re-run after package to verify) ===");
            return;
        }
        try (JarFile jar = new JarFile(fatJar.toFile())) {
            assertTrue(jar.isMultiRelease(), "shaded jar 应声明 Multi-Release: true");
            assertNotNull(jar.getEntry(VERSIONED_IMPL_RESOURCE), "shaded jar 应包含 versions/21 覆盖类");
            // 基线 stub 与版本化类签名须一致，防同名类漂移（MR-JAR 的经典坑）
            // Base stub and versioned override must stay signature-compatible
            assertImplSignaturesCompatible(jar);
        }
    }

    /**
     * 基线与 versions/21 的同名实现类方法签名一致（编译期父类契约之外的运行时复核）：
     * 提取两份 class 文件常量池中的方法名/描述符片段对比。
     * <p>
     * Asserts the base and versioned same-FQCN classes stay signature-compatible by
     * comparing method name/descriptor fragments from both class files' constant pools.
     */
    private void assertImplSignaturesCompatible(JarFile jar) throws Exception {
        List<String> baseMethods = publicMethodSignatures(jar, IMPL_CLASS.replace('.', '/') + ".class");
        List<String> versionedMethods = publicMethodSignatures(jar, VERSIONED_IMPL_RESOURCE);
        assertEquals(baseMethods, versionedMethods,
                "VirtualThreadServiceImpl 基线与 versions/21 版本 public 签名漂移");
    }

    private List<String> publicMethodSignatures(JarFile jar, String entry) throws Exception {
        try (InputStream in = jar.getInputStream(jar.getEntry(entry))) {
            byte[] bytes = in.readAllBytes();
            return Arrays.stream(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                    .split("[\\x00-\\x1f]+"))
                    .filter(s -> s.contains("isSupported") || s.contains("newExecutor"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    /** 构造最小 MR jar：基线 Service+Impl + versions/21 Impl + Multi-Release manifest */
    private Path buildMiniMrJar() throws Exception {
        Path jar = tempDir.resolve("mini-mr.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            copyFromClasspath(out, "cn/nukkit/scheduler/VirtualThreadService.class");
            copyFromClasspath(out, "cn/nukkit/scheduler/VirtualThreadService$DefaultHolder.class");
            copyFromClasspath(out, "cn/nukkit/scheduler/VirtualThreadServiceImpl.class");
            copyFromClasspath(out, VERSIONED_IMPL_RESOURCE);
        }
        return jar;
    }

    private void copyFromClasspath(JarOutputStream out, String resource) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "测试类路径缺少 " + resource + "（MR 编译接线被破坏？）");
            out.putNextEntry(new ZipEntry(resource));
            in.transferTo(out);
            out.closeEntry();
        }
    }

    private void assertClassMajor(String resource, int expectedMajor) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "classpath 缺少 " + resource);
            byte[] header = in.readNBytes(8);
            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            assertEquals(expectedMajor, major, resource + " 字节码版本");
        }
    }

    private Path findRepoRoot() {
        Path current = Path.of(".").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("pom.xml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("找不到仓库根目录");
        }
        return current;
    }
}
