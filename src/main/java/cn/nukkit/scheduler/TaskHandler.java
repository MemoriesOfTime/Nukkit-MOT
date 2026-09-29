package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * @author MagicDroidX
 */
public class TaskHandler {

    private final int taskId;
    private final boolean asynchronous;

    private final Plugin plugin;
    private final Runnable task;

    private int delay;
    private int period;

    private int lastRunTick;
    private int nextRunTick;

    private volatile boolean cancelled;
    private final Consumer<TaskHandler> cancellationListener;

    public TaskHandler(Plugin plugin, Runnable task, int taskId, boolean asynchronous) {
        this(plugin, task, taskId, asynchronous, null);
    }

    TaskHandler(Plugin plugin, Runnable task, int taskId, boolean asynchronous,
                Consumer<TaskHandler> cancellationListener) {
        this.cancellationListener = cancellationListener;
        this.asynchronous = asynchronous;
        this.plugin = plugin;
        this.task = task;
        this.taskId = taskId;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public int getNextRunTick() {
        return this.nextRunTick;
    }

    public void setNextRunTick(int nextRunTick) {
        this.nextRunTick = nextRunTick;
    }

    public int getTaskId() {
        return this.taskId;
    }

    public Runnable getTask() {
        return this.task;
    }

    public int getDelay() {
        return this.delay;
    }

    public boolean isDelayed() {
        return this.delay > 0;
    }

    public boolean isRepeating() {
        return this.period > 0;
    }

    public int getPeriod() {
        return this.period;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public int getLastRunTick() {
        return lastRunTick;
    }

    public void setLastRunTick(int lastRunTick) {
        this.lastRunTick = lastRunTick;
    }

    public void cancel() {
        if (this.cancelled) return;
        try {
            if (this.task instanceof Task) ((Task) this.task).onCancel();
        } finally {
            markCancelled();
        }
    }

    public void remove() {
        if (!this.cancelled) markCancelled();
    }

    private void markCancelled() {
        this.cancelled = true;
        // Never mutate the scheduler's ArrayDeque from a worker thread.
        if (cancellationListener != null) cancellationListener.accept(this);
    }

    public void run(int currentTick) {
        try {
            setLastRunTick(currentTick);
            task.run();
        } catch (RuntimeException ex) {
            Server.getInstance().getLogger().critical("Exception while invoking run", ex);
        }
    }

    public boolean isAsynchronous() {
        return asynchronous;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public void setPeriod(int period) {
        this.period = period;
    }
}
