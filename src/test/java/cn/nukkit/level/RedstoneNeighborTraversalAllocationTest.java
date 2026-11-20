package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedstoneNeighborTraversalAllocationTest {
    private static final List<BlockFace> ORDER = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

    private static RecordingLevel level() {
        return new ObjenesisStd().newInstance(RecordingLevel.class);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(BlockFace.class)
    void dispatchesEachAcceptedFaceInOrderWithoutRetainingSideVectors(BlockFace ignored) {
        RecordingLevel level = level();
        List<String> actual = new ArrayList<>();
        List<Vector3> retained = new ArrayList<>();
        Vector3 position = new Vector3(10, 20, 30) {
            @Override public Vector3 getSideVec(BlockFace face) {
                actual.add("side:" + face);
                return super.getSideVec(face);
            }
        };
        level.reader = pos -> {
            retained.add(pos);
            actual.add("read:" + pos.x + ":" + pos.y + ":" + pos.z);
            return new BlockAir() {
                @Override public int onUpdate(int type) {
                    assertEquals(Level.BLOCK_UPDATE_REDSTONE, type);
                    actual.add("update");
                    return 0;
                }
            };
        };
        level.updateAroundRedstone(position, ignored);
        List<String> expected = new ArrayList<>();
        for (BlockFace face : ORDER) {
            if (face == ignored) continue;
            expected.add("side:" + face);
            expected.add("read:" + (10d + face.getXOffset()) + ":"
                    + (20d + face.getYOffset()) + ":" + (30d + face.getZOffset()));
            expected.add("update");
        }
        assertEquals(expected, actual);
        for (int i = 0; i < retained.size(); i++) {
            for (int j = i + 1; j < retained.size(); j++) assertNotSame(retained.get(i), retained.get(j));
        }
        assertEquals(new Vector3(10, 20, 30), position);
    }

    @Test
    void callbacksKeepLivePositionAndFixedReceiverAcrossNestedTraversal() {
        RecordingLevel outer = level(), other = level();
        List<String> trace = new ArrayList<>();
        Position position = new Position(10, 20, 30, outer);
        boolean[] nested = {false};
        other.reader = pos -> { throw new AssertionError("Position.level cannot redirect receiver"); };
        outer.reader = pos -> {
            String coordinates = pos.x + ":" + pos.y + ":" + pos.z;
            trace.add((nested[0] ? "inner:" : "outer:") + coordinates);
            return new BlockAir() {
                @Override public int onUpdate(int type) {
                    assertEquals(Level.BLOCK_UPDATE_REDSTONE, type);
                    if (!nested[0] && trace.size() == 1) {
                        position.x = 100;
                        position.level = other;
                        nested[0] = true;
                        outer.updateAroundRedstone(new Vector3(0, 0, 0), BlockFace.UP);
                        nested[0] = false;
                    }
                    return 0;
                }
            };
        };
        outer.updateAroundRedstone(position);
        assertEquals(List.of("outer:10.0:19.0:30.0", "inner:0.0:-1.0:0.0",
                "inner:0.0:0.0:-1.0", "inner:0.0:0.0:1.0", "inner:-1.0:0.0:0.0",
                "inner:1.0:0.0:0.0", "outer:100.0:21.0:30.0", "outer:100.0:20.0:29.0",
                "outer:100.0:20.0:31.0", "outer:99.0:20.0:30.0", "outer:101.0:20.0:30.0"), trace);
    }

    @Test
    void callbackFailureStopsTraversalAndDoesNotPoisonNextCall() {
        RecordingLevel level = level();
        RuntimeException expected = new IllegalStateException("neighbor failed");
        List<Double> seen = new ArrayList<>();
        level.reader = pos -> {
            seen.add(pos.y);
            return new BlockAir() {
                @Override public int onUpdate(int type) { throw expected; }
            };
        };
        assertSame(expected, assertThrows(RuntimeException.class,
                () -> level.updateAroundRedstone(new Vector3())));
        assertEquals(List.of(-1d), seen);
        level.reader = pos -> { seen.add(pos.y); return new BlockAir(); };
        level.updateAroundRedstone(new Vector3(), BlockFace.DOWN);
        assertEquals(List.of(-1d, 1d, 0d, 0d, 0d, 0d), seen);
    }

    @Tag("perf")
    @Test
    void enumerationDoesNotAllocatePerDispatch(@TempDir Path directory) throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported());
        // Allocation-free test boundaries isolate traversal's enum array. Disable escape
        // analysis so this regression does not depend on a particular inlining decision.
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path log = directory.resolve("redstone-neighbor-allocation.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms128m", "-Xmx256m", "-XX:-DoEscapeAnalysis", "-cp", cp,
                AllocationProbe.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            String output;
            try (var input = Files.newInputStream(log)) {
                output = new String(input.readNBytes(65_536), java.nio.charset.StandardCharsets.UTF_8);
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
        public static void main(String[] args) {
            CounterLevel level = new ObjenesisStd().newInstance(CounterLevel.class);
            level.block = new CounterBlock();
            Vector3 position = new FixedPosition();
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            bean.setThreadAllocatedMemoryEnabled(true);
            for (BlockFace ignored : new BlockFace[]{null, BlockFace.UP}) {
                for (int i = 0; i < 100_000; i++) level.updateAroundRedstone(position, ignored);
                double[] values = new double[7];
                for (int round = 0; round < values.length; round++) {
                    long calls = level.reads, updates = level.block.updates;
                    long before = bean.getCurrentThreadAllocatedBytes();
                    for (int i = 0; i < 20_000; i++) level.updateAroundRedstone(position, ignored);
                    values[round] = (bean.getCurrentThreadAllocatedBytes() - before) / 20_000d;
                    long expected = (ignored == null ? 6L : 5L) * 20_000;
                    if (level.reads - calls != expected || level.block.updates - updates != expected)
                        throw new AssertionError("every admitted face must be read and updated");
                    sink = level.reads;
                }
                Arrays.sort(values);
                System.out.println("neighbor-enumeration ignored=" + ignored + " B/call=" + values[3]);
                if (values[3] > 8) throw new AssertionError("per-dispatch face enumeration allocated " + values[3]);
            }
        }
    }

    private static final class RecordingLevel extends Level {
        Function<Vector3, Block> reader;
        private RecordingLevel() { super(null, "", "", null); }
        @Override public Block getBlock(Vector3 pos) { return reader.apply(pos); }
    }

    private static final class CounterLevel extends Level {
        CounterBlock block;
        long reads;
        private CounterLevel() { super(null, "", "", null); }
        @Override public Block getBlock(Vector3 pos) { reads++; return block; }
    }

    private static final class CounterBlock extends BlockAir {
        long updates;
        @Override public int onUpdate(int type) {
            if (type != Level.BLOCK_UPDATE_REDSTONE) throw new AssertionError("redstone update type");
            updates++;
            return 0;
        }
    }

    private static final class FixedPosition extends Vector3 {
        final Vector3[] positions = new Vector3[6];
        FixedPosition() {
            for (BlockFace face : ORDER) positions[face.ordinal()] = super.getSideVec(face);
        }
        @Override public Vector3 getSideVec(BlockFace face) { return positions[face.ordinal()]; }
    }
}
