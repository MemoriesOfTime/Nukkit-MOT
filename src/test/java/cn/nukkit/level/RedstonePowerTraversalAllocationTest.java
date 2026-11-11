package cn.nukkit.level;

import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntBiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedstonePowerTraversalAllocationTest {
    private static final List<BlockFace> ORDER = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

    enum Query { STRONG, POWERED, INDIRECT }

    private static int query(Level level, Vector3 position, Query kind) {
        return switch (kind) {
            case STRONG -> level.getStrongPower(position);
            case POWERED -> level.isBlockPowered(position) ? 1 : 0;
            case INDIRECT -> level.isBlockIndirectlyGettingPowered(position);
        };
    }

    private static RecordingLevel level() {
        return new ObjenesisStd().newInstance(RecordingLevel.class);
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void readsAllFacesInOrderWithIndependentSidePositions(Query kind) {
        RecordingLevel level = level();
        List<BlockFace> calls = new ArrayList<>();
        List<Vector3> retained = new ArrayList<>();
        List<BlockFace> sides = new ArrayList<>();
        Vector3 position = new Vector3(10, 20, 30) {
            @Override public Vector3 getSideVec(BlockFace face) {
                sides.add(face);
                return super.getSideVec(face);
            }
        };
        level.reader = (pos, face) -> {
            calls.add(face);
            retained.add(pos);
            assertEquals(new Vector3(10 + face.getXOffset(), 20 + face.getYOffset(),
                    30 + face.getZOffset()), pos);
            return -1;
        };
        assertEquals(0, query(level, position, kind));
        assertEquals(ORDER, calls);
        assertEquals(ORDER, sides);
        for (int i = 0; i < retained.size(); i++) {
            for (int j = i + 1; j < retained.size(); j++) assertNotSame(retained.get(i), retained.get(j));
        }
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void keepsEachEarlyExitAndOutOfRangePowerContract(Query kind) {
        RecordingLevel level = level();
        for (int stop = 0; stop < ORDER.size(); stop++) {
            BlockFace powered = ORDER.get(stop);
            List<BlockFace> seen = new ArrayList<>();
            level.reader = (pos, face) -> { seen.add(face); return face == powered ? 16 : 0; };
            int expected = kind == Query.STRONG ? 16 : kind == Query.POWERED ? 1 : 15;
            assertEquals(expected, query(level, new Vector3(), kind));
            assertEquals(ORDER.subList(0, stop + 1), seen);
        }
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void preservesSubMaximumPowerAndFirstPositiveShortCircuit(Query kind) {
        RecordingLevel level = level();
        int[] powers = {-1, 0, 4, 3, 9, 6};
        List<BlockFace> seen = new ArrayList<>();
        level.reader = (pos, face) -> { seen.add(face); return powers[face.ordinal()]; };
        assertEquals(kind == Query.POWERED ? 1 : 9, query(level, new Vector3(), kind));
        assertEquals(kind == Query.POWERED ? ORDER.subList(0, 3) : ORDER, seen);
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void nestedQueryKeepsLivePositionAndOriginalReceiver(Query kind) {
        RecordingLevel outer = level(), other = level();
        Position position = new Position(10, 20, 30, outer);
        List<String> seen = new ArrayList<>();
        boolean[] nested = {false};
        other.reader = (pos, face) -> { throw new AssertionError("receiver changed"); };
        outer.reader = (pos, face) -> {
            seen.add((nested[0] ? "inner:" : "outer:") + face + ":" + pos.x);
            if (!nested[0] && face == BlockFace.DOWN) {
                position.x = 100;
                position.level = other;
                nested[0] = true;
                assertEquals(0, query(outer, new Vector3(30, 20, 30), kind));
                nested[0] = false;
            }
            return 0;
        };
        assertEquals(0, query(outer, position, kind));
        List<String> expected = new ArrayList<>();
        expected.add("outer:down:10.0");
        for (BlockFace face : ORDER) expected.add("inner:" + face + ":" + (30d + face.getXOffset()));
        for (BlockFace face : ORDER.subList(1, 6)) expected.add("outer:" + face + ":" + (100d + face.getXOffset()));
        assertEquals(expected, seen);
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void failureStopsQueryAndNextCallStartsAtDown(Query kind) {
        RecordingLevel level = level();
        List<BlockFace> seen = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("power reader failed");
        level.reader = (pos, face) -> {
            seen.add(face);
            if (face == BlockFace.SOUTH) throw failure;
            return 0;
        };
        assertSame(failure, assertThrows(RuntimeException.class, () -> query(level, new Vector3(), kind)));
        assertEquals(ORDER.subList(0, 4), seen);
        seen.clear();
        level.reader = (pos, face) -> { seen.add(face); return 0; };
        assertEquals(0, query(level, new Vector3(), kind));
        assertEquals(ORDER, seen);
    }

    @Tag("perf")
    @Test
    void powerQueriesDoNotAllocateFaceArrays(@TempDir Path directory) throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported());
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path log = directory.resolve("power-allocation.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms128m", "-Xmx256m", "-XX:-DoEscapeAnalysis", "-cp", cp,
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
        public static void main(String[] args) {
            CounterLevel level = new ObjenesisStd().newInstance(CounterLevel.class);
            Vector3 position = new FixedPosition();
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            bean.setThreadAllocatedMemoryEnabled(true);
            for (Query kind : Query.values()) {
                for (int i = 0; i < 100_000; i++) query(level, position, kind);
                double[] values = new double[7];
                for (int round = 0; round < values.length; round++) {
                    long reads = level.reads;
                    long before = bean.getCurrentThreadAllocatedBytes();
                    for (int i = 0; i < 20_000; i++) {
                        if (query(level, position, kind) != 0) throw new AssertionError("unexpected power");
                    }
                    values[round] = (bean.getCurrentThreadAllocatedBytes() - before) / 20_000d;
                    if (level.reads - reads != 120_000) throw new AssertionError("six reads per query");
                    sink = level.reads;
                }
                Arrays.sort(values);
                System.out.println(kind + " B/query=" + values[3]);
                if (values[3] > 8) throw new AssertionError("face array allocated: " + kind + " " + values[3]);
            }
        }
    }

    private static final class RecordingLevel extends Level {
        ToIntBiFunction<Vector3, BlockFace> reader;
        private RecordingLevel() { super(null, "", "", null); }
        @Override public int getStrongPower(Vector3 pos, BlockFace face) { return reader.applyAsInt(pos, face); }
        @Override public int getRedstonePower(Vector3 pos, BlockFace face) { return reader.applyAsInt(pos, face); }
    }

    private static final class CounterLevel extends Level {
        long reads;
        private CounterLevel() { super(null, "", "", null); }
        @Override public int getStrongPower(Vector3 pos, BlockFace face) { reads++; return 0; }
        @Override public int getRedstonePower(Vector3 pos, BlockFace face) { reads++; return 0; }
    }

    private static final class FixedPosition extends Vector3 {
        final Vector3[] positions = new Vector3[6];
        FixedPosition() {
            for (BlockFace face : ORDER) positions[face.ordinal()] = super.getSideVec(face);
        }
        @Override public Vector3 getSideVec(BlockFace face) { return positions[face.ordinal()]; }
    }
}
