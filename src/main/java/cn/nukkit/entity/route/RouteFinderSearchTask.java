package cn.nukkit.entity.route;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author zzz1999 @ MobPlugin
 */
public class RouteFinderSearchTask implements Runnable {

    private static final int DIRECT = 0;
    private static final int QUEUED = 1;
    private static final int RUNNING = 2;
    private static final int RELEASED = 3;
    private static final AtomicIntegerFieldUpdater<RouteFinderSearchTask> ADMISSION =
            AtomicIntegerFieldUpdater.newUpdater(RouteFinderSearchTask.class, "admissionState");

    private final RouteFinder route;
    private int retryTime;
    private volatile int admissionState;

    public RouteFinderSearchTask(RouteFinder route) {
        this.route = route;
    }

    boolean tryQueue() {
        if (this.route == null || this.admissionState != DIRECT || !this.route.reserveSearch(this)) {
            return false;
        }
        // Publish before execute; native movement creates a fresh task per request.
        this.admissionState = QUEUED;
        return true;
    }

    void discardQueued() {
        if (this.route != null && ADMISSION.compareAndSet(this, QUEUED, RELEASED)) {
            this.route.releaseSearch(this);
        }
    }

    @Override
    public void run() {
        if (this.route == null) {
            return;
        }

        boolean reserved = this.admissionState != DIRECT;
        if (reserved && !ADMISSION.compareAndSet(this, QUEUED, RUNNING)) {
            return;
        }

        try {
            while (this.retryTime < 5) {
                if (!this.route.isSearching()) {
                    this.route.research();
                    return;
                } else {
                    this.retryTime++;

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignore) {
                    }
                }
            }

            this.route.interrupt();
        } finally {
            if (reserved) {
                // Failed admission cannot cancel a task another worker already started.
                this.admissionState = RELEASED;
                this.route.releaseSearch(this);
            }
        }
    }
}
