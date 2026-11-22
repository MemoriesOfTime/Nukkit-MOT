package cn.nukkit.level.util;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Run unchanged against baseline and optimized production classpaths in separate JVMs. */
public final class PaddedBitArrayIndexBenchmark {
    private static volatile long checksum;

    public static void main(String[] args) {
        BitArray[] arrays = {BitArrayVersion.V3.createPalette(), BitArrayVersion.V5.createPalette(),
                BitArrayVersion.V6.createPalette()};
        for (BitArray array : arrays) {
            for (int index = 0; index < 4096; index++) {
                array.set(index, (index * 17 + 3) & array.getVersion().getMaxEntryValue());
            }
        }
        for (int round = 0; round < 32; round++) {
            exercise(arrays, 64, false);
            exercise(arrays, 64, true);
        }
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (boolean writes : new boolean[]{false, true}) {
            long[] nanos = new long[11];
            long bytes = 0;
            int rounds = 512;
            for (int sample = 0; sample < nanos.length; sample++) {
                long before = bean.getCurrentThreadAllocatedBytes();
                long start = System.nanoTime();
                exercise(arrays, rounds, writes);
                nanos[sample] = System.nanoTime() - start;
                bytes += bean.getCurrentThreadAllocatedBytes() - before;
            }
            Arrays.sort(nanos);
            double cells = (double) rounds * arrays.length * 4096;
            System.out.printf("%s: median %.3f ns/cell, allocation %d B, checksum %d%n",
                    writes ? "set+get" : "get", nanos[nanos.length / 2] / cells, bytes, checksum);
        }
    }

    private static void exercise(BitArray[] arrays, int rounds, boolean writes) {
        long value = 0;
        for (int round = 0; round < rounds; round++) {
            for (BitArray array : arrays) {
                int mask = array.getVersion().getMaxEntryValue();
                for (int i = 0; i < 4096; i++) {
                    int index = (i * 73) & 4095;
                    if (writes) array.set(index, (i + round) & mask);
                    value += array.get(index);
                }
            }
        }
        checksum = value;
    }
}
