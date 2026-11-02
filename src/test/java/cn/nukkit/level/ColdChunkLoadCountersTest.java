package cn.nukkit.level;

import cn.nukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ColdChunkLoadCountersTest {
    private Server server;
    private Object previousServer;
    private Field instance;

    @BeforeEach
    void installServer() throws Exception {
        instance = Server.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousServer = instance.get(null);
        server = mock(Server.class);
        server.coldChunkLoadCounters = true;
        Thread main = Thread.currentThread();
        when(server.isPrimaryThread()).thenAnswer(ignored -> Thread.currentThread() == main);
        instance.set(null, server);
        ColdChunkLoadCounters.reset();
    }

    @AfterEach
    void restoreServer() throws Exception {
        ColdChunkLoadCounters.reset();
        instance.set(null, previousServer);
    }

    @Test
    void disabledAndMissingServerReturnSharedNoopWithoutThreadInspection() throws Exception {
        server.coldChunkLoadCounters = false;
        ColdChunkLoadCounters.Scope disabled = ColdChunkLoadCounters.begin("world", ColdChunkLoadCounters.Kind.SYNCHRONOUS);
        disabled.close();
        disabled.close();
        assertTrue(ColdChunkLoadCounters.snapshot().isEmpty());
        verifyNoInteractions(server);
        instance.set(null, null);
        assertSame(disabled, ColdChunkLoadCounters.begin("other", ColdChunkLoadCounters.Kind.ASYNCHRONOUS));
    }

    @Test
    void inFlightAttemptIsVisibleAndDuplicateCloseCannotDoubleCount() {
        ColdChunkLoadCounters.Scope scope = ColdChunkLoadCounters.begin("world", ColdChunkLoadCounters.Kind.SYNCHRONOUS);
        ColdChunkLoadCounters.Snapshot active = onlySnapshot();
        assertTrue(ColdChunkLoadCounters.snapshot().keySet().iterator().next().contains(
                "site=cn.nukkit.level.ColdChunkLoadCountersTest.inFlightAttemptIsVisibleAndDuplicateCloseCannotDoubleCount:"));
        assertEquals(1, active.started());
        assertEquals(0, active.completed());
        assertEquals(0, active.totalNanos());
        scope.close();
        scope.close();
        ColdChunkLoadCounters.Snapshot done = onlySnapshot();
        assertEquals(1, done.completed());
        assertTrue(done.totalNanos() > 0);
        assertEquals(done.totalNanos(), done.maxNanos());
        assertEquals(0, active.completed(), "snapshots must not change when a scope completes");
        assertThrows(UnsupportedOperationException.class, () -> ColdChunkLoadCounters.snapshot().clear());
    }

    @Test
    void siteCardinalityIsBoundedWithoutLosingCounts() {
        for (int i = 0; i < 400; ++i) {
            try (var ignored = ColdChunkLoadCounters.begin("world-" + i, ColdChunkLoadCounters.Kind.DEFERRED)) {}
        }
        Map<String, ColdChunkLoadCounters.Snapshot> values = ColdChunkLoadCounters.snapshot();
        assertEquals(256, values.size());
        assertEquals(400, values.values().stream().mapToLong(ColdChunkLoadCounters.Snapshot::started).sum());
        assertEquals(400, values.values().stream().mapToLong(ColdChunkLoadCounters.Snapshot::completed).sum());
        assertTrue(values.keySet().stream().anyMatch(key -> key.endsWith("<overflow>")));
        values.values().forEach(value -> assertTrue(value.maxNanos() <= value.totalNanos()));
    }

    @Test
    void worldKindAndCallingThreadRemainSeparate() throws Exception {
        try (var ignored = ColdChunkLoadCounters.begin("overworld", ColdChunkLoadCounters.Kind.SYNCHRONOUS)) {}
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try (var ignored = ColdChunkLoadCounters.begin("nether", ColdChunkLoadCounters.Kind.ASYNCHRONOUS)) {
                assertEquals(2, ColdChunkLoadCounters.snapshot().size());
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        worker.start();
        worker.join(10_000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        var values = ColdChunkLoadCounters.snapshot();
        assertTrue(values.keySet().stream().anyMatch(key -> key.startsWith("world=overworld|kind=SYNCHRONOUS|thread=main|site=")));
        assertTrue(values.keySet().stream().anyMatch(key -> key.startsWith("world=nether|kind=ASYNCHRONOUS|thread=worker|site=")));
        assertEquals(2, values.values().stream().mapToLong(ColdChunkLoadCounters.Snapshot::completed).sum());
    }

    @Test
    void resettingCountersDoesNotMoveOldCompletionsIntoNewInterval() {
        ColdChunkLoadCounters.Scope previous = ColdChunkLoadCounters.begin("world", ColdChunkLoadCounters.Kind.SYNCHRONOUS);
        ColdChunkLoadCounters.reset();
        previous.close();
        assertTrue(ColdChunkLoadCounters.snapshot().isEmpty());
        try (var ignored = ColdChunkLoadCounters.begin("world", ColdChunkLoadCounters.Kind.SYNCHRONOUS)) {}
        assertEquals(1, onlySnapshot().started());
        assertEquals(1, onlySnapshot().completed());
    }

    private static ColdChunkLoadCounters.Snapshot onlySnapshot() {
        var values = ColdChunkLoadCounters.snapshot();
        assertEquals(1, values.size());
        return values.values().iterator().next();
    }
}
