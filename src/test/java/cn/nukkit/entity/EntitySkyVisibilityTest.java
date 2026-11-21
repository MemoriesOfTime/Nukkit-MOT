package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.utils.MainLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntitySkyVisibilityTest {
    @BeforeAll
    static void init() { MockServer.init(); }

    @Test
    void clearNativeColumnAvoidsHundredsOfScalarReads() {
        Fixture f = new Fixture(false, -64, 319);
        assertTrue(f.entity.canSeeSky());
        assertTrue(f.reads.get() <= 16, "256 scalar reads must collapse to at most 16: " + f.reads);
    }

    @Test
    void preservesInclusiveFeetLayerZeroAndAnyNonAirPredicate() {
        for (int id : new int[]{BlockID.STONE, BlockID.GLASS, BlockID.WATER, BlockID.LEAVES}) {
            for (int y : new int[]{64, 65, 79, 80, 255, 256, 319}) {
                Fixture f = new Fixture(false, -64, 319);
                f.chunk.setBlockId(3, y, 5, 0, id);
                assertFalse(f.entity.canSeeSky(), id + " at " + y);
                assertEquals(f.reference(), f.entity.canSeeSky());
            }
        }
        Fixture f = new Fixture(false, -64, 319);
        f.chunk.setBlockId(3, 63, 5, 0, BlockID.STONE);
        f.chunk.setBlockId(3, 80, 5, 1, BlockID.WATER);
        f.chunk.setBlockId(4, 319, 5, 0, BlockID.STONE);
        assertTrue(f.entity.canSeeSky(), "Ground, other columns and layer one do not cover this column");
    }

    @Test
    void roofPlacementRemovalAndSectionReplacementAreImmediatelyVisible() {
        Fixture f = new Fixture(false, -64, 319);
        assertTrue(f.entity.canSeeSky());
        f.chunk.setBlockId(3, 80, 5, 0, BlockID.GLASS);
        assertFalse(f.entity.canSeeSky());
        f.chunk.setBlockId(3, 80, 5, 0, BlockID.AIR);
        assertTrue(f.entity.canSeeSky());
        f.chunk.getSections()[5 - f.min / 16] = new EmptyChunkSection(5) {
            @Override public int getBlockId(int x, int y, int z, int layer) { return BlockID.STONE; }
        };
        assertFalse(f.entity.canSeeSky(), "An EmptyChunkSection subclass may override the read");
    }

    @Test
    void dimensionBoundsNegativeCoordinatesAndPartialSectionsMatchScalarScan() {
        for (int[] bounds : new int[][]{{-64, 319}, {0, 255}, {0, 127}}) {
            Fixture f = new Fixture(false, bounds[0], bounds[1]);
            for (int feet : new int[]{bounds[0] - 17, bounds[0], -1, 0, 15, 16, 65,
                    bounds[1], bounds[1] + 1}) {
                f.entity.y = feet + 0.25;
                assertEquals(f.reference(), f.entity.canSeeSky(), "feet=" + feet);
            }
            f.entity.x = -12.5;
            f.entity.z = -10.5;
            f.chunk.setPosition(-1, -1);
            f.entity.y = bounds[0] + 0.25;
            f.chunk.setBlockId(3, bounds[1], 5, 0, BlockID.GLASS);
            assertEquals(f.reference(), f.entity.canSeeSky());
            assertFalse(f.entity.canSeeSky());
        }
        Fixture partial = new Fixture(false, -64, 319);
        when(partial.level.getMaxBlockY()).thenReturn(310);
        partial.chunk.setBlockId(3, 315, 5, 0, BlockID.STONE);
        assertTrue(partial.entity.canSeeSky());
        partial.chunk.setBlockId(3, 310, 5, 0, BlockID.GLASS);
        assertEquals(partial.reference(), partial.entity.canSeeSky());
        assertFalse(partial.entity.canSeeSky());
    }

    @Test
    void nullOrWrongChunkKeepsTheOriginalLoadingPath() {
        for (boolean missing : new boolean[]{true, false}) {
            Fixture f = new Fixture(false, -64, 319);
            if (missing) f.entity.chunk = null;
            else f.entity.x = 19.5;
            f.chunk.setBlockId(3, 80, 5, 0, BlockID.STONE);
            assertFalse(f.entity.canSeeSky());
            verify(f.level, atLeastOnce()).getChunk(anyInt(), anyInt(), eq(true));
        }
    }

    @Test
    void customLevelAndChunkKeepVirtualBlockReads() {
        Fixture customLevel = new Fixture(true, -64, 319);
        doReturn(BlockID.STONE).when(customLevel.level).getBlockIdAt(any(), anyInt(), eq(80), anyInt());
        assertFalse(customLevel.entity.canSeeSky());

        Fixture customChunk = new Fixture(false, -64, 319);
        FullChunk chunk = mock(FullChunk.class);
        when(chunk.getBlockId(3, 80, 5, 0)).thenReturn(BlockID.STONE);
        customChunk.entity.chunk = chunk;
        assertFalse(customChunk.entity.canSeeSky());
        verify(chunk).getBlockId(3, 80, 5, 0);
    }

    @Test
    void customSectionCanChangeTheEntityChunkDuringItsRead() {
        Fixture f = new Fixture(false, -64, 319);
        FullChunk replacement = mock(FullChunk.class);
        when(replacement.getBlockId(3, 318, 5, 0)).thenReturn(BlockID.STONE);
        f.chunk.getSections()[19 - f.min / 16] = new EmptyChunkSection(19) {
            @Override public int getBlockId(int x, int y, int z, int layer) {
                f.entity.chunk = replacement;
                return BlockID.AIR;
            }
        };
        assertFalse(f.entity.canSeeSky(), "Do not retain an obsolete fast-path chunk across virtual reads");
    }

    @Test
    void capturedDimensionOffsetChangesKeepScalarReads() {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger offsets = new AtomicInteger();
        DimensionData capturedDimension = new DimensionData(0, -64, 319) {
            @Override public int getSectionOffset() {
                return !armed.get() || offsets.incrementAndGet() == 1 ? 4 : 3;
            }
        };
        Fixture f = new Fixture(false, -64, 319, capturedDimension, false);
        when(f.level.getDimensionData()).thenReturn(new DimensionData(0, -64, 319));
        f.entity.y = 318;
        f.chunk.setBlockId(3, 302, 5, 0, BlockID.STONE);
        armed.set(true);
        assertFalse(f.reference());
        assertEquals(2, offsets.get());
        offsets.set(0);
        assertFalse(f.entity.canSeeSky(), "The captured chunk dimension can change the section offset between reads");
        assertEquals(2, offsets.get(), "Do not consume an extra virtual offset before the scalar scan");
    }

    @Test
    void customProviderBoundsCanChangeTheEntityChunkDuringTheScan() {
        Fixture f = new Fixture(false, -64, 319, new DimensionData(0, -64, 319), true);
        FullChunk replacement = mock(FullChunk.class);
        when(replacement.getBlockId(3, 318, 5, 0)).thenReturn(BlockID.STONE);
        f.entity.y = 318;
        AtomicInteger maxBoundsReads = new AtomicInteger();
        doAnswer(invocation -> {
            if (maxBoundsReads.incrementAndGet() == 2) f.entity.chunk = replacement;
            return 319;
        }).when(f.provider).getMaxBlockY();
        doCallRealMethod().when(f.level).getMinBlockY();
        doCallRealMethod().when(f.level).getMaxBlockY();
        assertFalse(f.reference());
        assertEquals(3, maxBoundsReads.get());
        maxBoundsReads.set(0);
        f.entity.chunk = f.chunk;
        assertFalse(f.entity.canSeeSky(), "An exact Level can delegate bounds reads to a custom provider");
        assertEquals(3, maxBoundsReads.get(), "Preserve the provider callbacks of the scalar scan");
    }

    @Test
    void currentDimensionBoundsCanChangeTheEntityChunkDuringTheScan() {
        Fixture f = new Fixture(false, -64, 319);
        FullChunk replacement = mock(FullChunk.class);
        when(replacement.getBlockId(3, 318, 5, 0)).thenReturn(BlockID.STONE);
        f.entity.y = 318;
        AtomicInteger maxBoundsReads = new AtomicInteger();
        DimensionData currentDimension = new DimensionData(0, -64, 319) {
            @Override public int getMaxHeight() {
                if (maxBoundsReads.incrementAndGet() == 2) f.entity.chunk = replacement;
                return 319;
            }
        };
        when(f.level.getDimensionData()).thenReturn(currentDimension);
        doAnswer(invocation -> f.level.getDimensionData().getMinHeight()).when(f.provider).getMinBlockY();
        doAnswer(invocation -> f.level.getDimensionData().getMaxHeight()).when(f.provider).getMaxBlockY();
        doCallRealMethod().when(f.level).getMinBlockY();
        doCallRealMethod().when(f.level).getMaxBlockY();
        assertTrue(f.chunk.hasStableSectionOffset(), "The chunk retains its original native dimension");
        assertFalse(f.reference());
        assertEquals(3, maxBoundsReads.get());
        maxBoundsReads.set(0);
        f.entity.chunk = f.chunk;
        assertFalse(f.entity.canSeeSky(), "Native provider bounds may use a different custom dimension than the chunk");
        assertEquals(3, maxBoundsReads.get(), "Preserve the current dimension callbacks of the scalar scan");
    }

    @Test
    void minimumIntegerDimensionDoesNotWrapTheSkyScan() {
        Fixture f = new Fixture(false, Integer.MIN_VALUE, Integer.MIN_VALUE + 15);
        when(f.level.getServer()).thenReturn(Server.getInstance());
        f.entity.y = (double) Integer.MIN_VALUE + 1;
        AtomicInteger boundsReads = new AtomicInteger();
        doAnswer(invocation -> {
            assertTrue(boundsReads.incrementAndGet() <= 32, "The sky scan must not wrap into positive heights");
            return Integer.MIN_VALUE;
        }).when(f.level).getMinBlockY();
        assertTrue(f.reference());
        assertEquals(15, f.reads.get());
        boundsReads.set(0);
        f.reads.set(0);
        assertTrue(f.entity.canSeeSky());
        assertTrue(f.reads.get() <= 15, "Do not read beyond the fifteen blocks above the entity's feet");
    }

    @Test
    void invalidSectionIndexPreservesScalarDiagnostics() {
        Fixture f = new Fixture(false, -64, 319);
        when(f.level.getDimensionData()).thenReturn(new DimensionData(0, -64, 335));
        when(f.level.getMaxBlockY()).thenReturn(335);
        when(f.provider.getMaxBlockY()).thenReturn(335);
        f.entity.y = 320;
        Server server = mock(Server.class);
        MainLogger logger = mock(MainLogger.class);
        when(f.level.getServer()).thenReturn(server);
        when(server.getLogger()).thenReturn(logger);
        AtomicInteger diagnostics = new AtomicInteger();
        doAnswer(invocation -> {
            diagnostics.incrementAndGet();
            return null;
        }).when(logger).logException(any(Throwable.class));
        assertEquals(24, f.chunk.getSections().length);
        assertTrue(f.reference());
        assertEquals(16, diagnostics.get(), "Each out-of-range scalar read logs before clamping the section");
        diagnostics.set(0);
        assertTrue(f.entity.canSeeSky());
        assertEquals(16, diagnostics.get(), "The fast path must not add or suppress invalid-section diagnostics");
    }

    private static final class Fixture {
        final Level level;
        final LevelDBProvider provider;
        final LevelDBChunk chunk;
        final EntityZombie entity = mock(EntityZombie.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        final AtomicInteger reads = new AtomicInteger();
        final int min;

        Fixture(boolean customLevel, int min, int max) {
            this(customLevel, min, max, new DimensionData(0, min, max), false);
        }

        Fixture(boolean customLevel, int min, int max, DimensionData dimension, boolean customProvider) {
            this.min = min;
            level = customLevel ? mock(Level.class, withSettings().extraInterfaces(Runnable.class)) : mock(Level.class);
            assertEquals(!customLevel, level.getClass() == Level.class);
            when(level.getDimensionData()).thenReturn(dimension);
            when(level.getMinBlockY()).thenReturn(min);
            when(level.getMaxBlockY()).thenReturn(max);
            provider = customProvider ? mock(LevelDBProvider.class, withSettings().extraInterfaces(Runnable.class))
                    : mock(LevelDBProvider.class);
            assertEquals(!customProvider, provider.getClass() == LevelDBProvider.class);
            when(level.getProvider()).thenReturn(provider);
            doReturn(provider).when(level).requireProvider();
            when(provider.getLevel()).thenReturn(level);
            when(provider.getMinBlockY()).thenReturn(min);
            when(provider.getMaxBlockY()).thenReturn(max);
            chunk = LevelDBChunk.getEmptyChunk(0, 0, provider);
            when(level.getChunk(anyInt(), anyInt(), eq(true))).thenReturn(chunk);
            when(level.getBlockIdAt(nullable(FullChunk.class), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> { reads.incrementAndGet(); return invocation.callRealMethod(); });
            when(level.getBlockIdAt(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenCallRealMethod();
            entity.level = level;
            entity.chunk = chunk;
            entity.x = 3.5;
            entity.y = 64;
            entity.z = 5.5;
        }

        boolean reference() {
            for (int y = level.getMaxBlockY(); y >= entity.getFloorY(); y--) {
                if (level.getBlockIdAt(entity.chunk, entity.getFloorX(), y, entity.getFloorZ()) != 0) return false;
            }
            return true;
        }
    }
}
