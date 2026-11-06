package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticBlockBreakTraceTest {

    @Test
    void outerScopeKeepsEveryCellChangedByNestedCompoundBreaks() {
        AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        Block bottom = at(Block.get(BlockID.DOUBLE_PLANT), 4, 70, -2, 0);
        Block top = at(Block.get(BlockID.DOUBLE_PLANT), 4, 71, -2, 0);
        Block water = at(Block.get(BlockID.WATER), 4, 70, -2, 0);
        Block air = at(Block.get(BlockID.AIR), 4, 71, -2, 0);

        try (AutomaticBlockBreakTrace.Scope outer = trace.enter()) {
            assertTrue(outer.isOutermost());
            try (AutomaticBlockBreakTrace.Scope nested = trace.enter()) {
                assertFalse(nested.isOutermost());
                trace.record(bottom, water);
                trace.record(top, air);
                assertArrayEquals(new Block[] {bottom, top}, nested.changedBlocks());
            }
            assertArrayEquals(new Block[] {bottom, top}, outer.changedBlocks());
        }
    }

    @Test
    void stateChangesSecondaryLayersAndAirPlacementsAreNotRemovals() {
        AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        Block doorClosed = at(Block.get(BlockID.WOODEN_DOOR_BLOCK, 0), 1, 2, 3, 0);
        Block doorOpen = at(Block.get(BlockID.WOODEN_DOOR_BLOCK, 4), 1, 2, 3, 0);
        Block overlay = at(Block.get(BlockID.STONE), 1, 2, 3, 1);
        Block air = at(Block.get(BlockID.AIR), 5, 6, 7, 0);

        try (AutomaticBlockBreakTrace.Scope scope = trace.enter()) {
            trace.record(doorClosed, doorOpen);
            trace.record(overlay, Block.get(BlockID.AIR));
            trace.record(air, Block.get(BlockID.STONE));
            assertArrayEquals(Block.EMPTY_ARRAY, scope.changedBlocks());
        }
    }

    @Test
    void directDropsKeepTheExactSourceAcrossAThreeBlockChainButExcludeContents() {
        AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        Block first = at(Block.get(BlockID.POINTED_DRIPSTONE), 8, 40, 9, 0);
        Block second = at(Block.get(BlockID.POINTED_DRIPSTONE), 8, 41, 9, 0);
        Block third = at(Block.get(BlockID.POINTED_DRIPSTONE), 8, 42, 9, 0);

        assertFalse(trace.isActive());
        try (AutomaticBlockBreakTrace.Scope ignored = trace.enter()) {
            assertTrue(trace.isActive());
            for (Block source : new Block[] {first, second, third}) {
                assertSame(source, trace.directDropSource(source));
                Block air = at(
                        Block.get(BlockID.AIR),
                        source.getFloorX(),
                        source.getFloorY(),
                        source.getFloorZ(),
                        0);
                trace.record(source, air);
                assertSame(source, trace.directDropSource(air));
            }

            trace.enterBlockEntityDrops();
            try {
                assertNull(trace.directDropSource(first));
            } finally {
                trace.leaveBlockEntityDrops();
            }
        }
        assertFalse(trace.isActive());
    }

    private static Block at(Block block, int x, int y, int z, int layer) {
        block.x = x;
        block.y = y;
        block.z = z;
        block.layer = layer;
        return block;
    }
}
