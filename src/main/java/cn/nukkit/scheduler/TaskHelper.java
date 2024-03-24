package cn.nukkit.scheduler;

import cn.nukkit.Server;

import java.util.Optional;
import java.util.function.Consumer;

public class TaskHelper {

    /**
     * Usage: Server.getInstance().getScheduler().scheduleDelayedTask(TaskHelper.task(t -> {
     *    function
     * }), 20)
     *
     * @param consumer
     * @return
     */
    public static Task task(Consumer<Task> consumer) {
        return new Task() {
            @Override
            public void onRun(int currentTick) {
                consumer.accept(this);
            }
        };
    }

    public static AsyncTask asyncTask(Consumer<AsyncTask> consumer,
                                      Consumer<Server> completion) {
        return new AsyncTask() {
            @Override
            public void onRun() {
                consumer.accept(this);
            }

            @Override
            public void onCompletion(Server server) {
                Optional.ofNullable(completion).ifPresent(c -> c.accept(server));
            }
        };
    }


}
