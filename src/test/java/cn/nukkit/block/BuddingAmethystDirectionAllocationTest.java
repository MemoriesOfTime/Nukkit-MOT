package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockSpreadEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuddingAmethystDirectionAllocationTest {
    private static final List<BlockFace> ORDER = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

    @BeforeAll
    static void initializeBlocks() {
        Block.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 5, 6, 7, -1})
    void nonRandomUpdateReturnsInputWithoutRandomOrNeighbourReads(int type) {
        Fixture f = new Fixture(new BlockStone(), "accept");
        try (MockedStatic<ThreadLocalRandom> current = mockStatic(ThreadLocalRandom.class)) {
            assertEquals(type, f.source.onUpdate(type));
            current.verifyNoInteractions();
        }
        assertTrue(f.source.faces.isEmpty());
        assertTrue(f.trace.isEmpty());
        verifyNoInteractions(f.plugins, f.level);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void rejectedGrowthGateConsumesOnlyTheOriginalBoundFourDraw(int gate) {
        Fixture f = new Fixture(new BlockAir(), "accept");
        f.tick(gate, 0);
        assertTrue(f.source.faces.isEmpty());
        assertTrue(f.trace.isEmpty());
        verifyNoInteractions(f.plugins, f.level);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void everyDirectionKeepsOneBoundSixDrawAndOneNeighbourRead(int index) {
        Fixture f = new Fixture(new BlockStone(), "accept");
        f.tick(0, index);
        assertEquals(List.of(ORDER.get(index)), f.source.faces);
        assertTrue(f.trace.isEmpty());
        verifyNoInteractions(f.plugins, f.level);
    }

    static Stream<Arguments> growthCases() {
        return IntStream.range(0, 6).boxed().flatMap(face -> IntStream.range(0, 4).boxed()
                .flatMap(stage -> Stream.of("accept", "cancel", "mutate")
                        .map(mode -> Arguments.of(face, stage, mode))));
    }

    @ParameterizedTest
    @MethodSource("growthCases")
    void growthPreservesAllStagesOrientationCancellationAndEventMutation(int index, int stage, String mode) {
        BlockFace face = ORDER.get(index);
        Block neighbour = stage == 0 ? new BlockAir() : bud(stage, face);
        int oldId = neighbour.getId(), oldDamage = neighbour.getDamage();
        Fixture f = new Fixture(neighbour, mode);
        f.tick(0, index);
        assertEquals(List.of(face), f.source.faces);
        assertEquals(1, f.events.size());
        BlockSpreadEvent event = f.events.get(0);
        assertSame(neighbour, event.getBlock());
        assertSame(f.source, event.getSource());
        assertEquals(targetId(stage), event.getNewState().getId());
        assertEquals(List.of(face), f.eventFaces);
        assertEquals(mode.equals("mutate") ? face.getOpposite() : face,
                ((BlockAmethystBud) event.getNewState()).getBlockFace());
        assertEquals(mode.equals("cancel") ? List.of("event") : List.of("event", "write"), f.trace);
        assertEquals(mode.equals("cancel") ? 0 : 1, f.writes.size());
        if (!mode.equals("cancel")) assertSame(event.getNewState(), f.writes.get(0));
        assertEquals(oldId, neighbour.getId());
        assertEquals(oldDamage, neighbour.getDamage());
        assertEquals(Block.BUDDING_AMETHYST, f.source.getId());
        verify(f.plugins).callEvent(same(event));
        verifyNoMoreInteractions(f.plugins);
    }

    static Stream<Arguments> waterCases() {
        return Stream.of(false, true).flatMap(still -> Stream.of(0, 7, 8, 9)
                .map(meta -> Arguments.of(still, meta)));
    }

    @ParameterizedTest
    @MethodSource("waterCases")
    void onlyTheExistingWaterMetaEightCaseStartsGrowth(boolean still, int meta) {
        Block water = still ? new BlockWaterStill(meta) : new BlockWater(meta);
        Fixture f = new Fixture(water, "accept");
        f.tick(0, 5);
        assertEquals(List.of(BlockFace.EAST), f.source.faces);
        if (meta == 8) {
            assertEquals(List.of("event", "write"), f.trace);
            assertEquals(Block.SMALL_AMETHYST_BUD, f.writes.get(0).getId());
            assertEquals(BlockFace.EAST, ((BlockAmethystBud) f.writes.get(0)).getBlockFace());
        } else {
            assertTrue(f.trace.isEmpty());
            verifyNoInteractions(f.plugins, f.level);
        }
        assertEquals(meta, water.getDamage());
    }

    static Stream<Arguments> wrongFacingCases() {
        return IntStream.range(0, 6).boxed().flatMap(index -> IntStream.rangeClosed(1, 4)
                .mapToObj(stage -> Arguments.of(index, stage)));
    }

    @ParameterizedTest
    @MethodSource("wrongFacingCases")
    void wrongFacingBudsAndMatureClustersDoNotGrowOrRetry(int index, int stage) {
        BlockFace chosen = ORDER.get(index);
        Block neighbour = stage == 4 ? new BlockAmethystCluster() : bud(stage, chosen.getOpposite());
        if (neighbour instanceof BlockAmethystBud) ((BlockAmethystBud) neighbour).setBlockFace(chosen.getOpposite());
        Fixture f = new Fixture(neighbour, "accept");
        f.tick(0, index);
        assertEquals(List.of(chosen), f.source.faces);
        assertTrue(f.trace.isEmpty());
        verifyNoInteractions(f.plugins, f.level);
    }

    @Tag("perf")
    @Test
    void randomTickDoesNotAllocateDirectionArrays(@TempDir Path directory) throws Exception {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported());
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path log = directory.resolve("amethyst-allocation.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms128m", "-Xmx128m", "-XX:-DoEscapeAnalysis", "-cp", cp,
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

    private static BlockAmethystBud bud(int stage, BlockFace face) {
        BlockAmethystBud result = switch (stage) {
            case 1 -> new BlockAmethystBudSmall();
            case 2 -> new BlockAmethystBudMedium();
            case 3 -> new BlockAmethystBudLarge();
            default -> throw new IllegalArgumentException("stage=" + stage);
        };
        result.setBlockFace(face);
        return result;
    }

    private static int targetId(int stage) {
        return switch (stage) {
            case 0 -> Block.SMALL_AMETHYST_BUD;
            case 1 -> Block.MEDIUM_AMETHYST_BUD;
            case 2 -> Block.LARGE_AMETHYST_BUD;
            case 3 -> Block.AMETHYST_CLUSTER;
            default -> throw new IllegalArgumentException("stage=" + stage);
        };
    }

    private static final class RecordingAmethyst extends BlockBuddingAmethyst {
        final Block neighbour;
        final List<BlockFace> faces = new ArrayList<>();
        RecordingAmethyst(Block neighbour) { this.neighbour = neighbour; }
        @Override public Block getSide(BlockFace face) {
            faces.add(face);
            return neighbour;
        }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final PluginManager plugins = mock(PluginManager.class);
        final RecordingAmethyst source;
        final List<String> trace = new ArrayList<>();
        final List<BlockSpreadEvent> events = new ArrayList<>();
        final List<BlockFace> eventFaces = new ArrayList<>();
        final List<Block> writes = new ArrayList<>();

        Fixture(Block neighbour, String mode) {
            source = new RecordingAmethyst(neighbour);
            source.level = level;
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            doAnswer(call -> {
                BlockSpreadEvent event = (BlockSpreadEvent) call.getArgument(0);
                trace.add("event");
                events.add(event);
                BlockAmethystBud target = (BlockAmethystBud) event.getNewState();
                eventFaces.add(target.getBlockFace());
                if (mode.equals("cancel")) event.setCancelled(true);
                if (mode.equals("mutate")) target.setBlockFace(target.getBlockFace().getOpposite());
                return null;
            }).when(plugins).callEvent(any(Event.class));
            doAnswer(call -> {
                assertSame(neighbour, call.getArgument(0));
                assertFalse((boolean) call.getArgument(2), "direct=false");
                assertTrue((boolean) call.getArgument(3), "update=true");
                trace.add("write");
                writes.add(call.getArgument(1));
                return true;
            }).when(level).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
            clearInvocations(level, plugins);
        }

        void tick(int gate, int faceIndex) {
            ThreadLocalRandom random = mock(ThreadLocalRandom.class);
            when(random.nextInt(4)).thenReturn(gate);
            when(random.nextInt(6)).thenReturn(faceIndex);
            clearInvocations(random);
            try (MockedStatic<ThreadLocalRandom> current = mockStatic(ThreadLocalRandom.class)) {
                current.when(ThreadLocalRandom::current).thenReturn(random);
                assertEquals(Level.BLOCK_UPDATE_RANDOM, source.onUpdate(Level.BLOCK_UPDATE_RANDOM));
                current.verify(ThreadLocalRandom::current, times(gate == 0 ? 2 : 1));
                current.verifyNoMoreInteractions();
            }
            InOrder order = inOrder(random);
            order.verify(random).nextInt(4);
            if (gate == 0) order.verify(random).nextInt(6);
            order.verifyNoMoreInteractions();
            verifyNoMoreInteractions(random);
        }
    }

    public static final class AllocationProbe {
        private static volatile long sink;
        public static void main(String[] args) {
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            StoneNeighbours block = new StoneNeighbours();
            long normal = measure(bean, thread, block, Level.BLOCK_UPDATE_NORMAL);
            long random = measure(bean, thread, block, Level.BLOCK_UPDATE_RANDOM);
            if (normal > 1024 || random > 1024) {
                throw new AssertionError("direction allocations remain: normal=" + normal + ", random=" + random);
            }
        }

        private static long measure(ThreadMXBean bean, long thread, StoneNeighbours block, int type) {
            ticks(block, type, 300_000);
            bean.getThreadAllocatedBytes(thread);
            long total = 0;
            for (int window = 0; window < 3; window++) {
                long sidesBefore = block.sides;
                long before = bean.getThreadAllocatedBytes(thread);
                ticks(block, type, 100_000);
                long bytes = bean.getThreadAllocatedBytes(thread) - before;
                long sides = block.sides - sidesBefore;
                total += bytes;
                System.out.println("ALLOC,type=" + type + ",window=" + window + ",ticks=100000,getSide=" + sides
                        + ",bytes=" + bytes + ",bytesPerTick=" + bytes / 100000d
                        + ",bytesPerGetSide=" + (sides == 0 ? "n/a" : Double.toString((double) bytes / sides)));
                if (type == Level.BLOCK_UPDATE_NORMAL && sides != 0) throw new AssertionError("Normal update read neighbours");
                if (type == Level.BLOCK_UPDATE_RANDOM && (sides < 10000 || sides > 40000)) throw new AssertionError("Unexpected RNG gate: " + sides);
            }
            return total;
        }

        private static void ticks(StoneNeighbours block, int type, int count) {
            long sum = 0;
            for (int i = 0; i < count; i++) {
                int result = block.onUpdate(type);
                if (result != type) throw new AssertionError("Changed update result");
                sum += result;
            }
            sink = sum + block.sides + block.faceChecksum;
        }
    }

    private static final class StoneNeighbours extends BlockBuddingAmethyst {
        final Block stone = new BlockStone();
        long sides;
        long faceChecksum;
        @Override public Block getSide(BlockFace face) {
            sides++;
            faceChecksum += face.ordinal();
            return stone;
        }
    }
}
