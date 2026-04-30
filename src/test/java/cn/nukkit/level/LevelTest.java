package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

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

    @Test
    public void getSafeSpawnUsesScannedSafeHeight() {
        MockServer.init();

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(chunk.isGenerated()).thenReturn(true);
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> invocation.<Integer>getArgument(1) == 60 ? BlockID.STONE : BlockID.AIR);
        Mockito.when(chunk.getBlockState(anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> new int[]{
                        invocation.<Integer>getArgument(1) == 60 ? BlockID.STONE : BlockID.AIR,
                        0
                });

        Mockito.doReturn(chunk).when(level).getChunk(anyInt(), anyInt(), anyBoolean());
        Mockito.doReturn(-64).when(level).getMinBlockY();
        Mockito.doReturn(320).when(level).getMaxBlockY();

        Position safeSpawn = level.getSafeSpawn(new Vector3(0.5, 64, 0.5));

        Assertions.assertEquals(61.51, safeSpawn.y, 0.000001);
    }

    @Test
    public void getSafeSpawnKeepsConfiguredHeightWhenSpawnColumnHasNoGround() {
        MockServer.init();

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(chunk.isGenerated()).thenReturn(true);
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt())).thenReturn(BlockID.AIR);
        Mockito.when(chunk.getBlockState(anyInt(), anyInt(), anyInt())).thenReturn(new int[]{BlockID.AIR, 0});

        Mockito.doReturn(chunk).when(level).getChunk(anyInt(), anyInt(), anyBoolean());
        Mockito.doReturn(-64).when(level).getMinBlockY();
        Mockito.doReturn(320).when(level).getMaxBlockY();

        Position safeSpawn = level.getSafeSpawn(new Vector3(0.5, 64, 0.5));

        Assertions.assertEquals(64.1, safeSpawn.y, 0.000001);
    }

}
