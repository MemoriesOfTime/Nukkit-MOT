package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.utils.ThreadStore;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Nukkit Project Team
 */
public abstract class AsyncTask<T> implements Runnable {

    public static final Queue<AsyncTask<?>> FINISHED_LIST = new ConcurrentLinkedQueue<>();

    private volatile T result;
    private volatile int taskId;
    private volatile boolean finished = false;

    @Override
    public void run() {
        this.result = null;
        this.onRun();
        this.finished = true;
        FINISHED_LIST.offer(this);
    }

    public boolean isFinished() {
        return this.finished;
    }

    public T getResult() {
        return this.result;
    }

    public boolean hasResult() {
        return this.result != null;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public int getTaskId() {
        return this.taskId;
    }

    /**
     * Whether this task should run on a virtual thread.
     * Override and return true for I/O-bound tasks (e.g., file writes, network requests).
     */
    protected boolean isVirtual() {
        return false;
    }

    @Deprecated
    public Object getFromThreadStore(String identifier) {
        return this.finished ? null : ThreadStore.store.get(identifier);
    }

    @Deprecated
    public void saveToThreadStore(String identifier, Object value) {
        if (!this.finished) {
            if (value == null) {
                ThreadStore.store.remove(identifier);
            } else {
                ThreadStore.store.put(identifier, value);
            }
        }
    }

    public abstract void onRun();

    public void onCompletion(Server server) {

    }

    public void cleanObject() {
        this.result = null;
        this.taskId = 0;
        this.finished = false;
    }

    public static void collectTask() {
        AsyncTask<?> task;
        while ((task = FINISHED_LIST.poll()) != null) {
            try {
                task.onCompletion(Server.getInstance());
            } catch (Exception e) {
                Server.getInstance().getLogger().critical("Exception while async task "
                        + task.getTaskId()
                        + " invoking onCompletion", e);
            }
        }
    }
}
