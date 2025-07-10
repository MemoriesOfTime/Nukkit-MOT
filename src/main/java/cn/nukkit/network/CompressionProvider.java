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
    };

    CompressionProvider ZLIB = new CompressionProvider() {
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
    };

    CompressionProvider ZLIB_RAW = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return Zlib.deflateRaw(packet.getBuffer(), level);
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
    };

    CompressionProvider NETEASE_UNKNOWN = new CompressionProvider() {

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
    };

    byte[] compress(BinaryStream packet, int level) throws Exception;

    byte[] decompress(byte[] compressed) throws Exception;

    default byte[] decompress(byte[] compressed, int maxSize) throws Exception {
        return this.decompress(compressed);
    }

    default byte getPrefix() {
        throw new UnsupportedOperationException();
    }

    static CompressionProvider from(PacketCompressionAlgorithm algorithm, int raknetProtocol) {
        if (algorithm == null) {
            return NONE;
        } else if (algorithm == PacketCompressionAlgorithm.ZLIB) {
            if (raknetProtocol == 8 && Server.getInstance().netEaseMod) {
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
                        || (raknetProtocol == 8 && Server.getInstance().netEaseMod)) {
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