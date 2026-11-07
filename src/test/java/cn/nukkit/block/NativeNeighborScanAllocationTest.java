package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Measures the native private scans in isolated ordinary-JIT children. The fixtures
 * replace neighbor/world lookup with prebuilt blocks, retain native wire/power
 * predicates and count every lookup. No full tick, movement, world mutation,
 * melting, scheduling or production latency is measured.
 */
public final class NativeNeighborScanAllocationTest {
    private static final int WARMUP = 1_000_000;
    private static final int CALLS_PER_WINDOW = 100_000;
    private static final int WINDOWS = 7;
    private static volatile long sink;

    @Tag("perf")
    @Test
    void pistonScanDoesNotAllocateDirectionArrays(@TempDir Path directory) throws Exception {
        for (String scene : List.of("air-full", "power-last", "wire-first", "control")) {
            runChild(directory, "piston", scene, BlockPistonBase.class);
        }
    }

    @Tag("perf")
    @Test
    void frostScanDoesNotAllocateDirectionArrays(@TempDir Path directory) throws Exception {
        for (String scene : List.of("empty", "four-late", "all", "control")) {
            runChild(directory, "frost", scene, BlockIceFrosted.class);
        }
    }

    private static void runChild(Path directory, String mechanism, String scene,
                                 Class<?> nativeClass) throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported());
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = directory.resolve(mechanism + "-" + scene + ".log");
        Path origin = Path.of(nativeClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms128m", "-Xmx128m", "-Dexpected.core.source=" + origin,
                "-cp", classpath, NativeNeighborScanAllocationTest.class.getName(), mechanism, scene, "8")
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Native neighbor probe timed out");
            String output = Files.readString(outputFile);
            System.out.print(output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Native neighbor probe did not terminate");
            }
        }
    }

    private static final class PowerWorld extends Level {
        long reads;
        int trace;

        private PowerWorld() {
            super(null, null, null, null);
        }

        @Override
        public Block getBlock(Vector3 position) {
            reads++;
            trace = trace * 7 + (int) position.x + 1;
            return (Block) position;
        }
    }

    private static final class PistonNeighborhood extends BlockPiston {
        final Block[] neighbors = new Block[6];
        long reads;
        int trace;

        PistonNeighborhood(String scene, PowerWorld world) {
            for (int face = 0; face < neighbors.length; face++) {
                neighbors[face] = new BlockAir();
            }
            int selected = scene.endsWith("first") ? 1 : scene.endsWith("last") ? 5 : 0;
            if (scene.startsWith("wire")) {
                neighbors[selected] = new BlockRedstoneWire(15);
            }
            if (scene.startsWith("power")) {
                neighbors[selected] = new BlockRedstone();
            }
            if (scene.equals("zero-wire")) {
                neighbors[5] = new BlockRedstoneWire(0);
            }
            if (scene.equals("face-only")) {
                neighbors[0] = new BlockRedstoneWire(15);
            }
            for (int face = 0; face < neighbors.length; face++) {
                neighbors[face].x = face;
            }
            level = world;
        }

        @Override
        public Block getSide(BlockFace face) {
            reads++;
            trace = trace * 7 + face.getIndex() + 1;
            return neighbors[face.getIndex()];
        }
    }

    private static final class FrostNeighborhood extends BlockIceFrosted {
        final Block[] neighbors = new Block[6];
        long reads;
        int trace;

        FrostNeighborhood(int mask) {
            for (int face = 0; face < neighbors.length; face++) {
                neighbors[face] = (mask & (1 << face)) == 0 ? new BlockAir() : new BlockIceFrosted();
            }
        }

        @Override
        public Block getSide(BlockFace face) {
            reads++;
            trace = trace * 7 + face.getIndex() + 1;
            return neighbors[face.getIndex()];
        }
    }

    private static PowerWorld world() throws ReflectiveOperationException {
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field field = allocator.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (PowerWorld) allocator.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), PowerWorld.class);
    }

    private static void runPiston(MethodHandle power, PistonNeighborhood neighborhood, PowerWorld world,
                                  int count, boolean expected, boolean control,
                                  int expectedNeighborTrace, int expectedWorldTrace) throws Throwable {
        BlockPistonBase receiver = neighborhood;
        long sum = 0;
        for (int call = 0; call < count; call++) {
            neighborhood.trace = 0;
            world.trace = 0;
            boolean actual = control ? expected : (boolean) power.invokeExact(receiver);
            if (actual != expected || neighborhood.trace != expectedNeighborTrace || world.trace != expectedWorldTrace) {
                throw new AssertionError("Piston result or traversal changed");
            }
            sum += actual ? 1 : 0;
        }
        sink = sum;
    }

    private static void runFrost(MethodHandle countNeighbors, FrostNeighborhood neighborhood,
                                 int count, int expected, boolean control, int expectedTrace) throws Throwable {
        BlockIceFrosted receiver = neighborhood;
        long sum = 0;
        for (int call = 0; call < count; call++) {
            neighborhood.trace = 0;
            int actual = control ? expected : (int) countNeighbors.invokeExact(receiver);
            if (actual != expected || neighborhood.trace != expectedTrace) {
                throw new AssertionError("Frost result or traversal changed");
            }
            sum += actual;
        }
        sink = sum;
    }

    private static int trace(int first, int count) {
        int result = 0;
        for (int face = first; face < first + count; face++) {
            result = result * 7 + face + 1;
        }
        return result;
    }

    public static void main(String[] arguments) throws Throwable {
        String mechanism = arguments[0];
        String scene = arguments[1];
        double budget = Double.parseDouble(arguments[2]);
        // Initialize the native class before the fixture or BlockFace is touched.
        // Each scene is a fresh JVM, also exercising the new cache's cold load.
        Class<?> nativeClass = Class.forName(switch (mechanism) {
            case "piston" -> "cn.nukkit.block.BlockPistonBase";
            case "frost" -> "cn.nukkit.block.BlockIceFrosted";
            default -> throw new IllegalArgumentException(mechanism);
        });
        Path origin = Path.of(nativeClass.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!origin.equals(Path.of(System.getProperty("expected.core.source")).toRealPath())) {
            throw new AssertionError("Unexpected native class: " + origin);
        }
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            throw new AssertionError("Allocation accounting unsupported");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        boolean control = scene.equals("control");
        double[] windows = new double[WINDOWS];
        long calls = WARMUP + (long) CALLS_PER_WINDOW * WINDOWS;
        if (mechanism.equals("piston")) {
            if (!List.of("air-full", "face-only", "power-first", "power-last", "wire-first", "wire-last",
                    "zero-wire", "control").contains(scene)) {
                throw new IllegalArgumentException(scene);
            }
            MethodHandle power = MethodHandles.privateLookupIn(BlockPistonBase.class, MethodHandles.lookup())
                    .findVirtual(BlockPistonBase.class, "isPowered", MethodType.methodType(boolean.class));
            PowerWorld world = world();
            PistonNeighborhood neighborhood = new PistonNeighborhood(scene, world);
            if (neighborhood.getBlockFace() != BlockFace.DOWN) {
                throw new AssertionError("Fixture facing changed");
            }
            boolean expected = scene.startsWith("wire-") || scene.startsWith("power-");
            int neighborCount = control ? 0 : scene.endsWith("first") ? 1 : 5;
            int worldCount = control ? 0 : scene.startsWith("wire-") ? neighborCount - 1 : neighborCount;
            int neighborTrace = trace(1, neighborCount);
            int worldTrace = trace(1, worldCount);
            runPiston(power, neighborhood, world, WARMUP, expected, control, neighborTrace, worldTrace);
            for (int round = 0; round < WINDOWS; round++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                runPiston(power, neighborhood, world, CALLS_PER_WINDOW, expected, control, neighborTrace, worldTrace);
                windows[round] = (bean.getCurrentThreadAllocatedBytes() - before) / (double) CALLS_PER_WINDOW;
            }
            if (neighborhood.reads != calls * neighborCount || world.reads != calls * worldCount) {
                throw new AssertionError("Piston lookup count changed");
            }
        } else {
            int mask = switch (scene) {
                case "empty", "control" -> 0;
                case "three" -> 21;
                case "four-early" -> 15;
                case "four-late" -> 58;
                case "all" -> 63;
                default -> throw new IllegalArgumentException(scene);
            };
            MethodHandle countNeighbors = MethodHandles.privateLookupIn(BlockIceFrosted.class, MethodHandles.lookup())
                    .findVirtual(BlockIceFrosted.class, "countNeighbors", MethodType.methodType(int.class));
            FrostNeighborhood neighborhood = new FrostNeighborhood(mask);
            int expected = control ? 0 : Math.min(4, Integer.bitCount(mask));
            int readCount = control ? 0 : scene.equals("four-early") || scene.equals("all") ? 4 : 6;
            int neighborTrace = trace(0, readCount);
            runFrost(countNeighbors, neighborhood, WARMUP, expected, control, neighborTrace);
            for (int round = 0; round < WINDOWS; round++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                runFrost(countNeighbors, neighborhood, CALLS_PER_WINDOW, expected, control, neighborTrace);
                windows[round] = (bean.getCurrentThreadAllocatedBytes() - before) / (double) CALLS_PER_WINDOW;
            }
            if (neighborhood.reads != calls * readCount) {
                throw new AssertionError("Frost lookup count changed");
            }
        }
        for (int round = 0; round < WINDOWS; round++) {
            System.out.printf(Locale.ROOT, "window,%s,%s,%d,%.6f%n", mechanism, scene, round, windows[round]);
        }
        Arrays.sort(windows);
        double median = windows[WINDOWS / 2];
        System.out.printf(Locale.ROOT, "summary,%s,%s,median=%.6f,calls=%d,origin=%s,java=%s,arch=%s%n",
                mechanism, scene, median, calls, origin, System.getProperty("java.version"), System.getProperty("os.arch"));
        if (median > budget) {
            throw new AssertionError("Native scan allocated " + median + " bytes/call; budget=" + budget);
        }
    }
}
