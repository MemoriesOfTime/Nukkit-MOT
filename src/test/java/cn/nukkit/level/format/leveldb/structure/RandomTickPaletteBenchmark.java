package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/** Measures unchanged inert palettes against the real storage and section random-tick entry points. */
public final class RandomTickPaletteBenchmark {
    private static final int FIXTURES = 128;
    private static final int PROBES = 1 << 20;
    private static final int[] INERT_IDS = {
            BlockID.STONE, BlockID.DEEPSLATE, BlockID.DIRT, BlockID.GRAVEL,
            BlockID.COAL_ORE, BlockID.IRON_ORE, BlockID.DIAMOND_ORE, BlockID.GOLD_ORE
    };
    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile long checksum;

    private RandomTickPaletteBenchmark() {}

    public static void main(String[] args) {
        THREADS.setThreadAllocatedMemoryEnabled(true);
        for (int paletteSize : new int[]{1, 16, 17, 64, 256, 257}) {
            Fixture fixture = new Fixture(paletteSize);
            measure(paletteSize, "storage-probe", () -> probeStorage(fixture.storages));
            measure(paletteSize, "section-gate-plus-3-samples", () -> sampleSections(fixture.sections));
        }
    }

    private static void measure(int paletteSize, String scenario, Runnable probe) {
        for (int i = 0; i < 6; i++) probe.run();
        long[] nanos = new long[9];
        long[] bytes = new long[nanos.length];
        long thread = Thread.currentThread().getId();
        for (int i = 0; i < nanos.length; i++) {
            long allocated = THREADS.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            probe.run();
            nanos[i] = System.nanoTime() - start;
            bytes[i] = THREADS.getThreadAllocatedBytes(thread) - allocated;
        }
        Arrays.sort(nanos);
        Arrays.sort(bytes);
        System.out.printf(java.util.Locale.ROOT, "palette=%d %s: median %.3f ns/op, allocation %.6f B/op, sample-attempts=%d%n",
                paletteSize, scenario, (double) nanos[nanos.length / 2] / PROBES,
                (double) bytes[bytes.length / 2] / PROBES, checksum);
    }

    private static void probeStorage(StateBlockStorage[] storages) {
        long probes = 0;
        for (int i = 0; i < PROBES; i++) {
            if (storages[i & (FIXTURES - 1)].mayHaveRandomTickBlocks()) probes++;
        }
        checksum = probes;
    }

    private static void sampleSections(LevelDBChunkSection[] sections) {
        long attempts = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < PROBES; i++) {
            LevelDBChunkSection section = sections[i & (FIXTURES - 1)];
            if (section.mayHaveRandomTickBlocks()) {
                attempts += section.sampleRandomTickBlocks(3, random) >>> 44;
            }
        }
        checksum = attempts;
    }

    private static final class Fixture {
        final StateBlockStorage[] storages = new StateBlockStorage[FIXTURES];
        final LevelDBChunkSection[] sections = new LevelDBChunkSection[FIXTURES];

        Fixture(int paletteSize) {
            var palette = new ArrayList<BlockStateSnapshot>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                int blockId = INERT_IDS[i % INERT_IDS.length];
                palette.add(BlockStateSnapshot.builder().legacyId(blockId).legacyData(i / INERT_IDS.length).build());
            }
            BitArrayVersion version = BitArrayVersion.V0;
            while (version.getMaxEntryValue() < paletteSize - 1) version = version.next();
            BitArray cells = version.createPalette();
            for (int i = 0; i < cells.size(); i++) cells.set(i, i % paletteSize);
            for (int i = 0; i < FIXTURES; i++) {
                StateBlockStorage storage = new StateBlockStorage(cells, palette, null, null).copy();
                storages[i] = storage;
                sections[i] = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
            }
        }
    }
}
