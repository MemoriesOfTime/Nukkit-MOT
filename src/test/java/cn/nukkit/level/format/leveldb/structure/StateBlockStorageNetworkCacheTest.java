package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void saveSettings() { hashed = GlobalBlockPalette.useHashedBlockNetworkIds(); }

    @AfterEach
    void restoreSettings() { GlobalBlockPalette.setUseHashedBlockNetworkIds(hashed); }

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
        assertTrue(bytes < 64L * 1024, "Repeated snapshot encoding allocated " + bytes + " bytes; cell words were rebuilt");
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
}
