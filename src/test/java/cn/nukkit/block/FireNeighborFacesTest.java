package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public callback contracts and an isolated allocation fence; native tick evidence is separate. */
public final class FireNeighborFacesTest {
    private static final BlockFace[] ORDER = {
            BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CallbackFire extends BlockFire {
        final BurnProbe[] neighbors = new BurnProbe[6];
        final int[] trace = new int[128];
        int traceSize;
        boolean tracing = true;
        int depth;
        long sideCalls;
        long burnCalls;
        BlockFace hookFace;
        Runnable sideHook;
        Runnable burnHook;

        CallbackFire(int hit) {
            for (int i = 0; i < neighbors.length; i++) {
                neighbors[i] = new BurnProbe(this, i, i == hit ? 1 : (i % 2 == 0 ? 0 : -7));
            }
        }

        void record(int value) {
            if (tracing) {
                require(traceSize < trace.length, "trace overflow");
                trace[traceSize++] = value;
            }
        }

        @Override
        public Block getSide(BlockFace face) {
            int index = face.ordinal();
            sideCalls++;
            record(depth * 100 + index * 2);
            if (face == hookFace && sideHook != null) sideHook.run();
            return neighbors[index];
        }
    }

    private static final class BurnProbe extends BlockAir {
        final CallbackFire fire;
        final int index;
        final int chance;

        BurnProbe(CallbackFire fire, int index, int chance) {
            this.fire = fire;
            this.index = index;
            this.chance = chance;
        }

        @Override
        public int getBurnChance() {
            fire.burnCalls++;
            fire.record(fire.depth * 100 + index * 2 + 1);
            if (ORDER[index] == fire.hookFace && fire.burnHook != null) fire.burnHook.run();
            return fire.depth == 1 ? (index == 1 ? 1 : 0) : chance;
        }
    }

    private static void orderedPrefix(CallbackFire fire, int length) {
        require(fire.traceSize == length, "callback trace length");
        for (int i = 0; i < length; i++) require(fire.trace[i] == i, "D-U-N-S-W-E side/burn order");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void visitsEachFaceInOrderAndStopsOnlyAtPositiveBurnChance(int hit) {
        CallbackFire fire = new CallbackFire(hit);
        require(fire.canNeighborBurn() == (hit < 6), "neighbor result");
        int calls = Math.min(hit + 1, 6);
        orderedPrefix(fire, calls * 2);
        require(fire.sideCalls == calls && fire.burnCalls == calls, "one burn read per side");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameFireReentryHasIndependentTraversal(boolean fromBurn) {
        CallbackFire fire = new CallbackFire(5);
        fire.hookFace = BlockFace.NORTH;
        boolean[] nested = new boolean[1];
        Runnable callback = () -> {
            fire.sideHook = null;
            fire.burnHook = null;
            fire.depth = 1;
            try {
                nested[0] = fire.canNeighborBurn();
            } finally {
                fire.depth = 0;
            }
        };
        if (fromBurn) fire.burnHook = callback;
        else fire.sideHook = callback;
        require(fire.canNeighborBurn() && nested[0], "outer EAST and inner UP results");
        int[] expected = fromBurn
                ? new int[]{0, 1, 2, 3, 4, 5, 100, 101, 102, 103, 6, 7, 8, 9, 10, 11}
                : new int[]{0, 1, 2, 3, 4, 100, 101, 102, 103, 5, 6, 7, 8, 9, 10, 11};
        require(fire.traceSize == expected.length, "nested trace length");
        for (int i = 0; i < expected.length; i++) require(fire.trace[i] == expected[i], "nested trace order");
        require(fire.sideCalls == 8 && fire.burnCalls == 8, "nested callback counts");
    }

    private static void exceptionContract(int face, boolean fromBurn) {
        CallbackFire fire = new CallbackFire(5);
        fire.hookFace = ORDER[face];
        IllegalStateException failure = new IllegalStateException("neighbor callback");
        Runnable callback = () -> { throw failure; };
        if (fromBurn) fire.burnHook = callback;
        else fire.sideHook = callback;
        try {
            fire.canNeighborBurn();
            throw new AssertionError("missing callback failure");
        } catch (IllegalStateException actual) {
            require(actual == failure, "exception identity");
        }
        orderedPrefix(fire, face * 2 + (fromBurn ? 2 : 1));
        fire.sideHook = null;
        fire.burnHook = null;
        fire.traceSize = 0;
        require(fire.canNeighborBurn(), "clean traversal after callback failure");
        orderedPrefix(fire, 12);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 5})
    void sideExceptionPreservesIdentityAndStopsCallbacks(int face) {
        exceptionContract(face, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 5})
    void burnExceptionPreservesIdentityAndStopsCallbacks(int face) {
        exceptionContract(face, true);
    }

    private static final class LiveLevel extends Level {
        LiveState state;
        private LiveLevel() { super(null, "fire-faces", "fire-faces", null); }

        @Override
        public Block getBlock(int x, int y, int z, int layer) {
            int face = state.stage / 3;
            require(state.stage % 3 == 1, "level read must follow virtual side callback");
            require(this == (face % 2 == 0 ? state.second : state.first), "live level after side callback");
            require(x == 110 + face * 101 + ORDER[face].getXOffset()
                    && y == 220 + face * 202 + ORDER[face].getYOffset()
                    && z == 330 + face * 303 + ORDER[face].getZOffset(), "live coordinates after side callback");
            require(layer == face % 2, "live layer after side callback");
            state.stage++;
            return state.burn;
        }
    }

    private static final class LiveFire extends BlockFire {
        LiveState state;

        @Override
        public Block getSide(BlockFace face) {
            int index = state.stage / 3;
            require(state.stage % 3 == 0 && face == ORDER[index], "virtual side order");
            require(x == 10 + index * 101 && y == 20 + index * 202 && z == 30 + index * 303,
                    "previous burn callback coordinates remain live");
            require(level == (index == 0 || index % 2 == 1 ? state.first : state.second),
                    "previous burn callback level remains live");
            require(layer == (index == 0 ? 0 : 1 - (index - 1) % 2), "previous burn callback layer remains live");
            setComponents(x + 100, y + 200, z + 300);
            level = index % 2 == 0 ? state.second : state.first;
            layer = index % 2;
            state.stage++;
            return super.getSide(face);
        }
    }

    private static final class LiveBurn extends BlockAir {
        final LiveState state;
        LiveBurn(LiveState state) { this.state = state; }

        @Override
        public int getBurnChance() {
            int face = state.stage / 3;
            require(state.stage % 3 == 2, "burn callback immediately follows level read");
            LiveFire fire = state.fire;
            require(fire.x == 110 + face * 101 && fire.y == 220 + face * 202
                    && fire.z == 330 + face * 303, "side callback position visible to burn callback");
            fire.setComponents(fire.x + 1, fire.y + 2, fire.z + 3);
            fire.level = face % 2 == 0 ? state.first : state.second;
            fire.layer = 1 - fire.layer;
            state.stage++;
            return face == 5 ? 1 : 0;
        }
    }

    private static final class LiveState {
        final LiveLevel first = new ObjenesisStd().newInstance(LiveLevel.class);
        final LiveLevel second = new ObjenesisStd().newInstance(LiveLevel.class);
        final LiveFire fire = new LiveFire();
        final LiveBurn burn = new LiveBurn(this);
        int stage;

        LiveState() {
            first.state = this;
            second.state = this;
            fire.state = this;
            fire.setComponents(10, 20, 30);
            fire.level = first;
        }
    }

    @Test
    void callbacksKeepPositionLevelAndLayerLive() {
        LiveState state = new LiveState();
        require(state.fire.canNeighborBurn(), "live final EAST fuel");
        require(state.stage == 18, "all live side/level/burn callbacks");
        require(state.fire.x == 616 && state.fire.y == 1232 && state.fire.z == 1848,
                "final callback position retained");
        require(state.fire.level == state.second && state.fire.layer == 0, "final callback level/layer retained");
    }

    @Test
    void callerOwnedValuesArraysCannotChangeTraversal() {
        BlockFace[] external = BlockFace.values();
        Arrays.fill(external, null);
        CallbackFire fire = new CallbackFire(5);
        fire.hookFace = BlockFace.NORTH;
        fire.burnHook = () -> Arrays.fill(BlockFace.values(), BlockFace.EAST);
        require(fire.canNeighborBurn(), "external array mutation result");
        orderedPrefix(fire, 12);
    }

    @Test
    void traversalCreatesNoTemporaryFaceArray(@TempDir Path temporary) throws Exception {
        Path output = temporary.resolve("fire-allocation.log");
        Path loadedClasses = temporary.resolve("fire-classes.log");
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
            assertTrue(text.contains("FIRE_FACES=PASS"), text);
            String loaded = Files.readString(loadedClasses);
            assertFalse(loaded.contains("org.mockito."), "allocation child loaded Mockito");
            assertFalse(loaded.contains("net.bytebuddy."), "allocation child loaded ByteBuddy");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "allocation child was not reaped");
            }
        }
    }

    public static final class AllocationChild {
        private static volatile Object arraySink;
        private static volatile long resultSink;

        private static double run(CallbackFire fire, ThreadMXBean bean, int count) {
            long sides = fire.sideCalls, burns = fire.burnCalls;
            long found = 0;
            long before = bean.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < count; i++) if (fire.canNeighborBurn()) found++;
            long bytes = bean.getCurrentThreadAllocatedBytes() - before;
            resultSink = found;
            require(found == count, "measured EAST fuel");
            require(fire.sideCalls - sides == count * 6L && fire.burnCalls - burns == count * 6L,
                    "measured native callback counts");
            return bytes / (double) count;
        }

        private static double control(ThreadMXBean bean, int count, boolean allocate) {
            long before = bean.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < count; i++) {
                if (allocate) arraySink = BlockFace.values();
            }
            return (bean.getCurrentThreadAllocatedBytes() - before) / (double) count;
        }

        public static void main(String[] args) {
            BlockFire coldStart = new BlockFire();
            if (coldStart.getId() != Block.FIRE) throw new AssertionError("cold BlockFire id");
            System.out.println("FIRE_COLD_INIT=PASS");
            require(ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:-DoEscapeAnalysis"),
                    "this allocation fence requires EA disabled");
            FireNeighborFacesTest contracts = new FireNeighborFacesTest();
            for (int hit = 0; hit <= 6; hit++) contracts.visitsEachFaceInOrderAndStopsOnlyAtPositiveBurnChance(hit);
            for (boolean fromBurn : new boolean[]{false, true}) contracts.sameFireReentryHasIndependentTraversal(fromBurn);
            for (int face : new int[]{0, 2, 5}) {
                contracts.sideExceptionPreservesIdentityAndStopsCallbacks(face);
                contracts.burnExceptionPreservesIdentityAndStopsCallbacks(face);
            }
            contracts.callbacksKeepPositionLevelAndLayerLive();
            contracts.callerOwnedValuesArraysCannotChangeTraversal();
            System.out.println("FIRE_CONTRACTS=PASS,cases=17");
            System.out.println("NATIVE_BLOCK_FIRE=" + BlockFire.class.getProtectionDomain().getCodeSource().getLocation());
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(), "allocation accounting required");
            bean.setThreadAllocatedMemoryEnabled(true);
            control(bean, 100_000, false);
            control(bean, 100_000, true);
            double counter = control(bean, 20_000, false);
            double array = control(bean, 20_000, true);
            require(counter <= 1d, "counter allocates");
            require(array >= 32d && array <= 64d, "escaping enum array calibration");
            System.out.printf(Locale.ROOT, "CONTROL,counter=%.6f,enumArray=%.6f%n", counter, array);
            CallbackFire fire = new CallbackFire(5);
            fire.tracing = false;
            run(fire, bean, 100_000);
            run(fire, bean, 100_000);
            double[] values = new double[7];
            for (int round = 0; round < values.length; round++) {
                values[round] = run(fire, bean, 20_000);
                System.out.printf(Locale.ROOT, "ROUND,%d,bytes=%.6f%n", round, values[round]);
            }
            Arrays.sort(values);
            double median = values[values.length / 2];
            System.out.printf(Locale.ROOT, "FIRE_ARRAY,median=%.6f,budget=16,sink=%d%n", median, resultSink);
            require(median <= 16d, "neighbor traversal retains temporary faces array (budget 16 B/call)");
            System.out.println("FIRE_FACES=PASS");
        }
    }
}
