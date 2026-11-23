package cn.nukkit.block;

import cn.nukkit.MockServer;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/** Standalone warmed Block.get benchmark. Run with the Mockito jar passed through -javaagent. */
public final class BlockPrototypeLookupBenchmark {
    private static volatile Object sink;

    private BlockPrototypeLookupBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        MockServer.init();
        int iterations = args.length == 0 ? 2_000_000 : Integer.parseInt(args[0]);
        int samples = args.length < 2 ? 5 : Integer.parseInt(args[1]);
        for (int i = 0; i < 5; i++) {
            lookup(300_000);
        }
        for (int threads : new int[]{1, 4}) {
            for (int sample = 0; sample < samples; sample++) {
                measure(threads, iterations);
            }
        }
    }

    private static void measure(int threadCount, int iterations) throws Exception {
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();
        long[] allocated = new long[threadCount];
        Throwable[] failures = new Throwable[threadCount];
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
                    lookup(iterations);
                    allocated[index] = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
                } catch (Throwable failure) {
                    failures[index] = failure;
                } finally {
                    done.countDown();
                }
            }, "prototype-benchmark-" + i);
            threads.add(thread);
            thread.start();
        }
        ready.await();
        long before = System.nanoTime();
        start.countDown();
        done.await();
        long elapsed = System.nanoTime() - before;
        long bytes = 0;
        for (int i = 0; i < threadCount; i++) {
            threads.get(i).join();
            if (failures[i] != null) {
                throw new AssertionError(failures[i]);
            }
            bytes += allocated[i];
        }
        long operations = (long) threadCount * iterations;
        System.out.printf(Locale.ROOT, "prototype-lookup threads=%d operations=%d ns/op=%.3f bytes/op=%.3f%n",
                threadCount, operations, (double) elapsed / operations, (double) bytes / operations);
    }

    private static void lookup(int iterations) {
        Block[] escaped = new Block[256];
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            Block block = Block.get(Block.WATER, 7);
            escaped[i & 255] = block;
            checksum += block.getDamage();
        }
        if (checksum != 7L * iterations) {
            throw new AssertionError("Unexpected block metadata: " + checksum);
        }
        sink = escaped;
    }
}
