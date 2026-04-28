package cn.nukkit.utils.compression;

import cn.nukkit.Server;
import com.fulcrumgenomics.jlibdeflate.*;

import java.io.IOException;
import java.util.Arrays;

public class LibDeflateThreadLocal implements ZlibProvider {

    private static final ThreadLocal<LibdeflateDecompressor> DECOMPRESSOR =
            ThreadLocal.withInitial(LibdeflateDecompressor::new);

    @Override
    public byte[] deflate(byte[][] datas, int level) throws IOException {
        return compressMulti(datas, level, true);
    }

    @Override
    public byte[] deflate(byte[] data, int level) throws IOException {
        return compressSingle(data, level, true);
    }

    @Override
    public byte[] deflateRaw(byte[][] datas, int level) throws IOException {
        int effectiveLevel = datas.length < Server.getInstance().networkCompressionThreshold ? 0 : level;
        return compressMulti(datas, effectiveLevel, false);
    }

    @Override
    public byte[] deflateRaw(byte[] data, int level) throws IOException {
        return compressSingle(data, level, false);
    }

    private byte[] compressSingle(byte[] data, int level, boolean zlibFormat) throws IOException {
        try (var compressor = new LibdeflateCompressor(clampLevel(level))) {
            return zlibFormat ? compressor.zlibCompress(data) : compressor.deflateCompress(data);
        } catch (LibdeflateException e) {
            throw new IOException("Compression failed", e);
        }
    }

    private byte[] compressMulti(byte[][] datas, int level, boolean zlibFormat) throws IOException {
        int totalLen = 0;
        for (byte[] d : datas) totalLen += d.length;
        byte[] combined = new byte[totalLen];
        int offset = 0;
        for (byte[] d : datas) {
            System.arraycopy(d, 0, combined, offset, d.length);
            offset += d.length;
        }
        return compressSingle(combined, level, zlibFormat);
    }

    private static int clampLevel(int level) {
        if (level < LibdeflateCompressor.MIN_LEVEL) return LibdeflateCompressor.MIN_LEVEL;
        if (level > LibdeflateCompressor.MAX_LEVEL) return LibdeflateCompressor.MAX_LEVEL;
        return level;
    }

    @Override
    public byte[] inflate(byte[] data, int maxSize) throws IOException {
        return decompress(data, maxSize, true);
    }

    @Override
    public byte[] inflateRaw(byte[] data, int maxSize) throws IOException {
        return decompress(data, maxSize, false);
    }

    private byte[] decompress(byte[] data, int maxSize, boolean zlibFormat) throws IOException {
        LibdeflateDecompressor decompressor = DECOMPRESSOR.get();
        int initialSize = (maxSize > 0) ? maxSize : Math.min(data.length * 4, 16 * 1024);
        byte[] output = new byte[initialSize];

        try {
            DecompressionResult result;
            if (zlibFormat) {
                result = decompressor.zlibDecompressEx(data, 0, data.length, output, 0, output.length);
            } else {
                result = decompressor.deflateDecompressEx(data, 0, data.length, output, 0, output.length);
            }

            int produced = result.outputBytesProduced();
            if (maxSize > 0 && produced > maxSize) {
                throw new IOException("Inflated data exceeds maximum size");
            }
            return Arrays.copyOf(output, produced);

        } catch (LibdeflateException e) {
            throw new IOException("Unable to inflate Zlib stream", e);
        }
    }
}