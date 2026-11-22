package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.BlockStateMapping;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockStorageScratchPackingTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @AfterAll
    static void cleanup() {
        MockServer.reset();
    }

    @Test
    void singletonAndAllBitWidthsMatchReferenceWireBytes() {
        List<BlockStateSnapshot> states = collectDistinctStates(270);
        GameVersion[] versions = {
                GameVersion.V1_16_100,
                GameVersion.V1_19_80,
                GameVersion.V1_20_0,
                GameVersion.V1_26_50,
                GameVersion.V1_26_60
        };

        // V0 air and V0 non-air singletons
        StateBlockStorage airSingleton = new StateBlockStorage(BitArrayVersion.V0);
        BitArray stoneV0 = BitArrayVersion.V0.createPalette(4096);
        stoneV0.set(0, 1);
        StateBlockStorage stoneSingleton = new StateBlockStorage(stoneV0,
                new ObjectArrayList<>(List.of(states.get(0), states.get(1))), null, null);

        for (GameVersion gv : versions) {
            assertArrayEquals(referenceBytes(airSingleton, gv, false), encodeBytes(airSingleton, gv, false));
            assertArrayEquals(referenceBytes(stoneSingleton, gv, false), encodeBytes(stoneSingleton, gv, false));
            assertArrayEquals(referenceBytes(stoneSingleton.copy(), gv, false), encodeBytes(stoneSingleton.copy(), gv, false));
        }

        // Every source BitArrayVersion including padded V3, V5, V6 and small palettes (1..4)
        BitArrayVersion[] sourceVersions = {
                BitArrayVersion.V1, BitArrayVersion.V2, BitArrayVersion.V3, BitArrayVersion.V4,
                BitArrayVersion.V5, BitArrayVersion.V6, BitArrayVersion.V8, BitArrayVersion.V16
        };
        int[] counts = {2, 3, 4, 7, 13, 25, 45, 90, 260};
        for (int idx = 0; idx < sourceVersions.length; idx++) {
            BitArrayVersion srcVer = sourceVersions[idx];
            int count = Math.min(counts[idx], srcVer.getMaxEntryValue() + 1);
            List<BlockStateSnapshot> palette = new ObjectArrayList<>(states.subList(0, count));
            BitArray bits = srcVer.createPalette(4096);
            for (int cell = 0; cell < 4096; cell++) {
                bits.set(cell, (cell * 17 + (cell >>> 4)) % count);
            }
            StateBlockStorage uncached = new StateBlockStorage(bits, palette, null, null);
            StateBlockStorage cached = uncached.copy();
            for (GameVersion gv : versions) {
                byte[] expected = referenceBytes(uncached, gv, true);
                assertArrayEquals(expected, encodeBytes(uncached, gv, true), srcVer + " uncached " + gv);
                assertArrayEquals(expected, encodeBytes(cached, gv, true), srcVer + " cached " + gv);
            }
        }
    }

    @Test
    void corruptCellRewindsStreamPrefixAndThrowsOriginalException() {
        List<BlockStateSnapshot> states = collectDistinctStates(6);
        BitArray cells = BitArrayVersion.V4.createPalette(4096);
        for (int i = 0; i < 6; i++) {
            cells.set(i, i);
        }
        cells.set(3999, 11);
        StateBlockStorage storage = new StateBlockStorage(cells, new ObjectArrayList<>(states), null, null);

        BinaryStream stream = new BinaryStream(4096);
        stream.putByte((byte) 0x7A);
        stream.putLInt(0x12345678);
        int prefixCount = stream.getCount();

        assertThrows(IndexOutOfBoundsException.class,
                () -> storage.writeTo(GameVersion.V1_26_60, stream, false));
        assertEquals(prefixCount, stream.getCount(), "Failed write must rewind partial header/words");
    }

    @Tag("perf")
    @Test
    void uncachedAndCachedWritesAvoidTransientPaletteAndWordAllocations() throws Exception {
        if (isBinaryStreamInstrumentedByAgent()) {
            runCleanChildProbe();
            return;
        }
        verifyPaletteAndWordAllocations();
    }

    public static void main(String[] args) {
        MockServer.init();
        try {
            verifyPaletteAndWordAllocations();
        } finally {
            MockServer.reset();
        }
    }

    private static boolean isBinaryStreamInstrumentedByAgent() {
        BinaryStream probe = new BinaryStream(64);
        for (int i = 0; i < 64; i++) {
            probe.reset();
            probe.putByte((byte) i);
        }
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 64; i++) {
            probe.reset();
            probe.putByte((byte) i);
        }
        return mx.getThreadAllocatedBytes(tid) - before > 256L;
    }

    private static void runCleanChildProbe() throws Exception {
        String javaBin = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process proc = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                StateBlockStorageScratchPackingTest.class.getName())
                .redirectErrorStream(true)
                .start();
        String output;
        try (var in = proc.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "Child allocation probe timed out");
        assertEquals(0, proc.exitValue(), () -> "Child allocation probe failed:\n" + output);
    }

    private static void verifyPaletteAndWordAllocations() {
        List<BlockStateSnapshot> states = collectDistinctStates(24);
        BitArray cells = BitArrayVersion.V5.createPalette(4096);
        for (int i = 0; i < 4096; i++) {
            cells.set(i, (i * 7 + 3) % states.size());
        }
        // Protected constructor sets networkCacheable=false, forcing full writeSizedNetworkPalette on every call.
        StateBlockStorage uncached = new StateBlockStorage(cells, new ObjectArrayList<>(states), null, null);
        StateBlockStorage cached = uncached.copy();

        BinaryStream out = new BinaryStream(8192);
        for (int i = 0; i < 500; i++) {
            out.reset();
            uncached.writeTo(GameVersion.V1_26_60, out, false);
            out.reset();
            cached.writeTo(GameVersion.V1_26_60, out, false);
        }

        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();

        long beforeUncached = mx.getThreadAllocatedBytes(tid);
        int rounds = 200;
        for (int i = 0; i < rounds; i++) {
            out.reset();
            uncached.writeTo(GameVersion.V1_26_60, out, false);
        }
        long uncachedBytesPerCall = (mx.getThreadAllocatedBytes(tid) - beforeUncached) / rounds;
        // Previously allocated BitArray + int[683] (~2.7 KB) + IntArrayList + Int2IntOpenHashMap + int[24] (>4 KB/call).
        assertTrue(uncachedBytesPerCall < 128L,
                "Uncached palette packing should allocate 0 transient arrays, got " + uncachedBytesPerCall + " B/call");

        long beforeCached = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < rounds; i++) {
            out.reset();
            cached.writeTo(GameVersion.V1_26_60, out, false);
        }
        long cachedBytesPerCall = (mx.getThreadAllocatedBytes(tid) - beforeCached) / rounds;
        // Previously allocated int[palette.size()] + palette iterator + capturing Function on every cache hit.
        // Remaining bytes are only boxed Long key + Caffeine access-queue maintenance.
        assertTrue(cachedBytesPerCall < 192L,
                "Cached section write should avoid per-hit array/lambda allocations, got " + cachedBytesPerCall + " B/call");
    }

    private static byte[] encodeBytes(StateBlockStorage storage, GameVersion version, boolean antiXray) {
        BinaryStream stream = new BinaryStream();
        storage.writeTo(version, stream, antiXray);
        return stream.getBuffer();
    }

    private static byte[] referenceBytes(StateBlockStorage storage, GameVersion version, boolean antiXray) {
        PalettedBlockStorage out = PalettedBlockStorage.createFromBlockPalette(version);
        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(version);
        boolean hash = GlobalBlockPalette.shouldUseHashedBlockNetworkIds(version);
        for (int i = 0; i < 4096; i++) {
            int fullId = storage.get(i);
            int id = fullId >> Block.DATA_BITS;
            int meta = fullId & Block.DATA_MASK;
            if (antiXray && id < Block.MAX_BLOCK_ID && Level.xrayableBlocks[id]) {
                id = Block.STONE;
                meta = 0;
            }
            out.setBlock(i, hash ? palette.getHashId(id, meta) : palette.getRuntimeId(id, meta));
        }
        BinaryStream stream = new BinaryStream();
        out.writeTo(stream);
        return stream.getBuffer();
    }

    private static List<BlockStateSnapshot> collectDistinctStates(int count) {
        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.V1_26_60);
        HashSet<Integer> seen = new HashSet<>();
        List<BlockStateSnapshot> result = new ArrayList<>(count);
        for (int id = 0; id < Block.list.length && result.size() < count; id++) {
            if (id != 0 && Block.list[id] == null) {
                continue;
            }
            for (int meta = 0; meta < 16 && result.size() < count; meta++) {
                BlockStateSnapshot s = BlockStateMapping.get().getState(id, meta);
                if (s != null && seen.add(palette.getRuntimeId(s.getLegacyId(), s.getLegacyData()))) {
                    result.add(s);
                }
            }
        }
        assertEquals(count, result.size());
        return result;
    }
}
