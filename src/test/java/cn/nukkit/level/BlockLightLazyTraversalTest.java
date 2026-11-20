package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.anvil.Chunk;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objenesis.ObjenesisStd;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlockLightLazyTraversalTest {
    private static final DimensionData DIMENSION = DimensionEnum.OVERWORLD.getDimensionData();
    private static final Long ROOT = Level.chunkHash(0, 0);

    @BeforeAll
    static void initializeBlocks() {
        Block.init();
    }

    @Test
    void unchangedRootConsumesItsBucketWithExactReadsAndNoWrites() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunk.trace = new ArrayList<>();
        fixture.queue.put(ROOT, Set.of(hash(1, 64, 1)));

        fixture.level.updateBlockLight(fixture.queue);

        assertTrue(fixture.queue.isEmpty());
        assertEquals(List.of("light:1:64:1", "block:1:64:1"), fixture.chunk.trace);
        assertEquals(1, fixture.level.chunkReads);
        assertEquals(1, fixture.chunk.lightReads);
        assertEquals(1, fixture.chunk.blockReads);
        assertEquals(0, fixture.chunk.lightWrites);
    }

    @Test
    void unchangedUpdatesStayBelowTraversalContainerAllocationCost(@TempDir Path directory) throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof ThreadMXBean mx && mx.isThreadAllocatedMemorySupported());

        // Other tests instrument Level through Mockito, changing per-call allocation.
        // Measure the production traversal in a fresh JVM without that instrumentation.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = directory.resolve("unchanged-block-light.log");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms256m", "-Xmx512m", "-cp", classpath, AllocationProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            String output;
            try (InputStream stream = Files.newInputStream(outputFile)) {
                output = new String(stream.readNBytes(65_536), StandardCharsets.UTF_8);
            }
            assertTrue(completed, "Native allocation child timed out\n" + output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Native allocation child must be reaped");
            }
        }
    }

    public static final class AllocationProbe {
        public static void main(String[] args) throws Exception {
            Block.init();
            measureUnchangedUpdates();
        }
    }

    private static void measureUnchangedUpdates() throws Exception {
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean, "Thread allocation accounting unavailable");
        ThreadMXBean bean = (ThreadMXBean) platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported(), "Thread allocation accounting unsupported");
        boolean originallyEnabled = bean.isThreadAllocatedMemoryEnabled();
        if (!originallyEnabled) bean.setThreadAllocatedMemoryEnabled(true);
        try {
            Fixture fixture = new Fixture();
            Set<Integer> rootCell = Set.of(hash(1, 64, 1));
            int warmup = 50_000;
            int operations = 10_000;
            long[] windows = new long[5];
            updateRepeatedly(fixture, rootCell, warmup);
            for (int i = 0; i < windows.length; i++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                updateRepeatedly(fixture, rootCell, operations);
                windows[i] = bean.getCurrentThreadAllocatedBytes() - before;
                assertTrue(windows[i] >= 0, "Allocation counter must remain enabled");
            }
            Arrays.sort(windows);
            long calls = warmup + (long) operations * windows.length;
            assertTrue(fixture.queue.isEmpty());
            assertEquals(calls, fixture.level.chunkReads);
            assertEquals(calls, fixture.chunk.lightReads);
            assertEquals(calls, fixture.chunk.blockReads);
            assertEquals(0, fixture.chunk.lightWrites);
            long median = windows[windows.length / 2];
            // Includes the fresh HashMap node, native iterator and coordinate read.
            // The previous four eager containers exceed this budget even for a dark AIR root.
            assertTrue(median <= 512L * operations,
                    "Unchanged native light update allocated " + (median / (double) operations)
                            + " bytes/update; traversal queues must remain unallocated");
        } finally {
            if (!originallyEnabled) bean.setThreadAllocatedMemoryEnabled(false);
        }
    }

    @Test
    void unchangedFirstCellDoesNotSkipLaterEmitterOrPropagation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunk.trace = new ArrayList<>();
        fixture.chunk.ids[RecordingChunk.index(8, 64, 8)] = BlockID.REDSTONE_TORCH;
        Set<Integer> cells = new LinkedHashSet<>();
        cells.add(hash(1, 64, 1));
        cells.add(hash(8, 64, 8));
        fixture.queue.put(ROOT, cells);

        fixture.level.updateBlockLight(fixture.queue);

        assertTrue(fixture.queue.isEmpty());
        assertEquals(List.of("light:1:64:1", "block:1:64:1", "light:8:64:8", "block:8:64:8"),
                fixture.chunk.trace.subList(0, 4));
        assertEquals(7, fixture.chunk.emitted[RecordingChunk.index(8, 64, 8)]);
        assertEquals(6, fixture.chunk.emitted[RecordingChunk.index(9, 64, 8)]);
        assertEquals(0, fixture.chunk.emitted[RecordingChunk.index(1, 64, 1)]);
        assertTrue(fixture.chunk.lightWrites > 1, "Later changed root must spread to its neighbors");
    }

    @Test
    void enqueueDuringUnchangedReadRemainsForNextTick() throws Exception {
        Fixture fixture = new Fixture();
        fixture.queue.put(ROOT, Set.of(hash(1, 64, 1)));
        fixture.chunk.onNextBlockRead = () -> fixture.level.addLightUpdate(2, 64, 1);

        fixture.level.updateBlockLight(fixture.queue);

        assertEquals(Set.of(hash(2, 64, 1)), fixture.queue.get(ROOT));
        assertEquals(1, fixture.chunk.blockReads);
        assertEquals(0, fixture.chunk.lightWrites);
        fixture.level.updateBlockLight(fixture.queue);
        assertTrue(fixture.queue.isEmpty());
        assertEquals(2, fixture.chunk.blockReads);
        assertEquals(2, fixture.chunk.lightReads);
        assertEquals(0, fixture.chunk.lightWrites);
    }

    private static void updateRepeatedly(Fixture fixture, Set<Integer> rootCell, int operations) {
        for (int i = 0; i < operations; i++) {
            fixture.queue.put(ROOT, rootCell);
            fixture.level.updateBlockLight(fixture.queue);
        }
    }

    private static int hash(int x, int y, int z) {
        return Level.localBlockHash(x, y, z, DIMENSION);
    }

    private static final class Fixture {
        final QueryLevel level = new ObjenesisStd().newInstance(QueryLevel.class);
        final RecordingChunk chunk = new RecordingChunk();
        final Map<Long, Set<Integer>> queue = new HashMap<>();

        Fixture() throws Exception {
            level.chunk = chunk;
            level.setDimensionData(DIMENSION);
            Field lightQueue = Level.class.getDeclaredField("lightQueue");
            lightQueue.setAccessible(true);
            lightQueue.set(level, queue);
        }
    }

    /** Concrete Level subclass runs production methods without mocked Level calls or a server lifecycle. */
    private static final class QueryLevel extends Level {
        RecordingChunk chunk;
        long chunkReads;

        private QueryLevel() {
            super(null, null, null, (Class<? extends LevelProvider>) null);
        }

        @Override
        public BaseFullChunk getChunk(int x, int z, boolean generate) {
            chunkReads++;
            if (x != 0 || z != 0) throw new AssertionError("Unexpected chunk lookup: " + x + ',' + z);
            return chunk;
        }

        @Override
        public BaseFullChunk getChunkIfLoaded(int x, int z) {
            if (x != 0 || z != 0) return null;
            return chunk;
        }

        @Override
        public int getMinBlockY() {
            return 0;
        }

        @Override
        public int getMaxBlockY() {
            return 255;
        }
    }

    private static final class RecordingChunk extends Chunk {
        final int[] ids = new int[16 * 16 * 256];
        final int[] emitted = new int[16 * 16 * 256];
        long lightReads;
        long blockReads;
        long lightWrites;

        RecordingChunk() {
            super((LevelProvider) null);
        }

        static int index(int x, int y, int z) {
            return (x * 16 + z) * 256 + y;
        }

        @Override
        public int getBlockId(int x, int y, int z, int layer) {
            return layer == 0 ? getBlockId(x, y, z) : 0;
        }

        @Override
        public void setBlockLight(int x, int y, int z, int value) {
            lightWrites++;
            emitted[index(x, y, z)] = value;
        }

        List<String> trace;
        Runnable onNextBlockRead;

        @Override
        public int getBlockLight(int x, int y, int z) {
            if (trace != null) trace.add("light:" + x + ':' + y + ':' + z);
            lightReads++;
            return emitted[index(x, y, z)];
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            if (trace != null) trace.add("block:" + x + ':' + y + ':' + z);
            Runnable callback = onNextBlockRead;
            if (callback != null) {
                onNextBlockRead = null;
                callback.run();
            }
            blockReads++;
            return ids[index(x, y, z)];
        }
    }
}
