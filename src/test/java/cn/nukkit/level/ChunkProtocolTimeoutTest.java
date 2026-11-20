package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Isolated("MockServer")
class ChunkProtocolTimeoutTest {
    private static final GameVersion A = GameVersion.V1_20_0;
    private static final GameVersion B = GameVersion.V1_26_50;
    private static final int X = 3, Z = 5;
    private static final long INDEX = Level.chunkHash(X, Z);

    @BeforeAll static void init() { MockServer.init(); }

    @Test
    void aLaterProtocolDoesNotRefreshAnEarlierProtocolsTimeout() throws Exception {
        Fixture f = new Fixture();
        Player first = f.player(A, 1), second = f.player(B, 2);
        f.accept(first, 100);
        long original = f.epoch(A);
        f.accept(second, 250);
        long secondEpoch = f.epoch(B);
        f.accept(first, 301);
        assertNotEquals(original, f.epoch(A), "The first protocol is overdue despite the later submission");
        assertEquals(secondEpoch, f.epoch(B), "The later protocol keeps its own active request");
    }

    @Test
    void retryingOneProtocolDoesNotPostponeItsOlderSibling() throws Exception {
        Fixture f = new Fixture();
        Player first = f.player(A, 1), second = f.player(B, 2);
        f.accept(first, 100);
        f.accept(second, 100);
        long oldSecond = f.epoch(B);
        f.level.onAsyncChunkRequestFailed(A, X, Z, f.epoch(A));
        f.level.drainFailedChunkRequests();
        f.accept(first, 250);
        f.accept(second, 301);
        assertNotEquals(oldSecond, f.epoch(B), "A sibling retry must not renew this protocol's timeout");
    }

    @Test
    void completingOneProtocolKeepsTheOthersOriginalDeadline() throws Exception {
        Fixture f = new Fixture();
        Player first = f.player(A, 1), second = f.player(B, 2);
        f.accept(first, 100);
        f.accept(second, 250);
        long oldFirst = f.epoch(A);
        f.level.chunkRequestCallback(B, 0, X, Z, 0, new byte[]{1}, null, null, null, f.epoch(B));
        f.accept(first, 301);
        assertNotEquals(oldFirst, f.epoch(A));
        verify(second).sendChunk(eq(X), eq(Z), eq(0), any(byte[].class), eq(0));
    }

    @Test
    void aSubmissionAtTickZeroStillHasAnExactTimeoutBoundary() throws Exception {
        Fixture f = new Fixture();
        Player player = f.player(A, 1);
        f.accept(player, 0);
        long original = f.epoch(A);
        f.accept(player, 200);
        assertEquals(original, f.epoch(A), "The configured boundary is strictly greater than 200 ticks");
        f.accept(player, 201);
        assertNotEquals(original, f.epoch(A), "Tick zero is a valid start time, not an absent timestamp");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aQueuedCurrentResultDrainsAfterAsyncSendingIsDisabled(boolean enabled) throws Exception {
        Fixture f = new Fixture();
        Player player = f.player(A, 1);
        f.accept(player, 100);
        f.level.asyncChunkRequestCallback(A, 0, X, Z, 0, new byte[]{1}, null, null, null, f.epoch(A));
        f.server.asyncChunkSending = enabled;
        f.level.doTick(101);
        verify(player).sendChunk(eq(X), eq(Z), eq(0), any(byte[].class), eq(0));
        assertEquals(0, f.epoch(A), "Delivery completes even when no new async work is permitted");
    }

    @Test
    void aRetiredFailureDoesNotRenewOrClearTheCurrentTimeout() throws Exception {
        Fixture f = new Fixture();
        Player player = f.player(A, 1);
        f.accept(player, 100);
        long old = f.epoch(A);
        f.accept(player, 301);
        long current = f.epoch(A);
        f.level.onAsyncChunkRequestFailed(A, X, Z, old);
        f.level.drainFailedChunkRequests();
        f.accept(player, 501);
        assertEquals(current, f.epoch(A));
        f.accept(player, 502);
        assertNotEquals(current, f.epoch(A));
    }

    private static final class Fixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final LevelProvider provider = mock(LevelProvider.class);
        final Object2ObjectOpenHashMap<GameVersion, Long2LongOpenHashMap> epochs = new Object2ObjectOpenHashMap<>();

        Fixture() throws Exception {
            set(level, "server", server); set(level, "provider", provider);
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"})
                set(level, name, new Object2ObjectOpenHashMap<>());
            set(level, "chunkSendTaskStartTick", new Object2ObjectOpenHashMap<>());
            set(level, "chunkRequestEpochs", epochs);
            set(level, "asyncChunkRequestCallbackQueue", new ConcurrentLinkedQueue<>());
            set(level, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
            set(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
            set(level, "loaders", new Int2ObjectOpenHashMap<>());
            set(level, "chunkTickList", new Long2IntOpenHashMap());
            set(level, "players", new Long2ObjectNonBlockingMap<>());
            set(level, "updateEntities", new Long2ObjectNonBlockingMap<>());
            set(level, "changedBlocks", new Long2ObjectOpenHashMap<>());
            set(level, "changeBlocksFullMap", new Int2ObjectOpenHashMap<>());
            set(level, "dimensionData", DimensionData.LEGACY_DIMENSION);
            set(level, "lightQueue", new ConcurrentHashMap<>());
            set(level, "updateQueue", new BlockUpdateScheduler(level, 0));
            set(level, "normalUpdateQueue", new ConcurrentLinkedQueue<>());
            set(level, "normalUpdateQueueSize", new AtomicInteger());
            set(level, "updateBlockEntities", new ConcurrentLinkedQueue<>());
            set(level, "chunkPackets", new ConcurrentHashMap<>());
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            level.gameRules.refresh();
            server.chunksPerTick = 4;
            when(server.getLogger()).thenReturn(mock(MainLogger.class));
            doReturn(0).when(level).getDimension();
            doReturn(mock(BaseFullChunk.class)).when(level).getChunk(X, Z);
        }

        Player player(GameVersion version, int id) throws Exception {
            Player player = mock(Player.class);
            Field field = Player.class.getDeclaredField("usedChunks"); field.setAccessible(true);
            field.set(player, new Long2ObjectOpenHashMap<Boolean>());
            player.usedChunks.put(INDEX, false);
            when(player.getLoaderId()).thenReturn(id); when(player.getGameVersion()).thenReturn(version);
            when(player.isConnected()).thenReturn(true); when(player.getLevel()).thenReturn(level);
            return player;
        }

        void accept(Player player, int tick) throws Exception {
            when(server.getTick()).thenReturn(tick);
            level.requestChunk(X, Z, player);
            Method m = Level.class.getDeclaredMethod("processChunkRequest"); m.setAccessible(true); m.invoke(level);
        }

        long epoch(GameVersion version) {
            Long2LongOpenHashMap bucket = epochs.get(version);
            return bucket == null ? 0 : bucket.get(INDEX);
        }
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name); field.setAccessible(true); field.set(level, value);
    }
}
