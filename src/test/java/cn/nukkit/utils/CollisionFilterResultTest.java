package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStone;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.serverconfig.ServerConfig;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CollisionFilterResultTest {
    private final SimpleAxisAlignedBB bounds = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);

    @Test
    void fullResultsStayFreshAndDoNotShareInputOrPreviousResults() {
        for (int size : new int[]{1, 2, 4, 8, 9}) {
            Block[] input = new Block[size];
            for (int i = 0; i < size; i++) input[i] = new Collider(false, false, true);
            Block[] first = filter(input);
            Block[] second = filter(input);
            assertArrayEquals(input, first);
            assertArrayEquals(input, second);
            for (int i = 0; i < size; i++) {
                assertSame(input[i], first[i]);
                assertSame(input[i], second[i]);
            }
            assertNotSame(input, first);
            assertNotSame(first, second);
            first[0] = null;
            assertNotNull(input[0]);
            assertSame(input[0], second[0]);
        }
    }

    @Test
    void emptyAndRejectedResultsKeepExistingSharedEmptyArray() {
        assertSame(Block.EMPTY_ARRAY, filter(Block.EMPTY_ARRAY));
        assertSame(Block.EMPTY_ARRAY, filter(new Block[]{new Collider(false, false, false)}));
        assertSame(Block.EMPTY_ARRAY, filter(new Block[]{
                new Collider(true, false, false), new Collider(true, true, false)}));
    }

    @Test
    void partialResultsKeepAcceptedOrderWithoutSpareNullTail() {
        Block first = new Collider(false, false, true);
        Block rejected = new Collider(false, false, false);
        Block last = new Collider(false, false, true);
        assertArrayEquals(new Block[]{first, last}, filter(new Block[]{first, rejected, last, rejected}));
    }

    @Test
    void everyAcceptanceBranchCanAllocateFirstResult() {
        for (Collider collider : new Collider[]{new Collider(false, false, true),
                new Collider(true, false, true), new Collider(true, true, true)}) {
            assertArrayEquals(new Block[]{collider}, filter(new Block[]{collider}));
            assertEquals(1, collider.collisionCalls);
            assertTrue(collider.checkedCollisionShape);
            if (!collider.passThrough) {
                assertSame(bounds, collider.lastBounds);
            } else if (collider.dynamic) {
                assertEquals(-0.5, collider.lastBounds.getMinX());
                assertEquals(1.5, collider.lastBounds.getMaxX());
                assertEquals(-0.5, collider.lastBounds.getMinY());
                assertEquals(1.5, collider.lastBounds.getMaxY());
            } else {
                assertEquals(0.25, collider.lastBounds.getMinX());
                assertEquals(0.75, collider.lastBounds.getMaxX());
                assertEquals(0, collider.lastBounds.getMinY());
                assertEquals(1, collider.lastBounds.getMaxY());
            }
        }
    }

    private Block[] filter(Block[] input) {
        return CollisionHelper.filterCollisionBlocks(input, bounds, 0.5, 0.5, 0.5);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 9})
    void ownedCompactionPreservesOrderAndVisitsEveryPredicateOnce(int size) throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            List<String> trace = new ArrayList<>();
            List<Block> expected = new ArrayList<>();
            Block[] blocks = new Block[size];
            for (int i = 0; i < size; i++) {
                boolean accepted = mode == 0 || (mode == 1 && i % 2 == 0);
                TracingCollider block = new TracingCollider("b" + i, i % 3 != 0, i % 3 == 1, accepted, trace);
                blocks[i] = block;
                if (accepted) expected.add(block);
            }
            List<String> expectedTrace = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                expectedTrace.add("b" + i + ":pass");
                if (i % 3 != 0) expectedTrace.add("b" + i + ":dynamic");
                expectedTrace.add("b" + i + ":collide");
            }
            Block[] result = owned(blocks, bounds);
            assertArrayEquals(expected.toArray(Block[]::new), result);
            assertEquals(expectedTrace, trace);
            for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), result[i]);
            if (expected.isEmpty()) assertSame(Block.EMPTY_ARRAY, result);
        }
    }

    @Test
    void retainedBoundsAndLiveChangesKeepOriginalEvaluationOrder() throws Exception {
        List<String> trace = new ArrayList<>();
        TracingCollider dynamic = new TracingCollider("dynamic", true, true, true, trace);
        TracingCollider shrink = new TracingCollider("shrink", true, false, true, trace);
        TracingCollider solid = new TracingCollider("solid", false, false, true, trace);
        dynamic.action = () -> bounds.setBounds(0, 0, 0, 4, 1, 4);
        assertArrayEquals(new Block[]{dynamic, shrink, solid},
                owned(new Block[]{dynamic, shrink, solid}, bounds));
        assertEquals(-0.5, dynamic.checked.getMinX());
        assertEquals(1.5, dynamic.checked.getMaxX());
        assertEquals(1, shrink.checked.getMinX());
        assertEquals(3, shrink.checked.getMaxX());
        assertNotSame(dynamic.checked, shrink.checked);
        assertSame(bounds, solid.checked);
        assertEquals(List.of("dynamic:pass", "dynamic:dynamic", "dynamic:collide",
                "shrink:pass", "shrink:dynamic", "shrink:collide", "solid:pass", "solid:collide"), trace);
    }

    @Test
    void nestedQueryAndMutationOfLaterBlockRemainLive() throws Exception {
        List<String> trace = new ArrayList<>();
        TracingCollider first = new TracingCollider("first", false, false, true, trace);
        TracingCollider later = new TracingCollider("later", false, false, true, trace);
        TracingCollider last = new TracingCollider("last", false, false, true, trace);
        TracingCollider nested = new TracingCollider("nested", false, false, true, trace);
        Block[][] retainedNested = new Block[1][];
        first.action = () -> {
            later.accepted = false;
            try { retainedNested[0] = owned(new Block[]{nested}, bounds); }
            catch (Exception exception) { throw new AssertionError(exception); }
        };
        Block[] result = owned(new Block[]{first, later, last}, bounds);
        assertArrayEquals(new Block[]{first, last}, result);
        assertArrayEquals(new Block[]{nested}, retainedNested[0]);
        result[0] = null;
        assertSame(nested, retainedNested[0][0]);
        assertEquals(List.of("first:pass", "first:collide", "nested:pass", "nested:collide",
                "later:pass", "later:collide", "last:pass", "last:collide"), trace);
    }

    @Test
    void callbackFailureStopsAtSameBlockAndDoesNotPoisonNextQuery() throws Exception {
        List<String> trace = new ArrayList<>();
        TracingCollider first = new TracingCollider("first", false, false, true, trace);
        TracingCollider failure = new TracingCollider("failure", false, false, true, trace);
        TracingCollider last = new TracingCollider("last", false, false, true, trace);
        IllegalStateException expected = new IllegalStateException("collision callback");
        failure.action = () -> { throw expected; };
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> owned(new Block[]{first, failure, last}, bounds)));
        assertEquals(List.of("first:pass", "first:collide", "failure:pass", "failure:collide"), trace);
        failure.action = null;
        assertArrayEquals(new Block[]{first, failure, last},
                owned(new Block[]{first, failure, last}, bounds));
    }

    private static Block[] owned(Block[] blocks, AxisAlignedBB bounds) throws Exception {
        var method = CollisionHelper.class.getDeclaredMethod("filterCollisionBlocks", Block[].class,
                AxisAlignedBB.class, double.class, double.class, double.class, boolean.class);
        method.setAccessible(true);
        try { return (Block[]) method.invoke(null, blocks, bounds, 0.5, 0.5, 0.5, true); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            if (exception.getCause() instanceof Error error) throw error;
            throw exception;
        }
    }

    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 9})
    void nativeMixedResultsKeepOrderFreshnessAndVersionChanges(int size) throws Exception {
        CollisionGrowFixture fixture = new CollisionGrowFixture(false);
        fixture.entity.box(new SimpleAxisAlignedBB(1, 68, 1, 1 + size, 69, 2));
        for (int x = 1; x <= size; x++) {
            fixture.write(x, 68, 0, Block.STONE); // Scanned but outside the entity's collision box.
            fixture.write(x, 68, 1, (x & 1) == 0 ? Block.NETHER_PORTAL : Block.STONE);
        }
        Block[] first = fixture.helper.getCollisionBlocks();
        Block[] second = fixture.helper.getCollisionBlocks();
        assertEquals(size, first.length);
        assertEquals(size, second.length);
        assertNotSame(first, second);
        for (int i = 0; i < size; i++) {
            assertEquals(i + 1, first[i].x);
            assertEquals(68, first[i].y);
            assertEquals(1, first[i].z);
            assertEquals(first[i].getId(), second[i].getId());
            assertNotSame(first[i], second[i]);
        }
        first[0].x = 99;
        first[0] = null;
        Block[] third = fixture.helper.getCollisionBlocks();
        assertEquals(1, third[0].x);
        assertEquals(1, second[0].x);
        for (int x = 1; x <= size; x++) fixture.write(x, 68, 1, Block.AIR);
        assertSame(Block.EMPTY_ARRAY, fixture.helper.getCollisionBlocks());
        fixture.write(1, 68, 1, Block.NETHER_PORTAL);
        Block[] replacement = fixture.helper.getCollisionBlocks();
        assertEquals(1, replacement.length);
        assertEquals(Block.NETHER_PORTAL, replacement[0].getId());
        assertEquals(Block.STONE, second[0].getId());
    }

    @Test
    void nativePortalQueryDoesNotAllocateASecondResultArray() throws Exception {
        Path output = temporary.resolve("allocation.txt");
        Path classes = temporary.resolve("class-load.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-XX:-DoEscapeAnalysis", "-Xms128m", "-Xmx512m",
                "-Xlog:class+load=info:file=" + classes,
                "-Dnukkit.collision.owned.data=" + temporary,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AllocationChild.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "native allocation child timeout");
            String text = Files.readString(output);
            assertEquals(0, process.exitValue(), text);
            assertTrue(text.contains("PORTAL_NATIVE_ALLOCATION"), text);
            String loaded = Files.readString(classes);
            assertFalse(loaded.contains("org.mockito."), "Mockito must not instrument the native child");
            assertFalse(loaded.contains("net.bytebuddy."), "ByteBuddy must not instrument the native child");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "allocation child was not reaped");
            }
        }
    }

    public static final class AllocationChild {
        private static void require(boolean value, String message) {
            if (!value) throw new AssertionError(message);
        }
        private static double run(CollisionGrowFixture fixture, ThreadMXBean bean, int count) {
            long bytes = 0;
            Block[] previous = null;
            for (int i = 0; i < count; i++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                Block[] result = fixture.helper.getCollisionBlocks();
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
                require(result.length == 1 && result[0].getId() == Block.NETHER_PORTAL,
                        "one real portal must be returned");
                require(result[0].x == 5 && result[0].y == 68 && result[0].z == 5,
                        "native portal coordinates");
                if (previous != null) {
                    require(result != previous && result[0] != previous[0], "fresh array and block");
                    previous[0].x = 99;
                    previous[0] = null;
                }
                previous = result;
            }
            return bytes / (double) count;
        }
        public static void main(String[] args) throws Exception {
            Server server = new ObjenesisStd().newInstance(Server.class);
            CollisionGrowFixture.set(Server.class, null, "instance", server);
            CollisionGrowFixture.set(Server.class, server, "currentThread", Thread.currentThread());
            CollisionGrowFixture.set(Server.class, server, "dataPath", System.getProperty("nukkit.collision.owned.data"));
            ServerConfig config = new ServerConfig();
            config.customBlockSettings().autoDownloadVanillaPalette(false);
            CollisionGrowFixture.set(Server.class, server, "serverConfig", config);
            CollisionGrowFixture fixture = new CollisionGrowFixture(false);
            fixture.entity.box(new SimpleAxisAlignedBB(5, 68, 5, 6, 69, 6));
            fixture.write(5, 68, 5, Block.NETHER_PORTAL);
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(), "allocation accounting is required");
            bean.setThreadAllocatedMemoryEnabled(true);
            run(fixture, bean, 100_000);
            double[] windows = new double[7];
            for (int i = 0; i < windows.length; i++) windows[i] = run(fixture, bean, 20_000);
            Arrays.sort(windows);
            System.out.println("PORTAL_NATIVE_ALLOCATION=" + windows[3]);
            require(windows[3] <= 152, "owned native result must avoid the second array: " + windows[3]);
        }
    }

    private static final class Collider extends BlockStone {
        private final boolean passThrough;
        private final boolean dynamic;
        private final boolean accepted;
        private int collisionCalls;
        private boolean checkedCollisionShape;
        private AxisAlignedBB lastBounds;

        private Collider(boolean passThrough, boolean dynamic, boolean accepted) {
            this.passThrough = passThrough;
            this.dynamic = dynamic;
            this.accepted = accepted;
        }

        @Override
        public boolean canPassThrough() {
            return passThrough;
        }

        @Override
        public boolean hasDynamicCollision() {
            return dynamic;
        }

        @Override
        public boolean collidesWithBB(AxisAlignedBB bb, boolean collision) {
            collisionCalls++;
            checkedCollisionShape = collision;
            lastBounds = bb;
            return accepted;
        }
    }

    private static final class TracingCollider extends BlockStone {
        private final String label;
        private final boolean pass;
        private final boolean dynamic;
        private final List<String> trace;
        private boolean accepted;
        private Runnable action;
        private AxisAlignedBB checked;
        TracingCollider(String label, boolean pass, boolean dynamic, boolean accepted, List<String> trace) {
            this.label = label; this.pass = pass; this.dynamic = dynamic;
            this.accepted = accepted; this.trace = trace;
        }
        @Override public boolean canPassThrough() { trace.add(label + ":pass"); return pass; }
        @Override public boolean hasDynamicCollision() { trace.add(label + ":dynamic"); return dynamic; }
        @Override public boolean collidesWithBB(AxisAlignedBB box, boolean collisionBB) {
            trace.add(label + ":collide");
            assertTrue(collisionBB);
            checked = box;
            if (action != null) action.run();
            return accepted;
        }
    }
}
