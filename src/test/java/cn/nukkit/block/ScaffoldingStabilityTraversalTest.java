package cn.nukkit.block;

import cn.nukkit.math.BlockFace;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScaffoldingStabilityTraversalTest {
    private static final MethodHandle STABILITY;

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
    void neighborFailureStopsAtTheOriginalDirection() throws Throwable {
        TracedNeighborhood block = new TracedNeighborhood();
        block.failAt = BlockFace.WEST;
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> stability(block));
        assertEquals("neighbor failed", failure.getMessage());
        assertEquals(List.of(BlockFace.DOWN, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.WEST), block.reads);
    }

    @Test
    void directionTraversalDoesNotAllocatePerCalculation(@TempDir Path directory) throws Exception {
        // 分配测量必须在独立 JVM 栅栏内：满套件共享 JVM 中 getSide/down 虚调用链被数百个 Block
        // 子类污染成 megamorphic，部分平台 C2 下每调用泄漏 ~288B（Jenkins/本地全量假红，与 MethodHandle
        // 无关）；栅栏另强制 -XX:-DoEscapeAnalysis，防止逃逸分析吞掉真实回归。同系列 6 个探针同构。
        // The allocation measurement must run in a fenced fresh JVM: in the shared full-suite JVM the
        // getSide/down virtual chain goes megamorphic across the hundreds of Block subclasses and C2
        // leaks ~288B/call on some platforms (the Jenkins/local full-suite false red, independent of
        // MethodHandle). The fence also forces -XX:-DoEscapeAnalysis so escape analysis cannot swallow a
        // real regression. Same shape as the six existing probes in this series.
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported());
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path log = directory.resolve("scaffolding-allocation.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms128m", "-Xmx128m", "-XX:-DoEscapeAnalysis", "-cp", cp,
                AllocationProbe.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            String output;
            try (var input = Files.newInputStream(log)) {
                output = new String(input.readNBytes(65_536), StandardCharsets.UTF_8);
            }
            assertTrue(finished, "allocation probe timed out\n" + output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "allocation probe must be reaped");
            }
        }
    }

    public static final class AllocationProbe {
        private static volatile long sink;

        public static void main(String[] args) throws Throwable {
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            Neighborhood block = new Neighborhood();
            for (int warmup = 0; warmup < 1_000_000; warmup++) {
                sink = stability(block);
            }
            long total = 0;
            for (int window = 0; window < 3; window++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                long sum = 0;
                for (int i = 0; i < 100_000; i++) {
                    sum += stability(block);
                }
                long bytes = bean.getCurrentThreadAllocatedBytes() - before;
                if (sum != 7L * 100_000) {
                    throw new AssertionError("unexpected stability sum: " + sum);
                }
                sink = sum;
                total += bytes;
                System.out.println("ALLOC,window=" + window + ",calls=100000,bytes=" + bytes
                        + ",bytesPerCall=" + bytes / 100000d);
            }
            if (total > 8L * 300_000) {
                throw new AssertionError("allocated bytes per calculation: " + total / 300000d);
            }
        }
    }
}
