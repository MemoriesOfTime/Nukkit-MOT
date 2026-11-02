package cn.nukkit;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.process.processor.common.SubChunkRequestProcessor;
import cn.nukkit.network.protocol.SubChunkPacket;
import cn.nukkit.network.protocol.SubChunkRequestPacket;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerSubChunkTest {

    /**
     * 子区块高度图计算（规则镜像 dragonfly）：TOO_HIGH/TOO_LOW/HAS_DATA 与相对值映射。
     * <p>
     * Sub-chunk height map computation (mirrors dragonfly): TOO_HIGH / TOO_LOW / HAS_DATA
     * and the relative-value mapping.
     */
    private static final int OVERWORLD_MIN_SECTION_Y = -4; // -64

    /**
     * 世界 Y → 存档值数组（heights[i] = 世界 Y − minSectionY<<4，i = (z<<4)|x，与
     * getHeightMapArray 的元素语义一致）。
     * <p>
     * World Y → stored-value array (heights[i] = world Y − minSectionY<<4,
     * i = (z<<4)|x, matching getHeightMapArray's element semantics).
     */
    private static short[] storedHeights(IntBinaryOperator worldY, int minSectionY) {
        short[] heights = new short[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                heights[(z << 4) | x] = (short) (worldY.applyAsInt(x, z) - (minSectionY << 4));
            }
        }
        return heights;
    }

    @Test
    public void allHeightsInsideSubChunkYieldsHasData() {
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        SubChunkRequestProcessor.computeHeightMap(entry, storedHeights((x, z) -> -60, OVERWORLD_MIN_SECTION_Y), 0, OVERWORLD_MIN_SECTION_Y);
        assertEquals(SubChunkPacket.HeightMapDataType.HAS_DATA, entry.heightMapType);
        assertEquals(SubChunkPacket.HeightMapDataType.HAS_DATA, entry.renderHeightMapType);
        byte[] expected = new byte[256];
        java.util.Arrays.fill(expected, (byte) (-60 - -64)); // 相对值 4
        assertArrayEquals(expected, entry.heightMapData);
        assertArrayEquals(expected, entry.renderHeightMapData);
    }

    @Test
    public void allHeightsAboveYieldsTooHigh() {
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        SubChunkRequestProcessor.computeHeightMap(entry, storedHeights((x, z) -> 100, OVERWORLD_MIN_SECTION_Y), 0, OVERWORLD_MIN_SECTION_Y);
        assertEquals(SubChunkPacket.HeightMapDataType.TOO_HIGH, entry.heightMapType);
        assertNull(entry.heightMapData);
    }

    @Test
    public void allHeightsBelowYieldsTooLow() {
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        SubChunkRequestProcessor.computeHeightMap(entry, storedHeights((x, z) -> 70, OVERWORLD_MIN_SECTION_Y), 10, OVERWORLD_MIN_SECTION_Y); // sub 10 → y 96..111
        assertEquals(SubChunkPacket.HeightMapDataType.TOO_LOW, entry.heightMapType);
        assertNull(entry.heightMapData);
    }

    @Test
    public void mixedHeightsYieldHasDataWithMarkers() {
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        SubChunkRequestProcessor.computeHeightMap(entry, storedHeights((x, z) -> {
            if (z < 8) {
                return -60; // 本子区块内 / inside this sub-chunk
            }
            if (x < 8) {
                return 100; // 上方 / above
            }
            return -100; // 下方 / below
        }, OVERWORLD_MIN_SECTION_Y), 0, OVERWORLD_MIN_SECTION_Y);
        assertEquals(SubChunkPacket.HeightMapDataType.HAS_DATA, entry.heightMapType);

        int above = 0;
        int below = 0;
        int inside = 0;
        for (byte b : entry.heightMapData) {
            if (b == 16) {
                above++;
            } else if (b == -1) {
                below++;
            } else {
                inside++;
            }
        }
        assertEquals(8 * 8, above, "16 marker for higher columns");
        assertEquals(8 * 8, below, "-1 marker for lower columns");
        assertEquals(16 * 8, inside, "relative values for in-range columns");
        assertEquals(4, entry.heightMapData[0], "in-range value is y - subWorldY");
    }

    @Test
    public void boundaryValueMapsIntoSubChunk() {
        // sub 0 覆盖 y ∈ [-64, -49]；-49 属于它 / sub 0 covers y ∈ [-64, -49]
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        SubChunkRequestProcessor.computeHeightMap(entry, storedHeights((x, z) -> -49, OVERWORLD_MIN_SECTION_Y), 0, OVERWORLD_MIN_SECTION_Y);
        assertEquals(SubChunkPacket.HeightMapDataType.HAS_DATA, entry.heightMapType);
        assertEquals(15, entry.heightMapData[0]);

        // -64 属于它 / -64 belongs too
        SubChunkRequestProcessor.computeHeightMap(entry, storedHeights((x, z) -> -64, OVERWORLD_MIN_SECTION_Y), 0, OVERWORLD_MIN_SECTION_Y);
        assertEquals(0, entry.heightMapData[0]);
    }

    /**
     * 子区块应答的包级缓存一致性分组：cacheEnabled=true 的包里不得出现无 blobId 的
     * SUCCESS 条目——v486 格式（协议 534~2167，拉取模式主体）该开关开启时每条目必写
     * blobId，客户端会把降级 raw 条目的 data 按方块实体 NBT 解析，导致缺块/解析错乱。
     * <p>
     * Packet-level cache-consistency grouping of sub-chunk responses: a cacheEnabled=true
     * packet must never contain a SUCCESS entry without a blobId — in the v486 format
     * (protocols 534-2167, the bulk of pull mode) that switch forces a blobId per entry,
     * and the client parses a degraded raw entry's data as block-entity NBT, breaking
     * the sub-chunk.
     */
    private static SubChunkPacket.SubChunkData entry(SubChunkRequestResult result, boolean hasBlobId, int dataSize) {
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        entry.offset = new BlockVector3(0, 0, 0);
        entry.result = result;
        entry.hasBlobId = hasBlobId;
        entry.blobId = hasBlobId ? 0xC0FFEEL : 0;
        entry.data = new byte[dataSize];
        return entry;
    }

    private static SubChunkRequestProcessor.SubChunkResponseBuffer buffer(boolean cacheMode, List<SubChunkPacket> sent) {
        SubChunkRequestPacket request = new SubChunkRequestPacket();
        request.dimension = 0;
        request.subChunkPosition = new BlockVector3(1, 2, 3);
        return new SubChunkRequestProcessor.SubChunkResponseBuffer(request, cacheMode, sent::add);
    }

    /**
     * 线格式不变量：缓存包内每条 SUCCESS 条目必须带 blobId；raw 包不受限。
     * / Wire invariant: every SUCCESS entry in a cache packet carries a blobId; raw
     * packets are unrestricted.
     */
    private static void assertWireConsistent(List<SubChunkPacket> sent) {
        for (SubChunkPacket pk : sent) {
            if (pk.cacheEnabled) {
                for (SubChunkPacket.SubChunkData entry : pk.subChunks) {
                    assertFalse(entry.result == SubChunkRequestResult.SUCCESS && !entry.hasBlobId,
                            "cacheEnabled packet must not contain a SUCCESS entry without a blobId");
                }
            }
            assertEquals(2, pk.centerPosition.y, "response center must mirror the request position");
        }
    }

    @Test
    public void degradedEntrySplitsIntoRawPacket() {
        List<SubChunkPacket> sent = new ArrayList<>();
        SubChunkRequestProcessor.SubChunkResponseBuffer buffer = buffer(true, sent);

        buffer.add(entry(SubChunkRequestResult.SUCCESS, true, 64));
        // pending 打满后的降级条目：SUCCESS 但无 blobId，先翻包再入列 / a degraded entry
        // (SUCCESS, no blobId): the buffer flips before accepting it
        buffer.degradeToRaw();
        buffer.add(entry(SubChunkRequestResult.SUCCESS, false, 64));
        buffer.add(entry(SubChunkRequestResult.SUCCESS_ALL_AIR, false, 0));
        buffer.flush();

        assertEquals(2, sent.size());
        assertTrue(sent.get(0).cacheEnabled);
        assertEquals(1, sent.get(0).subChunks.size());
        assertTrue(sent.get(0).subChunks.get(0).hasBlobId);
        assertFalse(sent.get(1).cacheEnabled, "degraded entries must ride a raw packet");
        assertEquals(2, sent.get(1).subChunks.size());
        assertFalse(sent.get(1).subChunks.get(0).hasBlobId);
        assertWireConsistent(sent);
    }

    @Test
    public void degradeWithEmptyBufferStaysSingleRawPacket() {
        List<SubChunkPacket> sent = new ArrayList<>();
        SubChunkRequestProcessor.SubChunkResponseBuffer buffer = buffer(true, sent);

        buffer.degradeToRaw();
        buffer.add(entry(SubChunkRequestResult.SUCCESS, false, 16));
        buffer.add(entry(SubChunkRequestResult.CHUNK_NOT_FOUND, false, 0));
        buffer.flush();

        assertEquals(1, sent.size());
        assertFalse(sent.get(0).cacheEnabled);
        assertEquals(2, sent.get(0).subChunks.size());
        assertWireConsistent(sent);
    }

    @Test
    public void byteBudgetSplitKeepsCacheMode() {
        List<SubChunkPacket> sent = new ArrayList<>();
        SubChunkRequestProcessor.SubChunkResponseBuffer buffer = buffer(true, sent);

        // 条目线上尺寸 = 32 + data；700KB × 3 超过 2MB 预算触发即时成包
        // / Entry wire size = 32 + data; 3 x 700KB crosses the 2MB budget and flushes
        for (int i = 0; i < 4; i++) {
            buffer.add(entry(SubChunkRequestResult.SUCCESS, true, 700_000));
        }
        buffer.flush();

        assertEquals(2, sent.size());
        for (SubChunkPacket pk : sent) {
            assertTrue(pk.cacheEnabled, "byte-budget splits must not flip the packet mode");
        }
        assertEquals(3, sent.get(0).subChunks.size());
        assertEquals(1, sent.get(1).subChunks.size());
        assertWireConsistent(sent);
    }

    @Test
    public void undegradedBatchStaysSingleCachePacketInOrder() {
        List<SubChunkPacket> sent = new ArrayList<>();
        SubChunkRequestProcessor.SubChunkResponseBuffer buffer = buffer(true, sent);

        buffer.add(entry(SubChunkRequestResult.SUCCESS, true, 10));
        buffer.add(entry(SubChunkRequestResult.SUCCESS_ALL_AIR, false, 0));
        buffer.add(entry(SubChunkRequestResult.SUCCESS, true, 20));
        buffer.flush();

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).cacheEnabled);
        assertEquals(3, sent.get(0).subChunks.size());
        assertEquals(10, sent.get(0).subChunks.get(0).data.length);
        assertEquals(0, sent.get(0).subChunks.get(1).data.length);
        assertEquals(20, sent.get(0).subChunks.get(2).data.length);
        assertWireConsistent(sent);
    }

    @Test
    public void rawModeBufferNeverEmitsCachePackets() {
        List<SubChunkPacket> sent = new ArrayList<>();
        SubChunkRequestProcessor.SubChunkResponseBuffer buffer = buffer(false, sent);

        buffer.add(entry(SubChunkRequestResult.SUCCESS, false, 8));
        buffer.degradeToRaw(); // 已是 raw：no-op / already raw: no-op
        buffer.add(entry(SubChunkRequestResult.SUCCESS, false, 8));
        buffer.flush();

        assertEquals(1, sent.size());
        assertFalse(sent.get(0).cacheEnabled);
        assertEquals(2, sent.get(0).subChunks.size());
        assertWireConsistent(sent);
    }

    @Test
    public void flushWithoutEntriesSendsNothing() {
        List<SubChunkPacket> sent = new ArrayList<>();
        SubChunkRequestProcessor.SubChunkResponseBuffer buffer = buffer(true, sent);

        buffer.flush();
        buffer.degradeToRaw();
        buffer.flush();

        assertTrue(sent.isEmpty());
        assertFalse(buffer.isCacheMode());
    }
}
