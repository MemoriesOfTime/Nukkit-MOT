package cn.nukkit;

import cn.nukkit.network.process.processor.common.SubChunkRequestProcessor;
import cn.nukkit.network.protocol.SubChunkPacket;
import org.junit.jupiter.api.Test;

import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 子区块高度图计算（规则镜像 dragonfly）：TOO_HIGH/TOO_LOW/HAS_DATA 与相对值映射。
 * <p>
 * Sub-chunk height map computation (mirrors dragonfly): TOO_HIGH / TOO_LOW / HAS_DATA
 * and the relative-value mapping.
 */
public class PlayerSubChunkHeightMapTest {

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
}
