package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStone;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollisionFilterResultP03Test {
    private final AxisAlignedBB bounds = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);

    @Test
    void fullResultsStayFreshAndDoNotShareInputOrPreviousResults() {
        for (int size : new int[]{1, 2, 4, 8, 9}) {
            Block[] input = new Block[size];
            for (int i = 0; i < size; i++) input[i] = new Collider(false, false, true);
            Block[] first = filter(input);
            Block[] second = filter(input);
            assertArrayEquals(input, first);
            assertArrayEquals(input, second);
            for (int i = 0; i < size; i++) {
                assertSame(input[i], first[i]);
                assertSame(input[i], second[i]);
            }
            assertNotSame(input, first);
            assertNotSame(first, second);
            first[0] = null;
            assertNotNull(input[0]);
            assertSame(input[0], second[0]);
        }
    }

    @Test
    void emptyAndRejectedResultsKeepExistingSharedEmptyArray() {
        assertSame(Block.EMPTY_ARRAY, filter(Block.EMPTY_ARRAY));
        assertSame(Block.EMPTY_ARRAY, filter(new Block[]{new Collider(false, false, false)}));
        assertSame(Block.EMPTY_ARRAY, filter(new Block[]{
                new Collider(true, false, false), new Collider(true, true, false)}));
    }

    @Test
    void partialResultsKeepAcceptedOrderWithoutSpareNullTail() {
        Block first = new Collider(false, false, true);
        Block rejected = new Collider(false, false, false);
        Block last = new Collider(false, false, true);
        assertArrayEquals(new Block[]{first, last}, filter(new Block[]{first, rejected, last, rejected}));
    }

    @Test
    void everyAcceptanceBranchCanAllocateFirstResult() {
        for (Collider collider : new Collider[]{new Collider(false, false, true),
                new Collider(true, false, true), new Collider(true, true, true)}) {
            assertArrayEquals(new Block[]{collider}, filter(new Block[]{collider}));
            assertEquals(1, collider.collisionCalls);
            assertTrue(collider.checkedCollisionShape);
            if (!collider.passThrough) {
                assertSame(bounds, collider.lastBounds);
            } else if (collider.dynamic) {
                assertEquals(-0.5, collider.lastBounds.getMinX());
                assertEquals(1.5, collider.lastBounds.getMaxX());
                assertEquals(-0.5, collider.lastBounds.getMinY());
                assertEquals(1.5, collider.lastBounds.getMaxY());
            } else {
                assertEquals(0.25, collider.lastBounds.getMinX());
                assertEquals(0.75, collider.lastBounds.getMaxX());
                assertEquals(0, collider.lastBounds.getMinY());
                assertEquals(1, collider.lastBounds.getMaxY());
            }
        }
    }

    private Block[] filter(Block[] input) {
        return CollisionHelper.filterCollisionBlocks(input, bounds, 0.5, 0.5, 0.5);
    }

    private static final class Collider extends BlockStone {
        private final boolean passThrough;
        private final boolean dynamic;
        private final boolean accepted;
        private int collisionCalls;
        private boolean checkedCollisionShape;
        private AxisAlignedBB lastBounds;

        private Collider(boolean passThrough, boolean dynamic, boolean accepted) {
            this.passThrough = passThrough;
            this.dynamic = dynamic;
            this.accepted = accepted;
        }

        @Override
        public boolean canPassThrough() {
            return passThrough;
        }

        @Override
        public boolean hasDynamicCollision() {
            return dynamic;
        }

        @Override
        public boolean collidesWithBB(AxisAlignedBB bb, boolean collision) {
            collisionCalls++;
            checkedCollisionShape = collision;
            lastBounds = bb;
            return accepted;
        }
    }
}
