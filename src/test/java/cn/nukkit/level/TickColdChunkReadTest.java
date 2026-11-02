package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Executes the native tick paths; an unexpected loading provider accessor fails immediately. */
class TickColdChunkReadTest {
    private static final int CHUNK_X = 2;
    private static final int CHUNK_Z = 3;
    private static final long HASH = Level.chunkHash(CHUNK_X, CHUNK_Z);
    private static final Vector3 FIRST = new Vector3(35, 65, 50);
    private static final Vector3 SECOND = new Vector3(35, 66, 50);

    @BeforeAll
    static void bootstrap() {
        MockServer.init();
    }

    private static final class Fixture {
        final Level level;
        final Server server = mock(Server.class);
        final LevelProvider provider = mock(LevelProvider.class);
        final Map<Long, BaseFullChunk> loaded = new ConcurrentHashMap<>();
        final AtomicInteger diskReads = new AtomicInteger();
        final Long2ObjectOpenHashMap<SoftReference<Map<Integer, Object>>> changed = new Long2ObjectOpenHashMap<>();
        final Int2ObjectOpenHashMap<Object> whole = new Int2ObjectOpenHashMap<>();
        final Int2ObjectOpenHashMap<ChunkLoader> loaders = new Int2ObjectOpenHashMap<>();
        final Long2IntMap tickList = new Long2IntOpenHashMap();
        final Long2ObjectNonBlockingMap<Player> players = new Long2ObjectNonBlockingMap<>();
        final List<UpdateBlockPacket> packets = new ArrayList<>();
        final Player player = mock(Player.class);

        Fixture() throws Exception { this(mock(Level.class, CALLS_REAL_METHODS)); }
        Fixture(Level actual) throws Exception {
            this.level = actual;
            set(level, "server", server);
            set(level, "provider", provider);
            set(level, "loaders", loaders);
            set(level, "chunkTickList", tickList);
            set(level, "players", players);
            set(level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, "changedBlocks", changed);
            set(level, "changeBlocksFullMap", whole);
            set(level, "dimensionData", DimensionData.LEGACY_DIMENSION);
            set(level, "lightQueue", new ConcurrentHashMap<>());
            set(level, "updateQueue", new BlockUpdateScheduler(level, 0));
            set(level, "normalUpdateQueue", new ConcurrentLinkedQueue<>());
            set(level, "normalUpdateQueueSize", new AtomicInteger());
            set(level, "updateBlockEntities", new ConcurrentLinkedQueue<>());
            set(level, "chunkPackets", new ConcurrentHashMap<>());
            set(level, "chunkSendQueues", new Object2ObjectOpenHashMap<>());
            set(level, "pendingChunkRequests", new Object2ObjectOpenHashMap<>());
            set(level, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
            set(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            level.gameRules.refresh();
            when(provider.getLoadedChunk(anyLong())).thenAnswer(call -> loaded.get(call.<Long>getArgument(0)));
            when(provider.getLoadedChunk(anyInt(), anyInt())).thenAnswer(call -> loaded.get(Level.chunkHash(call.getArgument(0), call.getArgument(1))));
            when(provider.isChunkLoaded(anyLong())).thenAnswer(call -> loaded.containsKey(call.<Long>getArgument(0)));
            when(provider.getChunk(anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
                diskReads.incrementAndGet();
                throw new AssertionError("Tick attempted loading provider.getChunk(" + call.getArgument(0) + ", " + call.getArgument(1) + ")");
            });
            when(provider.getChunk(anyInt(), anyInt())).thenAnswer(call -> {
                diskReads.incrementAndGet();
                throw new AssertionError("Tick attempted loading provider.getChunk");
            });
            when(player.getGameVersion()).thenReturn(GameVersion.V1_26_50);
            doAnswer(call -> {
                DataPacket packet = call.getArgument(0);
                assertTrue(packet instanceof UpdateBlockPacket, "changed block path emits only UpdateBlockPacket");
                packets.add((UpdateBlockPacket) packet);
                return true;
            }).when(player).dataPacket(any(DataPacket.class));
            players.put(1, player);
            doReturn(Map.of(1, player)).when(level).getChunkPlayers(CHUNK_X, CHUNK_Z);
        }

        BaseFullChunk loadCenter() {
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(chunk.getX()).thenReturn(CHUNK_X);
            when(chunk.getZ()).thenReturn(CHUNK_Z);
            when(chunk.getEntities()).thenReturn(Map.of());
            when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> call.<Integer>getArgument(3) == 0 ? Block.STONE : Block.AIR);
            when(chunk.getFullBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> call.<Integer>getArgument(3) == 0 ? Block.STONE << Block.DATA_BITS : 0);
            when(chunk.getBlockRuntimeId(any(GameVersion.class), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                    GlobalBlockPalette.getOrCreateRuntimeId(call.getArgument(0), call.<Integer>getArgument(4) == 0 ? Block.STONE : Block.AIR, 0));
            loaded.put(HASH, chunk);
            return chunk;
        }

        void addLoaderAndNeighbors() throws Exception {
            set(level, "chunksPerTicks", 1);
            set(level, "chunkTickRadius", 1);
            ChunkLoader loader = mock(ChunkLoader.class);
            when(loader.getX()).thenReturn(CHUNK_X * 16.0);
            when(loader.getZ()).thenReturn(CHUNK_Z * 16.0);
            loaders.put(1, loader);
            loaded.put(HASH + 1, mock(BaseFullChunk.class));
            loaded.put(HASH - 1, mock(BaseFullChunk.class));
            loaded.put(HASH + (1L << 32), mock(BaseFullChunk.class));
            loaded.put(HASH - (1L << 32), mock(BaseFullChunk.class));
        }

        void queueWholeChange() {
            changed.put(HASH, new SoftReference<>(whole));
        }

        void queueSmallChanges(Vector3... positions) {
            Map<Integer, Object> blocks = new LinkedHashMap<>();
            for (Vector3 position : positions) {
                blocks.put(Level.localBlockHash(position.x, position.y, position.z, DimensionData.LEGACY_DIMENSION), new Object());
            }
            changed.put(HASH, new SoftReference<>(blocks));
        }

        void tick() {
            level.doTick(1);
            assertEquals(0, diskReads.get(), "tick must not perform provider disk reads");
            assertTrue(changed.isEmpty(), "outgoing notification bucket is consumed");
        }

        void assertPackets(GameVersion version, int blockCount) {
            assertEquals(blockCount * 2, packets.size(), "one packet per changed block per layer");
            for (UpdateBlockPacket packet : packets) {
                assertEquals((int) FIRST.x, packet.x);
                assertEquals((int) FIRST.z, packet.z);
                assertTrue(packet.y == (int) FIRST.y || blockCount == 2 && packet.y == (int) SECOND.y);
                assertEquals(UpdateBlockPacket.FLAG_ALL, packet.flags);
                assertTrue(packet.dataLayer == 0 || packet.dataLayer == 1);
                int id = packet.dataLayer == 0 ? Block.STONE : Block.AIR;
                assertEquals(GlobalBlockPalette.getOrCreateRuntimeId(version, id, 0), packet.blockRuntimeId);
            }
            for (int layer = 0; layer <= 1; layer++) {
                final int expectedLayer = layer;
                assertEquals(blockCount, packets.stream().filter(packet -> packet.dataLayer == expectedLayer).count());
            }
        }

        void assertEncodedPackets(GameVersion version, int blockCount) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < packets.size(); i++) {
                int layer = i / blockCount;
                Vector3 position = i % blockCount == 0 ? FIRST : SECOND;
                UpdateBlockPacket expected = new UpdateBlockPacket();
                expected.x = (int) position.x;
                expected.y = (int) position.y;
                expected.z = (int) position.z;
                expected.flags = UpdateBlockPacket.FLAG_ALL;
                expected.dataLayer = layer;
                expected.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(version, layer == 0 ? Block.STONE : Block.AIR, 0);
                expected.protocol = version.getProtocol();
                expected.gameVersion = version;
                expected.encode();
                UpdateBlockPacket actual = (UpdateBlockPacket) packets.get(i).clone();
                actual.protocol = version.getProtocol();
                actual.gameVersion = version;
                actual.encode();
                assertArrayEquals(expected.getBuffer(), actual.getBuffer(), "native encoded update " + version + " packet " + i);
                digest.update(actual.getBuffer());
            }
            System.out.println("TICK_CHANGE_BYTES version=" + version + " packets=" + packets.size()
                    + " sha256=" + HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static void tickChunks(Level level) throws Throwable {
        Method method = Level.class.getDeclaredMethod("tickChunks");
        method.setAccessible(true);
        try {
            method.invoke(level);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    @Test
    void staleLoaderWithLoadedNeighborsDoesNotReadColdCenter() throws Throwable {
        Fixture fixture = new Fixture();
        fixture.addLoaderAndNeighbors();

        tickChunks(fixture.level);

        assertEquals(0, fixture.diskReads.get());
        assertFalse(fixture.tickList.containsKey(HASH), "cold center leaves ticking candidates");
    }

    @Test
    void loadedCenterStillSchedulesEveryEntity() throws Throwable {
        Fixture fixture = new Fixture();
        fixture.addLoaderAndNeighbors();
        BaseFullChunk chunk = fixture.loadCenter();
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        when(chunk.getEntities()).thenReturn(Map.of(1L, first, 2L, second));

        tickChunks(fixture.level);

        verify(first, times(1)).scheduleUpdate();
        verify(second, times(1)).scheduleUpdate();
        assertEquals(0, fixture.diskReads.get());
        assertTrue(fixture.tickList.containsKey(HASH));
    }

    @Test
    void wholeChunkNotificationAfterUnloadDoesNotReloadOrNotify() throws Exception {
        Fixture fixture = new Fixture();
        BaseFullChunk chunk = fixture.loadCenter();
        fixture.queueWholeChange();
        fixture.loaded.remove(HASH);

        fixture.tick();

        verify(fixture.player, never()).onChunkChanged(any());
        verify(chunk, never()).replayDeferredBlockUpdates();
        assertTrue(fixture.packets.isEmpty());
    }

    @Test
    void loadedWholeChunkNotificationRetainsOriginalChunk() throws Exception {
        Fixture fixture = new Fixture();
        BaseFullChunk chunk = fixture.loadCenter();
        fixture.queueWholeChange();

        fixture.tick();

        verify(fixture.player, times(1)).onChunkChanged(chunk);
        assertTrue(fixture.packets.isEmpty());
    }

    @Test
    void smallNotificationAfterUnloadDoesNotReadOrSendPhantomAir() throws Exception {
        Fixture fixture = new Fixture();
        BaseFullChunk chunk = fixture.loadCenter();
        fixture.queueSmallChanges(FIRST);
        fixture.loaded.remove(HASH);

        fixture.tick();

        assertTrue(fixture.packets.isEmpty(), "cold chunks must not be encoded as air");
        verify(fixture.player, never()).onChunkChanged(any());
        verify(chunk, never()).replayDeferredBlockUpdates();
    }

    @Test
    void loadedSmallChangesPreserveBothLayerPacketsAcrossClientWindow() throws Exception {
        int checkedVersions = 0;
        for (GameVersion version : GameVersion.values()) {
            if (version.isNetEase() || version.getProtocol() < GameVersion.V1_20_0.getProtocol()
                    || version.getProtocol() > GameVersion.V1_26_50.getProtocol()) continue;
            Fixture fixture = new Fixture();
            when(fixture.player.getGameVersion()).thenReturn(version);
            fixture.loadCenter();
            fixture.queueSmallChanges(FIRST, SECOND);

            fixture.tick();

            fixture.assertPackets(version, 2);
            fixture.assertEncodedPackets(version, 2);
            checkedVersions++;
        }
        assertTrue(checkedVersions > 20, "regression checks the full supported version window");
    }

    @Test
    void unloadingProviderCacheDuringFirstPacketUsesCapturedChunkForRemainingLayers() throws Exception {
        Fixture fixture = new Fixture();
        fixture.loadCenter();
        fixture.queueSmallChanges(FIRST, SECOND);
        doAnswer(call -> {
            fixture.packets.add((UpdateBlockPacket) call.getArgument(0));
            if (fixture.packets.size() == 1) fixture.loaded.remove(HASH);
            return true;
        }).when(fixture.player).dataPacket(any(DataPacket.class));

        fixture.tick();

        assertFalse(fixture.loaded.containsKey(HASH), "first packet really detached the cache owner");
        fixture.assertPackets(GameVersion.V1_26_50, 2);
    }

    @Test
    void healthyTickCenterReplaysDeferredUpdatesOnPrimaryThread() throws Throwable {
        Fixture fixture = new Fixture();
        when(fixture.server.isPrimaryThread()).thenReturn(true);
        fixture.addLoaderAndNeighbors();
        BaseFullChunk chunk = fixture.loadCenter();

        tickChunks(fixture.level);

        verify(chunk, times(1)).replayDeferredBlockUpdates();
        assertEquals(0, fixture.diskReads.get());
    }

    @Test
    void healthyWholeChunkNotificationReplaysDeferredUpdatesOnPrimaryThread() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.server.isPrimaryThread()).thenReturn(true);
        BaseFullChunk chunk = fixture.loadCenter();
        fixture.queueWholeChange();

        fixture.tick();

        verify(chunk, times(1)).replayDeferredBlockUpdates();
        verify(fixture.player, times(1)).onChunkChanged(chunk);
    }

    @Test
    void nonPrimaryCachedOwnerDoesNotReplayDeferredUpdates() throws Throwable {
        Fixture fixture = new Fixture();
        fixture.addLoaderAndNeighbors();
        BaseFullChunk chunk = fixture.loadCenter();

        tickChunks(fixture.level);

        verify(chunk, never()).replayDeferredBlockUpdates();
        assertEquals(0, fixture.diskReads.get());
    }

    @Test
    void publicFakeBlockUpdatesRemainAvailableWhenOwnerIsCold() throws Exception {
        Fixture fixture = new Fixture();
        Block fake = Block.get(Block.STONE, 0, fixture.level, (int) FIRST.x, (int) FIRST.y, (int) FIRST.z);

        fixture.level.sendBlocks(new Player[]{fixture.player}, new Block[]{fake}, UpdateBlockPacket.FLAG_ALL);

        assertEquals(0, fixture.diskReads.get());
        assertEquals(2, fixture.packets.size());
        for (int layer = 0; layer <= 1; layer++) {
            UpdateBlockPacket packet = fixture.packets.get(layer);
            assertEquals(layer, packet.dataLayer);
            assertEquals(UpdateBlockPacket.FLAG_ALL, packet.flags);
            assertEquals(GlobalBlockPalette.getOrCreateRuntimeId(GameVersion.V1_26_50, Block.STONE, 0), packet.blockRuntimeId);
        }
    }

    @Test
    void replacementOwnerDuringFirstPacketSuppliesSubsequentUpdatesWithoutDiskRead() throws Exception {
        Fixture fixture = new Fixture();
        fixture.loadCenter();
        fixture.queueSmallChanges(FIRST, SECOND);
        BaseFullChunk replacement = mock(BaseFullChunk.class);
        when(replacement.getX()).thenReturn(CHUNK_X);
        when(replacement.getZ()).thenReturn(CHUNK_Z);
        when(replacement.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                call.<Integer>getArgument(3) == 0 ? Block.GOLD_BLOCK : Block.WATER);
        when(replacement.getFullBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (call.<Integer>getArgument(3) == 0 ? Block.GOLD_BLOCK : Block.WATER) << Block.DATA_BITS);
        when(replacement.getBlockRuntimeId(any(GameVersion.class), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                GlobalBlockPalette.getOrCreateRuntimeId(call.<GameVersion>getArgument(0),
                        call.<Integer>getArgument(4) == 0 ? Block.GOLD_BLOCK : Block.WATER, 0));
        doAnswer(call -> {
            fixture.packets.add((UpdateBlockPacket) call.getArgument(0));
            if (fixture.packets.size() == 1) fixture.loaded.put(HASH, replacement);
            return true;
        }).when(fixture.player).dataPacket(any(DataPacket.class));

        fixture.tick();

        assertEquals(4, fixture.packets.size());
        assertTrue(fixture.loaded.get(HASH) == replacement, "first packet installed the replacement owner");
        for (int i = 0; i < fixture.packets.size(); i++) {
            UpdateBlockPacket packet = fixture.packets.get(i);
            int layer = i / 2;
            int id = i == 0 ? Block.STONE : layer == 0 ? Block.GOLD_BLOCK : Block.WATER;
            assertEquals(layer, packet.dataLayer);
            assertEquals(UpdateBlockPacket.FLAG_ALL, packet.flags);
            assertEquals(GlobalBlockPalette.getOrCreateRuntimeId(GameVersion.V1_26_50, id, 0), packet.blockRuntimeId);
        }
    }

    @Test
    void replacementUnloadedDuringSecondPacketRemainsTheLastOwnerForRemainingLayers() throws Exception {
        Fixture fixture = new Fixture();
        fixture.loadCenter();
        fixture.queueSmallChanges(FIRST, SECOND);
        BaseFullChunk replacement = mock(BaseFullChunk.class);
        when(replacement.getX()).thenReturn(CHUNK_X);
        when(replacement.getZ()).thenReturn(CHUNK_Z);
        when(replacement.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                call.<Integer>getArgument(3) == 0 ? Block.GOLD_BLOCK : Block.WATER);
        when(replacement.getFullBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (call.<Integer>getArgument(3) == 0 ? Block.GOLD_BLOCK : Block.WATER) << Block.DATA_BITS);
        when(replacement.getBlockRuntimeId(any(GameVersion.class), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                GlobalBlockPalette.getOrCreateRuntimeId(call.<GameVersion>getArgument(0),
                        call.<Integer>getArgument(4) == 0 ? Block.GOLD_BLOCK : Block.WATER, 0));
        doAnswer(call -> {
            fixture.packets.add((UpdateBlockPacket) call.getArgument(0));
            if (fixture.packets.size() == 1) fixture.loaded.put(HASH, replacement);
            if (fixture.packets.size() == 2) fixture.loaded.remove(HASH);
            return true;
        }).when(fixture.player).dataPacket(any(DataPacket.class));

        fixture.tick();

        assertEquals(4, fixture.packets.size());
        assertFalse(fixture.loaded.containsKey(HASH), "second packet detached the replacement owner");
        for (int i = 0; i < fixture.packets.size(); i++) {
            UpdateBlockPacket packet = fixture.packets.get(i);
            int layer = i / 2;
            int id = i == 0 ? Block.STONE : layer == 0 ? Block.GOLD_BLOCK : Block.WATER;
            assertEquals(layer, packet.dataLayer);
            assertEquals(UpdateBlockPacket.FLAG_ALL, packet.flags);
            assertEquals(GlobalBlockPalette.getOrCreateRuntimeId(GameVersion.V1_26_50, id, 0), packet.blockRuntimeId);
        }
    }

    private static class ThreeArgumentHookLevel extends Level {
        int sends;
        ThreeArgumentHookLevel() { super(null, null, null, null); }
        @Override public void sendBlocks(Player[] targets, Vector3[] blocks, int flags) { sends++; }
    }
    private static class LayerHookLevel extends Level {
        final List<Integer> layers = new ArrayList<>();
        LayerHookLevel() { super(null, null, null, null); }
        @Override public void sendBlocks(Player[] targets, Vector3[] blocks, int flags, int layer,
                boolean optimize) { layers.add(layer); }
    }
    @Test void changedBlocksRetainThreeArgumentVirtualSendHook() throws Exception {
        ThreeArgumentHookLevel custom = mock(ThreeArgumentHookLevel.class, CALLS_REAL_METHODS);
        Fixture f = new Fixture(custom); f.loadCenter(); f.queueSmallChanges(FIRST); f.tick();
        assertEquals(1, custom.sends);
    }
    @Test void changedBlocksRetainVirtualPerLayerSendHooks() throws Exception {
        LayerHookLevel custom = mock(LayerHookLevel.class, CALLS_REAL_METHODS);
        // Constructor-free mock: initialize the callback-owned list explicitly.
        Field storage = LayerHookLevel.class.getDeclaredField("layers"); storage.setAccessible(true);
        storage.set(custom, new ArrayList<Integer>());
        Fixture f = new Fixture(custom); f.loadCenter(); f.queueSmallChanges(FIRST); f.tick();
        assertEquals(List.of(0, 1), custom.layers);
    }
}
