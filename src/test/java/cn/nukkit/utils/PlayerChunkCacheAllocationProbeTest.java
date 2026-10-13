package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Measures the native packet/batch builder with identical compressor work on both sources. */
class PlayerChunkCacheAllocationProbeTest {
    private static volatile byte[] published;

    private static final class IdentityCompressor implements ZlibProvider {
        @Override public byte[] deflateRaw(byte[] data, int level) { return data; }
        @Override public byte[] deflate(byte[] data, int level) { throw new AssertionError("raw branch required"); }
        @Override public byte[] deflate(byte[][] data, int level) { throw new AssertionError("single array required"); }
        @Override public byte[] deflateRaw(byte[][] data, int level) { throw new AssertionError("single array required"); }
        @Override public byte[] inflate(byte[] data, int maxSize) { throw new AssertionError("no inflate"); }
        @Override public byte[] inflateRaw(byte[] data, int maxSize) { throw new AssertionError("no inflate"); }
    }

    private static long build(byte[] input, int operations) {
        long checksum = 0;
        for (int i = 0; i < operations; i++) {
            byte[] bytes = PlayerChunkCacheOwnedBatchTest.actual(GameVersion.V1_26_50, input).payload;
            published = bytes;
            checksum += bytes.length + (bytes[0] & 255);
        }
        return checksum;
    }

    @Test
    void nativeChunkBatchAllocatedBytesKeepIdenticalWireChecksum() throws Exception {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        byte[] input = PlayerChunkCacheOwnedBatchTest.payload(65_536, true);
        byte[] expected = PlayerChunkCacheOwnedBatchTest.plainBatch(GameVersion.V1_26_50, input);
        int operations = 250;
        long expectedChecksum = (long) operations * (expected.length + (expected[0] & 255));
        try (PlayerChunkCacheOwnedBatchTest.Fixture fixture = new PlayerChunkCacheOwnedBatchTest.Fixture()) {
            fixture.server.useSnappy = false;
            fixture.provider(new IdentityCompressor());
            build(input, 2_000);
            for (int pass = 1; pass <= 5; pass++) {
                long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
                long checksum = build(input, operations);
                long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
                assertEquals(expectedChecksum, checksum);
                assertEquals(expected.length, published.length);
                System.out.printf("CHUNK_ALLOCATION_PROBE fixture=identity-compressor pass=%d operations=%d allocatedBytes=%d checksum=%d%n",
                        pass, operations, allocated, checksum);
            }
        }
    }
}
