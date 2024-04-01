package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.PluginException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * @author Nukkit Project Team
 */
public class ServerScheduler {

    public static int WORKERS = 4;

    private final AsyncPool asyncPool;

    private final Queue<TaskHandler> pending;
    private final Queue<TaskHandler> queue;
    private final Map<Integer, TaskHandler> taskMap;
    private final AtomicInteger currentTaskId;

    private volatile int currentTick;

    public ServerScheduler() {
        this.pending = new ConcurrentLinkedQueue<>();
        this.currentTaskId = new AtomicInteger();
        this.queue = new PriorityQueue<>(11, (left, right) -> {
            int i = left.getNextRunTick() - right.getNextRunTick();
            if (i == 0) {
                return left.getTaskId() - right.getTaskId();
            }
            return i;
        });
        this.taskMap = new ConcurrentHashMap<>();
        this.asyncPool = new AsyncPool(Server.getInstance(), WORKERS);
    }

    @Deprecated
    public TaskHandler scheduleTask(@NotNull Task task) {
        return addTask(task, 0, 0, false);
    }

    public TaskHandler scheduleTask(@NotNull PluginTask<Plugin> task) {
        return addTask(task, 0, 0, false);
    }

    @Deprecated
    public TaskHandler scheduleTask(@NotNull Runnable task) {
        return addTask(null, task, 0, 0, false);
    }

    public TaskHandler scheduleTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return addTask(plugin, task, 0, 0, false);
    }

    @Deprecated
    public TaskHandler scheduleTask(@NotNull Runnable task, boolean asynchronous) {
        return addTask(null, task, 0, 0, asynchronous);
    }

    public TaskHandler scheduleTask(@NotNull Plugin plugin, @NotNull Runnable task, boolean asynchronous) {
        return addTask(plugin, task, 0, 0, asynchronous);
    }

    public TaskHandler scheduleTask(@NotNull Plugin plugin,
                                    @NotNull BiConsumer<Task, Integer> task,
                                    boolean asynchronous) {
        PluginTask<Plugin> pluginTask = new PluginTask<>(plugin) {
            @Override
            public void onRun(int currentTick) {
                task.accept(this, currentTick);
            }
        };
        return addTask(plugin, pluginTask, 0, 0, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleAsyncTask(@NotNull AsyncTask task) {
        return addTask(null, task, 0, 0, true);
    }

    public TaskHandler scheduleAsyncTask(@NotNull Plugin plugin, @NotNull AsyncTask task) {
        return addTask(plugin, task, 0, 0, true);
    }

    @Deprecated
    public void scheduleAsyncTaskToWorker(@NotNull AsyncTask task, int worker) {
        scheduleAsyncTask(task);
    }

    public int getAsyncTaskPoolSize() {
        return asyncPool.getCorePoolSize();
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(@NotNull Task task, int delay) {
        return this.addTask(task, delay, 0, false);
    }

    public TaskHandler scheduleDelayedTask(@NotNull PluginTask<Plugin> task, int delay) {
        return this.addTask(task, delay, 0, false);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(@NotNull Task task, int delay, boolean asynchronous) {
        return this.addTask(task, delay, 0, asynchronous);
    }

    public TaskHandler scheduleDelayedTask(@NotNull PluginTask<Plugin> task, int delay, boolean asynchronous) {
        return this.addTask(task, delay, 0, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(@NotNull Runnable task, int delay) {
        return addTask(null, task, delay, 0, false);
    }

    public TaskHandler scheduleDelayedTask(@NotNull Plugin plugin, @NotNull Runnable task, int delay) {
        return addTask(plugin, task, delay, 0, false);
    }

    @Deprecated
    public TaskHandler scheduleDelayedTask(@NotNull Runnable task, int delay, boolean asynchronous) {
        return addTask(null, task, delay, 0, asynchronous);
    }

    public TaskHandler scheduleDelayedTask(@NotNull Plugin plugin, @NotNull Runnable task, int delay, boolean asynchronous) {
        return addTask(plugin, task, delay, 0, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(@NotNull Runnable task, int period) {
        return addTask(null, task, 0, period, false);
    }

    public TaskHandler scheduleRepeatingTask(@NotNull Plugin plugin, @NotNull Runnable task, int period) {
        return addTask(plugin, task, 0, period, false);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(@NotNull Runnable task, int period, boolean asynchronous) {
        return addTask(null, task, 0, period, asynchronous);
    }

    public TaskHandler scheduleRepeatingTask(@NotNull Plugin plugin, @NotNull Runnable task, int period, boolean asynchronous) {
        return addTask(plugin, task, 0, period, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(@NotNull Task task, int period) {
        return addTask(task, 0, period, false);
    }

    public TaskHandler scheduleRepeatingTask(@NotNull PluginTask<Plugin> task, int period) {
        return addTask(task, 0, period, false);
    }

    @Deprecated
    public TaskHandler scheduleRepeatingTask(@NotNull Task task, int period, boolean asynchronous) {
        return addTask(task, 0, period, asynchronous);
    }

    public TaskHandler scheduleRepeatingTask(@NotNull PluginTask<Plugin> task, int period, boolean asynchronous) {
        return addTask(task, 0, period, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Task task, int delay, int period) {
        return addTask(task, delay, period, false);
    }

    public TaskHandler scheduleDelayedRepeatingTask(@NotNull PluginTask<Plugin> task, int delay, int period) {
        return addTask(task, delay, period, false);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Task task, int delay, int period, boolean asynchronous) {
        return addTask(task, delay, period, asynchronous);
    }

    public TaskHandler scheduleDelayedRepeatingTask(@NotNull PluginTask<Plugin> task, int delay, int period, boolean asynchronous) {
        return addTask(task, delay, period, asynchronous);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Runnable task, int delay, int period) {
        return addTask(null, task, delay, period, false);
    }

    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Plugin plugin, @NotNull Runnable task, int delay, int period) {
        return addTask(plugin, task, delay, period, false);
    }

    @Deprecated
    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Runnable task, int delay, int period, boolean asynchronous) {
        return addTask(null, task, delay, period, asynchronous);
    }

    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Plugin plugin, @NotNull Runnable task, int delay, int period, boolean asynchronous) {
        return addTask(plugin, task, delay, period, asynchronous);
    }

    public TaskHandler scheduleDelayedRepeatingTask(@NotNull Plugin plugin,
                                                    @NotNull BiConsumer<Task, Integer> task,
                                                    int delay,
                                                    int period,
                                                    boolean asynchronous) {
        PluginTask<Plugin> pluginTask = new PluginTask<>(plugin) {
            @Override
            public void onRun(int currentTick) {
                task.accept(this, currentTick);
            }
        };
        return addTask(plugin, pluginTask, delay, period, asynchronous);
    }

    public void cancelTask(int taskId) {
        if (taskMap.containsKey(taskId)) {
            try {
                taskMap.remove(taskId).cancel();
            } catch (RuntimeException ex) {
                Server.getInstance().getLogger().critical("Exception while invoking onCancel", ex);
            }
        }
    }

    public void cancelTask(@NotNull Plugin plugin) {
        //兼容旧插件
        //noinspection ConstantConditions
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

    public void cancelAllTasks() {
        for (Map.Entry<Integer, TaskHandler> entry : this.taskMap.entrySet()) {
            try {
                entry.getValue().cancel();
            } catch (RuntimeException ex) {
                Server.getInstance().getLogger().critical("Exception while invoking onCancel", ex);
            }
        }
        this.taskMap.clear();
        this.queue.clear();
        this.currentTaskId.set(0);
    }

    public boolean isQueued(int taskId) {
        return this.taskMap.containsKey(taskId);
    }

    private TaskHandler addTask(Task task, int delay, int period, boolean asynchronous) {
        return addTask(task instanceof PluginTask ? ((PluginTask) task).getOwner() : null, task, delay, period, asynchronous);
    }

    private TaskHandler addTask(Plugin plugin, Runnable task, int delay, int period, boolean asynchronous) {
        if (plugin != null && plugin.isDisabled()) {
            throw new PluginException("Plugin '" + plugin.getName() + "' attempted to register a task while disabled.");
        }
        if (delay < 0 || period < 0) {
            throw new PluginException("Attempted to register a task with negative delay or period.");
        }

        TaskHandler taskHandler = new TaskHandler(plugin, task, nextTaskId(), asynchronous);
        taskHandler.setDelay(delay);
        taskHandler.setPeriod(period);
        taskHandler.setNextRunTick(taskHandler.isDelayed() ? currentTick + taskHandler.getDelay() : currentTick);

        if (task instanceof Task) {
            ((Task) task).setHandler(taskHandler);
        }

        pending.offer(taskHandler);
        taskMap.put(taskHandler.getTaskId(), taskHandler);

        return taskHandler;
    }

    public void mainThreadHeartbeat(int currentTick) {
        this.currentTick = currentTick;
        // Accepts pending
        while (!pending.isEmpty()) {
            queue.offer(pending.poll());
        }
        // Main heart beat
        while (isReady(currentTick)) {
            TaskHandler taskHandler = queue.poll();
            if (taskHandler.isCancelled()) {
                taskMap.remove(taskHandler.getTaskId());
                continue;
            } else if (taskHandler.isAsynchronous()) {
                asyncPool.execute(taskHandler.getTask());
            } else {
                try {
                    taskHandler.run(currentTick);
                } catch (Throwable e) {
                    Server.getInstance().getLogger().critical("Could not execute taskHandler " + taskHandler.getTaskId() + ": " + e.getMessage());
                    Server.getInstance().getLogger().logException(e instanceof Exception ? e : new RuntimeException(e));
                }
            }
            if (taskHandler.isRepeating()) {
                taskHandler.setNextRunTick(currentTick + taskHandler.getPeriod());
                pending.offer(taskHandler);
            } else {
                try {
                    Optional.ofNullable(taskMap.remove(taskHandler.getTaskId())).ifPresent(TaskHandler::cancel);
                } catch (RuntimeException ex) {
                    Server.getInstance().getLogger().critical("Exception while invoking onCancel", ex);
                }
            }
        }
        AsyncTask.collectTask();
    }

    public int getQueueSize() {
        return queue.size() + pending.size();
    }

    private boolean isReady(int currentTick) {
        return this.queue.peek() != null && this.queue.peek().getNextRunTick() <= currentTick;
    }

    private int nextTaskId() {
        return currentTaskId.incrementAndGet();
    }
}
