package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LevelPackedBlockReadTest {
    private static volatile Block sink;

    @BeforeAll static void init() { MockServer.init(); }

    @Test void randomizedMaterializationMatchesLegacyStateArrays() throws Exception {
        TestLevel level = level();
        StateBlockStorage[] storages = {new StateBlockStorage(), new StateBlockStorage()};
        Random random = new Random(0x347);
        int[] ids = {Block.AIR, Block.STONE, Block.WATER, Block.FENCE, Block.GLASS_PANE, 9000};
        for (StateBlockStorage storage : storages) {
            for (int cell = 0; cell < 4096; cell++) {
                int id = ids[random.nextInt(ids.length)];
                int meta = random.nextInt(5) == 0 ? 9001 : random.nextInt(16);
                storage.set(cell, BlockStateSnapshot.builder().legacyId(id).legacyData(meta).build());
            }
        }
        LevelDBChunkSection section = new LevelDBChunkSection(0, storages, false);
        LevelDBChunk chunk = chunk(-2, -3, section);
        level.loaded = chunk;
        for (int layer = 0; layer < 3; layer++) {
            for (int cell = 0; cell < 4096; cell++) {
                int x = -32 + (cell >> 8), y = cell & 15, z = -48 + ((cell >> 4) & 15);
                int[] state = chunk.getBlockState(x & 15, y, z & 15, layer);
                Block expected = Block.get(state[0], state[1], level, x, y, z, layer);
                Block actual = level.getBlock(chunk, x, y, z, layer, false);
                sameBlock(expected, actual);
                assertNotSame(expected, actual);
            }
        }
    }

    @Test void fullMetadataSurvivesAndInstancesRemainDetached() throws Exception {
        TestLevel level = level();
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunk chunk = chunk(0, 0, new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false));
        for (int meta : new int[]{8192, 9001, 1 << 23, -2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            storage.set(0, BlockStateSnapshot.builder().legacyId(9000).legacyData(meta).build());
            Block first = level.getBlock(chunk, 0, 0, 0, 0, false);
            Block second = level.getBlock(chunk, 0, 0, 0, 0, false);
            assertEquals(9000, first.getId());
            assertEquals(meta, first.getDamage());
            assertNotSame(first, second);
            first.x = 99;
            assertEquals(0, second.x);
        }
    }

    @Test void chunkSubclassKeepsItsVirtualStateOverride() throws Exception {
        TestLevel level = level();
        OverrideChunk chunk = new OverrideChunk(new EmptyChunkSection(0));
        Block actual = level.getBlock(chunk, 1, 2, 3, 1, false);
        assertEquals(Block.WATER, actual.getId());
        assertEquals(9001, actual.getDamage());
        assertEquals(1, chunk.calls);
    }

    @Test void sectionSubclassKeepsItsVirtualStateOverride() throws Exception {
        TestLevel level = level();
        OverrideSection section = new OverrideSection();
        LevelDBChunk chunk = chunk(0, 0, section);
        Block actual = level.getBlock(chunk, 1, 2, 3, 1, false);
        assertEquals(Block.WATER, actual.getId());
        assertEquals(9001, actual.getDamage());
        assertEquals(1, section.calls);
    }

    @Test void emptySectionSubclassKeepsItsVirtualStateOverride() throws Exception {
        TestLevel level = level();
        OverrideEmptySection section = new OverrideEmptySection();
        Block actual = level.getBlock(chunk(0, 0, section), 1, 2, 3, 1, false);
        assertEquals(Block.WATER, actual.getId());
        assertEquals(9001, actual.getDamage());
        assertEquals(1, section.calls);
    }

    @Test void arbitraryFullChunkKeepsItsVirtualStateOverride() throws Exception {
        TestLevel level = level();
        FullChunk chunk = org.mockito.Mockito.mock(FullChunk.class);
        org.mockito.Mockito.when(chunk.getBlockState(1, 2, 3, 1)).thenReturn(new int[]{Block.WATER, 9001});
        Block actual = level.getBlock(chunk, 1, 2, 3, 1, false);
        assertEquals(Block.WATER, actual.getId());
        assertEquals(9001, actual.getDamage());
        org.mockito.Mockito.verify(chunk).getBlockState(1, 2, 3, 1);
    }

    @Test void absentEmptyAndOutOfHeightRemainAirWithOriginalPlacement() throws Exception {
        TestLevel level = level();
        LevelDBChunk empty = chunk(0, 0, new EmptyChunkSection(0));
        for (int layer : new int[]{0, 1, 8, -1}) {
            Block actual = level.getBlock(empty, 1, 2, 3, layer, false);
            sameBlock(Block.get(0, 0, level, 1, 2, 3, layer), actual);
        }
        for (int y : new int[]{-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            sameBlock(Block.get(0, 0, level, -17, y, -33, 1), level.getBlock(empty, -17, y, -33, 1, false));
        }
        sameBlock(Block.get(0, 0, level, 32, 7, 48, 1), level.getBlock(null, 32, 7, 48, 1, false));
        assertEquals(1, level.lookupCalls);
        assertEquals(0, level.loadCalls);
    }

    @Test void wrongChunkHintsAndPublicCoordinateOverloadsKeepLoadChoice() throws Exception {
        TestLevel level = level();
        StateBlockStorage storage = new StateBlockStorage();
        storage.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
        level.loaded = chunk(-2, -3, new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false));
        LevelDBChunk wrong = chunk(0, 0, new EmptyChunkSection(0));
        assertEquals(Block.STONE, level.getBlock(wrong, -32, 0, -48, 0, false).getId());
        assertEquals(1, level.lookupCalls);
        assertEquals(0, level.loadCalls);
        assertEquals(Block.STONE, level.getBlock(-32, 0, -48, 0, true).getId());
        assertEquals(1, level.loadCalls);
        assertEquals(Block.STONE, level.getBlock(-32, 0, -48, 0, false).getId());
        assertEquals(2, level.lookupCalls);
    }

    @Test void expandedDimensionReadsNegativeWorldHeightAndUpperSection() throws Exception {
        TestLevel level = level();
        level.minY = -64;
        level.maxY = 319;
        level.setDimensionData(DimensionEnum.OVERWORLD.getDimensionData());
        cn.nukkit.level.format.leveldb.LevelDBProvider provider = org.mockito.Mockito.mock(cn.nukkit.level.format.leveldb.LevelDBProvider.class);
        org.mockito.Mockito.when(provider.getLevel()).thenReturn(level);
        StateBlockStorage bottom = new StateBlockStorage(), top = new StateBlockStorage();
        bottom.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
        top.set(15, BlockStateSnapshot.builder().legacyId(Block.WATER).legacyData(9001).build());
        ChunkSection[] sections = new ChunkSection[24];
        sections[0] = new LevelDBChunkSection(-4, new StateBlockStorage[]{bottom}, false);
        sections[23] = new LevelDBChunkSection(19, new StateBlockStorage[]{top}, false);
        LevelDBChunk chunk = new LevelDBChunk(provider, -2, -3, sections, null, null, null, null, null, ChunkState.FINISHED);
        for (int y : new int[]{-64, -63, -1, 0, 255, 256, 318, 319}) {
            int[] state = chunk.getBlockState(0, y, 0, 0);
            sameBlock(Block.get(state[0], state[1], level, -32, y, -48, 0), level.getBlock(chunk, -32, y, -48, 0, false));
        }
        assertEquals(Block.STONE, level.getBlock(chunk, -32, -64, -48, 0, false).getId());
        assertEquals(9001, level.getBlock(chunk, -32, 319, -48, 0, false).getDamage());
    }

    @Test void exactSectionKeepsInvalidLayerFailureAndAbsentLayerAir() throws Exception {
        TestLevel level = level();
        LevelDBChunk chunk = chunk(0, 0, new LevelDBChunkSection(0, new StateBlockStorage[]{new StateBlockStorage()}, false));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> chunk.getBlockState(0, 0, 0, -1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> level.getBlock(chunk, 0, 0, 0, -1, false));
        assertEquals(Block.AIR, level.getBlock(chunk, 0, 0, 0, 8, false).getId());
    }

    @Test void knownSectionMaterializationDoesNotAllocateAnIntermediateStateArray() throws Exception {
        // Other suites instrument Level/sections with Mockito. Measure allocation in a fresh,
        // uninstrumented JVM so the contract does not count Mockito dispatch bookkeeping.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Xms256m", "-Xmx256m",
                "-cp", classpath, AllocationProbe.class.getName())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("allocation probe exceeded 60 seconds");
        }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }

    public static class AllocationProbe {
        public static void main(String[] args) throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            Unsafe unsafe = (Unsafe)field.get(null);
            Field instance = cn.nukkit.Server.class.getDeclaredField("instance"); instance.setAccessible(true);
            instance.set(null, unsafe.allocateInstance(cn.nukkit.Server.class));
            Block.init();
            TestLevel level = level();
            StateBlockStorage storage = new StateBlockStorage();
            storage.set(0, BlockStateSnapshot.builder().legacyId(Block.STONE).legacyData(0).build());
            LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
            LevelDBChunk chunk = chunk(0, 0, section);
            LevelDBChunk reference = new NoArrayChunk();
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Thread allocation unsupported");
            bean.setThreadAllocatedMemoryEnabled(true);
            for (int i = 0; i < 200_000; i++) {
                sink = level.getBlock(chunk, 0, 0, 0, 0, false);
                sink = level.getBlock(reference, 0, 0, 0, 0, false);
            }
            double actual = minimumAllocated(bean, () -> sink = level.getBlock(chunk, 0, 0, 0, 0, false));
            double referenceBytes = minimumAllocated(bean, () -> sink = level.getBlock(reference, 0, 0, 0, 0, false));
            System.out.println("public getBlock=" + actual + " B/op; no-array virtual chunk=" + referenceBytes + " B/op");
            if (actual > referenceBytes + 8) throw new AssertionError("intermediate state-array allocation remains");
        }
    }

    private static double minimumAllocated(ThreadMXBean bean, Runnable action) {
        double min = Double.POSITIVE_INFINITY;
        long thread = Thread.currentThread().getId();
        for (int pass = 0; pass < 5; pass++) {
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 50_000; i++) action.run();
            min = Math.min(min, (bean.getThreadAllocatedBytes(thread) - before) / 50_000.0);
        }
        return min;
    }

    private static void sameBlock(Block expected, Block actual) {
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getDamage(), actual.getDamage());
        assertEquals(expected.x, actual.x);
        assertEquals(expected.y, actual.y);
        assertEquals(expected.z, actual.z);
        assertEquals(expected.layer, actual.layer);
        assertSame(expected.level, actual.level);
    }

    private static LevelDBChunk chunk(int x, int z, ChunkSection section) {
        return new LevelDBChunk(null, x, z, new ChunkSection[]{section}, null, null, null, null, null, ChunkState.FINISHED);
    }

    private static TestLevel level() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        TestLevel result = (TestLevel)((Unsafe)field.get(null)).allocateInstance(TestLevel.class);
        result.maxY = 255;
        return result;
    }

    static class TestLevel extends Level {
        BaseFullChunk loaded;
        int lookupCalls, loadCalls, minY, maxY;
        private TestLevel() { super(null, "", "", null); }
        @Override public int getMinBlockY() { return minY; }
        @Override public int getMaxBlockY() { return maxY; }
        @Override public BaseFullChunk getChunk(int x, int z) { loadCalls++; return loaded; }
        @Override public BaseFullChunk getChunkIfLoaded(int x, int z) { lookupCalls++; return loaded; }
    }

    static class NoArrayChunk extends LevelDBChunk {
        private static final int[] STATE = {Block.STONE, 0};
        NoArrayChunk() { super(null, 0, 0); }
        @Override public int[] getBlockState(int x, int y, int z, int layer) { return STATE; }
    }

    static class OverrideChunk extends LevelDBChunk {
        int calls;
        OverrideChunk(ChunkSection section) { super(null, 0, 0, new ChunkSection[]{section}, null, null, null, null, null, ChunkState.FINISHED); }
        @Override public int[] getBlockState(int x, int y, int z, int layer) { calls++; return new int[]{Block.WATER, 9001}; }
    }

    static class OverrideSection extends LevelDBChunkSection {
        int calls;
        OverrideSection() { super(0, new StateBlockStorage[]{new StateBlockStorage()}, false); }
        @Override public int[] getBlockState(int x, int y, int z, int layer) { calls++; return new int[]{Block.WATER, 9001}; }
    }

    static class OverrideEmptySection extends EmptyChunkSection {
        int calls;
        OverrideEmptySection() { super(0); }
        @Override public int[] getBlockState(int x, int y, int z, int layer) { calls++; return new int[]{Block.WATER, 9001}; }
    }
}
