package cn.nukkit.level;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.generic.LightingFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

class BlockLightLateBoundaryTest {
    @BeforeAll static void blocks() { BlockLightBoundaryTest.blocks(); }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 1, 0", "0, 0, -1, 0", "0, 0, 0, 1", "0, 0, 0, -1",
        "-2, -3, 1, 0", "-2, -3, -1, 0", "-2, -3, 0, 1", "-2, -3, 0, -1"
    })
    void lateMountRevisitsAFaceAlreadyScannedByAnUnfinishedSeed(int ax, int az, int dx, int dz) {
        BlockLightBoundaryTest.World world = new BlockLightBoundaryTest.World();
        world.halo(ax, az);
        world.chunks.remove(Level.chunkHash(ax + dx, az + dz));
        LightingFixture a = world.chunks.get(Level.chunkHash(ax, az));
        int x = (ax << 4) + (dx > 0 ? 15 : dx < 0 ? 0 : 8);
        int z = (az << 4) + (dz > 0 ? 15 : dz < 0 ? 0 : 8);
        emitter(world, a, x, z);
        world.boundary.mounted(a, false);
        // Nine budgeted scans reach above y=64, but do not finish this chunk's seed.
        for (int tick = 0; tick < 9; tick++) world.boundary.tick();
        assertTrue(world.boundary.hasWork());
        assertEquals(15, world.light(x, 64, z));
        LightingFixture b = world.chunk(ax + dx, az + dz);
        world.boundary.mounted(b, false);
        // Repeated notifications of the same generation must not starve the rewind.
        for (int tick = 0; tick < 500 && world.light(x + dx, 64, z + dz) != 14; tick++) {
            world.boundary.mounted(b, false);
            world.boundary.tick();
            clearInvocations(world.level); // Keep stress proof independent of Mockito's call-log heap.
        }
        assertEquals(14, world.light(x + dx, 64, z + dz));
        // New explicit requests may recreate completed seeds, but cannot prevent the newly
        // opened face from finishing; once requests stop, the rest must converge too.
        world.drain();
        assertFalse(world.boundary.hasWork());
        assertTrue(world.reads.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 1, 0, false", "0, 0, -1, 0, false",
        "0, 0, 0, 1, false", "0, 0, 0, -1, false",
        "-2, -3, 1, 0, false", "-2, -3, -1, 0, false",
        "-2, -3, 0, 1, false", "-2, -3, 0, -1, false",
        "0, 0, 1, 0, true", "0, 0, -1, 0, true",
        "0, 0, 0, 1, true", "0, 0, 0, -1, true",
        "-2, -3, 1, 0, true", "-2, -3, -1, 0, true",
        "-2, -3, 0, 1, true", "-2, -3, 0, -1, true"
    })
    void stableSavedLightPropagatesAcrossALateMountedFaceInBothDirections(
            int ax, int az, int dx, int dz, boolean lateEmitter) {
        BlockLightBoundaryTest.World world = new BlockLightBoundaryTest.World();
        LightingFixture a = world.chunk(ax, az);
        int edgeX = (ax << 4) + (dx > 0 ? 15 : dx < 0 ? 0 : 8);
        int edgeZ = (az << 4) + (dz > 0 ? 15 : dz < 0 ? 0 : 8);
        if (!lateEmitter) emitter(world, a, edgeX, edgeZ);
        world.boundary.mounted(a, true);
        world.drain();
        assertTrue(world.reads.containsKey(Level.chunkHash(ax + dx, az + dz)));

        // Loading B later must work even when A's source and saved light need no change.
        world.halo(ax, az);
        LightingFixture b = world.chunks.get(Level.chunkHash(ax + dx, az + dz));
        if (lateEmitter) emitter(world, b, edgeX + dx, edgeZ + dz);
        world.boundary.mounted(b, true);
        world.drain();
        int sourceX = edgeX + (lateEmitter ? dx : 0);
        int sourceZ = edgeZ + (lateEmitter ? dz : 0);
        int targetX = edgeX + (lateEmitter ? 0 : dx);
        int targetZ = edgeZ + (lateEmitter ? 0 : dz);
        assertEquals(15, world.light(sourceX, 64, sourceZ));
        assertEquals(14, world.light(targetX, 64, targetZ));
        assertEquals(13, world.light(targetX + (lateEmitter ? -dx : dx),
                64, targetZ + (lateEmitter ? -dz : dz)));
        assertEquals(8, world.reads.size(), "boundary repair expanded the cold halo");
        verify(world.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(world.level, never()).getBlockIdAt(anyInt(), anyInt(), anyInt());
    }

    private static void emitter(BlockLightBoundaryTest.World world, LightingFixture chunk,
                                int x, int z) {
        chunk.ids[LightingFixture.index(x & 15, 64, z & 15)] = BlockID.GLOWSTONE;
        world.stale(chunk, x, 64, z);
    }
}
