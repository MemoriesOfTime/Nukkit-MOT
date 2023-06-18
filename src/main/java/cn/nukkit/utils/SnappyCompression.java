package cn.nukkit.utils;

import org.xerial.snappy.Snappy;

import java.io.IOException;

public class SnappyCompression {

    public static byte[] compress(byte[] data) throws IOException {
        return Snappy.compress(data);
    }

    public static byte[] decompress(byte[] data, int maxSize) throws IOException {
        int length = Snappy.uncompressedLength(data);
        if (maxSize > 0 && length >= maxSize) {
            throw new IOException("Decompress data exceeds maximum size");
        }
        byte[] buffer = new byte[length];
        Snappy.uncompress(data, 0, data.length, buffer, 0);
        return buffer;
    }

}
