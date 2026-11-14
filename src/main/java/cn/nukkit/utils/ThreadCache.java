package cn.nukkit.utils;

import cn.nukkit.nbt.stream.FastByteArrayOutputStream;

import java.io.IOException;
import java.util.BitSet;

/**
 * Thread cache
 */
public class ThreadCache {

    @Deprecated
    public static void clean() {

    }

    public static final ThreadLocal<byte[][]> idArray = ThreadLocal.withInitial(() -> new byte[16][]);

    public static final ThreadLocal<byte[][]> dataArray = ThreadLocal.withInitial(() -> new byte[16][]);

    public static final ThreadLocal<byte[]> byteCache6144 = ThreadLocal.withInitial(() -> new byte[6144]);

    public static final ThreadLocal<byte[]> byteCache256 = ThreadLocal.withInitial(() -> new byte[256]);

    public static final ThreadLocal<BitSet> boolCache4096 = ThreadLocal.withInitial(() -> new BitSet(4096));

    public static final ThreadLocal<char[]> charCache4096v2 = ThreadLocal.withInitial(() -> new char[4096]);

    public static final ThreadLocal<char[]> charCache4096 = ThreadLocal.withInitial(() -> new char[4096]);

    public static final ThreadLocal<int[]> intCache256 = ThreadLocal.withInitial(() -> new int[256]);

    public static final ThreadLocal<FastByteArrayOutputStream> fbaos = ThreadLocal.withInitial(CachedOutputStream::new);

    public static final ThreadLocal<BinaryStream> binaryStream = ThreadLocal.withInitial(CachedBinaryStream::new);

    // Bound idle retention, not the size of an operation. Once a scratch buffer
    // grows past this limit, only its caller owns it, even if that caller fails.
    static final int MAX_CACHED_BUFFER_BYTES = 1024 * 1024;

    private static final ThreadLocal<BinaryStreamSlot> leasedBinaryStream = ThreadLocal.withInitial(BinaryStreamSlot::new);

    /**
     * 借用独占暂存流；必须在同线程的 finally 中归还，返回的结果应使用独立快照。
     * <p>
     * Borrows exclusive scratch storage; release in a same-thread finally and return only owned snapshots.
     */
    public static BinaryStream acquireBinaryStream() {
        BinaryStreamSlot slot = leasedBinaryStream.get();
        BinaryStream stream = slot.idle;
        slot.idle = null;
        return stream == null ? new BinaryStream() : stream;
    }

    /**
     * 归还后不得继续访问；每线程仅保留一份有界缓冲，重入时优先保留较大的可复用缓冲。
     * <p>
     * Ends the lease; retains one bounded buffer per thread, preferring the larger reusable buffer on reentry.
     */
    public static void releaseBinaryStream(BinaryStream stream) {
        byte[] bytes = stream.getBufferUnsafe();
        stream.reset();
        if (bytes == null || bytes.length > MAX_CACHED_BUFFER_BYTES || stream.getClass() != BinaryStream.class) {
            return;
        }
        BinaryStreamSlot slot = leasedBinaryStream.get();
        if (slot.idle == null || slot.idle.getBufferUnsafe().length < bytes.length) {
            slot.idle = stream;
        }
    }

    private static final class BinaryStreamSlot {
        private BinaryStream idle;
    }

    private static final class CachedOutputStream extends FastByteArrayOutputStream {
        private boolean detached;

        private CachedOutputStream() {
            super(1024);
        }

        @Override
        public void write(int value) {
            try {
                super.write(value);
            } finally {
                detachIfOversized();
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                super.write(bytes, offset, length);
            } finally {
                detachIfOversized();
            }
        }

        private void detachIfOversized() {
            if (!detached && array.length > MAX_CACHED_BUFFER_BYTES) {
                detached = true;
                fbaos.remove();
            }
        }
    }

    private static final class CachedBinaryStream extends BinaryStream {
        private boolean detached;

        @Override
        public void reserve(int additionalBytes) {
            try {
                super.reserve(additionalBytes);
            } finally {
                detachIfOversized();
            }
        }

        @Override
        public void put(byte[] bytes) {
            try {
                super.put(bytes);
            } finally {
                detachIfOversized();
            }
        }

        @Override
        public void setBuffer(byte[] bytes) {
            try {
                super.setBuffer(bytes);
            } finally {
                detachIfOversized();
            }
        }

        private void detachIfOversized() {
            byte[] bytes = getBufferUnsafe();
            if (!detached && bytes != null && bytes.length > MAX_CACHED_BUFFER_BYTES) {
                detached = true;
                binaryStream.remove();
            }
        }
    }
}
