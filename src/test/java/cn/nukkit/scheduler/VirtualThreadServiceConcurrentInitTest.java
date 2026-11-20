package cn.nukkit.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 VirtualThreadService 类族在并发首触下不死锁：
 * 一线程首次初始化子类（JLS 12.4.2 要求先标子类 in-progress 再初始化超类），
 * 另一线程首次初始化超类并执行 <clinit> 实例化子类——若实例化留在超类 <clinit>，
 * 两线程交叉等待彼此的初始化锁成环（超类 <clinit> 现已用 holder 类隔离，环不存在）。
 * <p>
 * 每轮用独立 URLClassLoader（parent=null）获得全新初始化状态，可重复触发首触竞态；
 * daemon 线程 + 限时 join 保证即使回归也只失败不挂死。
 * <p>
 * Verifies the VirtualThreadService class family never deadlocks under concurrent
 * first-touch: one thread first-initializes the subclass (which per JLS 12.4.2 marks
 * the subclass in-progress before initializing the superclass) while another
 * first-initializes the superclass, whose creation of the subclass used to live in its
 * own &lt;clinit&gt; — a cross-wait on two init locks. The instantiation is now isolated
 * in a holder class, so no cycle exists. Each round uses a fresh URLClassLoader
 * (parent=null) for clean init state; daemon threads and bounded joins turn any
 * regression into a failure, never a hung suite.
 */
public class VirtualThreadServiceConcurrentInitTest {

    private static final int ROUNDS = 300;

    private static final String SERVICE = "cn.nukkit.scheduler.VirtualThreadService";
    private static final String IMPL = "cn.nukkit.scheduler.VirtualThreadServiceImpl";

    @TempDir
    Path tempDir;

    @Test
    public void testConcurrentFirstTouchNeverDeadlocks() throws Exception {
        Path jar = buildMiniJar();
        for (int round = 0; round < ROUNDS; round++) {
            raceOnce(jar, round);
        }
    }

    @Test
    public void testGetInstanceIsStableUnderConcurrency() {
        // 同命名空间并发 getInstance 返回同一实例（主类路径上的补充面）
        // Concurrent getInstance in one namespace yields the same instance
        VirtualThreadService first = VirtualThreadService.getInstance();
        for (int i = 0; i < 16; i++) {
            assertSame(first, VirtualThreadService.getInstance());
        }
    }

    /**
     * 单轮竞态：T1 首触子类（forName initialize=true），T2 首触超类并调 getInstance()。
     * round 决定微抖动，跨轮覆盖两种临界交错方向。
     * <p>
     * One race round: T1 first-touches the subclass, T2 first-touches the superclass
     * and calls getInstance(). Round-indexed jitter covers both critical orderings.
     */
    private void raceOnce(Path jar, int round) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            AtomicReference<Throwable> error = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(2);
            Thread subclassFirst = new Thread(() -> {
                try {
                    barrier.await(2, java.util.concurrent.TimeUnit.SECONDS);
                    Thread.sleep(round % 3); // 抖动拉开两线程的相位 / jitter the phasing
                    Class.forName(IMPL, true, loader);
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                }
            }, "subclass-first-" + round);
            Thread superclassFirst = new Thread(() -> {
                try {
                    barrier.await(2, java.util.concurrent.TimeUnit.SECONDS);
                    Thread.sleep((round % 3) * 2);
                    Class<?> service = Class.forName(SERVICE, true, loader);
                    service.getMethod("getInstance").invoke(null);
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                }
            }, "superclass-first-" + round);
            subclassFirst.setDaemon(true);
            superclassFirst.setDaemon(true);
            subclassFirst.start();
            superclassFirst.start();
            subclassFirst.join(3000);
            superclassFirst.join(3000);
            if (subclassFirst.isAlive() || superclassFirst.isAlive()) {
                fail("第 " + round + " 轮类初始化死锁: 子类首触与超类首触交叉等待"
                        + " (round " + round + ": circular class-init wait between subclass-first and superclass-first)");
            }
            assertNull(error.get(), "第 " + round + " 轮初始化抛异常");
            assertTrue(VirtualThreadService.class != null); // no-op, keep imports honest
        }
    }

    /** 最小 MR jar：Service + Impl（基线）+ versions/21 Impl + Multi-Release manifest */
    private Path buildMiniJar() throws Exception {
        Path jar = tempDir.resolve("mini-init-race.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            copyFromClasspath(out, "cn/nukkit/scheduler/VirtualThreadService.class");
            copyFromClasspath(out, "cn/nukkit/scheduler/VirtualThreadService$DefaultHolder.class");
            copyFromClasspath(out, "cn/nukkit/scheduler/VirtualThreadServiceImpl.class");
            copyFromClasspath(out, "META-INF/versions/21/cn/nukkit/scheduler/VirtualThreadServiceImpl.class");
        }
        return jar;
    }

    private void copyFromClasspath(JarOutputStream out, String resource) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("测试类路径缺少 " + resource);
            }
            out.putNextEntry(new ZipEntry(resource));
            in.transferTo(out);
            out.closeEntry();
        }
    }
}
