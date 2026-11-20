package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.network.process.processor.common.RequestChunkRadiusProcessor;
import cn.nukkit.network.protocol.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerChunkSendTest {

    @Nested
    class ChunkPacketAdmission {
        private final long index = Level.chunkHash(3, 5);
        private Player player;
        private Level level;
        private DataPacket packet;

        @BeforeEach
        void initialize() throws Exception {
            player = mock(Player.class);
            level = mock(Level.class);
            packet = new LevelChunkPacket();
            player.level = level;
            player.connected = true;
            player.protocol = 0;
            Server server = mock(Server.class);
            when(server.isPrimaryThread()).thenReturn(true);
            UnloadRead.set(Entity.class, player, "server", server);
            field("usedChunks", new Long2ObjectOpenHashMap<Boolean>());
            field("loadQueue", new LongLinkedOpenHashSet());
            field("pendingChunkRetries", Player.newPendingRetryQueue());
            player.usedChunks.put(index, Boolean.FALSE);
            doCallRealMethod().when(player).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        }

        private void field(String name, Object value) throws Exception {
            Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(player, value);
        }

        private int sent() throws Exception {
            Field field = Player.class.getDeclaredField("chunksSent");
            field.setAccessible(true);
            return field.getInt(player);
        }

        @Test
        void usedChunksKeepsItsPublishedFieldDescriptor() throws Exception {
            assertEquals(java.util.Map.class, Player.class.getField("usedChunks").getType(),
                    "existing plugins link to usedChunks with the java.util.Map descriptor");
        }

        @Test
        void rejectedSubmissionKeepsTerrainPendingAndRetriesThroughExistingQueue() throws Exception {
            when(player.dataPacket(packet)).thenReturn(false);
            player.sendChunk(3, 5, packet);
            assertFalse(player.usedChunks.get(index));
            assertTrue(player.loadQueue.contains(index));
            assertEquals(0, sent());
        }

        @Test
        void rejectedSubmissionFromPreviousOrderDoesNotRetryInRebuiltQueue() throws Exception {
            doAnswer(call -> {
                field("chunkOrderEpoch", 1L);
                return false;
            }).when(player).dataPacket(packet);
            player.sendChunk(3, 5, packet);
            assertFalse(player.usedChunks.get(index));
            assertTrue(player.loadQueue.isEmpty());
            assertEquals(0, sent());
        }

        @Test
        void acceptedSubmissionMarksReadyOnlyAfterPacketAdmission() throws Exception {
            doAnswer(call -> {
                assertFalse(player.usedChunks.get(index), "readiness precedes transport admission");
                return true;
            }).when(player).dataPacket(packet);
            player.sendChunk(3, 5, packet);
            assertTrue(player.usedChunks.get(index));
            assertEquals(1, sent());
            assertTrue(player.loadQueue.isEmpty());
        }

        @Test
        void throwingSubmissionPreservesCauseAndSchedulesTrackedRetry() throws Exception {
            RuntimeException failure = new IllegalStateException("packet listener failure");
            doThrow(failure).when(player).dataPacket(packet);
            assertSame(failure, assertThrows(RuntimeException.class, () -> player.sendChunk(3, 5, packet)));
            assertFalse(player.usedChunks.get(index));
            assertTrue(player.loadQueue.contains(index));
            assertEquals(0, sent());
        }

        @Test
        void packetCallbackChangingWorldCannotMarkTheOldWorldReady() throws Exception {
            Level replacement = mock(Level.class);
            doAnswer(call -> {
                player.level = replacement;
                player.usedChunks.clear();
                return true;
            }).when(player).dataPacket(packet);
            player.sendChunk(3, 5, packet);
            assertFalse(player.usedChunks.containsKey(index));
            assertTrue(player.loadQueue.isEmpty());
            assertEquals(0, sent());
        }

        @Test
        void sameWorldCleanupAndResubscriptionInvalidateTheOldCallback() throws Exception {
            Method cleanup = Player.class.getDeclaredMethod("unloadChunks", boolean.class);
            cleanup.setAccessible(true);
            doAnswer(call -> {
                cleanup.invoke(player, false);
                player.usedChunks.put(index, Boolean.FALSE);
                return true;
            }).when(player).dataPacket(packet);
            player.sendChunk(3, 5, packet);
            assertFalse(player.usedChunks.get(index));
            assertEquals(0, sent());
        }

        @Test
        void rejectedUntrackedOrDisconnectedPacketsDoNotCreateRetries() throws Exception {
            player.usedChunks.clear();
            player.sendChunk(3, 5, packet);
            assertFalse(player.usedChunks.containsKey(index));
            assertTrue(player.loadQueue.isEmpty());
            player.connected = false;
            player.usedChunks.put(index, Boolean.FALSE);
            player.sendChunk(3, 5, packet);
            assertFalse(player.usedChunks.get(index));
            assertTrue(player.loadQueue.isEmpty());
            assertEquals(0, sent());
        }
        @Test
        void rejectedOuterSubmissionPreservesAcceptedReentrantReplacement() throws Exception {
            DataPacket replacement = new LevelChunkPacket();
            when(player.dataPacket(replacement)).thenReturn(true);
            doAnswer(call -> {
                player.sendChunk(3, 5, replacement);
                return false;
            }).when(player).dataPacket(packet);
            player.sendChunk(3, 5, packet);
            assertTrue(player.usedChunks.get(index));
            assertTrue(player.loadQueue.isEmpty());
            assertEquals(1, sent());
        }

        @Test
        void throwingOuterSubmissionPreservesAcceptedReentrantReplacement() throws Exception {
            DataPacket replacement = new LevelChunkPacket();
            RuntimeException failure = new IllegalStateException("listener failure after replacement");
            when(player.dataPacket(replacement)).thenReturn(true);
            doAnswer(call -> {
                player.sendChunk(3, 5, replacement);
                throw failure;
            }).when(player).dataPacket(packet);
            assertSame(failure, assertThrows(RuntimeException.class, () -> player.sendChunk(3, 5, packet)));
            assertTrue(player.usedChunks.get(index));
            assertTrue(player.loadQueue.isEmpty());
            assertEquals(1, sent());
        }

    }

    @Nested
    class RadiusReorder {
        @Test
        void clientShrinkSchedulesReorderForNextPlayerNetworkPass() {
            Player player = mock(Player.class);
            player.chunkRadius = 12;
            player.nextChunkOrderRun = 20;
            when(player.getMaximumViewDistance()).thenReturn(12);
            RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
            packet.radius = 3;
            RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
            assertEquals(3, player.chunkRadius);
            assertEquals(0, player.nextChunkOrderRun);
        }

        @Test
        void repeatedSameRadiusDoesNotTriggerExtraReorders() {
            Player player = mock(Player.class);
            player.chunkRadius = 12;
            player.nextChunkOrderRun = 17;
            when(player.getMaximumViewDistance()).thenReturn(12);
            RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
            packet.radius = 12;
            RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
            assertEquals(17, player.nextChunkOrderRun);
        }

        @Test
        void clientGrowthSchedulesReorder() {
            Player player = mock(Player.class);
            player.chunkRadius = 3;
            player.nextChunkOrderRun = 17;
            when(player.getMaximumViewDistance()).thenReturn(12);
            RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
            packet.radius = 8;
            RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
            assertEquals(8, player.chunkRadius);
            assertEquals(0, player.nextChunkOrderRun);
        }

        @Test
        void clampedRequestWithoutEffectiveChangeKeepsTheExistingOrder() {
            Player player = mock(Player.class);
            player.chunkRadius = 3;
            player.nextChunkOrderRun = 17;
            when(player.getMaximumViewDistance()).thenReturn(12);
            RequestChunkRadiusPacket packet = new RequestChunkRadiusPacket();
            packet.radius = 1;
            RequestChunkRadiusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
            assertEquals(3, player.chunkRadius);
            assertEquals(17, player.nextChunkOrderRun);
        }

        @Test
        void hostRadiusChangeAlsoInvalidatesQueue() {
            Player player = mock(Player.class);
            player.chunkRadius = 12;
            player.nextChunkOrderRun = 20;
            doCallRealMethod().when(player).setViewDistance(anyInt());
            player.setViewDistance(4);
            assertEquals(0, player.nextChunkOrderRun);
            player.nextChunkOrderRun = 17;
            player.setViewDistance(4);
            assertEquals(17, player.nextChunkOrderRun);
        }
    }

    @Nested
    class UnloadRead {
        private Player player;
        private Level level;
        private final long hash = Level.chunkHash(3, 5);

        @BeforeEach
        void setUp() throws Exception {
            player = mock(Player.class);
            level = loadedOnlyLevel();
            player.level = level;
            set(Player.class, player, "loaderId", 7);
            when(player.getLoaderId()).thenReturn(7);
            set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
            set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
            set(Player.class, player, "pendingChunkRetries", Player.newPendingRetryQueue());
            player.usedChunks.put(hash, Boolean.TRUE);
            player.loadQueue.add(hash);
            doCallRealMethod().when(player).unloadChunk(anyInt(), anyInt(), nullable(Level.class));
        }

        private Level loadedOnlyLevel() {
            Level result = mock(Level.class);
            when(result.getChunkEntities(anyInt(), anyInt())).thenCallRealMethod();
            when(result.getChunkEntities(anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            return result;
        }

        @Test
        void abandoningColdChunkDoesNotReadStorageAndStillClearsTracking() {
            player.unloadChunk(3, 5, null);
            verify(level, never()).getChunk(anyInt(), anyInt());
            verify(level).getChunkIfLoaded(3, 5);
            verify(level).unregisterChunkLoader(player, 3, 5);
            assertFalse(player.usedChunks.containsKey(hash));
            assertFalse(player.loadQueue.contains(hash));
        }

        @Test
        void loadedChunkDespawnsBeforeUnregisterAndSkipsPlayerItself() {
            Entity entity = mock(Entity.class);
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(level.getChunkIfLoaded(3, 5)).thenReturn(chunk);
            when(level.getChunk(3, 5)).thenReturn(chunk);
            when(chunk.getEntities()).thenReturn(Map.of(1L, entity, 2L, player));
            doAnswer(invocation -> {
                assertTrue(player.usedChunks.containsKey(hash));
                return null;
            }).when(entity).despawnFrom(player);
            doAnswer(invocation -> {
                assertFalse(player.usedChunks.containsKey(hash));
                assertTrue(player.loadQueue.contains(hash));
                return null;
            }).when(level).unregisterChunkLoader(player, 3, 5);
            player.unloadChunk(3, 5, null);
            var order = inOrder(entity, level);
            order.verify(entity).despawnFrom(player);
            order.verify(level).unregisterChunkLoader(player, 3, 5);
            verify(player, never()).despawnFrom(any());
            verify(level, never()).getChunk(anyInt(), anyInt());
        }

        @Test
        void explicitOldLevelOwnsLookupAndUnregister() {
            Level oldLevel = loadedOnlyLevel();
            player.unloadChunk(3, 5, oldLevel);
            verify(oldLevel).getChunkIfLoaded(3, 5);
            verify(oldLevel).unregisterChunkLoader(player, 3, 5);
            verify(oldLevel, never()).getChunk(anyInt(), anyInt());
            verifyNoInteractions(level);
        }

        @Test
        void worldSwitchAndDisconnectNeverReadColdChunks() throws Exception {
            for (boolean online : new boolean[]{true, false}) {
                player.usedChunks.put(hash, Boolean.TRUE);
                player.loadQueue.add(hash);
                unloadAll(online);
                assertTrue(player.usedChunks.isEmpty());
                assertTrue(player.loadQueue.isEmpty());
            }
            verify(level, never()).getChunk(anyInt(), anyInt());
            verify(level, times(2)).unregisterChunkLoader(player, 3, 5);
        }

        @Test
        void onlineCleanupKeepsEntityPacketAndOfflineCleanupSendsNothing() throws Exception {
            Entity entity = mock(Entity.class);
            set(Entity.class, entity, "hasSpawned", new ConcurrentHashMap<Integer, Player>());
            set(Entity.class, entity, "id", 123L);
            doCallRealMethod().when(entity).despawnFrom(player);
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(level.getChunkIfLoaded(3, 5)).thenReturn(chunk);
            when(level.getChunk(3, 5)).thenReturn(chunk);
            when(chunk.getEntities()).thenReturn(Map.of(123L, entity));
            int protocols = 0;
            for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS) {
                if (protocol < ProtocolInfo.v1_20_0 || protocol > ProtocolInfo.v1_26_50) continue;
                player.protocol = protocol;
                player.usedChunks.put(hash, Boolean.TRUE);
                entity.hasSpawned.put(7, player);
                unloadAll(true);
                assertFalse(entity.hasSpawned.containsKey(7));
                protocols++;
            }
            verify(player, times(protocols)).dataPacket(argThat(packet ->
                    packet instanceof RemoveEntityPacket remove && remove.eid == 123L));
            clearInvocations(player, entity, level);
            player.usedChunks.put(hash, Boolean.TRUE);
            entity.hasSpawned.put(7, player);
            unloadAll(false);
            assertFalse(entity.hasSpawned.containsKey(7));
            verify(player, never()).dataPacket(any());
            verify(entity, never()).despawnFrom(any());
            verify(level, never()).getChunk(anyInt(), anyInt());
        }

        private void unloadAll(boolean online) throws Exception {
            var method = Player.class.getDeclaredMethod("unloadChunks", boolean.class);
            method.setAccessible(true);
            method.invoke(player, online);
        }

        private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    @Nested
    class ChunkRetryQueue {
        @Test
        void asyncProducersOnlyStageRetriesUntilMainThreadDrain() throws Exception {
            RetryFixture f = new RetryFixture();
            int chunkCount = 32;
            for (int i = 0; i < chunkCount; i++) {
                f.player.usedChunks.put(Level.chunkHash(i, i), Boolean.FALSE);
            }

            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<?>> producers = new ArrayList<>();
                for (int thread = 0; thread < 8; thread++) {
                    producers.add(pool.submit(() -> {
                        start.await();
                        for (int i = 0; i < 500; i++) {
                            long index = Level.chunkHash(i % chunkCount, i % chunkCount);
                            f.handle.requestChunkRetry(index);
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> producer : producers) {
                    producer.get(10, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertTrue(f.player.loadQueue.isEmpty(), "async producers must not touch the ordered queue");
            f.runOnMainThread();
            f.drain();
            assertEquals(chunkCount, f.player.loadQueue.size(), "main-thread drain must deduplicate retries");
        }

        @Test
        void mainThreadRetriesApplyImmediatelyAndSnapshotIsDetached() throws Exception {
            RetryFixture f = new RetryFixture();
            f.runOnMainThread();
            long first = Level.chunkHash(1, 1);
            long second = Level.chunkHash(2, 2);
            f.player.usedChunks.put(first, Boolean.FALSE);
            f.player.usedChunks.put(second, Boolean.FALSE);

            f.handle.requestChunkRetry(first);
            f.handle.requestChunkRetry(second);
            f.handle.requestChunkRetry(first);

            assertEquals(List.of(first, second), new ArrayList<>(f.player.loadQueue));
            LongLinkedOpenHashSet snapshot = f.handle.getLoadQueueSnapshot();
            snapshot.clear();
            assertEquals(2, f.player.loadQueue.size());
        }

        @Test
        void staleRetriesAreDroppedAfterLifecycleChanges() throws Exception {
            RetryFixture f = new RetryFixture();
            long index = Level.chunkHash(3, 5);
            f.player.usedChunks.put(index, Boolean.FALSE);

            f.handle.requestChunkRetry(index);
            UnloadRead.set(Player.class, f.player, "chunkOrderEpoch", 1L);
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty(), "old order retry");

            f.handle.requestChunkRetry(index);
            UnloadRead.set(Player.class, f.player, "chunkDeliveryEpoch", 1L);
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty(), "old delivery retry");

            f.handle.requestChunkRetry(index);
            f.player.level = mock(Level.class);
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty(), "old world retry");

            f.handle.requestChunkRetry(index);
            f.player.connected = false;
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty(), "disconnected retry");
        }

        @Test
        void deliveredAndUntrackedChunksAreNotRetried() throws Exception {
            RetryFixture f = new RetryFixture();
            long index = Level.chunkHash(3, 5);
            f.player.usedChunks.put(index, Boolean.TRUE);
            f.handle.requestChunkRetry(index);
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty());

            f.player.usedChunks.remove(index);
            f.handle.requestChunkRetry(index);
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty());
        }

        @SuppressWarnings("deprecation")
        @Test
        void rawQueueAccessIsRejectedOffTheMainThread() throws Exception {
            RetryFixture f = new RetryFixture();
            assertThrows(IllegalStateException.class, () -> f.handle.getLoadQueue());
            assertThrows(IllegalStateException.class, () -> f.handle.getLoadQueueSnapshot());
        }

        @Test
        void asyncSendChunkRejectionIsStagedNotAppliedToLoadQueue() throws Exception {
            RetryFixture f = new RetryFixture();
            long index = Level.chunkHash(3, 5);
            f.player.usedChunks.put(index, Boolean.FALSE);
            doReturn(false).when(f.player).dataPacket(any(DataPacket.class));

            f.player.sendChunk(3, 5, new LevelChunkPacket());

            assertTrue(f.player.loadQueue.isEmpty(), "async sendChunk must not touch the ordered queue");
            assertEquals(1, f.pendingRetries.size(), "rejected submission is staged for the main thread");
            f.runOnMainThread();
            f.drain();
            assertTrue(f.player.loadQueue.contains(index));
        }

        @Test
        void unloadDropsStagedRetries() throws Exception {
            RetryFixture f = new RetryFixture();
            f.handle.requestChunkRetry(Level.chunkHash(3, 5));
            assertEquals(1, f.pendingRetries.size());

            f.player.preSwitchLevel();

            assertTrue(f.pendingRetries.isEmpty(), "world switch must drop staged retries");
            f.runOnMainThread();
            f.drain();
            assertTrue(f.player.loadQueue.isEmpty());
        }

        @Test
        void retryQueueIsBoundedAndDropsOverflow() throws Exception {
            RetryFixture f = new RetryFixture();
            int overflow = 16;
            for (int i = 0; i < Player.MAX_PENDING_CHUNK_RETRIES + overflow; i++) {
                f.handle.requestChunkRetry(Level.chunkHash(i, i));
            }

            assertEquals(Player.MAX_PENDING_CHUNK_RETRIES, f.pendingRetries.size(),
                    "over-cap retries are dropped instead of growing the queue");
            f.runOnMainThread();
            f.drain();
            assertTrue(f.pendingRetries.isEmpty());
            assertTrue(f.player.loadQueue.isEmpty(), "dropped retries must not resurrect");
        }

        @Test
        void checkNetworkDrainsStagedRetriesBeforeTheOnlineGate() throws Exception {
            RetryFixture f = new RetryFixture();
            long index = Level.chunkHash(3, 5);
            f.player.usedChunks.put(index, Boolean.FALSE);
            f.handle.requestChunkRetry(index);
            assertEquals(1, f.pendingRetries.size());

            // connected but not logged in: checkNetwork returns at the online gate, so only the drain runs
            f.runOnMainThread();
            f.player.checkNetwork();

            assertTrue(f.pendingRetries.isEmpty(), "checkNetwork is the drain call site");
            assertTrue(f.player.loadQueue.contains(index), "a still-valid retry is applied by the drain");
        }
    }

    private static final class RetryFixture {
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final Level level = mock(Level.class);
        final PlayerHandle handle = new PlayerHandle(player);
        final Queue<?> pendingRetries = Player.newPendingRetryQueue();

        RetryFixture() throws Exception {
            UnloadRead.set(Player.class, player, "usedChunks", new ConcurrentHashMap<Long, Boolean>());
            UnloadRead.set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
            UnloadRead.set(Player.class, player, "pendingChunkRetries", pendingRetries);
            UnloadRead.set(Entity.class, player, "server", server);
            player.level = level;
            player.connected = true;
            when(server.isPrimaryThread()).thenReturn(false);
        }

        void runOnMainThread() {
            when(server.isPrimaryThread()).thenReturn(true);
        }

        void drain() {
            player.drainChunkRetries();
        }
    }
}
