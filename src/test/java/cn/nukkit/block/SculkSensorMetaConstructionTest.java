package cn.nukkit.block;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies Block.get() constructs BlockSculkSensor (BlockTransparentMeta) with the requested meta,
 * and that getDamage()/getFullId()/getPhase() reflect it.
 */
public class SculkSensorMetaConstructionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void blockGetPreservesMetaForSculkSensor() {
        for (int phase : new int[]{0, 1, 2}) {
            BlockSculkSensor b = (BlockSculkSensor) Block.get(BlockID.SCULK_SENSOR, phase);
            assertEquals(phase, b.getDamage(), "getDamage must equal requested meta for phase " + phase);
            assertEquals(phase, b.getPhase(), "getPhase must reflect meta for phase " + phase);
            assertEquals(BlockID.SCULK_SENSOR, b.getFullId() >>> Block.DATA_BITS);
            assertEquals(phase, b.getFullId() & Block.DATA_MASK);
        }
    }

    @Test
    void blockGetPreservesMetaForCalibratedSculkSensor() {
        // Calibrated packs phase in bits 2-3 (facing NORTH=0). metas: 0=inactive, 4=active, 8=cooldown.
        for (int meta : new int[]{0, 4, 8}) {
            BlockCalibratedSculkSensor b = (BlockCalibratedSculkSensor) Block.get(BlockID.CALIBRATED_SCULK_SENSOR, meta);
            assertEquals(meta, b.getDamage(), "getDamage must equal requested meta " + meta);
        }
    }

    @Test
    void blockGetPreservesMetaForSculkShrieker() {
        // Shrieker stores bit 0 = shrieking, bit 1 = can_summon.
        for (int meta : new int[]{0, 1, 2, 3}) {
            BlockSculkShrieker b = (BlockSculkShrieker) Block.get(BlockID.SCULK_SHRIEKER, meta);
            assertEquals(meta, b.getDamage(), "getDamage must equal requested meta " + meta);
        }
    }
}
