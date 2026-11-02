package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 区块请求代次栅栏：一次受理的结果只服务这一次受理的接收者。
 * <p>
 * Epoch fence: one acceptance's results serve only that acceptance's recipients.
 */
class ChunkRequestEpochFenceTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private static final GameVersion OTHER = GameVersion.V1_26_50;

    @Test
    void lateFailureFromARetiredSubmissionKeepsTheRetryAndItsTimeout() throws Throwable {
        Fixture fixture = new Fixture();
        long hash = Level.chunkHash(3, 5);
        Player player = fixture.player(1, hash, VERSION);
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(3, 5);

        fixture.level.requestChunk(3, 5, player);
        process(fixture.level);
        long retired = fixture.epoch(hash, VERSION);
        assertTrue(retired > 0L, "The accepted submission is tracked while it is in flight");

        when(fixture.server.getTick()).thenReturn(400);
        fixture.level.requestChunk(3, 5, player);
        process(fixture.level);
        long retry = fixture.epoch(hash, VERSION);
        assertNotEquals(retired, retry, "A timeout retry is a new submission");
        assertEquals(400L, fixture.started(hash, VERSION));

        fixture.level.onAsyncChunkRequestFailed(VERSION, 3, 5, retired);
        fixture.level.drainFailedChunkRequests();

        assertTrue(fixture.tasks(VERSION).contains(hash), "A retired submission's failure must not drop the retry");
        assertEquals(retry, fixture.epoch(hash, VERSION));
        assertEquals(400L, fixture.started(hash, VERSION), "A retired submission's failure must not cancel the retry timeout");
    }

    @Test
    void retiringOneVersionKeepsTheOtherVersionsTimeout() throws Throwable {
        Fixture fixture = new Fixture();
        long hash = Level.chunkHash(3, 5);
        Player modern = fixture.player(1, hash, VERSION);
        Player legacy = fixture.player(2, hash, OTHER);
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(3, 5);

        fixture.level.requestChunk(3, 5, modern);
        fixture.level.requestChunk(3, 5, legacy);
        process(fixture.level);
        long submission = fixture.epoch(hash, VERSION);
        assertEquals(submission, fixture.epoch(hash, OTHER), "One submission covers both protocol buckets");

        fixture.level.onAsyncChunkRequestFailed(VERSION, 3, 5, submission);
        fixture.level.drainFailedChunkRequests();

        assertFalse(fixture.tasks(VERSION).contains(hash));
        assertTrue(fixture.tasks(OTHER).contains(hash), "The other version is still in flight");
        assertEquals(submission, fixture.epoch(hash, OTHER));
        assertEquals(0L, fixture.started(hash, VERSION), "The completed protocol retires its timestamp");
        assertEquals(100L, fixture.started(hash, OTHER), "The sibling keeps its original timeout");
    }

    @Test
    void staleResultFromARetiredSubmissionIsNotDispatched() throws Throwable {
        Fixture fixture = new Fixture();
        long hash = Level.chunkHash(3, 5);
        Player player = fixture.player(1, hash, VERSION);
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(3, 5);

        fixture.level.requestChunk(3, 5, player);
        process(fixture.level);
        long retired = fixture.epoch(hash, VERSION);

        when(fixture.server.getTick()).thenReturn(400);
        fixture.level.requestChunk(3, 5, player);
        process(fixture.level);
        long retry = fixture.epoch(hash, VERSION);

        fixture.level.chunkRequestCallback(VERSION, 1L, 3, 5, 0, new byte[]{1}, null, null, null, retired);

        verify(player, never()).sendChunk(anyInt(), anyInt(), anyInt(), any(byte[].class), anyInt());
        assertTrue(fixture.tasks(VERSION).contains(hash));
        assertEquals(retry, fixture.epoch(hash, VERSION));
    }

    @Test
    void duplicateResultAfterDeliveryIsDropped() throws Throwable {
        Fixture fixture = new Fixture();
        long hash = Level.chunkHash(3, 5);
        Player player = fixture.player(1, hash, VERSION);
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(3, 5);

        fixture.level.requestChunk(3, 5, player);
        process(fixture.level);
        long submission = fixture.epoch(hash, VERSION);

        fixture.level.chunkRequestCallback(VERSION, 1L, 3, 5, 0, new byte[]{1}, null, null, null, submission);

        verify(player).sendChunk(eq(3), eq(5), eq(0), any(byte[].class), eq(0));
        assertFalse(fixture.tasks(VERSION).contains(hash));
        assertFalse(fixture.queues(VERSION).containsKey(hash));
        assertEquals(0L, fixture.epoch(hash, VERSION), "Delivery retires the submission");

        fixture.level.chunkRequestCallback(VERSION, 2L, 3, 5, 0, new byte[]{2}, null, null, null, submission);

        verify(player, times(1)).sendChunk(eq(3), eq(5), eq(0), any(byte[].class), eq(0));
    }

    @Test
    void resultWithoutAnEchoedIdStillDispatches() throws Throwable {
        Fixture fixture = new Fixture();
        long hash = Level.chunkHash(3, 5);
        Player player = fixture.player(1, hash, VERSION);
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(3, 5);

        fixture.level.requestChunk(3, 5, player);
        process(fixture.level);

        fixture.level.chunkRequestCallback(VERSION, 1L, 3, 5, 0, new byte[]{1}, null, null, null);

        verify(player).sendChunk(eq(3), eq(5), eq(0), any(byte[].class), eq(0));
        assertFalse(fixture.tasks(VERSION).contains(hash));
    }

    private static void process(Level level) throws Throwable {
        method("processChunkRequest", void.class).invokeExact(level);
    }

    private static MethodHandle method(String name, Class<?> result, Class<?>... parameters)
            throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(Level.class, MethodHandles.lookup())
                .findVirtual(Level.class, name, MethodType.methodType(result, parameters));
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static final class Fixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final LevelProvider provider = mock(LevelProvider.class);
        final Server server = mock(Server.class);
        final Object2ObjectMap<GameVersion, LongSet> taskBuckets = new Object2ObjectOpenHashMap<>();
        final Object2ObjectMap<GameVersion, ConcurrentMap<Long, Int2ObjectMap<Player>>> queueBuckets =
                new Object2ObjectOpenHashMap<>();
        final Object2ObjectMap<GameVersion, LongSet> pendingBuckets = new Object2ObjectOpenHashMap<>();
        final Object2ObjectMap<GameVersion, Long2LongOpenHashMap> startedTicks = new Object2ObjectOpenHashMap<>();
        final Object2ObjectMap<GameVersion, Long2LongOpenHashMap> epochBuckets = new Object2ObjectOpenHashMap<>();

        Fixture() throws Exception {
            set(level, "pendingChunkRequests", pendingBuckets);
            set(level, "chunkSendTasks", taskBuckets);
            set(level, "chunkSendQueues", queueBuckets);
            set(level, "chunkSendTaskStartTick", startedTicks);
            set(level, "chunkRequestEpochs", epochBuckets);
            set(level, "server", server);
            set(level, "provider", provider);
            set(level, "asyncChunkRequestFailedQueue", new java.util.concurrent.ConcurrentLinkedQueue<>());
            when(server.getTick()).thenReturn(100);
            when(server.getLogger()).thenReturn(mock(cn.nukkit.utils.MainLogger.class));
            doReturn(0).when(level).getDimension();
        }

        Player player(int loader, long hash, GameVersion version) throws Exception {
            Player player = mock(Player.class);
            Field used = Player.class.getDeclaredField("usedChunks");
            used.setAccessible(true);
            used.set(player, new HashMap<Long, Boolean>());
            player.usedChunks.put(hash, Boolean.FALSE);
            when(player.getLoaderId()).thenReturn(loader);
            when(player.getGameVersion()).thenReturn(version);
            when(player.isConnected()).thenReturn(true);
            when(player.getLevel()).thenReturn(level);
            return player;
        }

        LongSet tasks(GameVersion version) {
            return taskBuckets.computeIfAbsent(version, v -> new LongOpenHashSet());
        }

        ConcurrentMap<Long, Int2ObjectMap<Player>> queues(GameVersion version) {
            return queueBuckets.computeIfAbsent(version, v -> new java.util.concurrent.ConcurrentHashMap<>());
        }

        long epoch(long hash, GameVersion version) {
            Long2LongOpenHashMap epochs = epochBuckets.get(version);
            return epochs == null ? 0L : epochs.get(hash);
        }

        long started(long hash, GameVersion version) {
            Long2LongOpenHashMap starts = startedTicks.get(version);
            return starts == null ? 0L : starts.get(hash);
        }
    }
}
