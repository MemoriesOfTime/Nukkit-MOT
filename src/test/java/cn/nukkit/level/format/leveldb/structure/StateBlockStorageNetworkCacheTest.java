package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StateBlockStorageNetworkCacheTest {
    private boolean hashed;

    @BeforeAll
    static void init() { MockServer.init(); }

    @AfterAll
    static void cleanupServer() { MockServer.reset(); }

    @BeforeEach
    void saveSettings() {
        hashed = GlobalBlockPalette.useHashedBlockNetworkIds();
        cleanCache();
    }

    @AfterEach
    void restoreSettings() { GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed); }

    @SuppressWarnings("unchecked")
    private static com.github.benmanes.caffeine.cache.Cache<Long, ?> cache() throws Exception {
        Field field = StateBlockStorage.class.getDeclaredField("NETWORK_BYTES");
        field.setAccessible(true);
        return (com.github.benmanes.caffeine.cache.Cache<Long, ?>) field.get(null);
    }

    private static void cleanCache() {
        try {
            var cache = cache();
            cache.invalidateAll();
            cache.cleanUp();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long cacheSize() {
        try {
            var cache = cache();
            cache.cleanUp();
            return cache.estimatedSize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static StateBlockStorage populated() {
        StateBlockStorage storage = new StateBlockStorage();
        int[] ids = {Block.STONE, Block.PLANKS, Block.WOOL, Block.LOG, Block.DIAMOND_ORE};
        for (int i = 0; i < 4096; i++) {
            storage.set(i, BlockStateMapping.get().getState(ids[i % ids.length], 0));
        }
        return storage;
    }

    private static byte[] encoded(StateBlockStorage storage, GameVersion version, boolean antiXray) {
        BinaryStream stream = new BinaryStream();
        storage.writeTo(version, stream, antiXray);
        return stream.getBuffer();
    }

    private static byte[] reference(StateBlockStorage storage, GameVersion version, boolean antiXray) {
        PalettedBlockStorage network = PalettedBlockStorage.createFromBlockPalette(version);
        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(version);
        boolean hash = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(version);
        for (int i = 0; i < 4096; i++) {
            int value = storage.get(i), id = value >> Block.DATA_BITS, meta = value & Block.DATA_MASK;
            if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) { id = Block.STONE; meta = 0; }
            network.setBlock(i, hash ? palette.getHashId(id, meta) : palette.getRuntimeId(id, meta));
        }
        BinaryStream stream = new BinaryStream();
        network.writeTo(stream);
        return stream.getBuffer();
    }

    @Test
    void repeatedAsyncSnapshotsReuseSerializedCells() {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        StateBlockStorage live = populated();
        List<StateBlockStorage> snapshots = new ArrayList<>();
        for (int i = 0; i < 64; i++) snapshots.add(live.copy());
        BinaryStream stream = new BinaryStream();
        GameVersion version = GameVersion.V1_26_50;
        snapshots.get(0).writeTo(version, stream, false);
        for (int i = 0; i < 2000; i++) snapshots.get(0).writeTo(version, stream.reset(), false);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (StateBlockStorage snapshot : snapshots) snapshot.writeTo(version, stream.reset(), false);
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        // Budget covers the per-call key material (network ids, boxed fingerprint, cache internals);
        // a full re-encode would allocate multiple kilobytes per snapshot (words, palette, stream).
        assertTrue(bytes < 128L * 1024, "Repeated snapshot encoding allocated " + bytes + " bytes; cell words were rebuilt");
    }

    @Test
    void cachedBytesMatchReferenceAcrossTheWholeWindow() {
        StateBlockStorage storage = populated();
        for (GameVersion version : GameVersion.getValues()) {
            if (version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (boolean anti : new boolean[]{false, true}) {
                    byte[] expected = reference(storage, version, anti);
                    assertArrayEquals(expected, encoded(storage, version, anti), version + " cold");
                    assertArrayEquals(expected, encoded(storage.copy(), version, anti), version + " snapshot hit");
                }
            }
        }
    }

    @Test
    void directMutationOfExistingPaletteEntryLeavesOldSnapshotsIntact() {
        StateBlockStorage live = populated();
        StateBlockStorage oldSnapshot = live.copy();
        byte[] before = encoded(oldSnapshot, GameVersion.V1_26_50, false);
        encoded(live, GameVersion.V1_26_50, false);
        live.set(0, BlockStateMapping.get().getState(Block.WOOL, 0));
        byte[] after = encoded(live.copy(), GameVersion.V1_26_50, false);
        assertFalse(java.util.Arrays.equals(before, after));
        assertArrayEquals(reference(live, GameVersion.V1_26_50, false), after);
        assertArrayEquals(before, encoded(oldSnapshot, GameVersion.V1_26_50, false));
    }

    @Test
    void snapshotMutationDetachesOnlyThatSnapshot() {
        StateBlockStorage live = populated(), changed = live.copy(), other = live.copy();
        byte[] before = encoded(changed, GameVersion.V1_26_50, false);
        changed.set(0, BlockStateMapping.get().getState(Block.WOOL, 0));
        assertArrayEquals(before, encoded(live, GameVersion.V1_26_50, false));
        assertArrayEquals(before, encoded(other, GameVersion.V1_26_50, false));
        assertArrayEquals(reference(changed, GameVersion.V1_26_50, false), encoded(changed, GameVersion.V1_26_50, false));
    }

    @Test
    void compressionAndDiskReloadCannotReuseOldContent() {
        StateBlockStorage storage = populated();
        encoded(storage, GameVersion.V1_26_50, false);
        assertTrue(storage.compress());
        assertArrayEquals(reference(storage, GameVersion.V1_26_50, false), encoded(storage, GameVersion.V1_26_50, false));
        StateBlockStorage replacement = new StateBlockStorage();
        replacement.set(0, BlockStateMapping.get().getState(Block.WOOL, 0));
        var buffer = Unpooled.buffer();
        try {
            replacement.writeToStorage(buffer);
            storage.readFromStorage(buffer, new ChunkBuilder(0, 0,
                    org.mockito.Mockito.mock(cn.nukkit.level.format.leveldb.LevelDBProvider.class)));
            assertArrayEquals(reference(replacement, GameVersion.V1_26_50, false), encoded(storage, GameVersion.V1_26_50, false));
        } finally { buffer.release(); }
    }

    @Test
    void antiXrayMaskChangesAreNotHiddenByCache() {
        StateBlockStorage storage = populated();
        boolean old = Level.xrayableBlocks[Block.DIAMOND_ORE];
        try {
            Level.xrayableBlocks[Block.DIAMOND_ORE] = true;
            byte[] concealed = encoded(storage, GameVersion.V1_26_50, true);
            Level.xrayableBlocks[Block.DIAMOND_ORE] = false;
            byte[] visible = encoded(storage, GameVersion.V1_26_50, true);
            assertFalse(java.util.Arrays.equals(concealed, visible));
            assertArrayEquals(reference(storage, GameVersion.V1_26_50, true), visible);
        } finally { Level.xrayableBlocks[Block.DIAMOND_ORE] = old; }
    }

    @Test
    @SuppressWarnings("unchecked")
    void paletteRebuildOnSameObjectDoesNotReuseOldIds() throws Exception {
        GameVersion version = GameVersion.V1_20_0;
        BlockPalette original = GlobalBlockPalette.getPaletteByProtocol(version);
        Field field = GlobalBlockPalette.class.getDeclaredField("paletteCache");
        field.setAccessible(true);
        Map<GameVersion, BlockPalette> palettes = (Map<GameVersion, BlockPalette>) field.get(null);
        BlockPalette replacement = new BlockPalette(original.getGameVersion());
        Map<Integer, Integer> oldIds = new java.util.HashMap<>();
        replacement.getLegacyToRuntimeIdMap().forEach((id, runtime) -> oldIds.put(id, runtime));
        palettes.put(original.getGameVersion(), replacement);
        GlobalBlockPalette.setUseHashedBlockNetworkIds(false);
        try {
            StateBlockStorage storage = populated();
            byte[] before = encoded(storage, version, false);
            replacement.clearStates();
            oldIds.forEach((full, runtime) -> replacement.registerState(full >> Block.DATA_BITS, full & Block.DATA_MASK,
                    runtime + 50000, new cn.nukkit.nbt.tag.CompoundTag().putString("name", "minecraft:test").putInt("version", 1)));
            replacement.lock();
            byte[] after = encoded(storage, version, false);
            assertFalse(java.util.Arrays.equals(before, after));
            assertArrayEquals(reference(storage, version, false), after);
        } finally { palettes.put(original.getGameVersion(), original); }
    }

    @Test
    void simultaneousColdSnapshotsProduceIdenticalOwnedBytes() throws Exception {
        StateBlockStorage live = populated();
        byte[] expected = reference(live, GameVersion.V1_26_50, false);
        List<Callable<byte[]>> tasks = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            StateBlockStorage snapshot = live.copy();
            tasks.add(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return encoded(snapshot, GameVersion.V1_26_50, false); });
        }
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<byte[]>>();
            for (Callable<byte[]> task : tasks) futures.add(pool.submit(task));
            start.countDown();
            for (var future : futures) assertArrayEquals(expected, future.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
        byte[] returned = encoded(live, GameVersion.V1_26_50, false);
        java.util.Arrays.fill(returned, (byte) 0);
        assertArrayEquals(expected, encoded(live, GameVersion.V1_26_50, false));
    }
    @Test
    void unusedNullPaletteEntryRetainsTheOriginalLazyRead() {
        StateBlockStorage storage = new StateBlockStorage();
        storage.set(0, (BlockStateSnapshot) null);
        storage.set(0, BlockStateMapping.get().getState(0, 0));
        assertArrayEquals(reference(storage, GameVersion.V1_26_50, true), encoded(storage, GameVersion.V1_26_50, true));
    }

    @Test
    void callerOwnedConstructorArraysCannotSilentlyChangeCachedBytes() {
        var bits = cn.nukkit.level.util.BitArrayVersion.V2.createPalette(4096);
        var palette = new ArrayList<>(List.of(BlockStateMapping.get().getState(0, 0),
                BlockStateMapping.get().getState(Block.STONE, 0)));
        StateBlockStorage storage = new StateBlockStorage(bits, palette, null, null);
        encoded(storage, GameVersion.V1_26_50, false);
        bits.set(0, 1);
        assertArrayEquals(reference(storage, GameVersion.V1_26_50, false), encoded(storage, GameVersion.V1_26_50, false));
        StateBlockStorage snapshot = storage.copy();
        byte[] expected = encoded(snapshot, GameVersion.V1_26_50, false);
        bits.set(0, 0);
        assertArrayEquals(expected, encoded(snapshot, GameVersion.V1_26_50, false));
    }

    @Test
    void rawMutationCannotAttachOldSnapshotBytesToFreshCopies() throws Exception {
        StateBlockStorage live = populated();
        BlockStateSnapshot target = BlockStateMapping.get().getState(Block.WOOL, 0);
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        BlockStateSnapshot paused = new BlockStateSnapshot(target.getVanillaState(), target.getRuntimeId(),
                target.getVersion(), target.isCustom(), target.getLegacyId(), target.getLegacyData()) {
            @Override public boolean equals(Object other) {
                entered.countDown();
                try { assertTrue(resume.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return other == target;
            }
            @Override public int hashCode() { return target.hashCode(); }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var write = pool.submit(() -> live.set(0, paused));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var copying = pool.submit(live::copy);
            try { copying.get(100, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException expectedWhileWriting) { }
            finally { resume.countDown(); }
            write.get(10, TimeUnit.SECONDS);
            encoded(copying.get(10, TimeUnit.SECONDS), GameVersion.V1_26_50, false);
            StateBlockStorage fresh = live.copy();
            assertArrayEquals(reference(fresh, GameVersion.V1_26_50, false), encoded(fresh, GameVersion.V1_26_50, false));
        } finally { resume.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test
    void negativeSectionHeaderAndSecondLayerStayFreshAfterReplacement() {
        StateBlockStorage first = populated(), second = new StateBlockStorage();
        second.set(0, BlockStateMapping.get().getState(Block.WATER, 0));
        LevelDBChunkSection section = new LevelDBChunkSection(-4, new StateBlockStorage[]{first, second}, false);
        for (GameVersion version : new GameVersion[]{GameVersion.V1_20_0, GameVersion.V1_26_50}) {
            BinaryStream expected = new BinaryStream(), actual = new BinaryStream();
            expected.putByte((byte) 9); expected.putByte((byte) 2); expected.putByte((byte) -4);
            expected.put(reference(first, version, false)); expected.put(reference(second, version, false));
            section.writeTo(version, actual, false);
            assertArrayEquals(expected.getBuffer(), actual.getBuffer());
            actual.reset(); section.copy().writeTo(version, actual, false);
            assertArrayEquals(expected.getBuffer(), actual.getBuffer());
        }
        section.getStorages()[1] = null;
        BinaryStream expected = new BinaryStream(), actual = new BinaryStream();
        expected.putByte((byte) 9); expected.putByte((byte) 1); expected.putByte((byte) -4);
        expected.put(reference(first, GameVersion.V1_26_50, false));
        section.writeTo(GameVersion.V1_26_50, actual, false);
        assertArrayEquals(expected.getBuffer(), actual.getBuffer());
    }

    @Test
    void changingGlobalFlagsCannotPoisonLaterStableRequests() throws Exception {
        StateBlockStorage storage = populated();
        boolean originalMask = Level.xrayableBlocks[Block.DIAMOND_ORE];
        var done = new java.util.concurrent.atomic.AtomicBoolean();
        var pool = Executors.newSingleThreadExecutor();
        try {
            var changing = pool.submit(() -> {
                for (int i = 0; i < 1000000 && !done.get(); i++) {
                    GlobalBlockPalette.setUseHashedBlockNetworkIds((i & 1) != 0);
                    Level.xrayableBlocks[Block.DIAMOND_ORE] = (i & 1) == 0;
                }
            });
            for (int i = 0; i < 1000; i++) encoded(storage, GameVersion.V1_26_50, true);
            done.set(true); changing.get(10, TimeUnit.SECONDS);
            for (boolean hash : new boolean[]{false, true}) {
                GlobalBlockPalette.setUseHashedBlockNetworkIds(hash);
                for (boolean conceal : new boolean[]{false, true}) {
                    Level.xrayableBlocks[Block.DIAMOND_ORE] = conceal;
                    byte[] expected = reference(storage, GameVersion.V1_26_50, true);
                    assertArrayEquals(expected, encoded(storage.copy(), GameVersion.V1_26_50, true));
                    assertArrayEquals(expected, encoded(storage, GameVersion.V1_26_50, true));
                }
            }
        } finally {
            done.set(true); pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            Level.xrayableBlocks[Block.DIAMOND_ORE] = originalMask;
        }
    }
    @Test
    void malformedMetadataKeepsTheOriginalPackedIdObfuscation() {
        StateBlockStorage storage = new StateBlockStorage();
        storage.set(0, BlockStateSnapshot.builder().legacyId(0)
                .legacyData(Block.DIAMOND_ORE << Block.DATA_BITS).build());
        assertArrayEquals(reference(storage, GameVersion.V1_26_50, true), encoded(storage, GameVersion.V1_26_50, true));
    }

    @Test
    void identicalContentInIndependentStoragesSharesOneEntry() {
        StateBlockStorage first = populated(), second = populated();
        byte[] bytes = encoded(first, GameVersion.V1_26_50, false);
        assertArrayEquals(bytes, encoded(second, GameVersion.V1_26_50, false));
        assertEquals(1, cacheSize(), "independent storages with identical content should share one cache entry");
    }

    @Test
    void returningToPreviousContentHitsTheOriginalEntry() {
        StateBlockStorage storage = populated();
        byte[] original = encoded(storage, GameVersion.V1_26_50, false);
        assertEquals(1, cacheSize());
        storage.set(0, BlockStateMapping.get().getState(Block.WOOL, 0));
        assertFalse(java.util.Arrays.equals(original, encoded(storage, GameVersion.V1_26_50, false)));
        assertEquals(2, cacheSize());
        storage.set(0, BlockStateMapping.get().getState(Block.STONE, 0));
        assertEquals(2, cacheSize(), "re-encoding the original content must hit the original entry, not add one");
        assertArrayEquals(original, encoded(storage, GameVersion.V1_26_50, false));
    }

    /**
     * V0 的索引存放在 SingletonBitArray.value,getWords() 恒为空,指纹与命中校验都必须覆盖该值。
     * <p>
     * A V0 storage keeps its index in SingletonBitArray.value while getWords() stays empty;
     * both the fingerprint and the hit check must cover it.
     */
    private static StateBlockStorage singleton(BitArrayVersion version, int index) {
        BitArray bits = version.createPalette(4096);
        bits.set(0, index);
        StateBlockStorage storage = new StateBlockStorage(bits, new ArrayList<>(List.of(
                BlockStateMapping.get().getState(BlockID.AIR, 0),
                BlockStateMapping.get().getState(Block.STONE, 0))), null, null);
        // copy() turns the caller-owned passthrough into a regular cacheable section.
        return storage.copy();
    }

    @Test
    void singletonValueIsPartOfTheCacheKeyAndTheHitCheck() {
        BlockStateSnapshot air = BlockStateMapping.get().getState(BlockID.AIR, 0);
        BlockStateSnapshot stone = BlockStateMapping.get().getState(BlockID.STONE, 0);
        int airId = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50).getRuntimeId(BlockID.AIR, 0);
        int stoneId = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_50).getRuntimeId(BlockID.STONE, 0);
        StateBlockStorage storage = singleton(BitArrayVersion.V0, 0);

        byte[] airBytes = encoded(storage, GameVersion.V1_26_50, false);
        assertEquals(1, cacheSize());

        // set() is a no-op for the existing air palette entry, so the cell only changes through the singleton value.
        storage.set(0, air);
        storage.set(0, stone);
        byte[] stoneBytes = encoded(storage, GameVersion.V1_26_50, false);

        assertFalse(java.util.Arrays.equals(airBytes, stoneBytes), "V0 index change must not reuse the air encoding");
        assertArrayEquals(reference(storage, GameVersion.V1_26_50, false), stoneBytes);
        assertEquals(2, cacheSize());
        // The live singleton storage must have moved on to the stone entry, not stayed on air.
        assertArrayEquals(stoneBytes, encoded(storage, GameVersion.V1_26_50, false));
    }

    /** V2 section whose palette is [air, stone] and whose named cells are stone. */
    private static StateBlockStorage twoEntryLayout(int... stoneIndices) {
        BitArray bits = BitArrayVersion.V2.createPalette(4096);
        StateBlockStorage storage = new StateBlockStorage(bits, new ArrayList<>(List.of(
                BlockStateMapping.get().getState(BlockID.AIR, 0),
                BlockStateMapping.get().getState(Block.STONE, 0))), null, null);
        for (int index : stoneIndices) {
            storage.set(index, BlockStateMapping.get().getState(Block.STONE, 0));
        }
        return storage.copy();
    }

    /**
     * V2 每 word 16 格,索引 0 与 1024 恰差 64 个 word——旧仿射折叠的碰撞周期。
     * <p>
     * A V2 word holds 16 cells, so indices 0 and 1024 sit exactly 64 words apart,
     * the collision period of the old affine fold.
     */
    @Test
    void layoutsSixtyFourWordsApartDoNotCollide() {
        StateBlockStorage first = twoEntryLayout(0);
        StateBlockStorage second = twoEntryLayout(1024);
        byte[] firstBytes = encoded(first, GameVersion.V1_26_50, false);
        byte[] secondBytes = encoded(second, GameVersion.V1_26_50, false);
        assertFalse(java.util.Arrays.equals(firstBytes, secondBytes), "the two layouts must encode differently");
        assertArrayEquals(reference(second, GameVersion.V1_26_50, false), secondBytes);
        assertEquals(2, cacheSize(), "layouts 64 words apart must not share one cache entry");

        // Alternating between them must hit both entries instead of re-encoding and evicting.
        assertArrayEquals(firstBytes, encoded(first, GameVersion.V1_26_50, false));
        assertArrayEquals(secondBytes, encoded(second, GameVersion.V1_26_50, false));
        assertArrayEquals(firstBytes, encoded(first, GameVersion.V1_26_50, false));
        assertEquals(2, cacheSize(), "alternating layouts must stop re-encoding once both entries are cached");
    }

    /**
     * 66 项调色板仅交换第 1 与第 65 项:网络 ID 序列恰在相差 64 的两个位置不同。
     * <p>
     * A 66-entry palette with only entries 1 and 65 swapped: the network-id sequence
     * differs at two positions exactly 64 apart.
     */
    @Test
    void longPalettesDoNotCollideSixtyFourIdsApart() {
        List<BlockStateSnapshot> states = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        states.add(BlockStateMapping.get().getState(BlockID.AIR, 0));
        seen.add(BlockID.AIR);
        for (int id = 1; id < 512 && states.size() < 66; id++) {
            int runtimeId = BlockStateMapping.get().getState(id, 0).getRuntimeId();
            if (seen.add(runtimeId)) {
                states.add(BlockStateMapping.get().getState(runtimeId));
            }
        }
        assertEquals(66, states.size(), "the fixture needs 66 distinct states");

        List<BlockStateSnapshot> swapped = new ArrayList<>(states);
        java.util.Collections.swap(swapped, 1, 65);
        // The same cells in both: only the palette order, hence the id sequence, moves.
        BitArray bits = BitArrayVersion.V8.createPalette(4096);
        bits.set(0, 1);
        bits.set(1, 65);
        StateBlockStorage plain = new StateBlockStorage(bits.copy(), new ArrayList<>(states), null, null).copy();
        StateBlockStorage swappedStorage = new StateBlockStorage(bits.copy(), new ArrayList<>(swapped), null, null).copy();

        byte[] plainBytes = encoded(plain, GameVersion.V1_26_50, false);
        byte[] swappedBytes = encoded(swappedStorage, GameVersion.V1_26_50, false);
        assertFalse(java.util.Arrays.equals(plainBytes, swappedBytes), "swapped palette entries must encode differently");
        assertArrayEquals(reference(plain, GameVersion.V1_26_50, false), plainBytes);
        assertArrayEquals(reference(swappedStorage, GameVersion.V1_26_50, false), swappedBytes);
        assertEquals(2, cacheSize(), "palette ids 64 positions apart must not collapse to one entry");
    }

    @Test
    void independentSingletonsDoNotShareAnEntry() {
        StateBlockStorage air = singleton(BitArrayVersion.V0, 0);
        StateBlockStorage stone = singleton(BitArrayVersion.V0, 1);
        byte[] airBytes = encoded(air, GameVersion.V1_26_50, false);
        byte[] stoneBytes = encoded(stone, GameVersion.V1_26_50, false);
        assertFalse(java.util.Arrays.equals(airBytes, stoneBytes));
        assertArrayEquals(reference(stone, GameVersion.V1_26_50, false), stoneBytes);
        assertEquals(2, cacheSize(), "a V0 air section and a V0 stone section must not share one entry");
    }
}
