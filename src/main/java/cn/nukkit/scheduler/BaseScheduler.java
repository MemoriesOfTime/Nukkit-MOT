package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.PluginException;

import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BaseScheduler {

    private static boolean initialized = false;
    public static int WORKERS = 4;
    private static AsyncPool asyncPool;

    protected final Queue<TaskHandler> pending;
    protected final Queue<TaskHandler> queue;
    protected final Map<Integer, TaskHandler> taskMap;
    protected final AtomicInteger currentTaskId;

    protected volatile int currentTick;

    public static void init(Server server, int workers) {
        if (initialized) {
            throw new IllegalStateException("Scheduler is already initialized");
        }
        initialized = true;
        WORKERS = workers;
        asyncPool = new AsyncPool(server, WORKERS);
    }

    public BaseScheduler() {
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
    }

    public int getAsyncTaskPoolSize() {
        return asyncPool.getCorePoolSize();
    }

    protected TaskHandler addTask(Plugin plugin, Runnable task, int delay, int period, boolean asynchronous) {
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

    public void cancelTask(int taskId) {
        if (taskMap.containsKey(taskId)) {
            try {
                taskMap.remove(taskId).cancel();
            } catch (RuntimeException ex) {
                Server.getInstance().getLogger().critical("Exception while invoking onCancel", ex);
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

    protected boolean isReady(int currentTick) {
        return this.queue.peek() != null && this.queue.peek().getNextRunTick() <= currentTick;
    }

    protected int nextTaskId() {
        return currentTaskId.incrementAndGet();
    }

}
