package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmptyAirChunkCacheTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;

    @BeforeAll static void init() { MockServer.init(); }

    static Stream<Arguments> modes() {
        return Stream.of(0, 1, 2).flatMap(dimension ->
                Stream.of(false, true).map(fullId -> Arguments.of(dimension, fullId)));
    }

    private static LevelDBChunk chunk(DimensionData dimension) {
        return chunk(dimension, true);
    }

    private static LevelDBChunk chunk(DimensionData dimension, boolean initialize) {
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(dimension);
        when(level.getDimension()).thenReturn(dimension.getDimensionId());
        LevelDBProvider provider = mock(LevelDBProvider.class);
        when(provider.getLevel()).thenReturn(level);
        when(provider.getMinBlockY()).thenReturn(dimension.getMinHeight());
        when(provider.getMaxBlockY()).thenReturn(dimension.getMaxHeight());
        LevelDBChunk chunk = LevelDBChunk.getEmptyChunk(3, 5, provider);
        if (initialize) chunk.initChunk();
        assertEquals(initialize, chunk.isInitialized());
        assertSame(EmptyChunkSection.class, chunk.getSection(0).getClass());
        chunk.setChanged(false);
        return chunk;
    }

    private static BatchPacket requestCached(LevelDBChunk chunk, DimensionData dimension,
                                             AtomicInteger serialized) {
        BatchPacket existing = chunk.getChunkPacket(VERSION);
        if (existing != null) return existing;
        serialized.incrementAndGet();
        NetworkChunkSerializer.serialize(new ObjectOpenHashSet<>(List.of(VERSION)),
                chunk.cloneForChunkSending(), data -> chunk.setChunkPacket(VERSION,
                        Player.getChunkCacheFromData(VERSION, chunk.getX(), chunk.getZ(),
                                data.getSubchunks(), data.getStream().getBuffer(), dimension.getDimensionId())),
                false, dimension);
        return chunk.getChunkPacket(VERSION);
    }

    @ParameterizedTest @MethodSource("modes")
    void emptyAirWritesKeepWarmNativeSerialization(int dimensionId, boolean fullId) throws Exception {
        DimensionData dimension = new DimensionData(dimensionId, 0, 31);
        LevelDBChunk chunk = chunk(dimension);
        AtomicInteger serialized = new AtomicInteger();
        BatchPacket first = requestCached(chunk, dimension, serialized);
        assertNotNull(first);
        for (String field : List.of("tiles", "tileList", "unknownTiles")) {
            var f = BaseFullChunk.class.getDeclaredField(field);
            f.setAccessible(true);
            assertNull(f.get(chunk), "Native serialization must not create owner tile maps: " + field);
        }
        long changes = chunk.getChanges();
        for (int i = 0; i < 64; i++) {
            boolean changed = fullId ? chunk.setFullBlockId(1, 1, 1, i & 1, 0)
                    : chunk.setBlockAtLayer(1, 1, 1, i & 1, 0, i & 15);
            assertFalse(changed);
            assertNotNull(requestCached(chunk, dimension, serialized));
        }
        System.out.println("empty-air dimension=" + dimensionId + " fullId=" + fullId
                + " serializations=" + serialized.get() + " writes=64");
        assertEquals(1, serialized.get(), "No-op AIR must not repeat native serialization/compression");
        assertSame(first, chunk.getChunkPacket(VERSION));
        assertEquals(changes, chunk.getChanges());
        assertEquals(0, chunk.getChanges());
    }

    private static boolean air(LevelDBChunk chunk, boolean fullId, int y) {
        return fullId ? chunk.setFullBlockId(1, y, 1, 1, 0)
                : chunk.setBlockAtLayer(1, y, 1, 1, 0, 13);
    }

    private static BatchPacket cache(LevelDBChunk chunk) {
        BatchPacket packet = new BatchPacket();
        chunk.setChunkPacket(VERSION, packet);
        return packet;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void negativeHeightPreservesEverySupportedVersionCache(boolean fullId) {
        LevelDBChunk chunk = chunk(new DimensionData(0, -64, 319));
        BatchPacket packet = cache(chunk);
        int versions = 0;
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
            chunk.setChunkPacket(version, packet);
            versions++;
        }
        assertFalse(air(chunk, fullId, -63));
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() >= 589 && version.getProtocol() <= 2193) {
                assertSame(packet, chunk.getChunkPacket(version), version.toString());
            }
        }
        assertTrue(versions >= 40);
        assertFalse(chunk.hasChanged());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void retainedUnknownTileCleanupRemainsDirty(boolean fullId) {
        LevelDBChunk chunk = chunk(new DimensionData(1, 0, 31), false);
        CompoundTag tile = new CompoundTag().putString("id", "MissingEmptyAirCacheTestTile")
                .putInt("x", 49).putInt("y", 1).putInt("z", 81);
        chunk.setNbtBlockEntities(List.of(tile));
        chunk.initChunk();
        assertEquals(1, chunk.getUnknownTiles().size());
        chunk.setChanged(false);
        cache(chunk);
        assertFalse(air(chunk, fullId, 1));
        assertTrue(chunk.getUnknownTiles().isEmpty());
        assertTrue(chunk.hasChanged(), "Removed retained NBT must reach the next durable save");
        assertNull(chunk.getChunkPacket(VERSION));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unfinishedInitializationAndMaterializedSectionsKeepOldInvalidation(boolean fullId) {
        LevelDBChunk uninitialized = chunk(new DimensionData(1, 0, 31), false);
        cache(uninitialized);
        assertFalse(air(uninitialized, fullId, 1));
        assertTrue(uninitialized.hasChanged());
        assertNull(uninitialized.getChunkPacket(VERSION));

        LevelDBChunk materialized = chunk(new DimensionData(1, 0, 31));
        assertTrue(materialized.setBlockAtLayer(1, 1, 1, 1, BlockID.STONE, 0));
        cache(materialized);
        assertTrue(air(materialized, fullId, 1));
        assertNull(materialized.getChunkPacket(VERSION), "A real removal must invalidate cache");
        materialized.setChanged(false);
        cache(materialized);
        assertFalse(fullId ? materialized.setFullBlockId(1, 1, 1, 1, 0)
                : materialized.setBlockAtLayer(1, 1, 1, 1, 0, 0));
        assertTrue(materialized.hasChanged(), "Mutable section remains on its existing virtual path");
        assertNull(materialized.getChunkPacket(VERSION));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void customChunkAndSectionKeepCallbacksAfterDirtyMark(boolean fullId) {
        class CustomChunk extends LevelDBChunk {
            int calls;
            CustomChunk() { super(null, 3, 5); this.isInit = true; setChanged(false); }
            @Override public cn.nukkit.level.format.ChunkSection getSection(float y) {
                assertTrue(hasChanged(), "Every virtual section read must follow the dirty mark");
                calls++;
                return super.getSection(y);
            }
        }
        CustomChunk custom = new CustomChunk();
        cache(custom);
        assertFalse(air(custom, fullId, 1));
        assertEquals(1, custom.calls);
        assertNull(custom.getChunkPacket(VERSION));

        LevelDBChunk nativeChunk = chunk(new DimensionData(1, 0, 31));
        AtomicInteger calls = new AtomicInteger();
        nativeChunk.getSections()[0] = new EmptyChunkSection(0) {
            @Override public boolean setFullBlockId(int x, int y, int z, int layer, int value) {
                assertTrue(nativeChunk.hasChanged()); calls.incrementAndGet(); return false;
            }
            @Override public boolean setBlockAtLayer(int x, int y, int z, int layer, int id, int meta) {
                assertTrue(nativeChunk.hasChanged()); calls.incrementAndGet(); return false;
            }
        };
        cache(nativeChunk);
        assertFalse(air(nativeChunk, fullId, 1));
        assertEquals(1, calls.get());
        assertNull(nativeChunk.getChunkPacket(VERSION));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void invalidLiveTileCleanupStillInvalidatesAndMarksDirty(boolean fullId) {
        LevelDBChunk chunk = chunk(new DimensionData(1, 0, 31));
        BlockEntity tile = mock(BlockEntity.class);
        when(tile.getId()).thenReturn(123L);
        when(tile.getFloorX()).thenReturn(49);
        when(tile.getFloorY()).thenReturn(1);
        when(tile.getFloorZ()).thenReturn(81);
        when(tile.isBlockEntityValid()).thenReturn(false);
        chunk.addBlockEntity(tile);
        assertSame(tile, chunk.getTile(1, 1, 1));
        chunk.setChanged(false);
        cache(chunk);
        assertFalse(air(chunk, fullId, 1));
        assertNull(chunk.getTile(1, 1, 1));
        assertTrue(chunk.hasChanged());
        assertNull(chunk.getChunkPacket(VERSION));
        verify(tile).isBlockEntityValid();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void customDimensionAndMalformedSectionKeepOldAccessAndExceptions(boolean fullId) {
        AtomicInteger calls = new AtomicInteger();
        DimensionData dimension = new DimensionData(1, 0, 31) {
            @Override public int getSectionOffset() { calls.incrementAndGet(); return 0; }
        };
        LevelDBChunk custom = chunk(dimension);
        cache(custom);
        calls.set(0);
        assertFalse(air(custom, fullId, 1));
        assertEquals(1, calls.get(), "No extra virtual offset read on the fallback path");
        assertNull(custom.getChunkPacket(VERSION));

        LevelDBChunk malformed = chunk(new DimensionData(1, 0, 31));
        malformed.getSections()[0] = null;
        cache(malformed);
        assertThrows(NullPointerException.class, () -> air(malformed, fullId, 1));
        assertTrue(malformed.hasChanged());
        assertNull(malformed.getChunkPacket(VERSION));
    }

    @Test
    void nativeWarmCacheServesRealLevelRequestsWithoutAnotherProviderTask() throws Exception {
        DimensionData dimension = new DimensionData(1, 0, 31);
        LevelDBChunk chunk = chunk(dimension);
        AtomicInteger serialized = new AtomicInteger();
        BatchPacket packet = requestCached(chunk, dimension, serialized);
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        LevelDBProvider provider = (LevelDBProvider) chunk.getProvider();
        Server server = mock(Server.class);
        server.cacheChunks = true;
        when(server.getTick()).thenReturn(1);
        set(Level.class, level, "server", server);
        set(Level.class, level, "provider", provider);
        for (String name : List.of("chunkSendQueues", "chunkSendTasks", "pendingChunkRequests", "chunkRequestEpochs")) {
            set(Level.class, level, name, new Object2ObjectOpenHashMap<>());
        }
        set(Level.class, level, "chunkSendTaskStartTick", new Object2ObjectOpenHashMap<GameVersion, Long2LongOpenHashMap>());
        doReturn(chunk).when(level).getChunk(3, 5);
        doReturn(chunk).when(level).getChunkIfLoaded(3, 5);
        doReturn(1).when(level).getDimension();
        var process = Level.class.getDeclaredMethod("processChunkRequest");
        process.setAccessible(true);
        for (int i = 0; i < 32; i++) {
            assertFalse(air(chunk, (i & 1) == 0, 1));
            Player player = mock(Player.class);
            set(Player.class, player, "usedChunks", new HashMap<Long, Boolean>());
            player.usedChunks.put(Level.chunkHash(3, 5), false);
            when(player.getLoaderId()).thenReturn(i + 1);
            when(player.getGameVersion()).thenReturn(VERSION);
            when(player.isConnected()).thenReturn(true);
            when(player.getLevel()).thenReturn(level);
            level.requestChunk(3, 5, player);
            process.invoke(level);
            verify(player).sendChunk(3, 5, packet);
        }
        verify(provider, never()).requestChunkTask(
                org.mockito.ArgumentMatchers.<ObjectSet<GameVersion>>any(), anyInt(), anyInt());
        assertEquals(1, serialized.get());
    }

    @Test
    void concurrentNativeMaterializationPreservesTheOtherCellAndInvalidation() throws Exception {
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 32; round++) {
                LevelDBChunk chunk = chunk(new DimensionData(1, 0, 31));
                assertSame(LevelDBChunk.class, chunk.getClass());
                cache(chunk);
                long changes = chunk.getChanges();
                var start = new java.util.concurrent.CyclicBarrier(3);
                var emptyWrite = workers.submit(() -> {
                    start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return chunk.setBlockAtLayer(1, 1, 1, 0, 0, 0);
                });
                var realWrite = workers.submit(() -> {
                    start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return chunk.setBlockAtLayer(2, 1, 1, 0, BlockID.STONE, 0);
                });
                start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                assertFalse(emptyWrite.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(realWrite.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, chunk.getBlockId(1, 1, 1));
                assertEquals(BlockID.STONE, chunk.getBlockId(2, 1, 1));
                assertTrue(chunk.getChanges() > changes);
                assertTrue(chunk.hasChanged());
                assertNull(chunk.getChunkPacket(VERSION));
            }
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
