package cn.nukkit.scheduler;

import cn.nukkit.Server;

import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Nukkit Project Team
 */
public class AsyncPool extends ThreadPoolExecutor {

    private final Server server;

    public AsyncPool(Server server, int size) {
        super(size, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>());
        this.setThreadFactory(runnable -> new Thread(runnable) {{
            setDaemon(true);
            setName(String.format("Nukkit Asynchronous Task Handler #%s", getPoolSize()));
        }});
        this.server = server;
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable throwable) {
        if (throwable != null) {
            server.getLogger().critical("Exception in asynchronous task", throwable);
        }
    }

    /**
     * Stop accepting new tasks, wait up to {@code timeoutSeconds} for in-flight
     * tasks to finish, then force-interrupt the rest.
     *
     * @return tasks that never started (drained by {@link #shutdownNow()} on timeout),
     *         or an empty list if the pool terminated cleanly.
     */
    public List<Runnable> shutdownGracefully(long timeoutSeconds) {
        this.shutdown();
        try {
            if (this.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                return List.of();
            }
            server.getLogger().warning("AsyncPool did not terminate within " + timeoutSeconds + "s, forcing shutdown");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return this.shutdownNow();
    }

    public Server getServer() {
        return server;
    }
}
