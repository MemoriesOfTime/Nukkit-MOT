package cn.nukkit.utils;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static cn.nukkit.utils.BlockRefusalDiagnostics.Reason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Isolated("Changes the existing process-wide debug level")
class BlockRefusalDiagnosticsTest {
    private final AtomicLong clock = new AtomicLong();
    private final BlockRefusalDiagnostics diagnostics = new BlockRefusalDiagnostics(clock::get);
    private final Server server = mock(Server.class);
    private final Level world = mock(Level.class);
    private final Block block = mock(Block.class);
    private final MainLogger logger = mock(MainLogger.class);
    private final Player player = mock(Player.class);
    private int previousDebug;

    @BeforeEach
    void setup() {
        previousDebug = Nukkit.DEBUG;
        Nukkit.DEBUG = 2;
        doReturn(UUID.randomUUID()).when(player).getUniqueId();
        when(world.getServer()).thenReturn(server);
        when(server.getLogger()).thenReturn(logger);
    }

    @AfterEach
    void restoreDebug() { Nukkit.DEBUG = previousDebug; }

    @Test
    void debugDisabledDoesNotEvaluateIdentityClockOrWorld() {
        Nukkit.DEBUG = 1;
        var audit = new BlockRefusalDiagnostics(() -> { throw new AssertionError("clock read"); });
        clearInvocations(player, block, world);
        audit.record(world, player, block, CACTUS_SUPPORT);
        verifyNoInteractions(player, block, world, logger);
    }

    @Test
    void independentCausesShareOneBoundedSamplerWithoutSuppressingEachOther() {
        record(); record();
        diagnostics.record(world, player, block, FLOWER_SUPPORT);
        verify(logger, times(2)).debug(anyString());
        clock.set(2_000_000_000L);
        record();
        verify(logger, times(3)).debug(anyString());
        clock.set(-1);
        record(); record();
        verify(logger, times(4)).debug(anyString());
    }

    @Test
    void rotatingClientsCannotEvictFreshEntriesOrGrowTheLoggingBudget() {
        UUID first = new UUID(0, 0);
        for (int i = 0; i < 10_000; i++) {
            when(player.getUniqueId()).thenReturn(new UUID(0, i));
            record();
        }
        verify(logger, times(512)).debug(anyString());
        when(player.getUniqueId()).thenReturn(first);
        record();
        verify(logger, times(512)).debug(anyString());
        clock.set(2_000_000_001L);
        when(player.getUniqueId()).thenReturn(new UUID(1, 0));
        record();
        verify(logger, times(513)).debug(anyString());
    }

    @Test
    void loggerReentryCannotLogTheSameRefusalTwice() {
        doAnswer(call -> { record(); return null; }).when(logger).debug(anyString());
        record();
        verify(logger).debug(anyString());
    }

    @Test
    void concurrentSameClientIsAdmittedOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> {
                try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                for (int j = 0; j < 20; j++) record();
            }));
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            verify(logger).debug(anyString());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void pausedOlderClockReadCannotUndoNewerAdmission() throws Exception {
        var firstClockEntered = new CountDownLatch(1);
        var releaseFirstClock = new CountDownLatch(1);
        var secondCallerEntered = new CountDownLatch(1);
        var clockCalls = new AtomicInteger();
        var secondCaller = new AtomicReference<Thread>();
        var audit = new BlockRefusalDiagnostics(() -> {
            if (clockCalls.incrementAndGet() == 1) {
                firstClockEntered.countDown();
                try { assertTrue(releaseFirstClock.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return 0;
            }
            return 1;
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> audit.record(world, player, block, CACTUS_SUPPORT));
            assertTrue(firstClockEntered.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> {
                secondCaller.set(Thread.currentThread());
                secondCallerEntered.countDown();
                audit.record(world, player, block, CACTUS_SUPPORT);
            });
            assertTrue(secondCallerEntered.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (clockCalls.get() < 2 && secondCaller.get().getState() != Thread.State.BLOCKED
                    && System.nanoTime() < deadline) Thread.onSpinWait();
            assertTrue(clockCalls.get() >= 2 || secondCaller.get().getState() == Thread.State.BLOCKED);
            if (clockCalls.get() >= 2) second.get(5, TimeUnit.SECONDS);
            releaseFirstClock.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            verify(logger).debug(anyString());
        } finally {
            releaseFirstClock.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void diagnosticMetadataAndLoggerFailuresStayLocal() {
        doThrow(new IllegalStateException("logger failed")).when(logger).debug(anyString());
        assertDoesNotThrow(this::record);
        when(player.getUniqueId()).thenThrow(new IllegalStateException("identity failed"));
        assertDoesNotThrow(this::record);
        var brokenClock = new BlockRefusalDiagnostics(() -> { throw new IllegalStateException("clock failed"); });
        doReturn(UUID.randomUUID()).when(player).getUniqueId();
        assertDoesNotThrow(() -> brokenClock.record(world, player, block, CACTUS_SUPPORT));
    }

    @Test
    void logContainsNoPlayerNameUuidWorldNameOrItemData() {
        doThrow(new AssertionError("name read")).when(player).getName();
        doThrow(new AssertionError("world name read")).when(world).getFolderName();
        record();
        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).debug(message.capture());
        assertTrue(message.getValue().contains("reason=cactus_support"));
        assertFalse(message.getValue().contains(player.getUniqueId().toString()));
        verify(player, never()).getName();
        verify(world, never()).getFolderName();
    }

    private void record() { diagnostics.record(world, player, block, CACTUS_SUPPORT); }
}
