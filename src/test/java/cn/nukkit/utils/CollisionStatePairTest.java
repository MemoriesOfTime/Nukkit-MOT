package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.*;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CollisionStatePairTest {
    @BeforeAll static void init() { MockServer.init(); }

    @Test void retainsFullSignedIdAndMetadataIncludingExtendedValues() {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
        for (int id : new int[]{0, 1, 10000, -2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int meta : new int[]{0, 8192, 9001, -2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                storage.set(0, BlockStateSnapshot.builder().legacyId(id).legacyData(meta).build());
                long pair = section.getBlockStatePair(0, 0, 0, 0);
                assertEquals(id, (int) (pair >>> 32));
                assertEquals(meta, (int) pair);
                assertArrayEquals(section.getBlockState(0, 0, 0, 0), new int[]{(int)(pair >>> 32), (int)pair});
            }
        }
    }

    @Test void absentLayerStillReportsAir() {
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{new StateBlockStorage()}, false);
        assertEquals(0L, section.getBlockStatePair(0, 0, 0, 1));
        assertEquals(0L, section.getBlockStatePair(0, 0, 0, 8));
    }

    @Test void knownProviderReadsSolidStateOnceAndPreservesMetadata() {
        Fixture f = new Fixture(false, false, false);
        f.storage.set(0, BlockStateSnapshot.builder().legacyId(Block.WATER).legacyData(8192).build());
        Block[] found = f.query();
        assertEquals(1, found.length);
        assertEquals(Block.WATER, found[0].getId());
        assertEquals(8192, found[0].getDamage());
        verify(f.section).getBlockStatePair(0, 0, 0, 0);
        verify(f.section, never()).getBlockId(anyInt(), anyInt(), anyInt(), anyInt());
        verify(f.section, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void knownProviderReadsAirOnceWithoutMaterializing() {
        Fixture f = new Fixture(false, false, false);
        assertEquals(0, f.query().length);
        verify(f.section).getBlockStatePair(0, 0, 0, 0);
        verify(f.section, never()).getBlockId(anyInt(), anyInt(), anyInt(), anyInt());
        verify(f.section, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void customChunkKeepsItsAirProbeOverride() {
        Fixture f = new Fixture(true, false, false);
        f.storage.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
        assertEquals(0, f.query().length);
        verify(f.section, never()).getBlockStatePair(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void customSectionKeepsItsAirProbeOverride() {
        Fixture f = new Fixture(false, true, false);
        f.storage.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
        assertEquals(0, f.query().length);
        verify(f.section, never()).getBlockStatePair(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void customProviderKeepsOriginalReads() {
        Fixture f = new Fixture(false, false, true);
        f.storage.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
        assertEquals(1, f.query().length);
        verify(f.section).getBlockId(0, 0, 0, 0);
        verify(f.section).getBlockState(0, 0, 0, 0);
        verify(f.section, never()).getBlockStatePair(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void foreignProviderHintUsesOriginalFallback() {
        Fixture f = new Fixture(false, false, false);
        f.chunk.setProvider(mock(LevelDBProvider.class));
        f.storage.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
        assertEquals(1, f.query().length);
        verify(f.section).getBlockId(0, 0, 0, 0);
        verify(f.section).getBlockState(0, 0, 0, 0);
        verify(f.section, never()).getBlockStatePair(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void boundsExcludeCellsBelowAndAboveLevel() {
        Fixture f = new Fixture(false, false, false);
        assertEquals(0, f.helper.getBlocksInBoundingBox(new SimpleAxisAlignedBB(0,-1,0,0,-1,0)).length);
        assertEquals(0, f.helper.getBlocksInBoundingBox(new SimpleAxisAlignedBB(0,256,0,0,256,0)).length);
        verify(f.section, never()).getBlockStatePair(anyInt(), anyInt(), anyInt(), anyInt());
        verify(f.section, never()).getBlockId(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void exactFastPathStillClonesCachedCustomFactoryResult() throws Exception {
        Fixture f = new Fixture(false, false, false);
        int id = cn.nukkit.block.custom.CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 993;
        CachedStone cached = new CachedStone();
        java.lang.reflect.Field field = cn.nukkit.block.custom.CustomBlockManager.class.getDeclaredField("legacy2CustomState");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var states = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<cn.nukkit.block.custom.CustomBlockState>)
                field.get(cn.nukkit.block.custom.CustomBlockManager.get());
        int key = id << Block.DATA_BITS;
        var previous = states.put(key, new cn.nukkit.block.custom.CustomBlockState(
                "test:pair_cached_stone", key, org.cloudburstmc.nbt.NbtMap.EMPTY, meta -> cached));
        try {
            f.storage.set(0, BlockStateSnapshot.builder().legacyId(id).legacyData(0).build());
            Block first = f.query()[0], second = f.query()[0];
            assertNotSame(cached, first);
            assertNotSame(first, second);
            first.x = 99;
            assertEquals(0, second.x);
            verify(f.section, times(2)).getBlockStatePair(0, 0, 0, 0);
            verify(f.section, never()).getBlockId(anyInt(), anyInt(), anyInt(), anyInt());
        } finally {
            if (previous == null) states.remove(key); else states.put(key, previous);
        }
    }

    @Test void exactFastPathKeepsUnknownIdAndExtendedMetadata() {
        Fixture f = new Fixture(false, false, false);
        f.storage.set(0, BlockStateSnapshot.builder().legacyId(9000).legacyData(9001).build());
        Block found = f.query()[0];
        assertEquals(9000, found.getId());
        assertEquals(9001, found.getDamage());
    }

    static class CachedStone extends cn.nukkit.block.BlockStone implements cn.nukkit.block.custom.container.BlockContainer {
        @Override public String getIdentifier() { return "test:pair_cached_stone"; }
        @Override public int getNukkitId() { return Block.STONE; }
    }

    static class Fixture {
        final StateBlockStorage storage = new StateBlockStorage();
        final LevelDBChunkSection section;
        final LevelDBChunk chunk;
        final CollisionHelper helper;
        Fixture(boolean customChunk, boolean customSection, boolean customProvider) {
            section = spy(customSection ? new CustomSection(storage)
                    : new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false));
            Level level = mock(Level.class);
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.isYInRange(anyInt())).thenCallRealMethod();
            LevelDBProvider provider = customProvider ? mock(CustomProvider.class) : mock(LevelDBProvider.class);
            when(level.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            when(level.getDimensionData()).thenReturn(DimensionData.LEGACY_DIMENSION);
            chunk = customChunk ? new CustomChunk(provider,section)
                    : new LevelDBChunk(provider, 0, 0, new ChunkSection[]{section}, null, null, null, null, null, ChunkState.FINISHED);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(0), eq(false))).thenCallRealMethod();
            Entity entity = mock(Entity.class);
            entity.chunk = chunk;
            when(entity.getLevel()).thenReturn(level);
            helper = new CollisionHelper(entity);
        }
        Block[] query() { return helper.getBlocksInBoundingBox(new SimpleAxisAlignedBB(0,0,0,0,0,0)); }
    }
    static class CustomChunk extends LevelDBChunk {
        CustomChunk(LevelProvider provider,ChunkSection section) { super(provider,0,0,new ChunkSection[]{section},null,null,null,null,null,ChunkState.FINISHED); }
        @Override public int getBlockId(int x,int y,int z,int layer) { return Block.AIR; }
    }
    static class CustomSection extends LevelDBChunkSection {
        CustomSection(StateBlockStorage storage) { super(0,new StateBlockStorage[]{storage},false); }
        @Override public int getBlockId(int x,int y,int z,int layer) { return Block.AIR; }
    }
    static class CustomProvider extends LevelDBProvider {
        CustomProvider() { super(null,""); }
    }
}
