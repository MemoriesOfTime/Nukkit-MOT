package cn.nukkit.utils;

import cn.nukkit.nbt.stream.FastByteArrayOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class ThreadCacheRetentionTest {
    private static final int LIMIT = 1024 * 1024;

    @AfterEach
    void clear() {
        ThreadCache.fbaos.remove();
        ThreadCache.binaryStream.remove();
    }

    @Test
    void reusesSmallBuffers() throws Exception {
        FastByteArrayOutputStream out = ThreadCache.fbaos.get();
        out.write(new byte[8192]);
        out.reset();
        assertSame(out, ThreadCache.fbaos.get());
        BinaryStream stream = ThreadCache.binaryStream.get();
        stream.put(new byte[8192]);
        stream.reset();
        assertSame(stream, ThreadCache.binaryStream.get());
    }

    @Test
    void detachesLargeOutputBeforeSnapshotOrReset() throws Exception {
        byte[] payload = new byte[2 * LIMIT];
        new Random(1741).nextBytes(payload);
        FastByteArrayOutputStream out = ThreadCache.fbaos.get();
        out.write(payload);
        assertNotSame(out, ThreadCache.fbaos.get());
        assertArrayEquals(payload, out.toByteArray());
        assertTrue(ThreadCache.fbaos.get().array.length <= LIMIT);
    }

    @Test
    void detachesOutputGrowingOneByteAtATime() {
        FastByteArrayOutputStream out = ThreadCache.fbaos.get();
        for (int i = 0; i <= LIMIT; i++) out.write(i);
        assertNotSame(out, ThreadCache.fbaos.get());
        byte[] encoded = out.toByteArray();
        assertEquals(LIMIT + 1, encoded.length);
        for (int i = 0; i < encoded.length; i++) assertEquals((byte)i, encoded[i]);
    }

    @Test
    void failedOperationDoesNotRetainLargeOutput() {
        FastByteArrayOutputStream out = ThreadCache.fbaos.get();
        assertThrows(IOException.class, () -> {
            out.write(new byte[2 * LIMIT]);
            throw new IOException("simulated encoder failure");
        });
        assertNotSame(out, ThreadCache.fbaos.get());
    }

    @Test
    void failedBulkCopyAfterGrowthStillDetaches() {
        FastByteArrayOutputStream out = ThreadCache.fbaos.get();
        byte[] payload = new byte[2 * LIMIT];
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(payload, 1, payload.length));
        assertTrue(out.array.length > LIMIT);
        assertNotSame(out, ThreadCache.fbaos.get());
    }

    @Test
    void detachedOutputCannotEvictItsReplacement() throws Exception {
        FastByteArrayOutputStream out = ThreadCache.fbaos.get();
        out.write(new byte[2 * LIMIT]);
        FastByteArrayOutputStream replacement = ThreadCache.fbaos.get();
        out.write(1);
        out.write(new byte[64]);
        assertSame(replacement, ThreadCache.fbaos.get());
    }

    @Test
    void detachesLargeBinaryWithoutChangingBytes() {
        byte[] payload = new byte[2 * LIMIT];
        new Random(927).nextBytes(payload);
        BinaryStream stream = ThreadCache.binaryStream.get();
        stream.put(payload);
        assertNotSame(stream, ThreadCache.binaryStream.get());
        assertArrayEquals(payload, stream.getBuffer());
        assertTrue(ThreadCache.binaryStream.get().getBufferUnsafe().length <= LIMIT);
    }

    @Test
    void detachesSetBufferButHandlesNull() {
        BinaryStream stream = ThreadCache.binaryStream.get();
        stream.setBuffer(null);
        assertSame(stream, ThreadCache.binaryStream.get());
        byte[] payload = new byte[2 * LIMIT];
        stream.setBuffer(payload, 11);
        assertNotSame(stream, ThreadCache.binaryStream.get());
        assertSame(payload, stream.getBufferUnsafe());
        assertEquals(11, stream.getOffset());
    }

    @Test
    void detachedBinaryCannotEvictItsReplacement() {
        BinaryStream stream = ThreadCache.binaryStream.get();
        stream.put(new byte[2 * LIMIT]);
        BinaryStream replacement = ThreadCache.binaryStream.get();
        stream.putByte(1);
        stream.setBuffer(new byte[2 * LIMIT]);
        assertSame(replacement, ThreadCache.binaryStream.get());
    }

    @Test
    void workerEvictionDoesNotTouchAnotherThread() throws Exception {
        FastByteArrayOutputStream main = ThreadCache.fbaos.get();
        BinaryStream mainBinary = ThreadCache.binaryStream.get();
        var worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try {
                    FastByteArrayOutputStream out = ThreadCache.fbaos.get();
                    out.write(new byte[2 * LIMIT]);
                    assertNotSame(out, ThreadCache.fbaos.get());
                    BinaryStream stream = ThreadCache.binaryStream.get();
                    stream.put(new byte[2 * LIMIT]);
                    assertNotSame(stream, ThreadCache.binaryStream.get());
                } catch (IOException e) { throw new AssertionError(e); }
            }).get();
            assertSame(main, ThreadCache.fbaos.get());
            assertSame(mainBinary, ThreadCache.binaryStream.get());
        } finally { worker.shutdownNow(); }
    }
}
