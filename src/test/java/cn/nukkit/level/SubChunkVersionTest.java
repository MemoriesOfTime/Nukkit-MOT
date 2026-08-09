package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1.19.80+ 的 SubChunk 网络格式为 version 9（version + layers + 有符号 section Y）。
 * 此前对所有版本都写 version 8（无 Y 字节），导致 1.19.80+（含 1.26.40）客户端解析 chunk 失败断开。
 * <p>
 * SubChunk wire format for v1.19.80+ is version 9 (version + layers + signed section Y).
 * The old code wrote version 8 for every version (no Y byte), making 1.19.80+ (incl. 1.26.40)
 * clients fail to parse chunks and disconnect.
 */
class SubChunkVersionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void subChunkV9LayoutForV1_19_80Plus() {
        LevelDBChunkSection section = new LevelDBChunkSection(3, null, true);

        BinaryStream stream = new BinaryStream();
        section.writeTo(GameVersion.byProtocol(ProtocolInfo.v1_26_40, false), stream, false);
        byte[] buf = stream.getBuffer();

        assertEquals(9, buf[0] & 0xFF, "v2168 sub-chunk must use version 9");
        assertEquals(3, (byte) buf[2], "v2168 sub-chunk must carry the signed section-Y byte");
    }

    @Test
    void subChunkV8LayoutForV1_18() {
        LevelDBChunkSection section = new LevelDBChunkSection(3, null, true);

        BinaryStream stream = new BinaryStream();
        section.writeTo(GameVersion.byProtocol(ProtocolInfo.v1_18_30, false), stream, false);
        byte[] buf = stream.getBuffer();

        assertEquals(8, buf[0] & 0xFF, "1.18 sub-chunk must stay version 8");
    }

    @Test
    void anvilSubChunkV9LayoutForV1_19_80Plus() {
        var section = new cn.nukkit.level.format.anvil.ChunkSection(3);

        BinaryStream stream = new BinaryStream();
        section.writeTo(GameVersion.byProtocol(ProtocolInfo.v1_26_40, false), stream, false);
        byte[] buf = stream.getBuffer();

        assertEquals(9, buf[0] & 0xFF, "anvil sub-chunk must use version 9 on 1.19.80+");
        assertEquals(3, (byte) buf[2], "anvil sub-chunk must carry the signed section-Y byte");
    }

    @Test
    void anvilSubChunkV8LayoutForV1_18() {
        var section = new cn.nukkit.level.format.anvil.ChunkSection(3);

        BinaryStream stream = new BinaryStream();
        section.writeTo(GameVersion.byProtocol(ProtocolInfo.v1_18_30, false), stream, false);
        byte[] buf = stream.getBuffer();

        assertEquals(8, buf[0] & 0xFF, "anvil sub-chunk must stay version 8 below 1.19.80");
    }

    @Test
    void emptySubChunkV9LayoutForV1_19_80Plus() {
        var section = new cn.nukkit.level.format.generic.EmptyChunkSection(3);

        BinaryStream stream = new BinaryStream();
        section.writeTo(GameVersion.byProtocol(ProtocolInfo.v1_26_40, false), stream, false);
        byte[] buf = stream.getBuffer();

        assertEquals(3, buf.length, "empty v9 sub-chunk is version + layers + section-Y");
        assertEquals(9, buf[0] & 0xFF, "empty sub-chunk must use version 9 on 1.19.80+");
        assertEquals(0, buf[1] & 0xFF, "empty sub-chunk must declare 0 layers");
        assertEquals(3, (byte) buf[2], "empty sub-chunk must carry the signed section-Y byte");
    }
}
