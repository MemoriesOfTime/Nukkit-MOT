package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClientCacheMissResponsePacket 编码线格式：uvarint 计数 + (LLong + byte[]) 对。
 * <p>
 * ClientCacheMissResponsePacket wire format: uvarint count + (LLong + byte[]) pairs.
 */
public class ClientCacheMissResponseEncodeTest {

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
