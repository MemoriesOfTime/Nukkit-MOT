package cn.nukkit.utils;

import java.util.concurrent.Executor;

/** Test-only executor injection; production always uses bounded AbortPolicy pools. */
public final class ManualPlayerDataQueue {
    private ManualPlayerDataQueue() { }
    public static PlayerDataSaveQueue create(Executor writes, Executor reads, int capacity) {
        return new PlayerDataSaveQueue(writes, reads, capacity);
    }
}
