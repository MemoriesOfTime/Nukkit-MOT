package cn.nukkit.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * versions/21 覆盖类（Java 21 字节码）：JVM 21+ 上由类加载器自动选中本类，
 * 用 {@code Thread.ofVirtual()} + {@code Executors.newThreadPerTaskExecutor} 提供虚拟线程执行器。
 * 本类只允许引用 Java 21+ API；与基线实现共享的契约是父类 {@link VirtualThreadService}，
 * 签名漂移会在编译期暴露。
 * <p>
 * versions/21 override (Java 21 bytecode): auto-selected by the class loader on JVM 21+,
 * exposing a virtual thread executor via {@code Thread.ofVirtual()} and
 * {@code Executors.newThreadPerTaskExecutor}. Java 21+ APIs only; the shared contract
 * is the base class {@link VirtualThreadService}, so signature drift fails at compile time.
 */
class VirtualThreadServiceImpl extends VirtualThreadService {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public ExecutorService newExecutor(String namePrefix, Thread.UncaughtExceptionHandler exceptionHandler) {
        ThreadFactory factory = Thread.ofVirtual()
                .name(namePrefix, 0)
                .uncaughtExceptionHandler(exceptionHandler)
                .factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }
}
