package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollisionCandidateResultP03Test {
    @BeforeAll static void init() { MockServer.init(); }

    @Test void emptyMissCommitsCacheAndKeepsEmptyResultOnHitAndMovedRescan() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        CollisionHelper helper = world.helperAt(5.5, 5.5);
        AxisAlignedBB box = new SimpleAxisAlignedBB(5.2, 64, 5.2, 5.8, 65.8, 5.8);
        AxisAlignedBB scan = box.grow(0.5, 0.5, 0.5);
        assertSame(Block.EMPTY_ARRAY, helper.getCollisionCandidates(scan, box));
        assertTrue(helper.scanCache().holds(world.level, world.chunk(0, 0), scan, box));
        assertSame(Block.EMPTY_ARRAY, helper.getCollisionCandidates(scan, box));
        AxisAlignedBB moved = box.getOffsetBoundingBox(0.125, 0, 0);
        AxisAlignedBB movedScan = moved.grow(0.5, 0.5, 0.5);
        assertSame(Block.EMPTY_ARRAY, helper.getCollisionCandidates(movedScan, moved));
        assertTrue(helper.scanCache().holds(world.level, world.chunk(0, 0), movedScan, moved));
        assertFalse(helper.scanCache().holds(world.level, world.chunk(0, 0), scan, box));
    }

    @Test void fullAndGrowingResultsAreFreshAndKeepEveryBlockInOrder() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 64);
        for (int count : new int[]{1, 4, 16, 17, 33}) {
            CollisionHelper helper = world.helperAt(0.5, 0.5);
            AxisAlignedBB box = new SimpleAxisAlignedBB(0.2, 64.2, 0.2, count - 0.2, 64.8, 0.8);
            Block[] first = helper.getCollisionCandidates(box, box);
            assertEquals(count, first.length);
            for (int i = 0; i < count; i++) {
                assertEquals(BlockID.STONE, first[i].getId());
                assertEquals(i, first[i].getFloorX());
                assertEquals(64, first[i].getFloorY());
                assertEquals(0, first[i].getFloorZ());
            }
            Block[] second = helper.getCollisionCandidates(box, box);
            assertNotSame(first, second);
            assertNotSame(first[0], second[0]);
            first[0] = null;
            assertNotNull(second[0]);
            second[0].x = 99;
            Block[] third = helper.getCollisionCandidates(box, box);
            assertEquals(0, third[0].getFloorX());
        }
    }

    @Test void partialResultIsTrimmedWithoutExposingCapacityOrCacheStorage() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 64);
        CollisionHelper helper = world.helperAt(5.5, 5.5);
        AxisAlignedBB box = new SimpleAxisAlignedBB(5.2, 64.2, 5.2, 5.8, 64.8, 5.8);
        AxisAlignedBB scan = box.grow(0.5, 0.5, 0.5);
        Block[] first = helper.getCollisionCandidates(scan, box);
        assertEquals(1, first.length);
        assertEquals(5, first[0].getFloorX());
        Block[] second = helper.getCollisionCandidates(scan, box);
        assertEquals(1, second.length);
        assertNotSame(first, second);
        first[0] = null;
        assertNotNull(second[0]);
    }

    @Test void versionedBlockEditsInvalidateZeroAndNonzeroCandidateSnapshots() {
        var world = CollisionCandidatesTest.World.flat(BlockID.AIR, 64);
        CollisionHelper helper = world.helperAt(5.5, 5.5);
        AxisAlignedBB box = new SimpleAxisAlignedBB(5.2, 64.2, 5.2, 5.8, 64.8, 5.8);
        AxisAlignedBB scan = box.grow(0.5, 0.5, 0.5);
        assertSame(Block.EMPTY_ARRAY, helper.getCollisionCandidates(scan, box));
        world.set(5, 64, 5, BlockID.STONE);
        Block[] stone = helper.getCollisionCandidates(scan, box);
        assertEquals(1, stone.length);
        assertEquals(BlockID.STONE, stone[0].getId());
        world.set(5, 64, 5, BlockID.AIR);
        assertSame(Block.EMPTY_ARRAY, helper.getCollisionCandidates(scan, box));
        assertEquals(BlockID.STONE, stone[0].getId());
    }
}
