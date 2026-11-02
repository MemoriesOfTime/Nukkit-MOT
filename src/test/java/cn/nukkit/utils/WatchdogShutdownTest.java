package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.utils.serverconfig.ServerConfig;
import cn.nukkit.utils.serverconfig.category.PerformanceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WatchdogShutdownTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyAnUnexpectedInterruptReportsLostMonitoring(boolean plannedStop) throws Exception {
        Server server = mock(Server.class);
        MainLogger logger = mock(MainLogger.class);
        CountDownLatch enteredRunLoop = new CountDownLatch(1);
        when(server.getLogger()).thenReturn(logger);
        when(server.getNextTick()).thenAnswer(ignored -> {
            enteredRunLoop.countDown();
            return 0L;
        });
        Watchdog watchdog = new Watchdog(server, 60_000);
        watchdog.start();
        try {
            assertTrue(enteredRunLoop.await(5, TimeUnit.SECONDS), "Watchdog must enter its real run loop");
            if (plannedStop) {
                watchdog.kill();
            } else {
                watchdog.interrupt();
            }
            watchdog.join(5_000);
            assertFalse(watchdog.isAlive(), "Both stop paths must terminate promptly");
            assertFalse(watchdog.running);
            if (plannedStop) {
                verify(logger, never()).emergency(anyString());
            } else {
                verify(logger).emergency("The Watchdog thread has been interrupted and is no longer monitoring the server state");
            }
            verify(server, never()).forceShutdown(anyString());
        } finally {
            watchdog.kill();
            watchdog.join(5_000);
        }
    }

    private Server mockShuttingDownServer(MainLogger logger, AtomicLong progressAt, long killTimeout) {
        Server server = mock(Server.class);
        when(server.getLogger()).thenReturn(logger);
        when(server.isShutdownInProgress()).thenReturn(true);
        when(server.getShutdownProgressAt()).thenAnswer(ignored -> progressAt.get());
        PerformanceSettings performance = mock(PerformanceSettings.class);
        when(performance.shutdownWatchdogKillTimeout()).thenReturn(killTimeout);
        ServerConfig serverConfig = mock(ServerConfig.class);
        when(serverConfig.performanceSettings()).thenReturn(performance);
        when(server.getServerConfig()).thenReturn(serverConfig);
        return server;
    }

    @Test
    void shutdownProgressKeepsWatchdogQuiet() throws Exception {
        MainLogger logger = mock(MainLogger.class);
        AtomicLong progressAt = new AtomicLong(System.currentTimeMillis());
        Server server = mockShuttingDownServer(logger, progressAt, 0);
        AtomicBoolean killed = new AtomicBoolean();
        Watchdog watchdog = new Watchdog(server, 200) {
            @Override
            protected void killServer() {
                killed.set(true);
            }
        };
        watchdog.start();
        try {
            // 保存持续出进度,停滞分支不得触发。/ Progress keeps flowing; the stall branch must not fire.
            long deadline = System.currentTimeMillis() + 2_500;
            while (System.currentTimeMillis() < deadline) {
                progressAt.set(System.currentTimeMillis());
                //noinspection BusyWait
                Thread.sleep(50);
            }
            verify(logger, never()).emergency(anyString());
            assertFalse(killed.get());
        } finally {
            watchdog.kill();
            watchdog.join(5_000);
        }
    }

    @Test
    void stalledShutdownDumpsOnceThenRepeatsShortNoticesWithoutKilling() throws Exception {
        MainLogger logger = mock(MainLogger.class);
        // 心跳停在 10 秒前,首轮即停滞。/ Heartbeat frozen 10s ago; the first cycle stalls.
        AtomicLong progressAt = new AtomicLong(System.currentTimeMillis() - 10_000);
        Server server = mockShuttingDownServer(logger, progressAt, 0);
        AtomicBoolean killed = new AtomicBoolean();
        Watchdog watchdog = new Watchdog(server, 200) {
            @Override
            protected void killServer() {
                killed.set(true);
            }
        };
        watchdog.start();
        try {
            // 循环 sleep 下限 1s,等 ~3 轮覆盖首次 dump + 两条短警告。/ Loop sleeps >= 1s; ~3 cycles cover the dump plus two notices.
            long deadline = System.currentTimeMillis() + 4_000;
            while (System.currentTimeMillis() < deadline) {
                //noinspection BusyWait
                Thread.sleep(100);
            }
            verify(logger, atLeastOnce()).emergency(contains("Shutdown stalled"));
            verify(logger, atLeast(2)).emergency(contains("still stalled"));
            verify(logger, never()).emergency(contains("kill timeout"));
            assertFalse(killed.get(), "warn-only mode must never kill");
            verify(server, never()).forceShutdown(anyString());
        } finally {
            watchdog.kill();
            watchdog.join(5_000);
        }
    }

    @Test
    void stalledShutdownExitsAfterConfiguredKillTimeout() throws Exception {
        MainLogger logger = mock(MainLogger.class);
        AtomicLong progressAt = new AtomicLong(System.currentTimeMillis() - 30_000);
        Server server = mockShuttingDownServer(logger, progressAt, 5_000);
        AtomicBoolean killed = new AtomicBoolean();
        Watchdog watchdog = new Watchdog(server, 200) {
            @Override
            protected void killServer() {
                killed.set(true);
            }
        };
        watchdog.start();
        try {
            long deadline = System.currentTimeMillis() + 5_000;
            while (!killed.get() && System.currentTimeMillis() < deadline) {
                //noinspection BusyWait
                Thread.sleep(50);
            }
            assertTrue(killed.get(), "kill timeout must trigger killServer");
            verify(logger).emergency(contains("kill timeout"));
        } finally {
            watchdog.kill();
            watchdog.join(5_000);
        }
    }
}
