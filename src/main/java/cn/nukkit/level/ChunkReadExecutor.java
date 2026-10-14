package cn.nukkit.level;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owned chunk reads report admission without running rejected work on the caller. */
final class ChunkReadExecutor extends ThreadPoolExecutor {
    private static final AbortPolicy ORDINARY_REJECTION = new AbortPolicy();
    private static final RejectedExecutionHandler READ_REJECTION = (task, executor) -> {
        if (task instanceof Level.ChunkReadTask read) {
            // ThreadPoolExecutor invokes this synchronously in the submitting thread.
            read.rejected = true;
        } else {
            ORDINARY_REJECTION.rejectedExecution(task, executor);
        }
    };

    ChunkReadExecutor(int capacity, ThreadFactory threads) {
        super(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), threads, READ_REJECTION);
    }

    boolean tryExecute(Level.ChunkReadTask task) {
        super.execute(task);
        return !task.rejected;
    }
}
