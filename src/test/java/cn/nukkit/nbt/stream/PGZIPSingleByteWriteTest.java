package cn.nukkit.nbt.stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PGZIPSingleByteWriteTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 255, 65535, 65536, 65537, 131071, 131072, 131073})
    void singleWritesMatchBulkGzipBytesAndTrailer(int length) throws IOException {
        byte[] expected = data(length);
        byte[] single = compressed(expected, true);
        assertArrayEquals(compressed(expected, false), single);
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(single))) {
            assertArrayEquals(expected, input.readAllBytes());
        }
    }

    @Test
    void truncatesAllIntValuesToLowEightBits() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 1)) {
            for (int value : new int[]{Integer.MIN_VALUE, -257, -1, 0, 255, 256, Integer.MAX_VALUE}) {
                stream.write(value);
            }
        }
        assertArrayEquals(new byte[]{0, -1, -1, 0, -1, 0, -1}, inflate(bytes.toByteArray()));
    }

    @Test
    void flushAndMixedBulkWritesPreserveExactGzipBytes() throws IOException {
        assertArrayEquals(mixed(false), mixed(true));
    }

    @Test
    void subclassArrayOverrideReceivesFreshRetainableArrays() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<byte[]> retained = new ArrayList<>();
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 1) {
            @Override
            public void write(byte[] value) throws IOException {
                retained.add(value);
                super.write(value);
            }
        }) {
            stream.write(17);
            stream.write(34);
            assertEquals(2, retained.size());
            assertNotSame(retained.get(0), retained.get(1));
            assertArrayEquals(new byte[]{17}, retained.get(0));
            assertArrayEquals(new byte[]{34}, retained.get(1));
            retained.get(0)[0] = 99;
        }
        assertArrayEquals(new byte[]{17, 34}, inflate(bytes.toByteArray()));
    }

    @Test
    void subclassSliceOverrideCanReenterBeforeCopy() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<byte[]> retained = new ArrayList<>();
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 1) {
            private boolean nested;

            @Override
            public void write(byte[] value, int offset, int length) throws IOException {
                retained.add(value);
                assertEquals(0, offset);
                assertEquals(1, length);
                if (!nested) {
                    nested = true;
                    write(42);
                }
                super.write(value, offset, length);
            }
        }) {
            stream.write(17);
        }
        assertEquals(2, retained.size());
        assertNotSame(retained.get(0), retained.get(1));
        assertArrayEquals(new byte[]{42, 17}, inflate(bytes.toByteArray()));
    }

    @Test
    void subclassArrayFailurePrecedesCrcAndInputMutation() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        IOException sentinel = new IOException("array interceptor");
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 1) {
            private boolean fail = true;

            @Override
            public void write(byte[] value) throws IOException {
                if (fail) {
                    fail = false;
                    throw sentinel;
                }
                super.write(value);
            }
        }) {
            assertSame(sentinel, assertThrows(IOException.class, () -> stream.write(17)));
            stream.write(34);
        }
        assertArrayEquals(new byte[]{34}, inflate(bytes.toByteArray()));
    }

    @Test
    void fullBlockCanRetryAfterRejectedSubmission() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InlineExecutor executor = new InlineExecutor();
        byte[] expected = data(PGZIPBlock.SIZE + 1);
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, executor, 1)) {
            stream.write(expected, 0, PGZIPBlock.SIZE - 1);
            executor.reject = true;
            assertThrows(RejectedExecutionException.class,
                    () -> stream.write(expected[PGZIPBlock.SIZE - 1]));
            executor.reject = false;
            stream.write(expected[PGZIPBlock.SIZE]);
        }
        assertArrayEquals(compressed(expected, false), bytes.toByteArray());
        assertArrayEquals(expected, inflate(bytes.toByteArray()));
    }

    @Test
    void writingAfterCloseKeepsExistingNoSecondCloseBehavior() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 1);
        stream.write(17);
        stream.close();
        byte[] closed = bytes.toByteArray();
        stream.write(34);
        stream.close();
        assertArrayEquals(closed, bytes.toByteArray());
        assertArrayEquals(new byte[]{17}, inflate(closed));
    }

    private static byte[] mixed(boolean single) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 2)) {
            byte[] data = data(PGZIPBlock.SIZE + 5);
            for (int section = 0; section < 3; section++) {
                stream.write(data, 0, 11);
                for (byte value : data) {
                    if (single) stream.write(value);
                    else stream.write(new byte[]{value});
                }
                stream.flush();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] compressed(byte[] data, boolean single) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PGZIPOutputStream stream = new PGZIPOutputStream(bytes, new InlineExecutor(), 1)) {
            if (single) {
                for (byte value : data) stream.write(value);
            } else {
                stream.write(data);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] inflate(byte[] bytes) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return input.readAllBytes();
        }
    }

    private static byte[] data(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) result[i] = (byte) (i * 1103515245 + 12345);
        return result;
    }

    private static class InlineExecutor extends AbstractExecutorService {
        private boolean reject;
        private boolean shutdown;
        @Override public void execute(Runnable command) {
            if (reject) throw new RejectedExecutionException("submission rejected");
            command.run();
        }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
