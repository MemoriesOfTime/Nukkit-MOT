package cn.nukkit.level;

import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author LT_Name
 */
public class LevelTest {

    @Test
    public void testLocalBlockHashAll() {
        testLocalBlockHashDimensionData(DimensionData.LEGACY_DIMENSION);
        testLocalBlockHashDimensionData(DimensionEnum.OVERWORLD.getDimensionData());
        testLocalBlockHashDimensionData(DimensionEnum.NETHER.getDimensionData());
        testLocalBlockHashDimensionData(DimensionEnum.END.getDimensionData());
    }

    public static void testLocalBlockHashDimensionData(DimensionData dimensionData) {
        testLocalBlockHash(-15000000, -64, -15000000, dimensionData);
        testLocalBlockHash(-15000000, -64, 15000000, dimensionData);
        testLocalBlockHash(-15000000, 319, -15000000, dimensionData);
        testLocalBlockHash(-15000000, 319, 15000000, dimensionData);
        testLocalBlockHash(15000000, -64, -15000000, dimensionData);
        testLocalBlockHash(15000000, -64, 15000000, dimensionData);
        testLocalBlockHash(15000000, 319, -15000000, dimensionData);
        testLocalBlockHash(15000000, 319, 15000000, dimensionData);
        for (int i = 0; i < 1000; i++) {
            int x = (int) (Math.random() * 30000000 - 15000000);
            int y = (int) (Math.random() * 384) - 64;
            int z = (int) (Math.random() * 30000000 - 15000000);
            testLocalBlockHash(x, y, z, dimensionData);
        }
    }

    public static void testLocalBlockHash(int x, int y, int z, DimensionData dimensionData) {
        long chunkHash = Level.chunkHash(x >> 4, z >> 4);
        int triple = Level.localBlockHash(x, y, z, dimensionData);

        Vector3 vector3 = Level.getBlockXYZ(chunkHash, triple, dimensionData);

        Assertions.assertEquals(x, vector3.x);
        Assertions.assertEquals(Math.max(Math.min(y, dimensionData.getMaxHeight()), dimensionData.getMinHeight()), vector3.y);
        Assertions.assertEquals(z, vector3.z);
    }

}
