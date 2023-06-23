package cn.nukkit.network;

import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.SnappyCompression;
import cn.nukkit.utils.Zlib;

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
    };

    CompressionProvider ZLIB = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return Zlib.deflatePre16Packet(packet.getBuffer(), level);
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return Zlib.inflate(compressed, 2097152); // 2 * 1024 * 1024
        }
    };

    CompressionProvider ZLIB_RAW = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return Zlib.deflateRaw(packet.getBuffer(), level);
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return Zlib.inflateRaw(compressed, 2097152); // 2 * 1024 * 1024
        }
    };

    CompressionProvider SNAPPY = new CompressionProvider() {
        @Override
        public byte[] compress(BinaryStream packet, int level) throws Exception {
            return SnappyCompression.compress(packet.getBuffer());
        }

        @Override
        public byte[] decompress(byte[] compressed) throws Exception {
            return SnappyCompression.decompress(compressed, 2097152); // 2 * 1024 * 1024
        }
    };


    byte[] compress(BinaryStream packet, int level) throws Exception;
    byte[] decompress(byte[] compressed) throws Exception;

    static CompressionProvider from(PacketCompressionAlgorithm algorithm, int raknetProtocol) {
        if (algorithm == null) {
            return NONE;
        } else if (algorithm == PacketCompressionAlgorithm.ZLIB) {
            return raknetProtocol < 10 ? ZLIB : ZLIB_RAW;
        } else if (algorithm == PacketCompressionAlgorithm.SNAPPY) {
            return SNAPPY;
        }
        throw new UnsupportedOperationException();
    }
}