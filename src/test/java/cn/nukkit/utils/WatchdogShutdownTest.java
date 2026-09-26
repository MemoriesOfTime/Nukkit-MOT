package cn.nukkit.utils;

import cn.nukkit.Server;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
}
