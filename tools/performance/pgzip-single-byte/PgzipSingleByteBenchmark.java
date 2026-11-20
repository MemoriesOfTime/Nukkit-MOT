import cn.nukkit.nbt.stream.PGZIPOutputStream;
import com.sun.management.ThreadMXBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Actual gzip write loops; main-thread allocation excludes construction and close. */
public final class PgzipSingleByteBenchmark {
    private static final int COUNT = 1 << 20;
    private static final ThreadMXBean ALLOC = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile long sink;

    private static long run(ExecutorService worker, boolean single, boolean measured, int sample) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] one = new byte[1];
        PGZIPOutputStream stream = new PGZIPOutputStream(bytes, worker, 1);
        long tid = Thread.currentThread().getId();
        long beforeBytes = ALLOC.getThreadAllocatedBytes(tid);
        long beforeNanos = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            int value = i * 1103515245 + 12345;
            if (single) stream.write(value);
            else { one[0] = (byte) value; stream.write(one, 0, 1); }
        }
        long nanos = System.nanoTime() - beforeNanos;
        long allocated = ALLOC.getThreadAllocatedBytes(tid) - beforeBytes;
        stream.close();
        byte[] gzip = bytes.toByteArray();
        long checksum = Arrays.hashCode(gzip);
        sink += checksum;
        if (measured) {
            System.out.printf(Locale.ROOT,
                    "{\"mode\":\"%s\",\"sample\":%d,\"input_bytes\":%d,\"ns_per_byte\":%.9f,\"main_bytes_per_byte\":%.9f,\"gzip_bytes\":%d,\"checksum\":%d}%n",
                    single ? "write-int" : "bulk-control", sample, COUNT,
                    (double) nanos / COUNT, (double) allocated / COUNT, gzip.length, checksum);
        }
        return checksum;
    }

    public static void main(String[] args) throws Exception {
        ALLOC.setThreadAllocatedMemoryEnabled(true);
        ExecutorService worker = Executors.newFixedThreadPool(1);
        try {
            long expected = 0;
            for (int i = 0; i < 8; i++) {
                long observed = run(worker, (i & 1) == 0, false, i);
                if (i == 0) expected = observed;
                else if (expected != observed) throw new AssertionError("Warmup gzip differs");
            }
            for (int i = 0; i < 4; i++) {
                for (boolean single : new boolean[]{true, false, false, true}) {
                    if (run(worker, single, true, i) != expected) throw new AssertionError("Measured gzip differs");
                }
            }
        } finally {
            worker.shutdown();
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                throw new AssertionError("Compression worker did not finish");
            }
        }
        if (sink == 0) throw new AssertionError("Unconsumed gzip");
    }
}
