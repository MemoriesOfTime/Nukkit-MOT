package cn.nukkit.scheduler;

import java.util.concurrent.ExecutorService;

/**
 * 基线实现（Java 17 字节码）：虚拟线程不可用。
 * JVM 21+ 上会被 {@code META-INF/versions/21} 下的同名覆盖类替换。
 * <p>
 * Base implementation (Java 17 bytecode): virtual threads unavailable.
 * Replaced by the same-FQCN override under {@code META-INF/versions/21} on JVM 21+.
 */
class VirtualThreadServiceImpl extends VirtualThreadService {

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public ExecutorService newExecutor(String namePrefix, Thread.UncaughtExceptionHandler exceptionHandler) {
        return null;
    }
}
