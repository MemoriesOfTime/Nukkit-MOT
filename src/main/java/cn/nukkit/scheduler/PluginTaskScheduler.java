package cn.nukkit.scheduler;

import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.PluginException;

public class PluginTaskScheduler extends BaseScheduler {

    private final Plugin plugin;

    public PluginTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public TaskHandler scheduleTask(Task task) {
        return addTask(task, 0, 0, false);
    }

    public TaskHandler scheduleTask(Runnable task) {
        return addTask(task, 0, 0, false);
    }

    public TaskHandler scheduleTask(Runnable task, boolean asynchronous) {
        return addTask(task, 0, 0, asynchronous);
    }

    public TaskHandler scheduleAsyncTask(AsyncTask task) {
        return addTask(task, 0, 0, true);
    }

    public TaskHandler scheduleDelayedTask(Task task, int delay) {
        return this.addTask(task, delay, 0, false);
    }

    public TaskHandler scheduleDelayedTask(Task task, int delay, boolean asynchronous) {
        return this.addTask(task, delay, 0, asynchronous);
    }

    public TaskHandler scheduleDelayedTask(Runnable task, int delay) {
        return addTask(task, delay, 0, false);
    }

    public TaskHandler scheduleDelayedTask(Runnable task, int delay, boolean asynchronous) {
        return addTask(task, delay, 0, asynchronous);
    }

    public TaskHandler scheduleRepeatingTask(Runnable task, int period) {
        return addTask(task, 0, period, false);
    }

    public TaskHandler scheduleRepeatingTask(Runnable task, int period, boolean asynchronous) {
        return addTask(task, 0, period, asynchronous);
    }

    public TaskHandler scheduleRepeatingTask(Task task, int period) {
        return addTask(task, 0, period, false);
    }

    public TaskHandler scheduleRepeatingTask(Task task, int period, boolean asynchronous) {
        return addTask(task, 0, period, asynchronous);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Task task, int delay, int period) {
        return addTask(task, delay, period, false);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Task task, int delay, int period, boolean asynchronous) {
        return addTask(task, delay, period, asynchronous);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Runnable task, int delay, int period) {
        return addTask(task, delay, period, false);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Runnable task, int delay, int period, boolean asynchronous) {
        return addTask(task, delay, period, asynchronous);
    }

    protected TaskHandler addTask(Task task, int delay, int period, boolean asynchronous) {
        return super.addTask(this.plugin, task, delay, period, asynchronous);
    }

    protected TaskHandler addTask(Runnable task, int delay, int period, boolean asynchronous) {
        return super.addTask(this.plugin, task, delay, period, asynchronous);
    }

    @Override
    @Deprecated
    protected TaskHandler addTask(Plugin plugin, Runnable task, int delay, int period, boolean asynchronous) {
        return super.addTask(this.plugin, task, delay, period, asynchronous);
    }

    public void mainThreadHeartbeat(int currentTick) {
        if (this.plugin.isDisabled()) {
            throw new PluginException("Cannot run heartbeat on a disabled scheduler");
        }
        super.mainThreadHeartbeat(currentTick);
    }
}
