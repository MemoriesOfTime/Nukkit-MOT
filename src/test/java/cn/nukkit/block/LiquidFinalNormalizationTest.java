package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LiquidFinalNormalizationTest {
    private static final class Overlay extends Level {
        Block[] cells;
        int reads;
        Runnable beforeRead;
        private Overlay() { super(null, "test", "test", null); }
        @Override public Block getBlock(int x, int y, int z, int layer) {
            int slot = reads++ % 4;
            if (beforeRead != null) beforeRead.run();
            return cells[slot];
        }
    }

    private static final class Fixture {
        final BlockLiquid liquid;
        final Overlay world;
        final Vector3 expected;
        Fixture(boolean lava, Vector3 contribution) {
            liquid = lava ? new BlockLava(0) : new BlockWater(0);
            world = new ObjenesisStd().newInstance(Overlay.class);
            world.cells = new Block[4];
            for (int i = 0; i < 4; i++) {
                Block side = lava ? new BlockLava(i == 0 ? 1 : 0) : new BlockWater(i == 0 ? 1 : 0);
                side.level = world;
                if (i == 0) side.setComponents(contribution.x, contribution.y, contribution.z);
                world.cells[i] = side;
            }
            liquid.level = world;
            // The public Vector3 operation is the pre-existing numeric contract. Native
            // accumulation starts at positive zero and adds three more zero contributions.
            expected = new Vector3(0d + contribution.x, 0d + contribution.y, 0d + contribution.z).normalize();
        }
    }

    static Stream<Object[]> numericCases() {
        double[][] vectors = {
                {0d, -0d, 0d}, {3d, 4d, 0d}, {7.3d, 13.9d, 0.25d},
                {Double.MIN_VALUE, 0d, -Double.MIN_VALUE},
                {Double.MIN_NORMAL, -Double.MIN_NORMAL, 0d},
                {Double.MAX_VALUE, -1d, -Double.MIN_VALUE},
                {-1d, Double.POSITIVE_INFINITY, 0d},
                {Double.NEGATIVE_INFINITY, 1d, 2d},
                {Double.NaN, 2d, 3d}
        };
        return Stream.of(false, true).flatMap(lava -> Arrays.stream(vectors)
                .map(v -> new Object[]{lava, new Vector3(v[0], v[1], v[2])}));
    }

    private static void sameBits(Vector3 expected, Vector3 actual) {
        assertEquals(Double.doubleToRawLongBits(expected.x), Double.doubleToRawLongBits(actual.x), "x bits");
        assertEquals(Double.doubleToRawLongBits(expected.y), Double.doubleToRawLongBits(actual.y), "y bits");
        assertEquals(Double.doubleToRawLongBits(expected.z), Double.doubleToRawLongBits(actual.z), "z bits");
    }

    @ParameterizedTest
    @MethodSource("numericCases")
    void nativeFinalNormalizationRetainsBitsAndCacheOwnership(boolean lava, Vector3 contribution) {
        Fixture f = new Fixture(lava, contribution);
        Vector3 first = f.liquid.getFlowVector();
        sameBits(f.expected, first);
        assertEquals(4, f.world.reads);
        assertSame(first, f.liquid.getFlowVector());
        assertEquals(4, f.world.reads);
        first.setComponents(73, 17, 29);
        assertSame(first, f.liquid.getFlowVector());
        f.liquid.clearCaches();
        Vector3 second = f.liquid.getFlowVector();
        assertNotSame(first, second);
        sameBits(f.expected, second);
        sameBits(new Vector3(73, 17, 29), first);
        assertEquals(8, f.world.reads);
    }

    @Test
    void nestedQueryPublishesIndependentVectorsAtTheOriginalBoundary() {
        Fixture f = new Fixture(false, new Vector3(3, 4, 0));
        Vector3[] nested = new Vector3[1];
        f.world.beforeRead = () -> {
            f.world.beforeRead = null;
            nested[0] = f.liquid.getFlowVector();
        };
        Vector3 outer = f.liquid.getFlowVector();
        assertEquals(8, f.world.reads);
        assertNotSame(nested[0], outer);
        sameBits(f.expected, nested[0]);
        sameBits(f.expected, outer);
        assertSame(outer, f.liquid.getFlowVector());
        outer.x = 123;
        sameBits(f.expected, nested[0]);
    }

    @Test
    void failedCallbackDoesNotPublishPartiallyAccumulatedFlow() {
        Fixture f = new Fixture(true, new Vector3(3, 4, 0));
        IllegalStateException failure = new IllegalStateException("callback");
        f.world.beforeRead = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, f.liquid::getFlowVector));
        assertNull(f.liquid.flowVector);
        f.world.beforeRead = null;
        f.world.reads = 0;
        sameBits(f.expected, f.liquid.getFlowVector());
        assertEquals(4, f.world.reads);
    }

    @Test
    void nativeFlatFlowAllocatesOnlyItsPublishedVector(@TempDir Path temporary) throws Exception {
        Path output = temporary.resolve("allocation.txt");
        Path classes = temporary.resolve("classes.txt");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:-DoEscapeAnalysis", "-Xms128m", "-Xmx512m", "-Xlog:class+load=info:file=" + classes,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AllocationChild.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "allocation child timeout");
            String text = Files.readString(output);
            assertEquals(0, process.exitValue(), text);
            assertTrue(text.contains("OWNED_FINAL_FLOW=PASS"), text);
            String loaded = Files.readString(classes);
            assertFalse(loaded.contains("org.mockito."), "native child must not load Mockito");
            assertFalse(loaded.contains("net.bytebuddy."), "native child must not load ByteBuddy");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "allocation child was not reaped");
            }
        }
    }

    public static final class AllocationChild {
        private static volatile Vector3 sink;
        private static void require(boolean value, String message) {
            if (!value) throw new AssertionError(message);
        }
        private static double run(Fixture f, ThreadMXBean bean, int count) {
            long bytes = 0;
            Vector3 previous = null;
            for (int i = 0; i < count; i++) {
                f.liquid.flowVector = null;
                long before = bean.getCurrentThreadAllocatedBytes();
                Vector3 result = f.liquid.getFlowVector();
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
                sink = result;
                require(result != previous, "fresh vector after invalidation");
                require(Double.doubleToRawLongBits(result.x) == Double.doubleToRawLongBits(f.expected.x), "x bits");
                require(Double.doubleToRawLongBits(result.y) == Double.doubleToRawLongBits(f.expected.y), "y bits");
                require(Double.doubleToRawLongBits(result.z) == Double.doubleToRawLongBits(f.expected.z), "z bits");
                if (previous != null) previous.setComponents(73, 17, 29);
                previous = result;
            }
            return bytes / (double) count;
        }
        public static void main(String[] args) {
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(), "allocation accounting required");
            bean.setThreadAllocatedMemoryEnabled(true);
            Fixture[] fixtures = {
                    new Fixture(false, new Vector3(0, 0, 0)), new Fixture(false, new Vector3(3, 4, 0)),
                    new Fixture(true, new Vector3(0, 0, 0)), new Fixture(true, new Vector3(3, 4, 0))
            };
            for (Fixture f : fixtures) run(f, bean, 100_000);
            for (int scene = 0; scene < fixtures.length; scene++) {
                double[] windows = new double[7];
                for (int round = 0; round < windows.length; round++) windows[round] = run(fixtures[scene], bean, 20_000);
                Arrays.sort(windows);
                System.out.println("OWNED_FINAL_FLOW_SCENE=" + scene + ",bytes=" + windows[3]);
                require(windows[3] <= 60, "final normalization allocated a temporary: " + windows[3]);
            }
            System.out.println("OWNED_FINAL_FLOW=PASS");
        }
    }
}
