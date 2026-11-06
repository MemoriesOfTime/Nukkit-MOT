package cn.nukkit.block;

import cn.nukkit.math.BlockFace;
import com.sun.management.ThreadMXBean;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Actual private findLog, prebuilt concrete graphs; no mocking or graph setup in timing. */
public final class LeafSearchBenchmark {
    private static final MethodHandle SEARCH;
    private static final ThreadMXBean ALLOCATION = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final long THREAD = Thread.currentThread().getId();
    private static volatile long sink;
    static {
        try {
            SEARCH = MethodHandles.privateLookupIn(BlockLeaves.class, MethodHandles.lookup())
                    .findVirtual(BlockLeaves.class, "findLog", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
        if (!ALLOCATION.isThreadAllocatedMemorySupported() || !ALLOCATION.isCurrentThreadCpuTimeSupported()) {
            throw new IllegalStateException("Thread allocation/CPU measurements unavailable");
        }
        ALLOCATION.setThreadAllocatedMemoryEnabled(true);
        ALLOCATION.setThreadCpuTimeEnabled(true);
    }

    private static final class Leaf extends BlockLeaves {
        final Block[] sides = new Block[6];
        Leaf(int x, int y, int z, Block solid) { this.x = x; this.y = y; this.z = z; Arrays.fill(sides, solid); }
        @Override protected Block getSideIfLoadedOrNull(BlockFace face) { return sides[face.ordinal()]; }
    }
    private record Fixture(String name, Leaf[] roots, int iterations, boolean supported, int graphLeaves) { }

    private static Leaf graph(String name) {
        Block solid = new BlockStone();
        Leaf root = new Leaf(512, 64, 512, solid);
        switch (name) {
            case "immediate-null" -> root.sides[0] = null;
            case "immediate-log" -> root.sides[0] = new BlockWood();
            case "isolated" -> { }
            case "small-chain" -> {
                Leaf leaf = new Leaf(512, 63, 512, solid);
                root.sides[0] = leaf; leaf.sides[1] = root; leaf.sides[5] = new BlockWood();
            }
            case "dense-no-support", "dense-log-distance5" -> {
                Map<String, Leaf> nodes = new HashMap<>();
                for (int x = -5; x <= 5; x++) for (int y = -5; y <= 5; y++) for (int z = -5; z <= 5; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= 5)
                        nodes.put(x + ":" + y + ":" + z, new Leaf(512 + x, 64 + y, 512 + z, solid));
                }
                for (var entry : nodes.entrySet()) {
                    Leaf leaf = entry.getValue();
                    for (BlockFace face : BlockFace.values()) {
                        int x = (int) leaf.x - 512 + face.getXOffset();
                        int y = (int) leaf.y - 64 + face.getYOffset();
                        int z = (int) leaf.z - 512 + face.getZOffset();
                        Leaf next = nodes.get(x + ":" + y + ":" + z);
                        if (next != null) leaf.sides[face.ordinal()] = next;
                    }
                }
                root = nodes.get("0:0:0");
                if (name.equals("dense-log-distance5")) nodes.get("4:0:0").sides[BlockFace.EAST.ordinal()] = new BlockWood();
            }
            default -> throw new IllegalArgumentException(name);
        }
        return root;
    }

    private static Fixture fixture(String name) {
        Leaf[] roots = new Leaf[16];
        for (int i = 0; i < roots.length; i++) roots[i] = graph(name);
        boolean dense = name.startsWith("dense");
        return new Fixture(name, roots, dense ? 8192 : 262144, !name.equals("isolated") && !name.equals("dense-no-support"),
                dense ? 231 : name.equals("small-chain") ? 2 : 1);
    }

    private static long loop(Fixture fixture, int iterations) throws Throwable {
        long checksum = 0x13579bdfL;
        for (int i = 0; i < iterations; i++) {
            BlockLeaves root = fixture.roots[i & 15];
            boolean result = (boolean) SEARCH.invokeExact(root);
            checksum = checksum * 31 + (result ? 1231 : 1237);
        }
        return checksum;
    }

    public static void main(String[] args) throws Throwable {
        List<Fixture> fixtures = List.of(fixture("immediate-null"), fixture("immediate-log"), fixture("isolated"),
                fixture("small-chain"), fixture("dense-no-support"), fixture("dense-log-distance5"));
        for (Fixture fixture : fixtures) {
            for (Leaf root : fixture.roots) {
                BlockLeaves value = root;
                if ((boolean) SEARCH.invokeExact(value) != fixture.supported) throw new AssertionError(fixture.name);
            }
        }
        if (args.length > 0 && args[0].equals("--check")) {
            for (Fixture fixture : fixtures)
                System.out.println("{\"scenario\":\"" + fixture.name + "\",\"checksum\":" + loop(fixture, 64) + "}");
            return;
        }
        for (int round = 0; round < 4; round++) for (Fixture fixture : fixtures) sink = loop(fixture, fixture.iterations);
        for (int sample = 0; sample < 7; sample++) {
            List<Fixture> order = new ArrayList<>(fixtures);
            if ((sample & 1) == 1) Collections.reverse(order);
            for (Fixture fixture : order) {
                long bytes = ALLOCATION.getThreadAllocatedBytes(THREAD);
                long cpu = ALLOCATION.getCurrentThreadCpuTime();
                long started = System.nanoTime();
                long checksum = loop(fixture, fixture.iterations);
                long elapsed = System.nanoTime() - started;
                long cpuElapsed = ALLOCATION.getCurrentThreadCpuTime() - cpu;
                long allocated = ALLOCATION.getThreadAllocatedBytes(THREAD) - bytes;
                sink = checksum;
                System.out.printf(Locale.ROOT,
                        "{\"scenario\":\"%s\",\"sample\":%d,\"iterations\":%d,\"graph_leaves\":%d,\"ns_per_search\":%.5f,\"main_cpu_ns_per_search\":%.5f,\"main_bytes_per_search\":%.5f,\"checksum\":%d}%n",
                        fixture.name, sample, fixture.iterations, fixture.graphLeaves,
                        (double) elapsed / fixture.iterations, (double) cpuElapsed / fixture.iterations,
                        (double) allocated / fixture.iterations, checksum);
            }
        }
    }
}
