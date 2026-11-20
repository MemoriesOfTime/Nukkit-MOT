package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.CollisionHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real block shapes and fastMove grounding; world I/O and fall side effects are isolated. */
class PlayerFootSupportTest {
    @BeforeAll static void initialize() { MockServer.init(); }

    @Test void exposedFloorSupportsFeet() {
        World world = new World();
        world.put(0, 63, 0, Block.STONE, 0);
        assertTrue(world.support(probe(0.2, 64, 0.2, 0.8, 0.8)));
    }

    @Test void stackedWallDoesNotExposeTheLowerBlocksTop() {
        World world = new World();
        world.put(0, 63, 0, Block.STONE, 0);
        world.put(0, 64, 0, Block.STONE, 0);
        AxisAlignedBB feet = probe(-0.5, 64, 0.2, 0.1, 0.8);
        assertTrue(CollisionHelper.hasCollisionBlocks(world.level, null, feet));
        assertFalse(world.support(feet), "A hidden top inside the wall is not foot support");
    }

    @Test void bodyHeightWallContactCannotGroundAnAirbornePlayer() {
        World world = new World();
        world.put(0, 65, 0, Block.STONE, 0);
        assertTrue(CollisionHelper.hasCollisionBlocks(world.level, null,
                new SimpleAxisAlignedBB(-0.5, 63.25, 0.2, 0.1, 65.8, 0.8)));
        assertFalse(world.support(probe(-0.5, 64, 0.2, 0.1, 0.8)));
    }

    @Test void nativeStairLowerTreadIsExposedButCoveredHalfIsNot() {
        World world = new World();
        world.put(0, 63, 0, Block.WOOD_STAIRS, 0);
        assertTrue(world.support(probe(0.1, 63.5, 0.2, 0.4, 0.8)));
        assertFalse(world.support(probe(0.6, 63.5, 0.2, 0.9, 0.8)));
        assertTrue(world.support(probe(0.4, 63.5, 0.2, 0.6, 0.8)),
                "An exposed part of a tread remains support when another part is covered");
        assertTrue(world.support(probe(0.6, 64, 0.2, 0.9, 0.8)));
    }

    @Test void narrowPositiveOverlapAndCoordinateToleranceStillFindFloor() {
        World world = new World();
        world.put(0, 63, 0, Block.STONE, 0);
        assertTrue(world.support(probe(-0.6, 64.00001, 0.2, 0.000001, 0.8)));
        assertFalse(world.support(probe(-0.6, 64, 0.2, 0, 0.8)),
                "Touching a boundary without positive area is not support");
        assertFalse(world.support(probe(0.2, 64.8, 0.2, 0.8, 0.8)),
                "The original bounded support depth is preserved");
    }

    @Test void unloadedAndOutOfRangeColumnsAreNeverLoadedForSupport() {
        World world = new World();
        world.put(0, 63, 0, Block.STONE, 0);
        world.chunks.clear();
        assertFalse(world.support(probe(0.2, 64, 0.2, 0.8, 0.8)));
        assertFalse(world.support(probe(0.2, -65, 0.2, 0.8, 0.8)));
        verify(world.level, never()).getChunk(anyInt(), anyInt());
        verify(world.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(world.level, never()).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test void malformedProbeIsRejectedBeforeWorldReads() {
        Level level = mock(Level.class);
        assertFalse(CollisionHelper.hasExposedBlockSupport(level, null,
                new SimpleAxisAlignedBB(Double.NaN, 0, 0, 1, 1, 1)));
        assertFalse(CollisionHelper.hasExposedBlockSupport(level, null,
                new SimpleAxisAlignedBB(1, 0, 0, -1, 1, 1)));
        verifyNoInteractions(level);
    }

    @Test void fastMoveSendsUnsupportedStateToFallProcessingForPlayers() {
        World world = new World();
        world.put(0, 63, 0, Block.STONE, 0);
        world.put(0, 64, 0, Block.STONE, 0);
        FootPlayer player = mock(FootPlayer.class, CALLS_REAL_METHODS);
        player.prepare(world.level, new SimpleAxisAlignedBB(-0.5, 64, 0.2, 0.1, 65.8, 0.8));
        assertTrue(player.fastMove(0.01, 0, 0));
        assertFalse(player.onGround);
        assertTrue(player.fallCalled);
        assertFalse(player.fallGrounded, "Wall contact must not reset fall state before the final player check");
    }

    @Test void fastMoveStillGroundsPlayersOnExposedFloor() {
        World world = new World();
        world.put(0, 63, 0, Block.STONE, 0);
        FootPlayer player = mock(FootPlayer.class, CALLS_REAL_METHODS);
        player.prepare(world.level, new SimpleAxisAlignedBB(0.2, 64, 0.2, 0.8, 65.8, 0.8));
        assertTrue(player.fastMove(0.01, 0, 0));
        assertTrue(player.onGround);
        assertTrue(player.fallCalled);
        assertTrue(player.fallGrounded);
    }

    @Test void nonPlayerFastMoveKeepsItsExistingBodyProbe() {
        World world = new World();
        world.put(0, 64, 0, Block.STONE, 0);
        OtherEntity entity = mock(OtherEntity.class, CALLS_REAL_METHODS);
        entity.prepare(world.level, new SimpleAxisAlignedBB(-0.5, 64, 0.2, 0.1, 65.8, 0.8));
        assertTrue(entity.fastMove(0.01, 0, 0));
        assertTrue(entity.onGround);
        assertTrue(entity.fallGrounded);
    }

    private static AxisAlignedBB probe(double minX, double feetY, double minZ, double maxX, double maxZ) {
        return new SimpleAxisAlignedBB(minX, feetY - 0.75, minZ, maxX, feetY + 1.0E-4, maxZ);
    }

    private static final class World {
        final Level level = mock(Level.class);
        final Map<String, Block> blocks = new HashMap<>();
        final Map<Long, BaseFullChunk> chunks = new HashMap<>();
        World() {
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(call ->
                    chunks.get(Level.chunkHash(call.getArgument(0), call.getArgument(1))));
            when(level.getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenAnswer(call -> blocks.get(key(call.getArgument(1), call.getArgument(2), call.getArgument(3))));
        }
        void put(int x, int y, int z, int id, int meta) {
            blocks.put(key(x, y, z), Block.get(id, meta, level, x, y, z));
            int cx = x >> 4, cz = z >> 4;
            chunks.computeIfAbsent(Level.chunkHash(cx, cz), ignored -> {
                BaseFullChunk chunk = mock(BaseFullChunk.class);
                when(chunk.getX()).thenReturn(cx);
                when(chunk.getZ()).thenReturn(cz);
                when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenAnswer(call -> {
                    Block block = blocks.get(key((cx << 4) + call.getArgument(0, Integer.class),
                            call.getArgument(1), (cz << 4) + call.getArgument(2, Integer.class)));
                    return block == null ? Block.AIR : block.getId();
                });
                return chunk;
            });
        }
        boolean support(AxisAlignedBB probe) { return CollisionHelper.hasExposedBlockSupport(level, null, probe); }
        static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }
    }

    // Mockito bypasses constructors; only world I/O and fall side effects are overridden.
    private static class FootPlayer extends Player {
        boolean fallCalled, fallGrounded;
        FootPlayer() { super(null, 0L, null); }
        void prepare(Level level, AxisAlignedBB box) {
            this.level = level; this.boundingBox = box; this.server = MockServer.get();
        }
        @Override protected void checkChunks() { }
        @Override protected void updateFallState(boolean grounded) { fallCalled = true; fallGrounded = grounded; }
    }

    private static class OtherEntity extends Entity {
        boolean fallGrounded;
        OtherEntity() { super(null, new CompoundTag()); }
        void prepare(Level level, AxisAlignedBB box) {
            this.level = level; this.boundingBox = box; this.server = MockServer.get();
        }
        @Override public int getNetworkId() { return 0; }
        @Override protected void checkChunks() { }
        @Override protected void updateFallState(boolean grounded) { fallGrounded = grounded; }
    }
}
