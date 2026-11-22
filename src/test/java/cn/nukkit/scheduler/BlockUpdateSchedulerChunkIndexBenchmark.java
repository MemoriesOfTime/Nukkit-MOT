package cn.nukkit.scheduler;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockUpdateEntry;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Run unchanged against baseline and optimized production classpaths in separate JVMs. */
public final class BlockUpdateSchedulerChunkIndexBenchmark {
    private static volatile long checksum;

    public static void main(String[] args) {
        boolean grid = args.length > 0 && args[0].equals("grid");
        int size = grid ? 4096 : 2048;
        Block block = new BlockAir();
        BlockUpdateEntry[] entries = new BlockUpdateEntry[size];
        AxisAlignedBB[] areas = new AxisAlignedBB[size];
        for (int i = 0; i < size; i++) {
            int x = grid ? (i & 63) - 32 : i - 1024;
            int z = grid ? (i >>> 6) - 32 : x;
            entries[i] = new BlockUpdateEntry(new Vector3(x * 16 + 1, 64, z * 16 + 1), block, 2, 0);
            areas[i] = new SimpleAxisAlignedBB(x * 16, 0, z * 16, x * 16 + 16, 1, z * 16 + 16);
        }
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(null, 0);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 32; i++) {
            cycle(scheduler, entries, areas, bean);
        }
        long[][] nanos = new long[3][11];
        long[] bytes = new long[3];
        for (int sample = 0; sample < 11; sample++) {
            for (int round = 0; round < 16; round++) {
                long[] measured = cycle(scheduler, entries, areas, bean);
                for (int phase = 0; phase < 3; phase++) {
                    nanos[phase][sample] += measured[phase];
                    bytes[phase] += measured[phase + 3];
                }
            }
        }
        for (int phase = 0; phase < 3; phase++) {
            Arrays.sort(nanos[phase]);
            System.out.printf("%s %s: %.3f ns/entry, %.3f B/entry, checksum %d%n",
                    grid ? "grid" : "diagonal", new String[]{"add", "query", "remove"}[phase],
                    nanos[phase][5] / (16d * size), bytes[phase] / (11d * 16 * size), checksum);
        }
    }

    private static long[] cycle(BlockUpdateScheduler scheduler, BlockUpdateEntry[] entries,
                                AxisAlignedBB[] areas, ThreadMXBean bean) {
        long[] result = new long[6];
        long bytes = bean.getCurrentThreadAllocatedBytes();
        long time = System.nanoTime();
        for (BlockUpdateEntry entry : entries) scheduler.add(entry);
        result[0] = System.nanoTime() - time;
        result[3] = bean.getCurrentThreadAllocatedBytes() - bytes;
        bytes = bean.getCurrentThreadAllocatedBytes();
        time = System.nanoTime();
        long count = 0;
        for (AxisAlignedBB area : areas) count += scheduler.getPendingBlockUpdates(area).size();
        result[1] = System.nanoTime() - time;
        result[4] = bean.getCurrentThreadAllocatedBytes() - bytes;
        bytes = bean.getCurrentThreadAllocatedBytes();
        time = System.nanoTime();
        for (BlockUpdateEntry entry : entries) {
            if (!scheduler.remove(entry)) throw new AssertionError("Scheduled entry was lost");
        }
        result[2] = System.nanoTime() - time;
        result[5] = bean.getCurrentThreadAllocatedBytes() - bytes;
        if (count != entries.length || scheduler.getPendingCount() != 0) {
            throw new AssertionError("Incorrect snapshot count: " + count);
        }
        checksum = count;
        return result;
    }
}
