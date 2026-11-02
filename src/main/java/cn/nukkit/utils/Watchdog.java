package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.utils.bugreport.BugReportGenerator;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;

/**
 * Watchdog monitors the server's main thread and kills the server if it gets frozen.
 * <p>
 * 运行期看 tick 心跳;关服后改看守进度心跳(Server.touchShutdownProgress),区分"保存慢"与"真死锁"。
 * During runtime it watches the tick heartbeat; once shutdown starts it watches the
 * progress heartbeat (Server.touchShutdownProgress) to tell a slow save from a deadlock.
 */
public class Watchdog extends Thread {

    private final Server server;
    private final long time;
    private boolean responding = true;
    private boolean shutdownStallReported;
    private long lastStallNotice;
    /**
     * Watchdog running
     */
    public volatile boolean running;

    public Watchdog(Server server, long time) {
        this.server = server;
        this.time = time;
        this.running = true;
        this.setName("Watchdog");
        this.setDaemon(true);
    }

    /**
     * Shotdown Watchdog
     */
    public void kill() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        while (this.running) {
            if (this.server.isShutdownInProgress()) {
                this.monitorShutdown();
            } else {
                this.monitorTick();
            }
            try {
                sleep(Math.max(time >> 2, 1000));
            } catch (InterruptedException ignore) {
                if (running) {
                    server.getLogger().emergency("The Watchdog thread has been interrupted and is no longer monitoring the server state");
                }
                running = false;
                return;
            }
        }
        server.getLogger().warning("Watchdog has been stopped");
    }

    private void monitorTick() {
        long current = server.getNextTick();
        if (current == 0) {
            return;
        }
        long diff = System.currentTimeMillis() - current;
        if (diff <= time) {
            responding = true;
        } else if (responding) {
            MainLogger logger = this.server.getLogger();
            StringBuilder log = new StringBuilder();

            print("--------- Server stopped responding ---------", logger, log);
            print("Last response " + Math.round(diff / 1000d) + " seconds ago", logger, log);
            print("---------------- Main thread ----------------", logger, log);

            dumpThread(ManagementFactory.getThreadMXBean().getThreadInfo(this.server.getPrimaryThread().getId(), Integer.MAX_VALUE), logger, log);

            print("---------------- All threads ----------------", logger, log);
            ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
            for (int i = 0; i < threads.length; i++) {
                if (i != 0) print("------------------------------", logger, log);
                dumpThread(threads[i], logger, log);
            }
            print("---------------------------------------------", logger, log);
            try {
                new BugReportGenerator(log.toString()).start();
                Thread.sleep(1000); // Wait for the report to be sent
            } catch (Exception ignored) {}
            responding = false;
            this.server.forceShutdown("\u00A7cServer stopped responding");
        }
    }

    /**
     * 关服模式:进度心跳停摆才算停滞;单次 LevelDB 写停顿等 compaction 可达分钟级,故阈值放宽到 4 倍。
     * Shutdown mode: only a frozen progress heartbeat counts as stalled; one LevelDB write
     * stall waiting on compaction can legitimately last minutes, hence the 4x threshold.
     */
    private void monitorShutdown() {
        long progressAt = this.server.getShutdownProgressAt();
        if (progressAt == Long.MAX_VALUE) {
            return;
        }
        long now = System.currentTimeMillis();
        long since = now - progressAt;
        if (since <= time << 2) {
            this.shutdownStallReported = false;
            return;
        }

        if (!this.shutdownStallReported) {
            this.shutdownStallReported = true;
            this.lastStallNotice = now;
            MainLogger logger = this.server.getLogger();
            StringBuilder log = new StringBuilder();
            print("--------- Shutdown stalled ---------", logger, log);
            print("No save progress for " + Math.round(since / 1000d) + " seconds", logger, log);
            print("---------------- All threads ----------------", logger, log);
            ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
            for (int i = 0; i < threads.length; i++) {
                if (i != 0) print("------------------------------", logger, log);
                dumpThread(threads[i], logger, log);
            }
            print("---------------------------------------------", logger, log);
        } else if (now - this.lastStallNotice >= time) {
            this.lastStallNotice = now;
            this.server.getLogger().emergency("Shutdown still stalled, no save progress for " + Math.round(since / 1000d) + " seconds");
        }

        long killTimeout = this.server.getServerConfig().performanceSettings().shutdownWatchdogKillTimeout();
        if (killTimeout > 0 && since > killTimeout) {
            this.server.getLogger().emergency("Shutdown stalled beyond the configured kill timeout, exiting");
            killServer();
        }
    }

    /**
     * 进程出口统一收口,便于测试覆写。/ Single process-exit choke point, overridable in tests.
     */
    protected void killServer() {
        System.exit(1);
    }

    /**
     * Dump thread stack trace
     *
     * @param thread thread to dump
     * @param logger logger
     * @param log bug report generator input
     */
    private static void dumpThread(ThreadInfo thread, Logger logger, StringBuilder log) {
        if (thread == null) {
            print("Attempted to dump a null thread!", logger, log);
            return;
        }

        print("Current Thread: " + thread.getThreadName(), logger, log);
        print("\tPID: " + thread.getThreadId() + " | Suspended: " + thread.isSuspended() + " | Native: " + thread.isInNative() + " | State: " + thread.getThreadState(), logger, log);

        if (thread.getLockedMonitors().length != 0) {
            print("\tThread is waiting on monitor(s):", logger, log);
            for (MonitorInfo monitor : thread.getLockedMonitors()) {
                print("\t\tLocked on:" + monitor.getLockedStackFrame(), logger, log);
            }
        }

        print("Stack:", logger, log);
        for (StackTraceElement stack : thread.getStackTrace()) {
            print("\t\t" + stack, logger, log);
        }
    }

    /**
     * Print a line to log
     *
     * @param text text to log
     * @param logger logger
     * @param log bug report generator input
     */
    private static void print(String text, Logger logger, StringBuilder log) {
        logger.emergency(text);
        log.append(text).append('\n');
    }
}
