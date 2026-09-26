package cn.nukkit.level.format.generic;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlockLightPopulationTest {
    @BeforeAll static void init() { cn.nukkit.MockServer.init(); }
    @Test void singleEmitterIsSymmetricOnAllSixAxes() {
        LightingFixture c = new LightingFixture();
        c.ids[LightingFixture.index(8,64,8)] = BlockID.GLOWSTONE;
        c.populateBlockLight();
        int mismatches=0,lit=0;
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=LightingFixture.MIN;y<=LightingFixture.MAX;y++) {
            int expected=Math.max(0,15-Math.abs(x-8)-Math.abs(y-64)-Math.abs(z-8));
            int actual=c.getBlockLight(x,y,z);
            if(actual>0) lit++;
            if(expected!=actual) mismatches++;
        }
        System.out.println("LIGHT_POPULATION lit="+lit+" mismatches="+mismatches);
        assertEquals(0,mismatches);
    }
    @Test void rebuildKeepsExistingBoundaryLightAsAPropagationSeed() {
        LightingFixture c=new LightingFixture();
        c.setBlockLight(0,64,8,14);
        c.populateBlockLight();
        assertEquals(14,c.getBlockLight(0,64,8));
        assertEquals(12,c.getBlockLight(2,64,8));
    }
    @Test void opaqueWallStopsPropagationAndVerticalLimitsDoNotWrap() {
        LightingFixture c=new LightingFixture();
        for(int z=0;z<16;z++) for(int y=LightingFixture.MIN;y<=LightingFixture.MAX;y++)
            c.ids[LightingFixture.index(7,y,z)]=BlockID.STONE;
        c.ids[LightingFixture.index(8,LightingFixture.MIN,8)]=BlockID.GLOWSTONE;
        c.ids[LightingFixture.index(8,LightingFixture.MAX,8)]=BlockID.GLOWSTONE;
        c.populateBlockLight();
        assertEquals(14,c.getBlockLight(8,LightingFixture.MIN+1,8));
        assertEquals(14,c.getBlockLight(8,LightingFixture.MAX-1,8));
        assertEquals(0,c.getBlockLight(6,LightingFixture.MIN,8));
    }
    @Test void darkChunkDoesNotScanEveryVoxelForPropagation() {
        LightingFixture c=new LightingFixture();
        c.populateBlockLight();
        System.out.println("DARK_CHUNK lightReads="+c.lightReads);
        assertEquals(0,c.lightReads);
    }
    @Test void levelDbMaterializesAirSectionsAndReadsCompressedLight() {
        var level=org.mockito.Mockito.mock(cn.nukkit.level.Level.class);
        var provider=org.mockito.Mockito.mock(cn.nukkit.level.format.leveldb.LevelDBProvider.class);
        org.mockito.Mockito.when(provider.getLevel()).thenReturn(level);
        org.mockito.Mockito.when(level.getDimensionData()).thenReturn(new cn.nukkit.level.DimensionData(0,-64,319));
        org.mockito.Mockito.when(level.getMinBlockY()).thenReturn(-64);
        org.mockito.Mockito.when(level.getMaxBlockY()).thenReturn(319);
        org.mockito.Mockito.when(provider.getMinBlockY()).thenReturn(-64);
        org.mockito.Mockito.when(provider.getMaxBlockY()).thenReturn(319);
        var c=cn.nukkit.level.format.leveldb.structure.LevelDBChunk.getEmptyChunk(0,0,provider);
        c.setBlock(8,63,8,BlockID.GLOWSTONE);
        c.populateBlockLight();
        assertEquals(13,c.getBlockLight(8,61,8));
        assertEquals(13,c.getBlockLight(8,65,8));
        c.compress();
        c.populateBlockLight();
        assertEquals(13,c.getBlockLight(6,63,8));
        assertEquals(13,c.getBlockLight(8,65,8));
    }
}
