package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.nbt.NbtMap;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/** Compare unchanged locked storage/section reads with baseline and optimized bit arrays. */
public final class StateBlockStorageReadBenchmark {
    private static volatile long checksum;

    public static void main(String[] args) {
        BitArrayVersion[] versions = {BitArrayVersion.V3, BitArrayVersion.V5, BitArrayVersion.V6};
        StateBlockStorage[] storages = new StateBlockStorage[versions.length];
        LevelDBChunkSection[] sections = new LevelDBChunkSection[versions.length];
        for (int i = 0; i < versions.length; i++) {
            BitArray cells = versions[i].createPalette();
            List<BlockStateSnapshot> palette = new ObjectArrayList<>();
            for (int value = 0; value <= versions[i].getMaxEntryValue(); value++) {
                palette.add(BlockStateSnapshot.builder().legacyId(value).legacyData(0).runtimeId(value)
                        .vanillaState(NbtMap.EMPTY).build());
            }
            for (int index = 0; index < 4096; index++) {
                cells.set(index, (index * 17 + 3) & versions[i].getMaxEntryValue());
            }
            storages[i] = new StateBlockStorage(cells, palette, null, null);
            sections[i] = new LevelDBChunkSection(0, new StateBlockStorage[]{storages[i]}, false);
        }
        for (int round = 0; round < 32; round++) {
            exercise(storages, sections, 64, false);
            exercise(storages, sections, 64, true);
        }
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (boolean section : new boolean[]{false, true}) {
            long[] nanos = new long[11];
            long bytes = 0;
            int rounds = 512;
            for (int sample = 0; sample < nanos.length; sample++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                long time = System.nanoTime();
                exercise(storages, sections, rounds, section);
                nanos[sample] = System.nanoTime() - time;
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
            }
            Arrays.sort(nanos);
            System.out.printf("%s: median %.3f ns/cell, allocation %d B, checksum %d%n",
                    section ? "section.getBlockId" : "storage.getBlockState", nanos[5] / (512d * 3 * 4096),
                    bytes, checksum);
        }
    }

    private static void exercise(StateBlockStorage[] storages, LevelDBChunkSection[] sections,
                                 int rounds, boolean section) {
        long value = 0;
        for (int round = 0; round < rounds; round++) {
            for (int layer = 0; layer < storages.length; layer++) {
                for (int i = 0; i < 4096; i++) {
                    int index = (i * 73) & 4095;
                    value += section ? sections[layer].getBlockId(index >>> 8, index & 15, (index >>> 4) & 15)
                            : storages[layer].getBlockState(index).getLegacyId();
                }
            }
        }
        checksum = value;
    }
}
