package cn.nukkit.scheduler;

import java.util.concurrent.ExecutorService;

/**
 * 虚拟线程支持的服务抽象。主代码只依赖本类与 {@link #getInstance()}；
 * 实际能力由 {@code VirtualThreadServiceImpl} 提供 —— 基线版本（Java 17 字节码）恒为不支持，
 * {@code META-INF/versions/21} 下的同名覆盖类（Java 21 字节码）在 JVM 21+ 上由类加载器自动选中，
 * 从而按运行时 JVM 自动启用或禁用虚拟线程，无需任何版本探测代码。
 * <p>
 * Service facade for virtual thread support. Main code depends only on this class;
 * {@code VirtualThreadServiceImpl} is the same-FQCN swap point: the base version
 * (Java 17 bytecode) reports unsupported, while the override under
 * {@code META-INF/versions/21} (Java 21 bytecode) is auto-selected by the JVM on 21+,
 * enabling or disabling virtual threads purely by the runtime JVM version.
 */
public abstract class VirtualThreadService {

    private static final class DefaultHolder {
        static final VirtualThreadService INSTANCE = new VirtualThreadServiceImpl();
    }

    public static VirtualThreadService getInstance() {
        return DefaultHolder.INSTANCE;
    }

    /**
     * 当前 JVM 是否支持虚拟线程（即类加载器选中了 versions/21 覆盖类）。
     * <p>
     * Whether the running JVM selected the versioned implementation.
     */
    public abstract boolean isSupported();

    /**
     * 创建虚拟线程 {@code thread-per-task} 执行器；不支持时返回 {@code null}。
     * 每个任务一个虚拟线程，线程名形如 {@code namePrefix + 序号}。
     * <p>
     * Creates a virtual {@code thread-per-task} executor, or {@code null} when unsupported.
     * One virtual thread per task, named {@code namePrefix + counter}.
     *
     * @param namePrefix 线程名前缀 thread name prefix
     * @param exceptionHandler 线程未捕获异常处理器 uncaught exception handler
     */
    public abstract ExecutorService newExecutor(String namePrefix, Thread.UncaughtExceptionHandler exceptionHandler);
}
