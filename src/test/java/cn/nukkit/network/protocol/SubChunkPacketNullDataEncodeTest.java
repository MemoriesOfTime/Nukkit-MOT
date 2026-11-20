package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 无载荷条目（错误结果 / 全空气）的编码：v486 前格式对 null data 写零长度而非 NPE。
 * 编码发生在 networkTick 的批处理里（出站队列已排空），抛 NPE 会丢弃该玩家当 tick
 * 的整个出站批次且随客户端重试复发。
 * <p>
 * Encoding payload-less entries (error results / all air): pre-v486 formats write a
 * zero-length payload for null data instead of NPE-ing. Encoding runs inside the
 * networkTick batch (after the outbound queue is drained), so an NPE would drop the
 * player's whole outbound batch for that tick and recur on every client retry.
 */
public class SubChunkPacketNullDataEncodeTest {

    private static SubChunkPacket packet(int protocol, boolean cacheEnabled, SubChunkRequestResult result) {
        SubChunkPacket pk = new SubChunkPacket();
        pk.protocol = protocol;
        pk.dimension = 0;
        pk.cacheEnabled = cacheEnabled;
        pk.centerPosition = new BlockVector3(0, 0, 0);
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        entry.position = new BlockVector3(0, 0, 0);
        entry.offset = new BlockVector3(0, 0, 0);
        entry.result = result;
        pk.subChunks.add(entry);
        return pk;
    }

    @Test
    public void errorResultsWriteZeroLengthDataOnV486() {
        for (int protocol : new int[]{ProtocolInfo.v1_19_10, ProtocolInfo.v1_21_130, ProtocolInfo.v1_26_30}) {
            for (SubChunkRequestResult result : new SubChunkRequestResult[]{
                    SubChunkRequestResult.CHUNK_NOT_FOUND, SubChunkRequestResult.INDEX_OUT_OF_BOUNDS}) {
                SubChunkPacket pk = packet(protocol, true, result);
                assertDoesNotThrow(pk::tryEncode, protocol + " " + result);
                byte[] buf = pk.getBuffer();
                // result 之后依次为：data varint、heightMapType、renderHeightMapType（≥v1_21_90）、blobId（cache）
                int tail = 1 + 1 + (protocol >= ProtocolInfo.v1_21_90 ? 1 : 0) + 8;
                int resultIdx = buf.length - tail - 1;
                assertEquals(result.ordinal(), buf[resultIdx] & 0xff, protocol + " " + result);
                assertEquals(0, buf[resultIdx + 1], "data must encode as zero-length varint");
            }
        }
    }

    @Test
    public void allAirWithoutCacheWritesZeroLengthData() {
        SubChunkPacket pk = packet(ProtocolInfo.v1_21_130, false, SubChunkRequestResult.SUCCESS_ALL_AIR);
        assertDoesNotThrow(pk::tryEncode);
        byte[] buf = pk.getBuffer();
        // 尾部：result、零长度 varint、heightMapType、renderHeightMapType
        int resultIdx = buf.length - 4;
        assertEquals(SubChunkRequestResult.SUCCESS_ALL_AIR.ordinal(), buf[resultIdx] & 0xff);
        assertEquals(0, buf[resultIdx + 1], "data must encode as zero-length varint");
    }

    @Test
    public void allAirWithCacheOmitsData() {
        SubChunkPacket pk = packet(ProtocolInfo.v1_21_130, true, SubChunkRequestResult.SUCCESS_ALL_AIR);
        assertDoesNotThrow(pk::tryEncode);
        byte[] buf = pk.getBuffer();
        // result 之后直接是 heightMapType（data 整体省略），再 renderHeightMapType、blobId
        int resultIdx = buf.length - 11;
        assertEquals(SubChunkRequestResult.SUCCESS_ALL_AIR.ordinal(), buf[resultIdx] & 0xff);
    }

    @Test
    public void v471NullDataEncodesZeroLength() {
        SubChunkPacket pk = packet(ProtocolInfo.v1_18_0, false, SubChunkRequestResult.CHUNK_NOT_FOUND);
        assertDoesNotThrow(pk::tryEncode);
    }

    @Test
    public void v2168NullDataEncodesAbsent() {
        SubChunkPacket pk = packet(ProtocolInfo.v1_26_40, true, SubChunkRequestResult.CHUNK_NOT_FOUND);
        assertDoesNotThrow(pk::tryEncode);
        byte[] buf = pk.getBuffer();
        // 条目尾部：result、hasData=false、heightMapType、hasHeightMap=false、
        // renderHeightMapType、hasRenderHeightMap=false、hasBlobId=false
        assertEquals(SubChunkRequestResult.CHUNK_NOT_FOUND.ordinal(), buf[buf.length - 7] & 0xff);
        assertEquals(0, buf[buf.length - 6], "null data must encode as the absence flag");
    }
}
