import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.util.Locale;

/** Actual native getName/toItem calls; fixture setup and registry initialization are untimed. */
public final class BlockNameBenchmark {
    private static final String[] TYPES = {
            "BlockBricksStone",
            "BlockCoralBlock",
            "BlockCoralFan",
            "BlockDoubleSlabRedSandstone",
            "BlockDoubleSlabStone",
            "BlockDoubleSlabStone3",
            "BlockDoubleSlabStone4",
            "BlockDoubleSlabWood",
            "BlockFence",
            "BlockFlower",
            "BlockLeaves",
            "BlockLeaves2",
            "BlockPlanks",
            "BlockPurpur",
            "BlockQuartz",
            "BlockRedSandstone",
            "BlockSandstone",
            "BlockSapling",
            "BlockSlabRedSandstone",
            "BlockSlabStone",
            "BlockSlabStone3",
            "BlockSlabStone4",
            "BlockSlabWood",
            "BlockStone",
            "BlockTallGrass",
            "BlockWood"
    };
    private static final int ITERATIONS = 200_000;
    private static final Block[][] INPUT = new Block[TYPES.length][256];
    private static final Object[] OUTPUT = new Object[256];
    private static volatile long sink;

    private static void prepare() throws ReflectiveOperationException {
        MockServer.init();
        System.out.println("helper=" + BlockNameBenchmark.class.getProtectionDomain().getCodeSource().getLocation());
        for (int type = 0; type < TYPES.length; type++) {
            Class<? extends Block> blockType = Class.forName("cn.nukkit.block." + TYPES[type]).asSubclass(Block.class);
            Constructor<? extends Block> constructor = blockType.getConstructor(int.class);
            System.out.printf(Locale.ROOT, "origin={\"class\":\"%s\",\"url\":\"%s\"}%n",
                    TYPES[type], blockType.getProtectionDomain().getCodeSource().getLocation());
            for (int i = 0; i < INPUT[type].length; i++) {
                // Coral fan types5..7 are invalid; failures are covered by behavior tests.
                int meta = TYPES[type].equals("BlockCoralFan") ? i % 5 : i & 15;
                Block block = constructor.newInstance(meta);
                if (block.getClass() != blockType) throw new AssertionError("Native fixture class changed");
                INPUT[type][i] = block;
            }
        }
    }

    private static long loop(int type, boolean items) {
        long sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            Block block = INPUT[type][i & 255];
            if (items) {
                Item item = block.toItem();
                OUTPUT[i & 255] = item;
                sum = sum * 31 + item.getName().hashCode() + item.getDamage() + item.getId() + item.getCount();
            } else {
                String name = block.getName();
                OUTPUT[i & 255] = name;
                sum = sum * 31 + name.hashCode();
            }
        }
        // Consume the retained results; no array or table is forced to escape from getName.
        for (Object value : OUTPUT) {
            if (items) {
                Item item = (Item) value;
                sum = sum * 31 + item.getName().hashCode() + item.getDamage() + item.getId() + item.getCount();
            } else {
                sum = sum * 31 + ((String) value).hashCode();
            }
        }
        return sum;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        prepare();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isCurrentThreadCpuTimeSupported()) {
            throw new IllegalStateException("Allocation and current-thread CPU counters are required");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        bean.setThreadCpuTimeEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int type = 0; type < TYPES.length; type++) for (boolean items : new boolean[]{false, true}) {
            long expected = loop(type, items);
            for (int warmup = 0; warmup < 8; warmup++) {
                sink = loop(type, items);
                if (sink != expected) throw new AssertionError("Warmup output changed");
            }
            for (int sample = 0; sample < 7; sample++) {
                // CPU counter calls remain outside the allocation interval.
                long cpuStart = bean.getCurrentThreadCpuTime();
                long bytes = bean.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                sink = loop(type, items);
                long elapsed = System.nanoTime() - start;
                long allocated = bean.getThreadAllocatedBytes(thread) - bytes;
                long cpu = bean.getCurrentThreadCpuTime() - cpuStart;
                if (sink != expected) throw new AssertionError("Measured output changed");
                System.out.printf(Locale.ROOT,
                        "{\"class\":\"%s\",\"items\":%s,\"sample\":%d,\"iterations\":%d,\"ns_per_op\":%.6f,\"cpu_ns_per_op\":%.6f,\"bytes_per_op\":%.6f,\"checksum\":%d}%n",
                        TYPES[type], items, sample, ITERATIONS, elapsed / (double) ITERATIONS,
                        cpu / (double) ITERATIONS, allocated / (double) ITERATIONS, sink);
            }
        }
    }
}
