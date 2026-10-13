package cn.nukkit.level;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Independent game loop for Level parallel ticking.
 * <p>
 * Adapted from Allay (<a href="https://github.com/AllayMC/Allay">Allay</a>)
 */
public final class GameLoop {

    private static final Logger log = LogManager.getLogger(GameLoop.class);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Runnable onStart;
    private final GameLoopTickCallback onTickCallback;
    private final Runnable onIdle;
    private final Runnable onStop;
    private volatile Thread loopThread;
    private final int loopCountPerSec;
    private final float[] tickSummary;
    private final float[] msptSummary;
    private volatile int ringIndex;
    private volatile long tick;
    private volatile long lastTickStartMillis;

    private GameLoop(Runnable onStart, GameLoopTickCallback onTick,
                     Runnable onIdle, Runnable onStop,
                     int loopCountPerSec, long currentTick) {
        if (loopCountPerSec <= 0) {
            throw new IllegalArgumentException("loopCountPerSec must be > 0");
        }
        this.onStart = onStart;
        this.onTickCallback = onTick;
        this.onIdle = onIdle;
        this.onStop = onStop;
        this.loopCountPerSec = loopCountPerSec;
        this.tick = currentTick;
        this.tickSummary = new float[loopCountPerSec];
        this.msptSummary = new float[loopCountPerSec];
        // Default to 0; getTPS/getMSPT skip unset entries
    }

    public static GameLoopBuilder builder() {
        return new GameLoopBuilder();
    }

    public void startLoop() {
        loopThread = Thread.currentThread();
        onStart.run();
        long nanoSleepTime = 0;
        long idealNanoPerTick = 1_000_000_000L / loopCountPerSec;
        try {
            while (running.get()) {
                long startTime = System.nanoTime();
                lastTickStartMillis = System.currentTimeMillis();
                long elapsedNanos = -1;
                try {
                    elapsedNanos = (long) onTickCallback.onTick(this, startTime);
                } catch (Exception e) {
                    log.error("Exception in game loop tick " + tick, e);
                }
                // 逻辑时间每次迭代都推进（对齐旧主循环语义），跳过的迭代后实体 tickDiff 才能补上
                tick++;
                if (elapsedNanos >= 0) {
                    updateTPS(elapsedNanos);
                    updateMSPT(elapsedNanos);
                    nanoSleepTime += idealNanoPerTick - elapsedNanos;
                } else {
                    // Skipped or errored tick - still account for actual elapsed time
                    nanoSleepTime += idealNanoPerTick - (System.nanoTime() - startTime);
                    recordSkippedTick();
                }
                // Limit catch-up to 1 tick to prevent burst after lag spikes
                nanoSleepTime = Math.max(nanoSleepTime, -idealNanoPerTick);
                while (nanoSleepTime > 0 && running.get()) {
                    long sleepStart = System.nanoTime();
                    LockSupport.parkNanos(nanoSleepTime);
                    if (onIdle != null && running.get()) {
                        onIdle.run();
                    }
                    nanoSleepTime -= System.nanoTime() - sleepStart;
                }
            }
        } finally {
            // finally 兜底：tick 抛 Error 时也清状态、触发 onStop
            lastTickStartMillis = 0L;
            loopThread = null;
            onStop.run();
        }
    }

    public void wakeUp() {
        Thread t = loopThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public void stop() {
        running.set(false);
        wakeUp();
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getTick() {
        return tick;
    }

    public long getLastTickStartMillis() {
        return lastTickStartMillis;
    }

    public float getTPS() {
        // 从 ringIndex（volatile）起按时间序读：volatile 读与 updateMSPT 的写配对，使已完成的环形槽位对本线程可见
        // Volatile read of ringIndex pairs with updateMSPT's write so completed slots are visible
        // 跳过刻记 -1：计入分母、不计入分子，限流/冻结中的世界 TPS 才会真实回落
        // Skipped ticks are stored as -1: they count in the denominator only, so a
        // rate-limited/frozen level reports a falling TPS instead of a flat ~20
        int oldest = this.ringIndex;
        float sum = 0;
        int count = 0;
        for (int i = 0; i < tickSummary.length; i++) {
            float t = tickSummary[(oldest + i) % tickSummary.length];
            if (t != 0) { sum += Math.max(t, 0); count++; }
        }
        return count > 0 ? sum / count : 0;
    }

    public float getMSPT() {
        int oldest = this.ringIndex;
        float sum = 0;
        int count = 0;
        for (int i = 0; i < msptSummary.length; i++) {
            float m = msptSummary[(oldest + i) % msptSummary.length];
            if (m > 0) { sum += m; count++; }
        }
        return count > 0 ? sum / count : 0;
    }

    public float getTickUsage() {
        return getMSPT() / (1000f / loopCountPerSec);
    }

    private void updateTPS(long timeTakenNanos) {
        float tps = Math.max(0, Math.min(loopCountPerSec,
                1_000_000_000f / Math.max(1, timeTakenNanos)));
        tickSummary[ringIndex] = tps;
    }

    private void updateMSPT(long timeTakenNanos) {
        msptSummary[ringIndex] = timeTakenNanos / 1_000_000f;
        ringIndex = (ringIndex + 1) % tickSummary.length;
    }

    // 跳过/异常刻记 -1 并推进环：计入 TPS 分母（见 getTPS），停跳期间 TPS 真实回落；
    // mspt 记 0 表示无样本，getMSPT 只对执行过的刻求均值
    // Skipped ticks are stored as -1 (counted in the TPS denominator, see getTPS) so TPS
    // drops during stalls; mspt stays 0 = no sample and getMSPT averages executed ticks only
    private void recordSkippedTick() {
        tickSummary[ringIndex] = -1;
        msptSummary[ringIndex] = 0;
        ringIndex = (ringIndex + 1) % tickSummary.length;
    }

    public static class GameLoopBuilder {
        private Runnable onStart = () -> {};
        private GameLoopTickCallback onTick = (gl, startNanos) -> 0L;
        private Runnable onIdle;
        private Runnable onStop = () -> {};
        private int loopCountPerSec = 20;
        private long currentTick = 0;

        public GameLoopBuilder onStart(Runnable onStart) {
            this.onStart = onStart;
            return this;
        }

        public GameLoopBuilder onTick(GameLoopTickCallback onTick) {
            this.onTick = onTick;
            return this;
        }

        public GameLoopBuilder onIdle(Runnable onIdle) {
            this.onIdle = onIdle;
            return this;
        }

        public GameLoopBuilder onStop(Runnable onStop) {
            this.onStop = onStop;
            return this;
        }

        public GameLoopBuilder loopCountPerSec(int loopCountPerSec) {
            this.loopCountPerSec = loopCountPerSec;
            return this;
        }

        public GameLoopBuilder currentTick(long currentTick) {
            this.currentTick = currentTick;
            return this;
        }

        public GameLoop build() {
            return new GameLoop(onStart, onTick, onIdle, onStop, loopCountPerSec, currentTick);
        }
    }

    /**
     * Callback for game loop tick.
     * @return elapsed nanoseconds, or negative value to skip TPS/MSPT update
     */
    @FunctionalInterface
    public interface GameLoopTickCallback {
        long onTick(GameLoop loop, long startNanos);
    }
}
