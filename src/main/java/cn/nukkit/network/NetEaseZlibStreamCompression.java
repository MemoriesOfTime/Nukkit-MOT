package cn.nukkit.network;

import cn.nukkit.utils.BinaryStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class NetEaseZlibStreamCompression implements CompressionProvider {

    private static final int CHUNK = 8192;
    private static final int MAX_DECOMPRESSED_BYTES = 3 * 1024 * 1024;

    private final Deflater deflater = new Deflater(7, true);
    private final Inflater inflater = new Inflater(true);

    @Override
    public synchronized byte[] compress(BinaryStream packet, int level) throws Exception {
        byte[] data = packet.getBuffer();
        this.deflater.setInput(data);
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        byte[] buf = new byte[CHUNK];
        while (!this.deflater.needsInput()) {
            int written = this.deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
            if (written > 0) outBuf.write(buf, 0, written);
        }
        while (true) {
            int written = this.deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
            if (written == 0) break;
            outBuf.write(buf, 0, written);
        }
        return outBuf.toByteArray();
    }

    @Override
    public synchronized byte[] decompress(byte[] compressed) throws Exception {
        return this.decompress(compressed, MAX_DECOMPRESSED_BYTES);
    }

    @Override
    public synchronized byte[] decompress(byte[] compressed, int maxSize) throws Exception {
        this.inflater.setInput(compressed);
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        byte[] buf = new byte[CHUNK];
        int total = 0;
        while (!this.inflater.needsInput()) {
            int written = this.inflater.inflate(buf, 0, buf.length);
            if (written == 0) break;
            outBuf.write(buf, 0, written);
            total += written;
            if (total > maxSize) {
                throw new IOException("Decompressed data exceeds maximum size");
            }
        }
        return outBuf.toByteArray();
    }

    @Override
    public byte getPrefix() {
        return (byte) 0x02;
    }
}
