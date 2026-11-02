package cn.nukkit.block;

import cn.nukkit.math.BlockFace;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScaffoldingStabilityTraversalTest {
    private static final MethodHandle STABILITY;
    private static volatile long sink;

    static {
        try {
            STABILITY = MethodHandles.privateLookupIn(BlockScaffolding.class, MethodHandles.lookup())
                    .findVirtual(BlockScaffolding.class, "calculateStability", MethodType.methodType(int.class));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static class Neighborhood extends BlockScaffolding {
        final Block[] neighbors = new Block[6];

        Neighborhood() {
            Arrays.fill(neighbors, new BlockAir());
        }

        @Override
        public Block getSide(BlockFace face) {
            return neighbors[face.getIndex()];
        }

        @Override
        public Block down() {
            return getSide(BlockFace.DOWN);
        }
    }

    private static final class TracedNeighborhood extends Neighborhood {
        final List<BlockFace> reads = new ArrayList<>();
        BlockFace failAt;

        @Override
        public Block getSide(BlockFace face) {
            reads.add(face);
            if (face == failAt) {
                throw new IllegalStateException("neighbor failed");
            }
            return super.getSide(face);
        }
    }

    private static int stability(BlockScaffolding block) throws Throwable {
        return (int) STABILITY.invokeExact(block);
    }

    @Test
    void solidBelowStopsBeforeTheNeighborTraversal() throws Throwable {
        TracedNeighborhood block = new TracedNeighborhood();
        block.neighbors[BlockFace.DOWN.getIndex()] = new BlockStone();
        assertEquals(0, stability(block));
        assertEquals(List.of(BlockFace.DOWN), block.reads);
    }

    @Test
    void unsupportedTraversalKeepsOrderAndRepeatedDownRead() throws Throwable {
        TracedNeighborhood block = new TracedNeighborhood();
        assertEquals(7, stability(block));
        assertEquals(List.of(BlockFace.DOWN, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST), block.reads);
    }

    @Test
    void everyCombinationOfFiveNeighborDistancesKeepsTheSameSupport() throws Throwable {
        Neighborhood block = new Neighborhood();
        BlockFace[] sides = {BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST};
        BlockScaffolding[] neighbors = new BlockScaffolding[sides.length];
        for (int i = 0; i < sides.length; i++) {
            neighbors[i] = new BlockScaffolding();
            block.neighbors[sides[i].getIndex()] = neighbors[i];
        }
        for (int combination = 0; combination < 32_768; combination++) {
            int value = combination;
            int expected = 7;
            for (int i = 0; i < sides.length; i++) {
                int distance = value & 7;
                value >>>= 3;
                neighbors[i].setStability(distance);
                expected = Math.min(expected, distance + (i == 0 ? 0 : 1));
            }
            assertEquals(expected, stability(block), "combination " + combination);
        }
    }

    @Test
    void supportAboveAndSolidBlocksBesideDoNotSupportScaffolding() throws Throwable {
        Neighborhood block = new Neighborhood();
        block.neighbors[BlockFace.UP.getIndex()] = new BlockScaffolding(0);
        block.neighbors[BlockFace.NORTH.getIndex()] = new BlockStone();
        block.neighbors[BlockFace.SOUTH.getIndex()] = new BlockStone();
        block.neighbors[BlockFace.WEST.getIndex()] = new BlockStone();
        block.neighbors[BlockFace.EAST.getIndex()] = new BlockStone();
        assertEquals(7, stability(block));
    }

    @Test
    void betterSideSupportIsStillReadAfterScaffoldingBelow() throws Throwable {
        TracedNeighborhood block = new TracedNeighborhood();
        block.neighbors[BlockFace.DOWN.getIndex()] = new BlockScaffolding(6);
        block.neighbors[BlockFace.EAST.getIndex()] = new BlockScaffolding(1);
        assertEquals(2, stability(block));
        assertEquals(BlockFace.EAST, block.reads.get(block.reads.size() - 1));
    }

    @Test
    void neighborFailureStopsAtTheOriginalDirection() {
        TracedNeighborhood block = new TracedNeighborhood();
        block.failAt = BlockFace.WEST;
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> stability(block));
        assertEquals("neighbor failed", failure.getMessage());
        assertEquals(List.of(BlockFace.DOWN, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.WEST), block.reads);
    }

    @Test
    void directionTraversalDoesNotAllocatePerCalculation() throws Throwable {
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean) platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        Neighborhood block = new Neighborhood();
        for (int warmup = 0; warmup < 1_000_000; warmup++) {
            sink = stability(block);
        }
        int count = 100_000;
        long sum = 0;
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < count; i++) {
            sum += stability(block);
        }
        long allocated = bean.getCurrentThreadAllocatedBytes() - before;
        sink = sum;
        assertEquals(7L * count, sum);
        assertTrue(allocated <= 8L * count, "allocated bytes per calculation: " + allocated / (double) count);
    }
}
