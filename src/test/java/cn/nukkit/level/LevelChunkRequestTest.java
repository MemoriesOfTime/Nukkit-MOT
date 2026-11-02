package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LevelChunkRequestTest {

    /**
     * 区块请求代次栅栏：一次受理的结果只服务这一次受理的接收者。
     * <p>
     * Epoch fence: one acceptance's results serve only that acceptance's recipients.
     */
    @Nested
    class EpochFence {
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
            assertEquals(400L, fixture.started(hash));

            fixture.level.onAsyncChunkRequestFailed(VERSION, 3, 5, retired);
            fixture.level.drainFailedChunkRequests();

            assertTrue(fixture.tasks(VERSION).contains(hash), "A retired submission's failure must not drop the retry");
            assertEquals(retry, fixture.epoch(hash, VERSION));
            assertEquals(400L, fixture.started(hash), "A retired submission's failure must not cancel the retry timeout");
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
            assertEquals(100L, fixture.started(hash), "A live task keeps the shared timeout fence");
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

        private final class Fixture {
            final Level level = mock(Level.class, CALLS_REAL_METHODS);
            final LevelProvider provider = mock(LevelProvider.class);
            final Server server = mock(Server.class);
            final Object2ObjectMap<GameVersion, LongSet> taskBuckets = new Object2ObjectOpenHashMap<>();
            final Object2ObjectMap<GameVersion, ConcurrentMap<Long, Int2ObjectMap<Player>>> queueBuckets =
                    new Object2ObjectOpenHashMap<>();
            final Object2ObjectMap<GameVersion, LongSet> pendingBuckets = new Object2ObjectOpenHashMap<>();
            final Long2LongOpenHashMap startedTicks = new Long2LongOpenHashMap();
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

            long started(long hash) {
                return startedTicks.get(hash);
            }
        }
    }

    @Nested
    class RecipientFiltering {
        private Level level;
        private LevelProvider provider;
        private BaseFullChunk chunk;
        private Player player;
        private Server server;
        private final long hash = Level.chunkHash(3, 5);

        @BeforeEach
        void setUp() throws Exception {
            level = mock(Level.class, CALLS_REAL_METHODS);
            provider = mock(LevelProvider.class);
            server = mock(Server.class);
            when(server.getTick()).thenReturn(1);
            set(Level.class, level, "server", server);
            set(Level.class, level, "provider", provider);
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"}) {
                set(Level.class, level, name, new Object2ObjectOpenHashMap<>());
            }
            set(Level.class, level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(Level.class, level, "chunkRequestEpochs", new Object2ObjectOpenHashMap());
            chunk = mock(BaseFullChunk.class);
            doReturn(chunk).when(level).getChunk(3, 5);
            doReturn(chunk).when(level).getChunkIfLoaded(3, 5);
            doReturn(0).when(level).getDimension();
            player = mock(Player.class);
            set(Player.class, player, "usedChunks", new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<>());
            player.usedChunks.put(hash, Boolean.FALSE);
            when(player.getLoaderId()).thenReturn(1);
            when(player.getGameVersion()).thenReturn(GameVersion.V1_20_0);
            when(player.isConnected()).thenReturn(true);
            when(player.getLevel()).thenReturn(level);
        }

        @Test
        void removesOtherWorldRequestBeforeSnapshotOrDiskLookup() throws Exception {
            level.requestChunk(3, 5, player);
            when(player.getLevel()).thenReturn(mock(Level.class));
            process();
            verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
            verify(level, never()).getChunk(3, 5);
            verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        }

        @Test
        void removesDisconnectedRequestBeforeSnapshot() throws Exception {
            level.requestChunk(3, 5, player);
            when(player.isConnected()).thenReturn(false);
            process();
            verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
            verify(level, never()).getChunk(3, 5);
        }

        @Test
        void removesUnsubscribedRequestBeforeSnapshot() throws Exception {
            level.requestChunk(3, 5, player);
            player.usedChunks.clear();
            process();
            verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void lateCallbackNeverSendsSameCoordinatesIntoAnotherWorld(boolean cached) throws Exception {
            server.cacheChunks = cached;
            level.requestChunk(3, 5, player);
            process();
            when(player.getLevel()).thenReturn(mock(Level.class));
            try (var packets = mockStatic(Player.class)) {
                packets.when(() -> Player.getChunkCacheFromData(any(GameVersion.class), anyInt(),
                        anyInt(), anyInt(), any(byte[].class), anyInt())).thenReturn(new BatchPacket());
                level.chunkRequestCallback(GameVersion.V1_20_0, 0, 3, 5, 1, new byte[]{0});
            }
            verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
            verify(player, never()).sendChunk(anyInt(), anyInt(), anyInt(), any(byte[].class), anyInt());
        }

        @Test
        void stillServesRemainingRecipientWhenAnotherPlayerLeaves() throws Exception {
            Player departed = mock(Player.class);
            set(Player.class, departed, "usedChunks", new cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap<>());
            departed.usedChunks.put(hash, Boolean.FALSE);
            when(departed.getLoaderId()).thenReturn(2);
            when(departed.getGameVersion()).thenReturn(GameVersion.V1_20_0);
            when(departed.isConnected()).thenReturn(true);
            when(departed.getLevel()).thenReturn(mock(Level.class));
            level.requestChunk(3, 5, departed);
            level.requestChunk(3, 5, player);
            BatchPacket cached = new BatchPacket();
            when(chunk.getChunkPacket(GameVersion.V1_20_0)).thenReturn(cached);
            process();
            verify(player).sendChunk(3, 5, cached);
            verify(departed, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
            verify(provider, never()).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), anyInt(), anyInt(), anyLong());
        }

        /**
         * 超时重试换新代次，旧任务的回调随后可被判旧丢弃。
         * <p>
         * A timeout retry takes a fresh epoch; the old task's callback is then droppable.
         */
        @Test
        void timeoutRetryTakesANewIdAndResubmitsWithIt() throws Exception {
            when(server.getLogger()).thenReturn(mock(cn.nukkit.utils.MainLogger.class));
            level.requestChunk(3, 5, player);
            process();
            verify(provider).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(3), eq(5), eq(1L));

            // 200 ticks without a callback → forced retry with a new epoch
            when(server.getTick()).thenReturn(300);
            level.requestChunk(3, 5, player);
            process();

            verify(provider).requestChunkTask(any(it.unimi.dsi.fastutil.objects.ObjectSet.class), eq(3), eq(5), eq(2L));
            // the wedged older serialization (which carried 1 at submit) is now droppable
            Assertions.assertTrue(level.isSupersededChunkCallback(callbackWithSubmission(1L)));
        }

        /**
         * 只丢弃代次不符的回调；代次 0（第三方/插件直调）永不丢弃。
         * <p>
         * Only mismatched-epoch callbacks are dropped; epoch 0 (third parties) never is.
         */
        @Test
        void dropsCallbacksThatDoNotMatchTheCurrentSubmission() throws Exception {
            Object2ObjectMap<GameVersion, Long2LongOpenHashMap> epochs = get(Level.class, level, "chunkRequestEpochs");
            epochs.computeIfAbsent(GameVersion.V1_20_0, version -> new Long2LongOpenHashMap()).put(hash, 2L);

            Assertions.assertTrue(level.isSupersededChunkCallback(callbackWithSubmission(1L)));
            Assertions.assertFalse(level.isSupersededChunkCallback(callbackWithSubmission(2L)));
            Assertions.assertFalse(level.isSupersededChunkCallback(callbackWithSubmission(0L)));
        }

        private static cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData
        callbackWithSubmission(long generation) {
            return new cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData(
                    GameVersion.V1_20_0, 0L, 3, 5, 1, new byte[]{0}, null, null, null, generation);
        }

        private void process() throws Exception {
            var method = Level.class.getDeclaredMethod("processChunkRequest");
            method.setAccessible(true);
            method.invoke(level);
        }

        private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }

        private static <T> T get(Class<?> type, Object target, String name) throws Exception {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        }
    }
}
