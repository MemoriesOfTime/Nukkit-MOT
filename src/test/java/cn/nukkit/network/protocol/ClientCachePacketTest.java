package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 客户端缓存相关包的编解码：ClientCacheBlobStatusPacket 两种线格式解码与数组上限防护，
 * ClientCacheMissResponsePacket 编码线格式（uvarint 计数 + (LLong + byte[]) 对）。
 * <p>
 * Client cache packet codecs: decodes both ClientCacheBlobStatusPacket wire formats with the
 * per-array cap, and encodes ClientCacheMissResponsePacket (uvarint count + (LLong + byte[]) pairs).
 */
public class ClientCachePacketTest {

    private static final int PROTO_OLD_FORMAT = GameVersion.V1_21_130.getProtocol(); // < v1_26_30
    private static final int PROTO_NEW_FORMAT = GameVersion.V1_26_30.getProtocol();  // >= 1001

    @Test
    public void legacyFormatDoubleCountPrefix() {
        BinaryStream s = new BinaryStream();
        s.putUnsignedVarInt(2); // miss count
        s.putUnsignedVarInt(1); // hit count
        s.putLLong(0x1122334455667788L);
        s.putLLong(0xAABBCCDDEEFF0011L);
        s.putLLong(0x0102030405060708L);

        ClientCacheBlobStatusPacket pk = new ClientCacheBlobStatusPacket();
        pk.protocol = PROTO_OLD_FORMAT;
        pk.setBuffer(s.getBuffer());
        pk.decode();

        assertArrayEquals(new long[]{0x1122334455667788L, 0xAABBCCDDEEFF0011L}, pk.missHashes);
        assertArrayEquals(new long[]{0x0102030405060708L}, pk.hitHashes);
    }

    @Test
    public void modernFormatPerArrayPrefix() {
        BinaryStream s = new BinaryStream();
        s.putUnsignedVarInt(1); // miss count
        s.putLLong(0x1122334455667788L);
        s.putUnsignedVarInt(2); // hit count
        s.putLLong(0x0102030405060708L);
        s.putLLong(0xAABBCCDDEEFF0011L);

        ClientCacheBlobStatusPacket pk = new ClientCacheBlobStatusPacket();
        pk.protocol = PROTO_NEW_FORMAT;
        pk.setBuffer(s.getBuffer());
        pk.decode();

        assertArrayEquals(new long[]{0x1122334455667788L}, pk.missHashes);
        assertArrayEquals(new long[]{0x0102030405060708L, 0xAABBCCDDEEFF0011L}, pk.hitHashes);
    }

    @Test
    public void rejectsOverCapArrays() {
        BinaryStream s = new BinaryStream();
        s.putUnsignedVarInt(4097); // miss count over the 4095 cap
        s.putUnsignedVarInt(0);    // hit count

        ClientCacheBlobStatusPacket pk = new ClientCacheBlobStatusPacket();
        pk.protocol = PROTO_OLD_FORMAT;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode);
    }

    @Test
    public void rejectsOverCapArrayModernFormat() {
        BinaryStream s = new BinaryStream();
        s.putUnsignedVarInt(5000); // miss count over the cap

        ClientCacheBlobStatusPacket pk = new ClientCacheBlobStatusPacket();
        pk.protocol = PROTO_NEW_FORMAT;
        pk.setBuffer(s.getBuffer());
        assertThrows(IllegalArgumentException.class, pk::decode);
    }

    @Test
    public void encodesHashPayloadPairs() {
        ClientCacheMissResponsePacket pk = new ClientCacheMissResponsePacket();
        pk.protocol = GameVersion.V1_21_130.getProtocol();
        pk.hashes = new long[]{0x1122334455667788L, 0x0000000000000099L};
        pk.payloads = new byte[][]{{'a', 'b'}, {}};
        pk.encode();
        byte[] buffer = pk.getBuffer();

        // 期望尾部（reset() 头部不参与断言）/ expected tail (the reset() header is not asserted)
        BinaryStream expected = new BinaryStream();
        expected.putUnsignedVarInt(2);
        expected.putLLong(0x1122334455667788L);
        expected.putUnsignedVarInt(2);
        expected.putByte((byte) 'a');
        expected.putByte((byte) 'b');
        expected.putLLong(0x0000000000000099L);
        expected.putUnsignedVarInt(0);
        byte[] tail = expected.getBuffer();

        assertTrue(buffer.length >= tail.length, () -> "buffer shorter than expected tail");
        byte[] actual = Arrays.copyOfRange(buffer, buffer.length - tail.length, buffer.length);
        assertArrayEquals(tail, actual);
    }
}
