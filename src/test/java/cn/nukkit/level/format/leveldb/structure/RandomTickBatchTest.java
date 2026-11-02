package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.util.BitArrayVersion;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RandomTickBatchTest {
    @Test
    void nativeLevelBatchesThreeInertReadsIntoOneLockAcquisition() throws Exception {
        // A stale grass palette entry keeps this section eligible, but every cell is stone.
        LevelDBChunkSection section = section(-4, storage(BlockID.STONE, BlockID.GRASS));
        assertTrue(section.mayHaveRandomTickBlocks());
        CountingLock reads = countReads(section);
        LevelFixture fixture = new LevelFixture(section, 3);
        fixture.tick();
        assertEquals(1, reads.acquisitions, "the baseline tickChunks loop acquires three read locks");
        assertEquals(1, reads.releases);
        assertEquals(0, reads.held);
    }

    @Test
    void nativeLevelSkipsInertPaletteWithoutSamplingCells() throws Exception {
        LevelDBChunkSection section = section(2, storage(BlockID.STONE));
        CountingLock reads = countReads(section);
        new LevelFixture(section, 3).tick();
        assertEquals(1, reads.acquisitions, "only the conservative palette probe takes a lock");
        assertEquals(1, reads.releases);
    }

    @Test
    void nativeLevelRetainsSingleReadAtSpeedOneAndSplitsSeventeenAtTheBatchBound() throws Exception {
        LevelDBChunkSection section = section(0, storage(BlockID.STONE, BlockID.GRASS));
        assertTrue(section.mayHaveRandomTickBlocks());
        CountingLock reads = countReads(section);
        new LevelFixture(section, 1).tick();
        assertEquals(1, reads.acquisitions, "one attempt retains the original single-read path");
        assertEquals(1, reads.releases);
        reads.acquisitions = reads.releases = 0;
        new LevelFixture(section, 17).tick();
        assertEquals(2, reads.acquisitions, "sixteen batched reads plus the final ordinary read");
        assertEquals(2, reads.releases);
        assertEquals(0, reads.held);
    }

    @Test
    void nativeLevelDoesNotProbeSectionsWhenTickSpeedIsZeroOrRandomTicksDisabled() throws Exception {
        LevelDBChunkSection zero = section(0, storage(BlockID.GRASS));
        CountingLock zeroReads = countReads(zero);
        new LevelFixture(zero, 0).tick();
        assertEquals(0, zeroReads.acquisitions);
        LevelDBChunkSection disabled = section(0, storage(BlockID.GRASS));
        CountingLock disabledReads = countReads(disabled);
        LevelFixture fixture = new LevelFixture(disabled, 3);
        doReturn(false).when(fixture.level).randomTickingEnabled();
        fixture.tick();
        assertEquals(0, disabledReads.acquisitions);
    }

    @Test
    void nativeLevelPreservesVirtualReadsForSubclassAndUnknownProvider() throws Exception {
        VirtualSection subclass = new VirtualSection();
        new LevelFixture(subclass, 3).tick();
        assertEquals(3, subclass.reads);
        ChunkSection unknown = mock(ChunkSection.class);
        when(unknown.mayHaveRandomTickBlocks()).thenReturn(true);
        when(unknown.getFullBlock(anyInt(), anyInt(), anyInt())).thenReturn(full(BlockID.STONE, 0));
        new LevelFixture(unknown, 3).tick();
        verify(unknown, times(3)).getFullBlock(anyInt(), anyInt(), anyInt());
    }

    @Test
    void nativeLevelRetainsLegacySectionOverrideWithoutNewEligibilityHook() throws Exception {
        LegacyVirtualSection section = new LegacyVirtualSection();
        assertNativeVirtualGrassCallbacks(section);
        assertEquals(3, section.reads);
    }

    @Test
    void nativeLevelRetainsLegacyStorageOverrideDespiteItsInertBackingPalette() throws Exception {
        LegacyVirtualStorage storage = new LegacyVirtualStorage();
        assertNativeVirtualGrassCallbacks(section(0, storage));
        assertEquals(3, storage.reads);
    }

    @Test
    void nativeLevelRetainsLegacyIndexedStorageOverride() throws Exception {
        LegacyIndexedStorage storage = new LegacyIndexedStorage();
        assertNativeVirtualGrassCallbacks(section(0, storage));
        assertEquals(3, storage.reads);
    }

    private static void assertNativeVirtualGrassCallbacks(ChunkSection section) throws Exception {
        LevelFixture fixture = new LevelFixture(section, 3);
        Block callback = mock(Block.class);
        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(anyInt(), same(fixture.level), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(call -> {
                        assertEquals(full(BlockID.GRASS, 0), (int) call.getArgument(0));
                        return callback;
                    });
            fixture.tick();
        }
        verify(callback, times(3)).onUpdate(Level.BLOCK_UPDATE_RANDOM);
    }

    @Test
    void nativeLevelUsesNegativeSectionAndChunkCoordinatesAndUnlocksBeforeCallbacks() throws Exception {
        LevelDBChunkSection section = section(-4, storage(BlockID.GRASS));
        assertTrue(section.mayHaveRandomTickBlocks());
        CountingLock reads = countReads(section);
        LevelFixture fixture = new LevelFixture(section, 3);
        Block callback = mock(Block.class);
        List<int[]> coordinates = new ArrayList<>();
        when(callback.onUpdate(Level.BLOCK_UPDATE_RANDOM)).thenAnswer(invocation -> {
            assertEquals(0, reads.held, "onUpdate must never execute under the sampler read lock");
            assertTrue(section.writeLock.tryLock(), "the callback can acquire the section writer");
            section.writeLock.unlock();
            return 0;
        });
        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(anyInt(), same(fixture.level), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        assertEquals(full(BlockID.GRASS, 0), (int) invocation.getArgument(0));
                        int x = invocation.getArgument(2), y = invocation.getArgument(3), z = invocation.getArgument(4);
                        coordinates.add(new int[]{x, y, z});
                        return callback;
                    });
            fixture.tick();
        }
        assertEquals(3, coordinates.size());
        for (int[] point : coordinates) {
            assertTrue(point[0] >= -32 && point[0] < -16);
            assertTrue(point[1] >= -64 && point[1] < -48);
            assertTrue(point[2] >= -48 && point[2] < -32);
        }
        verify(callback, times(3)).onUpdate(Level.BLOCK_UPDATE_RANDOM);
        assertEquals(3, reads.acquisitions, "each active block is returned before another sample is read");
        assertEquals(3, reads.releases);
    }

    @Test
    void threeInertSamplesUseOneLockAndConsumeExactlyThreeRandomValues() {
        LevelDBChunkSection section = section(0, storage(BlockID.STONE));
        CountingLock reads = countReads(section);
        SequenceRandom random = new SequenceRandom(sample(1, 2, 3), sample(4, 5, 6), sample(7, 8, 9));
        long result = section.sampleRandomTickBlocks(3, random);
        assertEquals(3, consumed(result));
        assertEquals(0, (int) result);
        assertEquals(3, random.used);
        assertEquals(1, reads.acquisitions);
        assertEquals(1, reads.releases);
        assertEquals(0, reads.held);
    }

    @Test
    void stopsAtFirstActiveBlockWithoutPrefetchingTheNextRandomValue() {
        StateBlockStorage storage = storage(BlockID.STONE);
        storage.set(4, 5, 6, state(BlockID.WHEAT_BLOCK, 7));
        LevelDBChunkSection section = section(0, storage);
        SequenceRandom random = new SequenceRandom(sample(1, 2, 3), sample(4, 5, 6), 0x12345678);
        long result = section.sampleRandomTickBlocks(3, random);
        assertEquals(2, consumed(result));
        assertEquals(full(BlockID.WHEAT_BLOCK, 7), (int) result);
        assertEquals(packed(4, 5, 6), (result >>> 32) & 0xfffL);
        assertEquals(2, random.used);
        assertEquals(0x12345678, random.nextInt(), "callback owns the next RNG draw");
    }

    @Test
    void returnedSamplePreservesAllCoordinateBitsMetadataAndNegativeSectionY() {
        StateBlockStorage storage = storage(BlockID.AIR);
        storage.set(15, 14, 13, state(BlockID.WHEAT_BLOCK, 6));
        LevelDBChunkSection section = section(-4, storage);
        int draw = sample(15, 14, 13) | 0xf00000f0;
        long result = section.sampleRandomTickBlocks(1, new SequenceRandom(draw));
        int position = (int) ((result >>> 32) & 0xfff);
        assertEquals(1, consumed(result));
        assertEquals(15, position & 15);
        assertEquals(13, (position >>> 4) & 15);
        assertEquals(14, (position >>> 8) & 15);
        assertEquals(-50, (section.getY() << 4) + ((position >>> 8) & 15));
        assertEquals(full(BlockID.WHEAT_BLOCK, 6), (int) result);
    }

    @Test
    void callbackCanWriteSectionAndChangeLaterSampleWhileConsumingRandom() {
        StateBlockStorage oldStorage = storage(BlockID.STONE);
        oldStorage.set(1, 2, 3, state(BlockID.GRASS, 0));
        StateBlockStorage newStorage = oldStorage.copy();
        LevelDBChunkSection baseline = section(-4, oldStorage);
        LevelDBChunkSection batched = section(-4, newStorage);
        SequenceRandom oldRandom = scenarioRandom(), newRandom = scenarioRandom();
        List<Long> oldCallbacks = runImmediate(baseline, oldRandom, 3);
        List<Long> newCallbacks = runBatched(batched, newRandom, 3);
        assertEquals(oldCallbacks, newCallbacks);
        assertEquals(2, newCallbacks.size(), "first callback turns the next sampled stone into wheat");
        assertEquals(full(BlockID.WHEAT_BLOCK, 5), (int) (long) newCallbacks.get(1));
        assertEquals(4, oldRandom.used);
        assertEquals(oldRandom.used, newRandom.used);
        assertEquals(oldRandom.nextInt(), newRandom.nextInt(), "RNG remains aligned after callbacks");
    }

    @Test
    void upperLayerWaterDoesNotTurnLayerZeroStoneIntoAnActiveSample() {
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage(BlockID.STONE), storage(BlockID.WATER)}, false);
        SequenceRandom random = new SequenceRandom(0, 1, 2);
        long result = section.sampleRandomTickBlocks(3, random);
        assertEquals(3, consumed(result));
        assertEquals(0, (int) result);
        assertEquals(3, random.used);
    }

    @Test
    void absentLayerZeroStillConsumesEachScheduledRandomDraw() {
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[0], false);
        SequenceRandom random = new SequenceRandom(0, 1, 2);
        long result = section.sampleRandomTickBlocks(3, random);
        assertEquals(3, consumed(result));
        assertEquals(0, (int) result);
        assertEquals(3, random.used);
    }

    @Test
    void hugeTickSpeedIsBoundedToSixteenReadsPerLock() {
        LevelDBChunkSection section = section(0, storage(BlockID.STONE));
        CountingLock reads = countReads(section);
        SequenceRandom random = new SequenceRandom(new int[16]);
        long result = section.sampleRandomTickBlocks(Integer.MAX_VALUE, random);
        assertEquals(16, consumed(result));
        assertEquals(0, (int) result);
        assertEquals(16, random.used);
        assertEquals(1, reads.acquisitions);
        assertEquals(1, reads.releases);
    }

    @Test
    void zeroAndNegativeAttemptsDoNotDrawRandomOrTakeALock() {
        LevelDBChunkSection section = section(0, storage(BlockID.GRASS));
        CountingLock reads = countReads(section);
        SequenceRandom random = new SequenceRandom();
        assertEquals(0, section.sampleRandomTickBlocks(0, random));
        assertEquals(0, section.sampleRandomTickBlocks(-1, random));
        assertEquals(0, random.used);
        assertEquals(0, reads.acquisitions);
        assertEquals(0, reads.releases);
    }

    @Test
    void randomFailureAlwaysReleasesSectionLock() {
        LevelDBChunkSection section = section(0, storage(BlockID.STONE));
        CountingLock reads = countReads(section);
        IllegalStateException failure = new IllegalStateException("scripted RNG failure");
        Random random = new Random() {
            @Override public int nextInt() { throw failure; }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> section.sampleRandomTickBlocks(3, random)));
        assertEquals(1, reads.acquisitions);
        assertEquals(1, reads.releases);
        assertEquals(0, reads.held);
        assertTrue(section.writeLock.tryLock());
        section.writeLock.unlock();
    }

    private static List<Long> runImmediate(LevelDBChunkSection section, Random random, int attempts) {
        List<Long> callbacks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int n = random.nextInt();
            int x = n & 15, z = (n >>> 8) & 15, y = (n >>> 16) & 15;
            int fullBlock = section.getFullBlock(x, y, z);
            if (Level.isRandomTickBlock(fullBlock >> Block.DATA_BITS)) {
                callbacks.add(((long) packed(x, y, z) << 32) | (fullBlock & 0xffffffffL));
                callback(section, random, callbacks.size());
            }
        }
        return callbacks;
    }

    private static List<Long> runBatched(LevelDBChunkSection section, Random random, int attempts) {
        List<Long> callbacks = new ArrayList<>();
        while (attempts > 0) {
            long result = section.sampleRandomTickBlocks(attempts, random);
            int used = consumed(result);
            assertTrue(used > 0 && used <= attempts);
            attempts -= used;
            if ((int) result != BlockID.AIR) {
                callbacks.add(result & 0xfffffffffffL);
                callback(section, random, callbacks.size());
            }
        }
        return callbacks;
    }

    private static void callback(LevelDBChunkSection section, Random random, int callbackNumber) {
        assertTrue(section.writeLock.tryLock(), "callback must run after the sampler unlocks");
        try {
            if (callbackNumber == 1) {
                assertEquals(0x13579bdf, random.nextInt());
                section.getStorages()[0].set(4, 5, 6, state(BlockID.WHEAT_BLOCK, 5));
            }
        } finally {
            section.writeLock.unlock();
        }
    }

    private static SequenceRandom scenarioRandom() {
        return new SequenceRandom(sample(1, 2, 3), 0x13579bdf,
                sample(4, 5, 6), sample(7, 8, 9), 0x2468ace0);
    }

    private static int consumed(long result) { return (int) (result >>> 44); }
    private static int sample(int x, int y, int z) { return x | (z << 8) | (y << 16); }
    private static int packed(int x, int y, int z) { return x | (z << 4) | (y << 8); }
    private static int full(int id, int data) { return (id << Block.DATA_BITS) | data; }

    private static LevelDBChunkSection section(int y, StateBlockStorage storage) {
        return new LevelDBChunkSection(y, new StateBlockStorage[]{storage}, false);
    }

    private static StateBlockStorage storage(int... ids) {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        for (int id : ids) palette.add(state(id, 0));
        return new StateBlockStorage(BitArrayVersion.V2.createPalette(4096), palette, null, null);
    }

    private static BlockStateSnapshot state(int id, int data) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(data).runtimeId(full(id, data))
                .vanillaState(NbtMap.builder().putString("name", "test:block_" + id + "_" + data)
                        .putCompound("states", NbtMap.EMPTY).putInt("version", 1).build()).build();
    }

    private static CountingLock countReads(LevelDBChunkSection section) {
        CountingLock result = new CountingLock(section.readLock);
        section.readLock = result;
        return result;
    }

    private static final class SequenceRandom extends Random {
        final int[] values;
        int used;
        SequenceRandom(int... values) { this.values = values; }
        @Override public int nextInt() {
            assertTrue(used < values.length, "sampler must not prefetch or exceed its bound");
            return values[used++];
        }
    }

    private static final class CountingLock implements Lock {
        final Lock delegate;
        int acquisitions, releases, held;
        CountingLock(Lock delegate) { this.delegate = delegate; }
        @Override public void lock() { delegate.lock(); acquisitions++; held++; }
        @Override public void lockInterruptibly() throws InterruptedException {
            delegate.lockInterruptibly(); acquisitions++; held++;
        }
        @Override public boolean tryLock() {
            boolean acquired = delegate.tryLock();
            if (acquired) { acquisitions++; held++; }
            return acquired;
        }
        @Override public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            boolean acquired = delegate.tryLock(time, unit);
            if (acquired) { acquisitions++; held++; }
            return acquired;
        }
        @Override public void unlock() { delegate.unlock(); releases++; held--; }
        @Override public Condition newCondition() { return delegate.newCondition(); }
    }

    private static final class LegacyVirtualSection extends LevelDBChunkSection {
        int reads;
        LegacyVirtualSection() { super(0, new StateBlockStorage[]{storage(BlockID.STONE)}, false); }
        @Override public int getFullBlock(int x, int y, int z) {
            reads++;
            return full(BlockID.GRASS, 0);
        }
    }

    private static final class LegacyVirtualStorage extends StateBlockStorage {
        int reads;
        @Override public int get(int x, int y, int z) {
            reads++;
            return full(BlockID.GRASS, 0);
        }
    }

    private static final class LegacyIndexedStorage extends StateBlockStorage {
        int reads;
        @Override public int get(int index) {
            reads++;
            return full(BlockID.GRASS, 0);
        }
    }

    private static final class VirtualSection extends LevelDBChunkSection {
        int reads;
        VirtualSection() { super(0, new StateBlockStorage[]{storage(BlockID.STONE)}, false); }
        @Override public boolean mayHaveRandomTickBlocks() { return true; }
        @Override public int getFullBlock(int x, int y, int z) {
            reads++;
            return full(BlockID.STONE, 0);
        }
    }

    private static final class LevelFixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);

        LevelFixture(ChunkSection section, int speed) throws Exception {
            LevelProvider provider = mock(LevelProvider.class);
            when(provider.isChunkLoaded(anyLong())).thenReturn(true);
            LevelDBChunk chunk = mock(LevelDBChunk.class);
            when(chunk.getSections()).thenReturn(new ChunkSection[]{section});
            when(chunk.getEntities()).thenReturn(Collections.emptyMap());
            when(provider.getLoadedChunk(Level.chunkHash(-2, -3))).thenReturn(chunk);
            set("server", mock(Server.class));
            doReturn(null).when(level).getChunk(anyInt(), anyInt(), eq(false));
            doReturn(chunk).when(level).getChunk(-2, -3, false);
            doReturn(true).when(level).randomTickingEnabled();
            Int2ObjectOpenHashMap<ChunkLoader> loaders = new Int2ObjectOpenHashMap<>();
            ChunkLoader loader = mock(ChunkLoader.class);
            when(loader.getX()).thenReturn(-32.0);
            when(loader.getZ()).thenReturn(-48.0);
            loaders.put(1, loader);
            set("provider", provider);
            set("loaders", loaders);
            set("chunksPerTicks", 1);
            set("chunkTickRadius", 1);
            set("chunkTickList", new Long2IntOpenHashMap());
            set("useSections", true);
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.RANDOM_TICK_SPEED, speed);
        }

        void tick() throws Exception {
            Method method = Level.class.getDeclaredMethod("tickChunks");
            method.setAccessible(true);
            method.invoke(level);
        }

        private void set(String name, Object value) throws Exception {
            Field field = Level.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(level, value);
        }
    }
}
