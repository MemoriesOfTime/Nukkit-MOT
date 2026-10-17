package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollisionStableOffsetEligibilityTest {
    private static final AxisAlignedBB BOX = new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6);
    private static final AxisAlignedBB SCAN = BOX.grow(.5, .5, .5);

    @BeforeAll static void init() { MockServer.init(); }

    @Test void onlyExactDimensionOrKnownLegacyIsEligible() {
        assertTrue(new Fixture(new DimensionData(0, 0, 255)).chunk.hasStableSectionOffset());
        assertTrue(new Fixture(DimensionData.LEGACY_DIMENSION).chunk.hasStableSectionOffset());
        assertFalse(new Fixture(new FixedCustomDimension()).chunk.hasStableSectionOffset());
        assertFalse(new Fixture(new MutableCustomDimension()).chunk.hasStableSectionOffset());
    }

    @Test void customDimensionKeepsOldGetterWithoutExtraOffsetOrArrayProbe() {
        Fixture fixture = new Fixture(new FixedCustomDimension());
        LevelDBChunk chunk = spy(fixture.chunk);
        assertEquals(LevelDBChunk.class, chunk.getClass());
        ChunkSection section = chunk.getSection(4);
        var cache = new CollisionHelper.ScanCache();
        cache.begin(fixture.world.level);
        clearInvocations(chunk);
        cache.stamp(0, 0, 4, chunk, section, CollisionHelper.ScanCache.NO_VERSION);
        verify(chunk, never()).getSectionOffset();
        verify(chunk, never()).getSections();
        cache.commit(SCAN, BOX);
        clearInvocations(chunk);
        assertTrue(cache.holds(fixture.world.level, chunk, SCAN, BOX));
        verify(chunk).getSection(4);
        verify(chunk).getSectionOffset();
        verify(chunk, never()).getSections();
    }

    @Test void capturedCustomDimensionRemainsIneligibleAfterProviderReattachment() {
        var custom = new MutableCustomDimension();
        Fixture fixture = new Fixture(custom);
        LevelDBProvider replacement = mock(LevelDBProvider.class);
        when(replacement.getLevel()).thenReturn(fixture.world.level);
        when(fixture.world.level.getDimensionData()).thenReturn(new DimensionData(0, 0, 255));
        fixture.chunk.setProvider(replacement);
        assertFalse(fixture.chunk.hasStableSectionOffset());
        custom.offset = 1;
        assertEquals(1, fixture.chunk.getSectionOffset(), "captured custom getter still controls the chunk");
    }

    @Test void capturedExactDimensionRemainsEligibleAfterProviderReattachment() {
        Fixture fixture = new Fixture(new DimensionData(0, 0, 255));
        var custom = new MutableCustomDimension();
        custom.offset = 1;
        when(fixture.world.level.getDimensionData()).thenReturn(custom);
        LevelDBProvider replacement = mock(LevelDBProvider.class);
        when(replacement.getLevel()).thenReturn(fixture.world.level);
        fixture.chunk.setProvider(replacement);
        assertTrue(fixture.chunk.hasStableSectionOffset());
        assertEquals(0, fixture.chunk.getSectionOffset(), "captured exact data controls the chunk");
    }

    private static final class Fixture {
        final CollisionCandidatesTest.World world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        final LevelDBChunk chunk;
        Fixture(DimensionData dimension) {
            when(world.level.getDimensionData()).thenReturn(dimension);
            chunk = new LevelDBChunk(world.provider, 0, 0, new ChunkSection[dimension.getHeight() >> 4],
                    null, null, null, null, null, ChunkState.FINISHED);
        }
    }

    private static class FixedCustomDimension extends DimensionData {
        FixedCustomDimension() { super(0, 0, 255); }
        @Override public int getSectionOffset() { return 0; }
    }

    private static final class MutableCustomDimension extends DimensionData {
        int offset;
        MutableCustomDimension() { super(0, 0, 255); }
        @Override public int getSectionOffset() { return offset; }
    }
}
