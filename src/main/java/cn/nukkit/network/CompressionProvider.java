package cn.nukkit.network;

import cn.nukkit.Server;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.SnappyCompression;
import cn.nukkit.utils.Zlib;

import java.io.IOException;
import java.util.zip.DataFormatException;

public interface CompressionProvider {

    CompressionProvider NONE = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) {
            return packet.getBuffer();
        }

        @Override
        public byte[] decompress(byte[] compressed) {
            return compressed;
        }

        @Override
        public byte getPrefix() {
            return (byte) 0xff;
        }

        @Override
        public String toString() {
            return "none";
        }
    };

    CompressionProvider ZLIB = new CompressionProvider() {
        // 带 zlib 头（0x78）的封装编码，1.20.60+ 的前缀表里没有对应值
        // Header-bearing (0x78) codec: the 1.20.60+ prefix table has no value for it
        @Override
        public boolean supportsPrefix() {
            return false;
        }

        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return Zlib.deflatePre16Packet(packet.getBuffer(), level);
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return Zlib.inflate(compressed, 3145728); // 3 * 1024 * 1024
        }

        @Override
        public byte[] decompress(byte[] compressed, int maxSize) throws Exception {
            return Zlib.inflate(compressed, maxSize);
        }

        @Override
        public String toString() {
            return "zlib-header";
        }
    };

    CompressionProvider ZLIB_RAW = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return Zlib.deflateRaw(packet, level);
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return Zlib.inflateRaw(compressed, 3145728); // 3 * 1024 * 1024
        }

        @Override
        public byte[] decompress(byte[] compressed, int maxSize) throws Exception {
            return Zlib.inflateRaw(compressed, maxSize);
        }

        @Override
        public byte getPrefix() {
            return (byte) 0x00;
        }

        @Override
        public String toString() {
            return "zlib-raw";
        }
    };

    CompressionProvider SNAPPY = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return SnappyCompression.compress(packet.getBuffer());
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return SnappyCompression.decompress(compressed, 3145728); // 3 * 1024 * 1024
        }

        @Override
        public byte[] decompress(byte[] compressed, int maxSize) throws Exception {
            return SnappyCompression.decompress(compressed, maxSize);
        }

        @Override
        public byte getPrefix() {
            return 0x01;
        }

        @Override
        public String toString() {
            return "snappy";
        }
    };

    CompressionProvider NETEASE_UNKNOWN = new CompressionProvider() {
        // 出站压缩即 ZLIB_RAW 的 raw deflate，与 0x00 前缀一致
        // Outbound compression is ZLIB_RAW's raw deflate and shares its 0x00 prefix
        @Override
        public byte getPrefix() {
            return (byte) 0x00;
        }

        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return ZLIB_RAW.compress(packet, level);
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return decompress(compressed, 3145728); // 3 * 1024 * 1024
        }

        @Override
        public byte[] decompress(byte[] compressed, int maxSize) throws Exception {
            if (compressed.length == 0) {
                throw new IOException("Cannot decompress empty packet");
            }

            byte header = compressed[0];
            if (header == ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET) {
                try {
                    return NONE.decompress(compressed);
                } catch (DataFormatException dfe) { // make javac happy...
                    return compressed;
                }
            }

            // header == 0x78 // zlib
            try {
                return ZLIB_RAW.decompress(compressed);
            }catch (Exception e) {
                try {
                    return ZLIB.decompress(compressed);
                } catch (Exception ex) {
                    try {
                        return NONE.decompress(compressed);
                    } catch (DataFormatException dfe) { // make javac happy again...
                        return compressed;
                    }
                }
            }
        }
        @Override
        public String toString() {
            return "netease-auto";
        }
    };

    byte[] compress(BinaryStream packet, int level) throws Exception;

    byte[] decompress(byte[] compressed) throws Exception;

    default byte[] decompress(byte[] compressed, int maxSize) throws Exception {
        return this.decompress(compressed);
    }

    /**
     * 能否写出 1.20.60+ 的每批压缩前缀。默认 {@code true} 保持既有契约：在此方法存在之前，
     * 任何能在 1.20.60+ 上工作的实现都必须已实现 {@link #getPrefix()}；无法写出前缀的编码器需显式返回 {@code false}。
     * <p>Whether this provider can emit the per-batch 1.20.60+ compression prefix. The default is {@code true}
     * to preserve the pre-existing contract: before this method existed, any provider usable on 1.20.60+ had
     * to implement {@link #getPrefix()}. Codecs without a prefixed form must override this to {@code false}.
     */
    default boolean supportsPrefix() {
        return true;
    }

    /**
     * 本批的压缩前缀，仅在 {@link #supportsPrefix()} 为 {@code true} 时有意义。
     * <p>Compression prefix of one batch; meaningful only when {@link #supportsPrefix()} is {@code true}.
     */
    default byte getPrefix() {
        throw new UnsupportedOperationException("Compression provider has no batch prefix");
    }

    static CompressionProvider from(PacketCompressionAlgorithm algorithm, int raknetProtocol) {
        if (algorithm == null) {
            return NONE;
        } else if (algorithm == PacketCompressionAlgorithm.ZLIB) {
            if (raknetProtocol == 8 && Server.getInstance().netEaseMode) {
                return ZLIB_RAW;
            }
            return raknetProtocol < 10 ? ZLIB : ZLIB_RAW;
        } else if (algorithm == PacketCompressionAlgorithm.SNAPPY) {
            return SNAPPY;
        }
        throw new UnsupportedOperationException();
    }

    static CompressionProvider byPrefix(byte prefix, int raknetProtocol) {
        switch (prefix) {
            case 0x00 -> {
                if (raknetProtocol >= 10
                        || (raknetProtocol == 8 && Server.getInstance().netEaseMode)) {
                    return ZLIB_RAW;
                } else {
                    return ZLIB;
                }
            }
            case 0x01 -> {
                return SNAPPY;
            }
            case (byte) 0xff -> {
                return NONE;
            }
            default -> throw new IllegalArgumentException("Unsupported compression type: " + prefix);
        }
    }
}
