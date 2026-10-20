package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ClientCacheBlobStatusPacket 两种线格式解码与数组上限防护。
 * <p>
 * Decodes both ClientCacheBlobStatusPacket wire formats and enforces the per-array cap.
 */
public class ClientCacheBlobStatusDecodeTest {

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
}
