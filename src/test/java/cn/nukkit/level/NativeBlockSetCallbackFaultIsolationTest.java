package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real native callback dispatch; only server and outgoing world notifications are mocked. */
@Isolated("native Server.instance and registered block definitions")
public final class NativeBlockSetCallbackFaultIsolationTest {
    private static final String ERROR = "Error while calling block set callback";
    private Server server;
    private MainLogger previousLogger;
    private MainLogger logger;

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @AfterAll
    static void restoreServer() {
        MockServer.restore();
    }

    @BeforeEach
    void captureLogger() {
        server = MockServer.get();
        previousLogger = server.getLogger();
        logger = mock(MainLogger.class);
        when(server.getLogger()).thenReturn(logger);
    }

    @AfterEach
    void restoreLogger() {
        when(server.getLogger()).thenReturn(previousLogger);
    }

    @ParameterizedTest
    @ValueSource(strings = {"illegal-state", "illegal-argument"})
    void ordinaryFailureDoesNotSkipFollowingNativeCallbacks(String kind) throws Throwable {
        Fixture f = new Fixture();
        List<Probe> order = f.probes(3);
        RuntimeException failure = kind.equals("illegal-state")
                ? new IllegalStateException("foreign block listener")
                : new IllegalArgumentException("foreign block listener");
        order.get(0).failure = failure;

        f.notifyChange();

        for (Probe probe : order) {
            assertEquals(1, probe.calls, "every existing callback receives this accepted change once");
            assertSame(f.before, probe.before);
            assertSame(f.after, probe.after);
        }
        verify(logger).error(ERROR, failure);
        f.notificationSent();
    }

    @Test
    void eachOrdinaryFailureIsLoggedAndFinalCallbackStillRuns() throws Throwable {
        Fixture f = new Fixture();
        List<Probe> order = f.probes(3);
        RuntimeException first = new IllegalStateException("first foreign listener");
        RuntimeException second = new UnsupportedOperationException("second foreign listener");
        order.get(0).failure = first;
        order.get(1).failure = second;

        f.notifyChange();

        for (Probe probe : order) assertEquals(1, probe.calls);
        verify(logger).error(ERROR, first);
        verify(logger).error(ERROR, second);
        f.notificationSent();
    }

    @Test
    void fatalErrorStillPropagatesAndStopsTheRemainingNotifications() throws Exception {
        Fixture f = new Fixture();
        List<Probe> order = f.probes(3);
        AssertionError failure = new AssertionError("fatal native listener");
        order.get(0).failure = failure;

        assertSame(failure, assertThrows(AssertionError.class, f::notifyChange));

        assertEquals(1, order.get(0).calls);
        assertEquals(0, order.get(1).calls);
        assertEquals(0, order.get(2).calls);
        verify(logger, never()).error(anyString(), any(Throwable.class));
        verify(f.level, never()).sendBlocks(any(Player[].class), any(Block[].class), anyInt(), anyInt());
    }

    @Test
    void emptyRegistryKeepsPositionedOldNewBlocksAndNativeNotification() throws Throwable {
        Fixture f = new Fixture();

        f.notifyChange();

        for (Block block : List.of(f.before, f.after)) {
            assertSame(f.level, block.getLevel());
            assertEquals(17, block.getFloorX());
            assertEquals(64, block.getFloorY());
            assertEquals(-2, block.getFloorZ());
            assertEquals(0, block.layer);
        }
        verify(logger, never()).error(anyString(), any(Throwable.class));
        f.notificationSent();
    }

    private static final class Probe implements BiConsumer<Block, Block> {
        private int calls;
        private Throwable failure;
        private Block before;
        private Block after;

        @Override
        public void accept(Block previous, Block current) {
            calls++;
            before = previous;
            after = current;
            if (failure instanceof RuntimeException ordinary) throw ordinary;
            if (failure instanceof Error fatal) throw fatal;
        }
    }

    private static final class Fixture {
        private final Level level = mock(Level.class);
        private final Block before = Block.get(Block.SNOW_LAYER);
        private final Block after = Block.get(Block.AIR);
        private final BaseFullChunk chunk = mock(BaseFullChunk.class);
        private final LevelProvider provider = mock(LevelProvider.class);
        private final Int2ObjectMap<BiConsumer<Block, Block>> callbacks = new Int2ObjectOpenHashMap<>();

        private Fixture() throws ReflectiveOperationException {
            field(level, "callbackBlockSet", callbacks);
            field(level, "provider", provider);
            doCallRealMethod().when(level).addCallbackBlockSet(
                    org.mockito.ArgumentMatchers.<BiConsumer<Block, Block>>any());
            doCallRealMethod().when(level).setBlock(anyInt(), anyInt(), anyInt(), anyInt(),
                    any(Block.class), anyBoolean(), anyBoolean());
            when(level.requireProvider()).thenReturn(provider);
            when(level.isYInRange(64)).thenReturn(true);
            when(provider.getMinBlockY()).thenReturn(0);
            when(provider.getMaxBlockY()).thenReturn(127);
            when(provider.getMaximumLayer()).thenReturn(1);
            when(level.getChunk(1, -1, true)).thenReturn(chunk);
            when(chunk.getAndSetBlock(1, 64, 14, 0, after)).thenReturn(before);
            when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Map.of());
            when(level.getChunkLoaders(anyInt(), anyInt())).thenReturn(new ChunkLoader[0]);
        }

        private List<Probe> probes(int count) {
            for (int i = 0; i < count; i++) level.addCallbackBlockSet(new Probe());
            List<Probe> actualOrder = new ArrayList<>();
            // Use the real map's current order; do not assume global registration IDs or hash order.
            for (BiConsumer<Block, Block> callback : callbacks.values()) actualOrder.add((Probe) callback);
            return actualOrder;
        }

        private void notifyChange() {
            level.setBlock(17, 64, -2, 0, after, true, false);
        }

        private void notificationSent() {
            verify(level).sendBlocks(any(Player[].class), any(Block[].class),
                    eq(UpdateBlockPacket.FLAG_ALL_PRIORITY), eq(0));
        }
    }

    private static void field(Level level, String name, Object value) throws ReflectiveOperationException {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }
}
