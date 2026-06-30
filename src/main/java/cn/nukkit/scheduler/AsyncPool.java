package cn.nukkit.scheduler;

import cn.nukkit.Server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Nukkit Project Team
 */
public class AsyncPool {

    private final Server server;
    private final ExecutorService executorService;

    public AsyncPool(Server server) {
        this.server = server;

        ThreadFactory virtualFactory = Thread.ofVirtual()
            .name("Nukkit Async Task #", 0)
            .uncaughtExceptionHandler((t, e) -> server.getLogger()
                .critical("Exception in async task " + t.getName() + ": " + e.getMessage(),
                    e instanceof Exception ? e : new RuntimeException(e)))
            .factory();

        this.executorService = Executors.newThreadPerTaskExecutor(virtualFactory);
    }

    public void execute(Runnable command) {
        executorService.execute(command);
    }

    public void shutdownNow() {
        executorService.shutdownNow();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    public Server getServer() {
        return server;
    }

    protected void afterExecute(Runnable runnable, Throwable throwable) {
        if (throwable != null) {
            server.getLogger().critical("Exception in asynchronous task", throwable);
        }
    }
}
