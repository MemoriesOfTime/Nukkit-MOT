package cn.nukkit.utils;

import cn.nukkit.Server;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.zip.Deflater;

public abstract class Zlib {

    private static ZlibProvider[] providers;
    private static volatile ZlibProvider provider;
    private static final ReentrantReadWriteLock providerLock = new ReentrantReadWriteLock();

    static {
        providers = new ZlibProvider[3];
        providers[2] = new ZlibThreadLocal();
        provider = providers[2];
    }

    /**
     * Set Zlib provider that is used to compress data
     *
     * 0 = ZlibOriginal
     * 1 = ZlibSingleThreadLowMem
     * 2 = ZlibThreadLocal (default)
     */
    public static void setProvider(int providerIndex) {
        providerLock.writeLock().lock();
        try {
            switch (providerIndex) {
                case 0:
                    if (providers[providerIndex] == null)
                        providers[providerIndex] = new ZlibOriginal();
                    break;
                case 1:
                    if (providers[providerIndex] == null)
                        providers[providerIndex] = new ZlibSingleThreadLowMem();
                    break;
                case 2:
                    if (providers[providerIndex] == null)
                        providers[providerIndex] = new ZlibThreadLocal();
                    break;
                default:
                    throw new UnsupportedOperationException("Invalid provider: " + providerIndex);
            }
            provider = providers[providerIndex];
        } finally {
            providerLock.writeLock().unlock();
        }
    }

    public static byte[] deflate(byte[] data) throws Exception {
        return deflate(data, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * Run with a stable concurrent compressor, or return null for the main-thread fallback.
     * The action must not change the provider. Concurrent actions share the read lock.
     */
    public static <T> T withConcurrentCompression(Supplier<T> action) {
        providerLock.readLock().lock();
        try {
            return provider instanceof ZlibSingleThreadLowMem ? null : action.get();
        } finally {
            providerLock.readLock().unlock();
        }
    }

    public static byte[] deflate(byte[] data, int level) throws Exception {
        return provider.deflate(data, level);
    }

    public static byte[] deflatePre16Packet(byte[] data, int level) throws Exception {
        return provider.deflate(data, data.length < Server.getInstance().networkCompressionThreshold ? 0 : level);
    }

    public static byte[] deflate(byte[][] data, int level) throws Exception {
        return provider.deflate(data, level);
    }

    public static byte[] deflateRaw(byte[] data, int level) throws Exception {
        return provider.deflateRaw(data, level);
    }

    public static byte[] deflateRaw(byte[][] data, int level) throws Exception {
        return provider.deflateRaw(data, level);
    }

    public static byte[] inflate(byte[] data) throws IOException {
        return inflate(data, -1);
    }

    public static byte[] inflate(byte[] data, int maxSize) throws IOException {
        return provider.inflate(data, maxSize);
    }

    public static byte[] inflateRaw(byte[] data, int maxSize) throws IOException {
        return provider.inflateRaw(data, maxSize);
    }
}
