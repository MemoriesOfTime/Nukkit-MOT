package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;

import java.util.Map;

/**
 * @author Nukkit Project Team
 */
public class ServerScheduler extends BaseScheduler {

    public ServerScheduler() {
        super();
    }

    @Deprecated
    public TaskHandler scheduleTask(Task task) {
        return addTask(task, 0, 0, false);
    }

    @Deprecated
    public TaskHandler scheduleTask(Runnable task) {
        return addTask(null, task, 0, 0, false);
    }

    public TaskHandler scheduleTask(Plugin plugin, Runnable task) {
        return plugin.getScheduler().scheduleTask(task);
    }

    @Deprecated
    public TaskHandler scheduleTask(Runnable task, boolean asynchronous) {
        return addTask(null, task, 0, 0, asynchronous);
    }

    public TaskHandler scheduleTask(Plugin plugin, Runnable task, boolean asynchronous) {
        return plugin.getScheduler().scheduleTask(task, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleAsyncTask(AsyncTask task) {
        return addTask(null, task, 0, 0, true);
    }

    public TaskHandler scheduleAsyncTask(Plugin plugin, AsyncTask task) {
        return plugin.getScheduler().scheduleAsyncTask(task);
    }

    @Deprecated
    public void scheduleAsyncTaskToWorker(AsyncTask task, int worker) {
        scheduleAsyncTask(task);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(Task task, int delay) {
        return this.addTask(task, delay, 0, false);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(Task task, int delay, boolean asynchronous) {
        return this.addTask(task, delay, 0, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(Runnable task, int delay) {
        return addTask(null, task, delay, 0, false);
    }

    public TaskHandler scheduleDelayedTask(Plugin plugin, Runnable task, int delay) {
        return plugin.getScheduler().scheduleDelayedTask(task, delay);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(Runnable task, int delay, boolean asynchronous) {
        return addTask(null, task, delay, 0, asynchronous);
    }

    public TaskHandler scheduleDelayedTask(Plugin plugin, Runnable task, int delay, boolean asynchronous) {
        return plugin.getScheduler().scheduleDelayedTask(task, delay, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(Runnable task, int period) {
        return addTask(null, task, 0, period, false);
    }

    public TaskHandler scheduleRepeatingTask(Plugin plugin, Runnable task, int period) {
        return plugin.getScheduler().scheduleRepeatingTask(task, period);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(Runnable task, int period, boolean asynchronous) {
        return addTask(null, task, 0, period, asynchronous);
    }

    public TaskHandler scheduleRepeatingTask(Plugin plugin, Runnable task, int period, boolean asynchronous) {
        return plugin.getScheduler().scheduleRepeatingTask(task, period, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(Task task, int period) {
        return addTask(task, 0, period, false);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(Task task, int period, boolean asynchronous) {
        return addTask(task, 0, period, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(Task task, int delay, int period) {
        return addTask(task, delay, period, false);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(Task task, int delay, int period, boolean asynchronous) {
        return addTask(task, delay, period, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(Runnable task, int delay, int period) {
        return addTask(null, task, delay, period, false);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Plugin plugin, Runnable task, int delay, int period) {
       return plugin.getScheduler().scheduleDelayedRepeatingTask(task, delay, period);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(Runnable task, int delay, int period, boolean asynchronous) {
        return addTask(null, task, delay, period, asynchronous);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Plugin plugin, Runnable task, int delay, int period, boolean asynchronous) {
        return plugin.getScheduler().scheduleDelayedRepeatingTask(task, delay, period, asynchronous);
    }

    protected TaskHandler addTask(Task task, int delay, int period, boolean asynchronous) {
        return addTask(task instanceof PluginTask ? ((PluginTask) task).getOwner() : null, task, delay, period, asynchronous);
    }

    @Override
    public void cancelTask(int taskId) {
        if (taskMap.containsKey(taskId)) {
            try {
                taskMap.remove(taskId).cancel();
            } catch (RuntimeException ex) {
                Server.getInstance().getLogger().critical("Exception while invoking onCancel", ex);
            }
        } else {
            for (Plugin plugin : Server.getInstance().getPluginManager().getPlugins().values()) {
                plugin.getScheduler().cancelTask(taskId);
            }
        }
    }

    public void cancelTask(Plugin plugin) {
        if (plugin == null) {
            throw new NullPointerException("Plugin cannot be null!");
        }
        for (Map.Entry<Integer, TaskHandler> entry : taskMap.entrySet()) {
            TaskHandler taskHandler = entry.getValue();
            if (taskHandler.getPlugin() == null || plugin.equals(taskHandler.getPlugin())) {
                try {
                    taskHandler.cancel(); // It will remove from task map automatic in next main heartbeat
                } catch (RuntimeException ex) {
                    Server.getInstance().getLogger().critical("Exception while invoking onCancel", ex);
                }
            }
        }
    }
}
