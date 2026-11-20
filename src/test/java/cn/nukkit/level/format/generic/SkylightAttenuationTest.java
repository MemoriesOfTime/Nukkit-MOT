package cn.nukkit.level.format.generic;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkylightAttenuationTest {
    @BeforeAll static void init() { Block.init(); }
    @Test void transparentFilterNeverWrapsAboveFifteen() {
        LightingFixture c = new LightingFixture();
        c.tops[0] = 65;
        c.ids[LightingFixture.index(0,64,0)] = BlockID.GLASS;
        c.populateSkyLight();
        assertEquals(15, c.getBlockSkyLight(0,64,0));
        assertEquals(14, c.getBlockSkyLight(0,63,0));
        for (int y=63; y>LightingFixture.MIN; y--)
            assertTrue(c.getBlockSkyLight(0,y-1,0) <= c.getBlockSkyLight(0,y,0));
    }
    @Test void opaqueRoofStaysDarkBelowAndOpenColumnStaysFifteen() {
        LightingFixture c = new LightingFixture();
        c.tops[0] = 65;
        c.ids[LightingFixture.index(0,64,0)] = BlockID.STONE;
        c.populateSkyLight();
        assertEquals(0,c.getBlockSkyLight(0,63,0));
        assertEquals(15,c.getBlockSkyLight(1,LightingFixture.MIN,0));
        assertEquals(16L*16*384,c.skyWrites);
    }
}
