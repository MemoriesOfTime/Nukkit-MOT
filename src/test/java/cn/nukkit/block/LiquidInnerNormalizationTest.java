package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone tests for the private, first falling-flow normalization. The allocation
 * contract assumes the separate add-temporary production patch is already applied;
 * it does not depend on that patch's tests or on final-normalization optimization.
 * The child disables EA deliberately. Default-JIT benefit requires separate ABBA.
 */
public final class LiquidInnerNormalizationTest {
    private static final int WEST = cell(-1, 0, 0, 0);
    private static final int EAST = cell(1, 0, 0, 0);
    private static final int NORTH = cell(0, 0, -1, 0);
    private static final int SOUTH = cell(0, 0, 1, 0);

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void sameBits(Vector3 expected, Vector3 actual) {
        require(Double.doubleToRawLongBits(expected.x) == Double.doubleToRawLongBits(actual.x), "x raw bits");
        require(Double.doubleToRawLongBits(expected.y) == Double.doubleToRawLongBits(actual.y), "y raw bits");
        require(Double.doubleToRawLongBits(expected.z) == Double.doubleToRawLongBits(actual.z), "z raw bits");
    }

    private static int cell(int x, int y, int z, int layer) {
        require(x >= -1 && x <= 1 && y >= -1 && y <= 1 && z >= -1 && z <= 1
                && layer >= 0 && layer <= 1, "read outside fixed overlay");
        return (((y + 1) * 3 + z + 1) * 3 + x + 1) * 2 + layer;
    }

    private static final class InnerOverlay extends Level {
        Block[] cells;
        long reads;
        int[] trace;
        int traceSize;
        boolean tracing;
        Runnable beforeGuard;

        private InnerOverlay() { super(null, "inner-test", "inner-test", null); }

        @Override
        public Block getBlock(int x, int y, int z, int layer) {
            int slot = cell(x, y, z, layer);
            reads++;
            if (tracing) {
                require(traceSize < trace.length, "trace overflow");
                trace[traceSize++] = slot;
            }
            // The first five reads accumulate flow; the sixth is the falling guard.
            if (reads == 6 && beforeGuard != null) beforeGuard.run();
            return cells[slot];
        }
    }

    private static final class InnerFixture {
        final BlockLiquid liquid;
        final InnerOverlay world;
        final Vector3 expected;
        final int[] expectedTrace;

        InnerFixture(boolean lava, boolean falling, Vector3 contribution) {
            liquid = fluid(lava, falling ? 8 : 0);
            world = new ObjenesisStd().newInstance(InnerOverlay.class);
            world.cells = new Block[54];
            world.trace = new int[32];
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        put(new BlockAir(), x, y, z, 0);
                        put(new BlockAir(), x, y, z, 1);
                    }
                }
            }
            put(liquid, 0, 0, 0, 0);
            Block west = fluid(lava, 1);
            put(west, -1, 0, 0, 0);
            // Only this neighbor contributes. Positioned custom blocks expose IEEE edges.
            west.setComponents(contribution.x, contribution.y, contribution.z);
            put(fluid(lava, 0), 1, 0, 0, 0);
            put(new BlockStone(), 0, 0, -1, 0);
            put(fluid(lava, 0), 0, 0, 1, 0);
            Vector3 accumulator = new Vector3(0d + contribution.x, 0d + contribution.y, 0d + contribution.z);
            // The east and south source blocks add zero after the west contribution.
            accumulator = accumulator.add(0, 0, 0).add(0, 0, 0);
            expected = falling ? accumulator.normalize().add(0, -6, 0).normalize() : accumulator.normalize();
            expectedTrace = falling ? new int[]{WEST, EAST, NORTH, NORTH + 1, SOUTH, NORTH}
                    : new int[]{WEST, EAST, NORTH, NORTH + 1, SOUTH};
        }

        private static BlockLiquid fluid(boolean lava, int meta) {
            return lava ? new BlockLava(meta) : new BlockWater(meta);
        }

        private void put(Block block, int x, int y, int z, int layer) {
            block.setComponents(x, y, z);
            block.level = world;
            block.layer = layer;
            world.cells[cell(x, y, z, layer)] = block;
        }

        void reset() {
            liquid.flowVector = null;
            world.reads = 0;
            world.traceSize = 0;
        }

        void verifyTrace(int repetitions) {
            require(world.reads == (long) expectedTrace.length * repetitions, "read count");
            require(world.traceSize == expectedTrace.length * repetitions, "trace length");
            for (int i = 0; i < world.traceSize; i++)
                require(world.trace[i] == expectedTrace[i % expectedTrace.length], "read order");
        }
    }

    private static double[][] vectors() {
        return new double[][]{
                {0d, -0d, 0d}, {3d, 4d, 0d}, {7.3d, 13.9d, 0.25d},
                {Double.MIN_VALUE, 0d, -Double.MIN_VALUE},
                {Double.MIN_NORMAL, -Double.MIN_NORMAL, 0d},
                {Double.MAX_VALUE, -1d, -Double.MIN_VALUE},
                {-1d, Double.POSITIVE_INFINITY, 0d},
                {Double.NEGATIVE_INFINITY, 1d, 2d}, {Double.NaN, 2d, 3d}
        };
    }

    static Stream<Object[]> numericCases() {
        return Stream.of(false, true).flatMap(lava -> Arrays.stream(vectors())
                .map(v -> new Object[]{lava, new Vector3(v[0], v[1], v[2])}));
    }

    @ParameterizedTest
    @MethodSource("numericCases")
    void fallingRetainsBitsReadOrderAndCacheOwnership(boolean lava, Vector3 contribution) {
        InnerFixture f = new InnerFixture(lava, true, contribution);
        f.world.tracing = true;
        Vector3 first = f.liquid.getFlowVector();
        sameBits(f.expected, first);
        f.verifyTrace(1);
        require(first == f.liquid.getFlowVector(), "cache identity");
        f.verifyTrace(1);
        first.setComponents(73, 17, 29);
        require(first == f.liquid.getFlowVector(), "mutable cache alias");
        sameBits(new Vector3(73, 17, 29), f.liquid.getFlowVector());
        f.verifyTrace(1);
        f.reset();
        Vector3 second = f.liquid.getFlowVector();
        require(first != second, "fresh result after invalidation");
        sameBits(f.expected, second);
        sameBits(new Vector3(73, 17, 29), first);
        f.verifyTrace(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallingGuardReentryPublishesIndependentResults(boolean lava) {
        InnerFixture f = new InnerFixture(lava, true, new Vector3(3, 4, 0));
        f.world.tracing = true;
        Vector3[] nested = new Vector3[1];
        f.world.beforeGuard = () -> {
            require(f.liquid.flowVector == null, "outer accumulator published before guard");
            f.world.beforeGuard = null;
            nested[0] = f.liquid.getFlowVector();
            require(f.liquid.flowVector == nested[0], "nested publication");
        };
        Vector3 outer = f.liquid.getFlowVector();
        f.verifyTrace(2);
        require(nested[0] != null && nested[0] != outer, "independent nested result");
        sameBits(f.expected, nested[0]);
        sameBits(f.expected, outer);
        require(f.liquid.getFlowVector() == outer, "outer publishes last");
        outer.x = 123;
        sameBits(f.expected, nested[0]);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallingGuardFailureDoesNotPublishPartialFlow(boolean lava) {
        InnerFixture f = new InnerFixture(lava, true, new Vector3(3, 4, 0));
        f.world.tracing = true;
        IllegalStateException failure = new IllegalStateException("guard callback");
        f.world.beforeGuard = () -> { throw failure; };
        try {
            f.liquid.getFlowVector();
            throw new AssertionError("guard callback did not throw");
        } catch (IllegalStateException actual) {
            require(actual == failure, "exception identity");
        }
        require(f.liquid.flowVector == null, "partial result published on failure");
        f.verifyTrace(1);
        f.world.beforeGuard = null;
        f.reset();
        sameBits(f.expected, f.liquid.getFlowVector());
        f.verifyTrace(1);
    }

    @Test
    void fallingAddsNoPrivateVectorBeyondFlatFlow(@TempDir Path temporary) throws Exception {
        Path output = temporary.resolve("allocation.log");
        Path loadedClasses = temporary.resolve("classes.log");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:-DoEscapeAnalysis", "-Xms128m", "-Xmx512m",
                "-Xlog:class+load=info:file=" + loadedClasses,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AllocationChild.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "allocation child timeout");
            String text = Files.readString(output);
            assertEquals(0, process.exitValue(), text);
            assertTrue(text.contains("INNER_NORMALIZATION=PASS"), text);
            String loaded = Files.readString(loadedClasses);
            assertFalse(loaded.contains("org.mockito."), "native child loaded Mockito");
            assertFalse(loaded.contains("net.bytebuddy."), "native child loaded ByteBuddy");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "allocation child was not reaped");
            }
        }
    }

    public static final class AllocationChild {
        private static volatile Vector3 sink;

        private static double run(InnerFixture f, ThreadMXBean bean, int count) {
            long bytes = 0;
            long beforeReads = f.world.reads;
            Vector3 previous = null;
            for (int i = 0; i < count; i++) {
                f.liquid.flowVector = null;
                long before = bean.getCurrentThreadAllocatedBytes();
                Vector3 result = f.liquid.getFlowVector();
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
                sink = result;
                require(result != previous, "fresh measured result");
                sameBits(f.expected, result);
                if (previous != null) previous.setComponents(73, 17, 29);
                previous = result;
            }
            require(f.world.reads - beforeReads == (long) count * f.expectedTrace.length, "measured read count");
            return bytes / (double) count;
        }

        private static double control(ThreadMXBean bean, int count, boolean allocate) {
            long bytes = 0;
            for (int i = 0; i < count; i++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                if (allocate) sink = new Vector3(3, 4, 0);
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
            }
            return bytes / (double) count;
        }

        private static double median(double[] values) {
            double[] sorted = values.clone();
            Arrays.sort(sorted);
            return sorted[sorted.length / 2];
        }

        public static void main(String[] args) {
            require(ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:-DoEscapeAnalysis"),
                    "this allocation contract requires EA disabled");
            LiquidInnerNormalizationTest contracts = new LiquidInnerNormalizationTest();
            for (boolean lava : new boolean[]{false, true}) {
                for (double[] v : vectors())
                    contracts.fallingRetainsBitsReadOrderAndCacheOwnership(lava, new Vector3(v[0], v[1], v[2]));
                contracts.fallingGuardReentryPublishesIndependentResults(lava);
                contracts.fallingGuardFailureDoesNotPublishPartialFlow(lava);
            }
            System.out.println("INNER_CONTRACTS=PASS,cases=22");
            System.out.println("NATIVE_BLOCK_LIQUID=" + BlockLiquid.class.getProtectionDomain().getCodeSource().getLocation());
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(), "allocation accounting required");
            bean.setThreadAllocatedMemoryEnabled(true);
            control(bean, 100_000, false);
            control(bean, 100_000, true);
            double counter = control(bean, 20_000, false);
            double oneVector = control(bean, 20_000, true);
            require(counter <= 1d, "allocation counter itself allocates");
            require(oneVector >= 32d && oneVector <= 64d, "unexpected escaping vector calibration");
            System.out.printf(Locale.ROOT, "CONTROL,counter=%.6f,vector=%.6f%n", counter, oneVector);
            boolean allPassed = true;
            for (boolean lava : new boolean[]{false, true}) {
                InnerFixture flat = new InnerFixture(lava, false, new Vector3(3, 4, 0));
                InnerFixture falling = new InnerFixture(lava, true, new Vector3(3, 4, 0));
                for (InnerFixture f : new InnerFixture[]{flat, falling}) {
                    f.world.tracing = true;
                    sameBits(f.expected, f.liquid.getFlowVector());
                    f.verifyTrace(1);
                    f.world.tracing = false;
                    run(f, bean, 100_000);
                    run(f, bean, 100_000);
                }
                double[] flatBytes = new double[7];
                double[] fallingBytes = new double[7];
                double[] differences = new double[7];
                for (int round = 0; round < 7; round++) {
                    if ((round & 1) == 0) {
                        flatBytes[round] = run(flat, bean, 20_000);
                        fallingBytes[round] = run(falling, bean, 20_000);
                    } else {
                        fallingBytes[round] = run(falling, bean, 20_000);
                        flatBytes[round] = run(flat, bean, 20_000);
                    }
                    differences[round] = fallingBytes[round] - flatBytes[round];
                    System.out.printf(Locale.ROOT, "ROUND,%s,%d,flat=%.6f,falling=%.6f,delta=%.6f%n",
                            lava ? "lava" : "water", round, flatBytes[round], fallingBytes[round], differences[round]);
                }
                double flatMedian = median(flatBytes);
                double fallingMedian = median(fallingBytes);
                double difference = median(differences);
                boolean absolute = flatMedian >= oneVector - 1d && flatMedian <= 2d * oneVector + 16d
                        && fallingMedian >= oneVector - 1d && fallingMedian <= 3d * oneVector + 16d;
                boolean delta = difference >= -16d && difference <= 16d;
                allPassed &= absolute && delta;
                System.out.printf(Locale.ROOT, "INNER_SCENE,%s,flat=%.6f,falling=%.6f,delta=%.6f,budget=16,absolute=%s,deltaPass=%s%n",
                        lava ? "lava" : "water", flatMedian, fallingMedian, difference, absolute, delta);
            }
            require(allPassed, "falling normalization retains a private temporary vector (delta budget 16 B/call)");
            System.out.println("INNER_NORMALIZATION=PASS");
        }
    }
}
