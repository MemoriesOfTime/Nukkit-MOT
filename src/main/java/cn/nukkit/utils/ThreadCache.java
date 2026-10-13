package cn.nukkit.utils;

import cn.nukkit.nbt.stream.FastByteArrayOutputStream;

import java.util.BitSet;
import java.io.IOException;

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
