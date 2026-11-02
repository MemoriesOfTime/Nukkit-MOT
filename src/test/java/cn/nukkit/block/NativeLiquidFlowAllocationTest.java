package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

/**
 * Native BlockLiquid.getFlowVector with normal BlockWater/BlockLava behavior. Only the
 * Level lifecycle is bypassed, and Level.getBlock returns preconstructed positioned
 * blocks from a fixed overlay. No registry, chunks, storage, entity movement or world
 * allocation is measured. The returned native vector escapes through a volatile field.
 * No mocks, copied flow implementation or escape-analysis JVM switches are used.
 */
public final class NativeLiquidFlowAllocationTest {
    @org.junit.jupiter.api.Test
    void nativeFlowRetainsBitsReadsAndCacheWithinAllocationBudget(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporaryDirectory) throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof ThreadMXBean mx
                && mx.isThreadAllocatedMemorySupported());
        // MockServerExtension instruments core classes in every JUnit JVM. Measure native
        // classes in a bounded child using the same test classpath, without that extension.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        java.nio.file.Path outputFile = temporaryDirectory.resolve("allocation.log");
        Process process = new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms256m", "-Xmx512m", "-cp", classpath,
                NativeLiquidFlowAllocationTest.class.getName(), "junit-child", "200000", "100000", "7", "140")
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(
                    process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "Native allocation probe timed out");
            String output = java.nio.file.Files.readString(outputFile);
            System.out.print(output);
            org.junit.jupiter.api.Assertions.assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    private static volatile Vector3 sink;
    private interface Resettable { void resetFlow(); }
    private static final class Water extends BlockWater implements Resettable {
        Water(int meta) { super(meta); }
        public void resetFlow() { flowVector = null; }
    }
    private static final class Lava extends BlockLava implements Resettable {
        Lava(int meta) { super(meta); }
        public void resetFlow() { flowVector = null; }
    }

    private static final class Overlay extends Level {
        Block[] cells;
        long reads;
        boolean tracing;
        int[] trace;
        int traceSize;
        private Overlay() { super(null, "probe", "probe", null); }
        @Override public Block getBlock(int x, int y, int z, int layer) {
            int index = cell(x, y - 64, z, layer);
            reads++;
            if (tracing) {
                if (traceSize == trace.length) throw new AssertionError("trace overflow");
                trace[traceSize++] = index;
            }
            return cells[index];
        }
    }

    private static final class Scene {
        final String name;
        final BlockLiquid liquid;
        final Resettable reset;
        final Overlay world;
        final boolean cached;
        final double expectedX, expectedY, expectedZ;
        final int[] expectedTrace;
        final double[] bytes;
        Vector3 cachedVector;
        long calls;
        Scene(boolean lava, String kind, Unsafe unsafe, int rounds) throws Exception {
            name = (lava ? "lava-" : "water-") + kind;
            cached = kind.equals("cached");
            boolean falling = kind.startsWith("falling");
            liquid = lava ? new Lava(falling ? 8 : kind.equals("zero") ? 0 : 1)
                    : new Water(falling ? 8 : kind.equals("zero") ? 0 : 1);
            reset = (Resettable) liquid;
            world = (Overlay) unsafe.allocateInstance(Overlay.class);
            world.cells = new Block[54];
            world.trace = new int[64];
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        put(world, new BlockAir(), x, y, z, 0);
                        put(world, new BlockAir(), x, y, z, 1);
                    }
                }
            }
            put(world, liquid, 0, 0, 0, 0);
            put(world, fluid(lava, kind.equals("zero") ? 0 : 1), -1, 0, 0, 0);
            put(world, fluid(lava, kind.equals("zero") ? 0 : falling ? 1 : 3), 1, 0, 0, 0);
            put(world, fluid(lava, kind.equals("zero") ? 0 : 1), 0, 0, -1, 0);
            put(world, fluid(lava, kind.equals("zero") ? 0 : 1), 0, 0, 1, 0);
            if (kind.equals("falling-first") || kind.equals("falling-negative-zero"))
                put(world, new BlockStone(), 0, 0, -1, 0);
            if (kind.equals("falling-negative-zero")) {
                // Unusual positioned blocks expose normalize() producing negative zero.
                Block west = world.cells[cell(-1, 0, 0, 0)];
                west.x = 0d;
                west.z = Double.MAX_VALUE;
                world.cells[cell(1, 0, 0, 0)].x = -Double.MIN_VALUE;
            }
            if (kind.equals("falling-last")) put(world, new BlockStone(), 1, 1, 0, 0);
            expectedX = cached || kind.equals("slope") ? 1d : 0d;
            expectedY = kind.equals("falling-first") ? -6d / Math.sqrt(37d)
                    : kind.equals("falling-last") || kind.equals("falling-negative-zero") ? -1d : 0d;
            expectedZ = kind.equals("falling-first") ? 1d / Math.sqrt(37d) : 0d;
            expectedTrace = traceFor(lava, kind);
            bytes = new double[rounds];
        }
    }

    private static int cell(int x, int y, int z, int layer) {
        if (x < -1 || x > 1 || y < -1 || y > 1 || z < -1 || z > 1 || layer < 0 || layer > 1)
            throw new AssertionError("read outside fixed overlay");
        return (((y + 1) * 3 + z + 1) * 3 + x + 1) * 2 + layer;
    }

    private static Block fluid(boolean lava, int meta) {
        return lava ? new BlockLava(meta) : new BlockWater(meta);
    }

    private static void put(Overlay world, Block block, int x, int y, int z, int layer) {
        block.x = x;
        block.y = 64 + y;
        block.z = z;
        block.layer = layer;
        block.level = world;
        world.cells[cell(x, y, z, layer)] = block;
    }

    private static int[] traceFor(boolean lava, String kind) {
        int w = cell(-1, 0, 0, 0), e = cell(1, 0, 0, 0);
        int n = cell(0, 0, -1, 0), s = cell(0, 0, 1, 0);
        if (!kind.startsWith("falling")) return new int[]{w, e, n, s};
        if (kind.equals("falling-first") || kind.equals("falling-negative-zero"))
            return new int[]{w, e, n, n + 1, s, n};
        int un = cell(0, 1, -1, 0), us = cell(0, 1, 1, 0);
        int uw = cell(-1, 1, 0, 0), ue = cell(1, 1, 0, 0);
        if (lava) return new int[]{w, e, n, s, n, s, w, e, un, us, uw, ue};
        if (kind.equals("falling-last"))
            return new int[]{w, e, n, s, n, n + 1, s, s + 1, w, w + 1, e, e + 1,
                    un, un + 1, us, us + 1, uw, uw + 1, ue};
        return new int[]{w, e, n, s, n, n + 1, s, s + 1, w, w + 1, e, e + 1,
                un, un + 1, us, us + 1, uw, uw + 1, ue, ue + 1};
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void exact(Vector3 actual, double x, double y, double z, String scene) {
        check(actual != null && actual.getClass() == Vector3.class, scene + " exact native vector");
        check(Double.doubleToRawLongBits(actual.x) == Double.doubleToRawLongBits(x)
                && Double.doubleToRawLongBits(actual.y) == Double.doubleToRawLongBits(y)
                && Double.doubleToRawLongBits(actual.z) == Double.doubleToRawLongBits(z),
                scene + " exact result bits");
    }

    private static void verify(Scene s) {
        s.reset.resetFlow();
        s.world.tracing = true;
        Vector3 first = s.liquid.getFlowVector();
        exact(first, s.expectedX, s.expectedY, s.expectedZ, s.name);
        check(s.world.traceSize == s.expectedTrace.length, s.name + " trace length");
        for (int i = 0; i < s.expectedTrace.length; i++)
            check(s.world.trace[i] == s.expectedTrace[i], s.name + " native read order " + i);
        s.world.tracing = false;
        long reads = s.world.reads;
        check(first == s.liquid.getFlowVector() && reads == s.world.reads, s.name + " cache identity/no reads");
        first.x = 17d;
        check(s.liquid.getFlowVector().x == 17d, s.name + " cached mutable alias");
        s.reset.resetFlow();
        Vector3 second = s.liquid.getFlowVector();
        check(second != first && first.x == 17d, s.name + " independent old result");
        exact(second, s.expectedX, s.expectedY, s.expectedZ, s.name);
        s.cachedVector = second;
        s.world.reads = 0;
        s.calls = 0;
    }

    private static void run(Scene s, int iterations) {
        for (int i = 0; i < iterations; i++) {
            if (!s.cached) s.reset.resetFlow();
            sink = s.liquid.getFlowVector();
        }
        s.calls += iterations;
    }

    private static void newVectors(int iterations) {
        for (int i = 0; i < iterations; i++) sink = new Vector3(i, 0, 0);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public static void main(String[] args) throws Exception {
        String label = args.length > 0 ? args[0] : "baseline";
        int warmup = args.length > 1 ? Integer.parseInt(args[1]) : 200000;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 100000;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 7;
        double maxFallingBytes = args.length > 4 ? Double.parseDouble(args[4]) : 140d;
        runRegression(label, warmup, iterations, rounds, maxFallingBytes);
    }

    public static void runRegression(String label, int warmup, int iterations, int rounds,
                                     double maxFallingBytes) throws Exception {
        check(warmup > 0 && iterations > 0 && rounds > 0 && rounds % 2 == 1, "positive counts/odd rounds");
        check(maxFallingBytes >= 0d, "nonnegative falling allocation budget");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(mx.isThreadAllocatedMemorySupported(), "thread allocation accounting supported");
        mx.setThreadAllocatedMemoryEnabled(true);
        String[] kinds = {"cached", "zero", "slope", "falling-open", "falling-first", "falling-last", "falling-negative-zero"};
        Scene[] scenes = new Scene[kinds.length * 2];
        for (int i = 0; i < scenes.length; i++) {
            scenes[i] = new Scene(i >= kinds.length, kinds[i % kinds.length], unsafe, rounds);
            verify(scenes[i]);
        }
        System.out.printf(Locale.ROOT, "runtime,%s,%s,%s,%s%n", System.getProperty("java.version"),
                System.getProperty("java.vm.name"), System.getProperty("os.arch"),
                BlockLiquid.class.getProtectionDomain().getCodeSource().getLocation());
        // Warm both liquid receiver types and all branch profiles before any measurement.
        newVectors(warmup);
        for (Scene scene : scenes) run(scene, warmup);
        double[] vectorBytes = new double[rounds];
        System.out.println("label,scene,round,bytes_per_op");
        for (int round = 0; round < rounds; round++) {
            long before = mx.getCurrentThreadAllocatedBytes();
            newVectors(iterations);
            vectorBytes[round] = (mx.getCurrentThreadAllocatedBytes() - before) / (double) iterations;
            System.out.printf(Locale.ROOT, "%s,escaping-new-vector,%d,%.6f%n", label, round, vectorBytes[round]);
        }
        for (Scene scene : scenes) {
            run(scene, warmup);
            for (int round = 0; round < rounds; round++) {
                long before = mx.getCurrentThreadAllocatedBytes();
                run(scene, iterations);
                scene.bytes[round] = (mx.getCurrentThreadAllocatedBytes() - before) / (double) iterations;
                exact(sink, scene.expectedX, scene.expectedY, scene.expectedZ, scene.name);
                if (scene.cached) check(sink == scene.cachedVector, scene.name + " retained cache identity");
                System.out.printf(Locale.ROOT, "%s,%s,%d,%.6f%n", label, scene.name, round, scene.bytes[round]);
            }
            long expectedReads = scene.cached ? 0 : scene.calls * scene.expectedTrace.length;
            check(scene.world.reads == expectedReads, scene.name + " total native read count");
            System.out.printf(Locale.ROOT, "invariants,%s,PASS,calls=%d,reads=%d,median=%.6f%n",
                    scene.name, scene.calls, scene.world.reads, median(scene.bytes));
        }
        double oneVector = median(vectorBytes);
        boolean onlyFinalVector = true;
        for (Scene scene : scenes) {
            if (scene.name.contains("falling-") && median(scene.bytes) > oneVector + 0.01d)
                onlyFinalVector = false;
        }
        System.out.printf(Locale.ROOT, "summary,escaping_vector_bytes,%.6f%n", oneVector);
        System.out.println("summary,falling_temporary_candidate," + (onlyFinalVector
                ? "REJECT_NO_EXTRA_ALLOCATION_IN_THIS_FIXTURE" : "EXTRA_ALLOCATION_REMAINS_NOT_YET_ATTRIBUTED"));
        System.out.printf(Locale.ROOT, "summary,falling_budget_bytes,%.6f%n", maxFallingBytes);
        for (Scene scene : scenes) {
            if (scene.name.contains("falling-"))
                check(median(scene.bytes) <= maxFallingBytes,
                        scene.name + " allocated " + median(scene.bytes)
                                + " bytes/call; budget " + maxFallingBytes);
        }
    }
}
