package cn.nukkit.level;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticBlockBreakTraceTest {

    @Test
    void outerScopeKeepsEveryCellChangedByNestedCompoundBreaks() {
        AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        Block bottom = at(Block.get(BlockID.DOUBLE_PLANT), 4, 70, -2, 0);
        Block top = at(Block.get(BlockID.DOUBLE_PLANT), 4, 71, -2, 0);
        Block water = at(Block.get(BlockID.WATER), 4, 70, -2, 0);
        Block air = at(Block.get(BlockID.AIR), 4, 71, -2, 0);

        try (AutomaticBlockBreakTrace.Scope outer = trace.enter(null)) {
            assertTrue(outer.isOutermost());
            try (AutomaticBlockBreakTrace.Scope nested = trace.enter(null)) {
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

        try (AutomaticBlockBreakTrace.Scope scope = trace.enter(null)) {
            trace.record(doorClosed, doorOpen);
            trace.record(overlay, Block.get(BlockID.AIR));
            trace.record(air, Block.get(BlockID.STONE));
            assertArrayEquals(Block.EMPTY_ARRAY, scope.changedBlocks());
        }
    }

    @Test
    void nestedScopeInheritsThePlayerThatStartedTheChain() {
        AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        Player player = Mockito.mock(Player.class);

        try (AutomaticBlockBreakTrace.Scope automatic = trace.enter(null)) {
            assertNull(automatic.getOrigin());
            try (AutomaticBlockBreakTrace.Scope nested = trace.enter(null)) {
                assertNull(nested.getOrigin(), "an automatic chain has no player origin");
            }
        }

        try (AutomaticBlockBreakTrace.Scope outer = trace.enter(player)) {
            assertSame(player, outer.getOrigin());
            try (AutomaticBlockBreakTrace.Scope nested = trace.enter(null)) {
                assertSame(player, nested.getOrigin(), "nested breaks inherit the chain origin");
            }
        }

        Player other = Mockito.mock(Player.class);
        try (AutomaticBlockBreakTrace.Scope outer = trace.enter(player)) {
            try (AutomaticBlockBreakTrace.Scope nested = trace.enter(other)) {
                assertSame(other, nested.getOrigin(), "an explicit player wins over inheritance");
            }
        }
    }

    @Test
    void firstRemovalOfAReplacedCellWins() {
        AutomaticBlockBreakTrace trace = new AutomaticBlockBreakTrace();
        Block original = at(Block.get(BlockID.CACTUS), 5, 10, 5, 0);
        Block transition = at(Block.get(BlockID.STONE), 5, 10, 5, 0);
        Block replayed = at(Block.get(BlockID.CACTUS), 5, 10, 5, 0);
        Block air = at(Block.get(BlockID.AIR), 5, 10, 5, 0);

        try (AutomaticBlockBreakTrace.Scope scope = trace.enter(null)) {
            trace.record(original, air);
            trace.record(transition, air);
            assertArrayEquals(new Block[] {original}, scope.changedBlocks());
            trace.record(replayed, air);
            assertArrayEquals(new Block[] {original}, scope.changedBlocks());
        }
    }

    private static Block at(Block block, int x, int y, int z, int layer) {
        block.x = x;
        block.y = y;
        block.z = z;
        block.layer = layer;
        return block;
    }
}
