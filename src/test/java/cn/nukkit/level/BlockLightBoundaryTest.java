package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.generic.LightingFixture;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.Chunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockLightBoundaryTest {
    @BeforeAll static void blocks() { Block.init(); }

    static final class World {
        final Level level = mock(Level.class);
        final Map<Long, LightingFixture> chunks = new HashMap<>();
        final Map<Long, CompletableFuture<ChunkLoadResult>> reads = new HashMap<>();
        final BlockLightBoundary boundary = new BlockLightBoundary(level);
        World() {
            when(level.isYInRange(anyInt())).thenCallRealMethod();
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(inv ->
                chunks.get(Level.chunkHash(inv.getArgument(0), inv.getArgument(1))));
            when(level.requestChunkLoadAsyncResult(anyInt(), anyInt())).thenAnswer(inv ->
                reads.computeIfAbsent(Level.chunkHash(inv.getArgument(0), inv.getArgument(1)), ignored -> new CompletableFuture<>()));
        }
        LightingFixture chunk(int x, int z) {
            LightingFixture chunk = new LightingFixture();
            chunk.setPosition(x, z);
            chunk.setGenerated(true); chunk.setPopulated(true); chunk.setLightPopulated(true);
            chunks.put(Level.chunkHash(x, z), chunk);
            return chunk;
        }
        void halo(int x, int z) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (!chunks.containsKey(Level.chunkHash(x + dx, z + dz))) chunk(x + dx, z + dz);
        }
        void drain() {
            for (int i = 0; i < 1500 && boundary.hasWork(); i++) boundary.tick();
            assertFalse(boundary.hasWork(), "bounded relaxation did not converge");
        }
        int light(int x, int y, int z) {
            return chunks.get(Level.chunkHash(x >> 4, z >> 4)).getBlockLight(x & 15, y, z & 15);
        }
        void stale(LightingFixture chunk, int sx, int sy, int sz) {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = sy - 15; y <= sy + 15; y++) {
                int v = Math.max(0, 15 - Math.abs((chunk.getX() << 4) + x - sx)
                        - Math.abs(y - sy) - Math.abs((chunk.getZ() << 4) + z - sz));
                chunk.setBlockLight(x, y, z, v);
            }
        }
    }

    @Test void propagationCannotReadAColdChunkSynchronously() {
        World world = new World();
        LightingFixture chunk = world.chunk(0, 0);
        chunk.ids[LightingFixture.index(15, 64, 8)] = BlockID.GLOWSTONE;
        doCallRealMethod().when(world.level).updateBlockLight(anyMap());
        Map<Long, Set<Integer>> changes = new HashMap<>();
        changes.put(Level.chunkHash(0, 0), Set.of(Level.localBlockHash(15, 64, 8, world.level.getDimensionData())));
        world.level.updateBlockLight(changes);
        assertEquals(15, chunk.getBlockLight(15, 64, 8));
        assertTrue(world.reads.containsKey(Level.chunkHash(1, 0)));
        verify(world.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(world.level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
    }

    @Test void coldRootRetainsItsBucketUntilActualMount() {
        World world = new World();
        doCallRealMethod().when(world.level).updateBlockLight(anyMap());
        Map<Long, Set<Integer>> changes = new HashMap<>();
        long hash = Level.chunkHash(-2, -3);
        changes.put(hash, Set.of(Level.localBlockHash(-17, 64, -33, world.level.getDimensionData())));
        world.level.updateBlockLight(changes);
        assertTrue(changes.containsKey(hash));
        LightingFixture chunk = world.chunk(-2, -3);
        chunk.ids[LightingFixture.index(15, 64, 15)] = BlockID.GLOWSTONE;
        world.level.updateBlockLight(changes);
        assertTrue(changes.isEmpty());
        assertEquals(15, chunk.getBlockLight(15, 64, 15));
    }

    @Test void restartWithBFirstReconstructsEvenADarkBoundary() {
        World world = new World();
        LightingFixture b = world.chunk(1, 0);
        world.boundary.mounted(b, true);
        assertEquals(8, world.reads.size());
        assertEquals(8, world.boundary.pendingBoundaries());
        assertFalse(world.boundary.hasWork());
        LightingFixture a = world.chunk(0, 0);
        a.ids[LightingFixture.index(14, 64, 8)] = BlockID.GLOWSTONE;
        world.halo(1, 0);
        world.boundary.mounted(a, true);
        world.drain();
        assertEquals(13, world.light(16, 64, 8));
        assertEquals(8, world.reads.size(), "light-only mount expanded the cold halo");
    }

    @Test void restartWithBFirstClearsASavedGhostPeakInsideA() {
        World world = new World();
        LightingFixture b = world.chunk(1, 0);
        world.stale(b, 14, 64, 8);
        world.boundary.mounted(b, true);
        LightingFixture a = world.chunk(0, 0);
        world.stale(a, 14, 64, 8); // Persisted lighting, but source block already removed.
        world.halo(1, 0);
        world.boundary.mounted(a, true);
        world.drain();
        assertEquals(0, world.light(16, 64, 8));
        assertEquals(0, world.light(20, 64, 8));
    }

    @Test void failedReadKeepsBoundaryWithoutBusyRetryUntilNaturalLoad() {
        World world = new World();
        LightingFixture b = world.chunk(1, 0);
        world.stale(b, 14, 64, 8);
        world.boundary.mounted(b, true);
        world.reads.get(Level.chunkHash(0, 0)).complete(new ChunkLoadResult(ChunkLoadResult.Status.FAILED, null, new IllegalStateException("disk failed")));
        for (int i = 0; i < 20; i++) world.boundary.tick();
        assertTrue(world.boundary.pendingBoundaries() > 0);
        verify(world.level, times(1)).requestChunkLoadAsyncResult(0, 0);
        world.halo(1, 0);
        world.boundary.mounted(world.chunks.get(Level.chunkHash(0, 0)), false);
        world.drain();
        assertEquals(0, world.light(16, 64, 8));
    }

    @Test void unknownOuterFaceRetainsSavedContributionUntilNeighbourLoads() {
        World world = new World();
        LightingFixture a = world.chunk(0, 0), b = world.chunk(1, 0);
        world.stale(a, -1, 64, 8);
        world.boundary.mounted(b, false);
        world.drain();
        assertEquals(14, a.getBlockLight(0, 64, 8));
        assertTrue(world.boundary.pendingBoundaries() > 0);
        assertTrue(world.reads.isEmpty(), "repair initiated new IO");
    }

    @Test void secondRealEmitterSurvivesRemovalAndOpaqueBlocksKeepAttenuation() {
        World world = new World(); world.halo(0, 0);
        LightingFixture a = world.chunks.get(Level.chunkHash(0, 0));
        a.ids[LightingFixture.index(14, 64, 8)] = BlockID.GLOWSTONE;
        a.ids[LightingFixture.index(12, 64, 8)] = BlockID.GLOWSTONE;
        a.ids[LightingFixture.index(13, 64, 8)] = BlockID.STONE;
        world.boundary.mounted(a, false); world.drain();
        assertEquals(15, world.light(14, 64, 8));
        assertEquals(15, world.light(12, 64, 8));
        a.ids[LightingFixture.index(14, 64, 8)] = BlockID.AIR;
        world.boundary.mounted(a, false); world.drain();
        assertEquals(15, world.light(12, 64, 8));
        assertTrue(world.light(14, 64, 8) > 0);
    }

    @Test void scanningAndRelaxationStayBoundedAndRevisitDecreases() {
        World world = new World(); world.halo(0, 0);
        LightingFixture a = world.chunks.get(Level.chunkHash(0, 0));
        java.util.Arrays.fill(a.emitted, (byte) 15);
        world.boundary.mounted(a, false);
        a.blockReads = 0; a.lightWrites = 0;
        world.boundary.tick();
        assertTrue(world.boundary.hasWork());
        assertTrue(a.blockReads <= 8L * BlockLightBoundary.WORK_PER_TICK);
        assertTrue(a.lightWrites <= BlockLightBoundary.WORK_PER_TICK);
    }

    @Test void unloadedSeedNeverWritesIntoOldChunkAndReplacementIsRecomputed() {
        World world = new World(); world.halo(0, 0);
        LightingFixture a = world.chunks.get(Level.chunkHash(0, 0));
        world.stale(a, 14, 64, 8);
        world.boundary.mounted(a, false);
        world.boundary.tick();
        LightingFixture replacement = world.chunk(0, 0);
        replacement.ids[LightingFixture.index(14, 64, 8)] = BlockID.GLOWSTONE;
        world.boundary.mounted(replacement, false);
        long before = a.lightWrites;
        world.drain();
        assertEquals(before, a.lightWrites);
        assertEquals(13, world.light(16, 64, 8));
    }

    @Test void generationAndPopulationMustFinishBeforeBoundaryRepair() {
        World world = new World(); world.halo(0, 0);
        LightingFixture a = world.chunks.get(Level.chunkHash(0, 0));
        a.setLightPopulated(false);
        a.ids[LightingFixture.index(15, 64, 8)] = BlockID.GLOWSTONE;
        world.boundary.mounted(a, false); world.drain();
        assertEquals(0, world.light(16, 64, 8));
        a.setLightPopulated(true);
        world.boundary.mounted(a, false); world.drain();
        assertEquals(14, world.light(16, 64, 8));
    }

    @Test void diagonalHaloContributesWithoutReadingBeyondIt() {
        World world = new World();
        LightingFixture b = world.chunk(0, 0);
        world.boundary.mounted(b, true);
        world.halo(0, 0);
        LightingFixture diagonal = world.chunks.get(Level.chunkHash(1, 1));
        diagonal.ids[LightingFixture.index(0, 64, 0)] = BlockID.GLOWSTONE;
        world.boundary.mounted(diagonal, true); world.drain();
        assertEquals(13, world.light(15, 64, 15));
        assertEquals(8, world.reads.size());
    }

    @Test void removalAfterHaloUnloadReadsDiagonalFromTheOriginalRoot() {
        World world = new World();
        LightingFixture b = world.chunk(0, 0);
        world.stale(b, 14, 64, 14);
        doCallRealMethod().when(world.level).updateBlockLight(anyMap());
        Map<Long, Set<Integer>> changes = new HashMap<>();
        changes.put(Level.chunkHash(0, 0), Set.of(Level.localBlockHash(14, 64, 14, world.level.getDimensionData())));
        world.level.updateBlockLight(changes);
        assertTrue(world.reads.containsKey(Level.chunkHash(1, 1)), "ordinary corner removal omitted the diagonal");
        // Use Level's real boundary instance, created by the production removal path.
        try {
            var field = Level.class.getDeclaredField("blockLightBoundary"); field.setAccessible(true);
            BlockLightBoundary actual = (BlockLightBoundary) field.get(world.level);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                LightingFixture halo = world.chunk(dx, dz);
                world.stale(halo, 14, 64, 14);
                actual.mounted(halo, true);
            }
            for (int tick = 0; tick < 1500 && actual.hasWork(); tick++) actual.tick();
            assertFalse(actual.hasWork());
            assertEquals(0, world.light(15, 64, 15));
            assertEquals(8, world.reads.size(), "ordinary wave halo expanded recursively");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void emptySectionInspectionConsumesBudgetAcrossManyChunks() {
        Level level = mock(Level.class);
        Map<Long, BaseFullChunk> chunks = new HashMap<>();
        when(level.getMinBlockY()).thenReturn(-64); when(level.getMaxBlockY()).thenReturn(319);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(inv ->
                chunks.get(Level.chunkHash(inv.getArgument(0), inv.getArgument(1))));
        ChunkSection section = mock(ChunkSection.class);
        when(section.getLightArray()).thenReturn(new byte[2048]);
        BlockLightBoundary boundary = new BlockLightBoundary(level);
        for (int x = 0; x < 40; x++) {
            Chunk chunk = mock(Chunk.class);
            when(chunk.getX()).thenReturn(x); when(chunk.getZ()).thenReturn(0);
            when(chunk.isGenerated()).thenReturn(true); when(chunk.isPopulated()).thenReturn(true);
            when(chunk.isLightPopulated()).thenReturn(true);
            when(chunk.getSectionOffset()).thenReturn(4);
            ChunkSection[] sections = new ChunkSection[24]; java.util.Arrays.fill(sections, section);
            when(chunk.getSections()).thenReturn(sections);
            chunks.put(Level.chunkHash(x, 0), chunk);
        }
        for (BaseFullChunk chunk : chunks.values()) boundary.mounted(chunk, false);
        boundary.tick();
        assertTrue(boundary.hasWork(), "one tick drained all dark chunks without accounting work");
        verify(section, atMost(BlockLightBoundary.WORK_PER_TICK / 256)).getLightArray();
    }

    @Test void repeatedColdCellsAdmitTheOriginalHaloOnlyOncePerInvocation() {
        World world = new World(); world.chunk(0, 0);
        java.util.List<Long> roots = new java.util.ArrayList<>(java.util.List.of(Level.chunkHash(0, 0)));
        clearInvocations(world.level);
        for (int i = 0; i < 100; i++) world.boundary.defer(16, 64 + i, 8, roots);
        assertEquals(8, world.reads.size());
        verify(world.level, atMost(20)).getChunkIfLoaded(anyInt(), anyInt());
    }

    @Test void provenMissingNeighbourIsAirAndCanRemoveGhostLight() {
        World world = new World(); world.halo(0, 0);
        LightingFixture b = world.chunks.get(Level.chunkHash(0, 0));
        world.stale(b, 16, 64, 8);
        LightingFixture a = world.chunks.get(Level.chunkHash(1, 0));
        a.setGenerated(false); a.setPopulated(false); a.setLightPopulated(false);
        world.boundary.mounted(b, false); world.drain();
        assertEquals(0, world.light(15, 64, 8));
    }
}
