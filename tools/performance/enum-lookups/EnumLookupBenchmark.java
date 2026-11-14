import cn.nukkit.block.BlockShelf;
import cn.nukkit.block.BlockShelfOak;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Calls native lookups and the whole native stored-palette reader. */
public final class EnumLookupBenchmark {
    private static final int[] READ = {0, 1, 2, 3, 4, 5, 6, 8, 16};
    private static final int[] WRITE = {0, 1, 2, 3, 4, 5, 6, 8, 16, 32, 64, 4096};
    private static final String[] MODES = {
            "enum-read", "enum-write", "shelf-type", "shelf-powered-control",
            "palette-v0", "palette-v1", "palette-v4"
    };
    private static final BlockShelf[] SHELVES = new BlockShelf[256];
    private static final PalettedBlockStorage[] STORAGE = new PalettedBlockStorage[256];
    private static volatile long sink;

    private static long loop(String mode, int iterations, ByteBuf[] inputs) {
        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            int slot = i & 255;
            switch (mode) {
                case "enum-read" -> sum += BitArrayVersion.get(READ[i % READ.length], true).ordinal();
                case "enum-write" -> sum += BitArrayVersion.get(WRITE[i % WRITE.length], false).ordinal();
                case "shelf-type" -> sum += SHELVES[slot].getShelfType().ordinal();
                case "shelf-powered-control" -> sum += SHELVES[slot].isPowered() ? 1 : 0;
                case "palette-v0", "palette-v1", "palette-v4" -> {
                    ByteBuf input = inputs[slot];
                    input.readerIndex(0);
                    STORAGE[slot].readFromStorage(input);
                    sum += STORAGE[slot].getBlock((i * 31) & 4095);
                }
                default -> throw new IllegalArgumentException(mode);
            }
        }
        return sum;
    }

    private static long expected(String mode, int iterations) {
        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += switch (mode) {
                case "enum-read" -> 8 - i % READ.length;
                // Preserve the original ordered entriesPerWord predicate: 0/1 select V0,
                // and every other input used here selects the first constant, V16.
                case "enum-write" -> i % WRITE.length < 2 ? 8 : 0;
                case "shelf-type" -> i & 3;
                case "shelf-powered-control" -> (i & 4) != 0 ? 1 : 0;
                case "palette-v0", "palette-v1", "palette-v4" -> 117 + (i & 15);
                default -> throw new IllegalArgumentException(mode);
            };
        }
        return sum;
    }

    private static ByteBuf[] inputs(String mode) {
        if (!mode.startsWith("palette-")) return null;
        BitArrayVersion version = switch (mode) {
            case "palette-v0" -> BitArrayVersion.V0;
            case "palette-v1" -> BitArrayVersion.V1;
            case "palette-v4" -> BitArrayVersion.V4;
            default -> throw new IllegalArgumentException(mode);
        };
        ByteBuf[] result = new ByteBuf[256];
        for (int slot = 0; slot < result.length; slot++) {
            int words = version.getWordsForSize(4096);
            int size = 1 + words * Integer.BYTES + (version == BitArrayVersion.V0 ? 0 : Integer.BYTES)
                    + Integer.BYTES;
            ByteBuf input = Unpooled.buffer(size, size);
            input.writeByte((version.getId() << 1) | (slot & 1));
            for (int i = 0; i < words; i++) input.writeIntLE(0);
            if (version != BitArrayVersion.V0) input.writeIntLE(1);
            input.writeIntLE(117 + (slot & 15));
            result[slot] = input;
        }
        return result;
    }

    public static void main(String[] args) {
        for (Class<?> type : new Class<?>[]{BitArrayVersion.class, BlockShelf.class,
                BlockShelfOak.class, PalettedBlockStorage.class, EnumLookupBenchmark.class}) {
            System.out.printf("origin=%s\t%s%n", type.getName(),
                    type.getProtectionDomain().getCodeSource().getLocation());
        }
        for (int i = 0; i < 256; i++) {
            SHELVES[i] = new BlockShelfOak(i & 31);
            STORAGE[i] = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 0);
        }
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isCurrentThreadCpuTimeSupported()) {
            throw new IllegalStateException("Current-thread allocation and CPU counters required");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        bean.setThreadCpuTimeEnabled(true);
        long thread = Thread.currentThread().getId();
        for (String mode : MODES) {
            int iterations = mode.startsWith("palette-") ? 8192 : 1_000_000;
            long expected = expected(mode, iterations);
            ByteBuf[] inputs = inputs(mode);
            try {
                for (int warmup = 0; warmup < 8; warmup++) {
                    sink = loop(mode, iterations, inputs);
                    if (sink != expected) throw new AssertionError(mode + " warmup checksum");
                }
                for (int sample = 0; sample < 7; sample++) {
                    long cpuStart = bean.getCurrentThreadCpuTime();
                    long bytesStart = bean.getThreadAllocatedBytes(thread);
                    long start = System.nanoTime();
                    sink = loop(mode, iterations, inputs);
                    long elapsed = System.nanoTime() - start;
                    long bytes = bean.getThreadAllocatedBytes(thread) - bytesStart;
                    long cpu = bean.getCurrentThreadCpuTime() - cpuStart;
                    if (sink != expected) throw new AssertionError(mode + " measured checksum");
                    System.out.printf(Locale.ROOT,
                            "{\"mode\":\"%s\",\"sample\":%d,\"iterations\":%d,"
                                    + "\"ns_per_op\":%.9f,\"cpu_ns_per_op\":%.9f,"
                                    + "\"bytes_per_op\":%.9f,\"checksum\":%d}%n",
                            mode, sample, iterations, elapsed / (double) iterations,
                            cpu / (double) iterations, bytes / (double) iterations, sink);
                }
                if (inputs != null) {
                    for (int i = 0; i < inputs.length; i++) {
                        if (inputs[i].isReadable() || STORAGE[i].getBlock(4095) != 117 + (i & 15)) {
                            throw new AssertionError(mode + " stored state or unread bytes");
                        }
                    }
                }
            } finally {
                if (inputs != null) for (ByteBuf input : inputs) input.release();
            }
        }
    }
}
