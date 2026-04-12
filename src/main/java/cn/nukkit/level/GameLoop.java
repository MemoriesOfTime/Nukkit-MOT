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
    private int ringIndex;
    private long tick;

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
        while (running.get()) {
            long startTime = System.nanoTime();
            long elapsedNanos = -1;
            try {
                elapsedNanos = (long) onTickCallback.onTick(this, startTime);
            } catch (Exception e) {
                log.error("Exception in game loop tick " + tick, e);
            }
            if (elapsedNanos >= 0) {
                tick++;
                updateTPS(elapsedNanos);
                updateMSPT(elapsedNanos);
            }

            nanoSleepTime += idealNanoPerTick - (elapsedNanos >= 0 ? elapsedNanos : 0);
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
        loopThread = null;
        onStop.run();
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

    public float getTPS() {
        float sum = 0;
        int count = 0;
        for (float t : tickSummary) {
            if (t > 0) { sum += t; count++; }
        }
        return count > 0 ? sum / count : 0;
    }

    public float getMSPT() {
        float sum = 0;
        int count = 0;
        for (float m : msptSummary) {
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
