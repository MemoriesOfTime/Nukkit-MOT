import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Measures actual ListTag.copy; every result escapes into a retained ring. */
public final class ListTagCopyBenchmark {
    private static final ThreadMXBean BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final Tag[] RETAINED = new Tag[64];
    private static volatile long consumed;

    public static void main(String[] args) {
        BEAN.setThreadAllocatedMemoryEnabled(true);
        System.out.println("classes=" + ListTag.class.getProtectionDomain().getCodeSource().getLocation());
        for (int size : new int[]{0, 1, 10, 11, 36, 256, 4096}) {
            ListTag<IntTag> source = new ListTag<>("values");
            for (int i = 0; i < size; i++) source.add(new IntTag(i));
            measure("int-" + size, source, Math.max(1000, 200000 / Math.max(1, size)));
        }
        ListTag<CompoundTag> inventory = new ListTag<>("inventory");
        for (int i = 0; i < 36; i++) {
            inventory.add(new CompoundTag().putInt("Slot", i).putString("Name", "minecraft:stone")
                    .putList(new ListTag<IntTag>("ench").add(new IntTag(i))));
        }
        measure("compound-36", inventory, 3000);
        if (consumed == Long.MIN_VALUE) throw new AssertionError();
    }

    private static void copies(ListTag<?> source, int count) {
        for (int i = 0; i < count; i++) RETAINED[i & 63] = source.copy();
        long sum = 0;
        for (Tag value : RETAINED) if (value != null) sum += ((ListTag<?>) value).size();
        consumed = sum;
    }

    private static void measure(String workload, ListTag<?> source, int count) {
        for (int i = 0; i < 8; i++) copies(source, count);
        long tid = Thread.currentThread().getId();
        for (int round = 0; round < 5; round++) {
            long bytes = BEAN.getThreadAllocatedBytes(tid);
            long start = System.nanoTime();
            copies(source, count);
            long elapsed = System.nanoTime() - start;
            long allocated = BEAN.getThreadAllocatedBytes(tid) - bytes;
            System.out.printf(Locale.ROOT,
                    "{\"workload\":\"%s\",\"round\":%d,\"ns_op\":%.3f,\"bytes_op\":%.3f,\"count\":%d,\"consumed\":%d}%n",
                    workload, round, (double) elapsed / count, (double) allocated / count, count, consumed);
        }
    }
}
