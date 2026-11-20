import cn.nukkit.nbt.tag.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Each operation is one numeric read from a 36-field native NBT compound. */
public final class CompoundTagLookupBenchmark {
    private static volatile long sink;
    private static long read(CompoundTag tag, String[] names, int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) sum += tag.getInt(names[i & 63]);
        return sum;
    }
    public static void main(String[] args) {
        String mode = args.length == 0 ? "hit" : args[0];
        Map<String, Tag> map = args.length > 1 && args[1].equals("linked") ? new LinkedHashMap<>() : new HashMap<>();
        for (int i = 0; i < 36; i++) map.put("field-" + i, new IntTag(1000 + i));
        CompoundTag tag = new CompoundTag(map);
        String[] names = new String[64];
        for (int i = 0; i < names.length; i++) names[i] = new String(
                mode.equals("missing") || mode.equals("mixed") && (i & 3) == 0 ? "missing-" + i : "field-" + i % 36);
        for (int i = 0; i < 10; i++) sink = read(tag, names, 1_000_000);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().getId();
        for (int sample = 0; sample < 7; sample++) {
            long bytes = bean.getThreadAllocatedBytes(tid), start = System.nanoTime();
            sink = read(tag, names, 3_000_000);
            long elapsed = System.nanoTime() - start, allocated = bean.getThreadAllocatedBytes(tid) - bytes;
            System.out.printf(Locale.ROOT, "%s %.3f ns/read %.3f B/read checksum=%d%n", mode,
                    elapsed / 3_000_000d, allocated / 3_000_000d, sink);
        }
    }
}
