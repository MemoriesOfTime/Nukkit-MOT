package cn.nukkit.entity.route;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zzz1999 @ MobPlugin
 */
public class RouteFinderThreadPool {

    private static volatile boolean running = true;

    private static final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(
                    1,
                    Runtime.getRuntime().availableProcessors(),
                    1, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new ThreadPoolExecutor.AbortPolicy()
            );

    public static void executeRouteFinderThread(RouteFinderSearchTask t) {
        if (running) {
            submit(executor, t);
        }
    }

    static void submit(ThreadPoolExecutor target, RouteFinderSearchTask task) {
        if (!task.tryQueue()) {
            return;
        }
        boolean accepted = false;
        try {
            target.execute(task);
            accepted = true;
        } finally {
            if (!accepted) {
                try {
                    // Thread creation can fail after execute has offered the task.
                    target.remove(task);
                } finally {
                    task.discardQueued();
                }
            }
        }
    }

    static void discardQueued(List<Runnable> tasks) {
        for (Runnable task : tasks) {
            if (task instanceof RouteFinderSearchTask search) {
                search.discardQueued();
            }
        }
    }

    public static void shutdown() {
        running = false;
        discardQueued(executor.shutdownNow());
    }
}
