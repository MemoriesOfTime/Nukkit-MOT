package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollisionHelperAirTest {
    private final Level level = mock(Level.class);
    private final Entity entity = mock(Entity.class);
    private final Map<Long, FullChunk> chunks = new HashMap<>();
    private final AxisAlignedBB bounds = new SimpleAxisAlignedBB(-0.5, 64, 0.1, 0.5, 65, 0.9);

    @BeforeEach
    void prepareWorld() {
        when(level.getMinBlockY()).thenReturn(-64);
        when(level.getMaxBlockY()).thenReturn(319);
        when(level.isYInRange(anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(0);
            return y >= -64 && y <= 319;
        });
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(invocation ->
                chunks.get(Level.chunkHash(invocation.getArgument(0), invocation.getArgument(1))));
        doCallRealMethod().when(level).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
        doCallRealMethod().when(level).getBlock(anyInt(), anyInt(), anyInt(), anyBoolean());
        when(entity.getLevel()).thenReturn(level);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void customPredicateCanFindAirInLoadedAndUnloadedChunks(boolean targetFirst) {
        addChunk(0, 0, Block.AIR);

        List<Block> result = CollisionHelper.getCollisionBlocks(level, bounds, entity, targetFirst, true,
                block -> block.getId() == Block.AIR);

        assertEquals(targetFirst ? 1 : 4, result.size());
        assertTrue(result.stream().allMatch(Block::isAir));
        // Traversal starts in the unloaded negative-X chunk, which also represents air.
        assertEquals(-1, result.get(0).getFloorX());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    @Test
    void inclusiveBlockRangesDoNotVisitAnExtraFractionalUpperLayer() {
        for (double[] range : new double[][]{{0.1, 0.9}, {-0.9, -0.1}, {-1.9, -1.1},
                {-0.1, 0.1}, {0.0, 1.0}, {-1.0, 0.0}, {-16.1, -15.9}}) {
            AxisAlignedBB box = new SimpleAxisAlignedBB(range[0], 64.1, 0.1, range[1], 64.9, 0.9);
            List<Block> result = CollisionHelper.getCollisionBlocks(level, box, entity, false, true,
                    block -> block.getId() == Block.AIR);
            List<Integer> expected = java.util.stream.IntStream.rangeClosed((int) Math.floor(range[0]),
                    (int) Math.floor(range[1])).boxed().toList();
            assertEquals(expected, result.stream().map(Block::getFloorX).toList(),
                    "inclusive cells for " + range[0] + ".." + range[1]);
            assertTrue(result.stream().allMatch(block -> block.getFloorY() == 64 && block.getFloorZ() == 0));
        }
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    @Test
    void entityQueriesUseInclusiveContainingChunksWithoutAnExtraFractionalChunk() {
        java.util.List<String> visited = new java.util.ArrayList<>();
        when(level.getChunkEntities(anyInt(), anyInt(), anyBoolean())).thenAnswer(i -> {
            visited.add(i.getArgument(0) + ":" + i.getArgument(1));
            return Map.of();
        });
        doCallRealMethod().when(level).getNearbyEntities(any(AxisAlignedBB.class), nullable(Entity.class),
                anyBoolean(), eq(false));
        for (int shift : new int[]{-16, 0, 16}) {
            for (double end : new double[]{12.1, 14.0}) {
                AxisAlignedBB box = new SimpleAxisAlignedBB(2.1 + shift, 64, 2.1 + shift,
                        end + shift, 65, end + shift);
                int min = shift / 16;
                int max = (int) Math.floor((end + shift + 2) / 16);
                java.util.List<String> expected = new java.util.ArrayList<>();
                for (int x = min; x <= max; x++) {
                    for (int z = min; z <= max; z++) expected.add(x + ":" + z);
                }
                visited.clear();
                assertTrue(CollisionHelper.getCollidingEntities(level, box, null).isEmpty());
                assertEquals(expected, visited);
                visited.clear();
                assertEquals(0, level.getNearbyEntities(box, null, false, false).length);
                assertEquals(expected, visited);
            }
        }
    }

    @Test
    void defaultQueriesSkipAirWithoutMaterializingBlocksOrLoadingMissingChunks() {
        addChunk(0, 0, Block.AIR);

        assertTrue(CollisionHelper.getCollisionBlocks(level, bounds, entity, false, true).isEmpty());
        assertFalse(CollisionHelper.hasCollisionBlocks(level, entity, bounds, true));
        assertTrue(CollisionHelper.getCollisionCubes(level, entity, bounds, false, false).isEmpty());
        CollisionHelper helper = new CollisionHelper(entity);
        assertEquals(0, helper.getBlocksInBoundingBox(bounds).length);
        assertFalse(helper.isInsideBlock(bounds, Block.LAVA));

        verify(level, never()).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    @Test
    void explicitAirInsideQueryStillMaterializesAirAndChecksItsCollisionBox() {
        addChunk(0, 0, Block.AIR);

        assertFalse(new CollisionHelper(entity).isInsideBlock(bounds, Block.AIR));

        verify(level, atLeastOnce()).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), eq(0), eq(false));
    }

    @Test
    void solidBlocksAcrossChunkBoundaryKeepCoordinatesAndCollisionsWithWrongChunkHint() {
        addChunk(-1, 0, Block.STONE);
        addChunk(0, 0, Block.STONE);
        entity.chunk = addChunk(5, 5, Block.AIR);

        List<Block> result = CollisionHelper.getCollisionBlocks(level, bounds, entity, false, false);

        assertEquals(2, result.size());
        assertEquals(List.of(-1, 0), result.stream().map(Block::getFloorX).toList());
        assertTrue(result.stream().allMatch(block -> block.getId() == Block.STONE && block.getFloorY() == 64));
        assertTrue(CollisionHelper.hasCollisionBlocks(level, entity, bounds, true));
        assertEquals(2, CollisionHelper.getCollisionCubes(level, entity, bounds, false, false).size());
        assertTrue(new CollisionHelper(entity).isInsideBlock(bounds, Block.STONE));
        assertEquals(4, new CollisionHelper(entity).getBlocksInBoundingBox(bounds).length);
    }

    @Test
    void matchingEntityChunkHintWorksWithoutAnotherLoadedChunkLookup() {
        entity.chunk = addChunk(0, 0, Block.STONE);
        chunks.clear(); // The hint is still a valid chunk, even without a lookup entry.
        AxisAlignedBB localBounds = new SimpleAxisAlignedBB(0.1, -63.9, 0.1, 0.9, -63.1, 0.9);

        assertEquals(1, CollisionHelper.getCollisionBlocks(level, localBounds, entity, true, false).size());

        verify(level, never()).getChunkIfLoaded(anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void destinationQueriesIgnoreAnEntityChunkFromAnotherWorldAtTheSameCoordinates(boolean destinationIsSolid) {
        int destinationId = destinationIsSolid ? Block.STONE : Block.AIR;
        int sourceId = destinationIsSolid ? Block.AIR : Block.STONE;
        addChunk(0, 0, destinationId);
        Level sourceLevel = mock(Level.class);
        FullChunk sourceChunk = mock(BaseFullChunk.class);
        when(sourceChunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(sourceId);
        when(sourceChunk.getBlockState(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(new int[]{sourceId, 0});
        when(entity.getLevel()).thenReturn(sourceLevel);
        entity.chunk = sourceChunk;
        AxisAlignedBB destinationBounds = new SimpleAxisAlignedBB(0.1, 64.1, 0.1, 0.9, 64.9, 0.9);
        int expectedCollisions = destinationIsSolid ? 1 : 0;

        assertAll(
                () -> assertEquals(expectedCollisions,
                        CollisionHelper.getCollisionBlocks(level, destinationBounds, entity, true, false).size()),
                () -> assertEquals(expectedCollisions,
                        CollisionHelper.getCollisionBlocks(level, destinationBounds, entity, false, false).size()),
                () -> assertEquals(expectedCollisions,
                        CollisionHelper.getCollisionCubes(level, entity, destinationBounds, false, false).size()),
                () -> assertEquals(destinationIsSolid,
                        CollisionHelper.hasCollisionBlocks(level, entity, destinationBounds, true)));
        verify(sourceChunk, never()).getBlockId(anyInt(), anyInt(), anyInt(), anyInt());
        verify(sourceChunk, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
    }

    private FullChunk addChunk(int chunkX, int chunkZ, int id) {
        FullChunk chunk = mock(BaseFullChunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(id);
        when(chunk.getBlockState(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(new int[]{id, 0});
        chunks.put(Level.chunkHash(chunkX, chunkZ), chunk);
        return chunk;
    }
}
