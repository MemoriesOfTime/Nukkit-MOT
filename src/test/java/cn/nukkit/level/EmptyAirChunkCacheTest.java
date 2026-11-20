package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.BatchPacket;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
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
        // getUnknownTiles() 为非物化读取(null 时返回空集合)，可直接断言无 owner tile map；
        // tiles/tileList 一旦被序列化创建，下方 AIR 写将走不到跳过路径，setChanged() 会推高
        // getChanges() 并清空缓存包，由循环后的断言捕获。
        // getUnknownTiles() is a non-materializing read (empty list when null); if tiles/tileList
        // were created by serialization, the AIR-write skip path below would be disabled, tripping
        // the getChanges()/packet-identity asserts after the loop.
        assertTrue(chunk.getUnknownTiles().isEmpty(), "Native serialization must not create owner tile maps");
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
}
