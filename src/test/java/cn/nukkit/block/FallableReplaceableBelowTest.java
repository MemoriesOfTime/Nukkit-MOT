package cn.nukkit.block;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gravity blocks fall through replaceable vegetation and thin snow, as in vanilla Bedrock. */
class FallableReplaceableBelowTest {
    @BeforeAll static void bootstrap() { MockServer.init(); }

    private static boolean falls(int falling, int below, int meta) {
        return BlockFallable.isFallThrough(Block.get(falling), Block.get(below, meta));
    }

    @Test void sandAndGravelFallThroughReplaceablePlants() {
        for (int falling : new int[]{Block.SAND, Block.GRAVEL, Block.CONCRETE_POWDER, Block.ANVIL}) {
            assertTrue(falls(falling, Block.SEAGRASS, 0), "seagrass on the sea floor");
            assertTrue(falls(falling, Block.TALL_GRASS, 1), "short grass");
            assertTrue(falls(falling, Block.DEAD_BUSH, 0), "dead bush");
            assertTrue(falls(falling, Block.VINE, 0), "vine");
            assertTrue(falls(falling, Block.GLOW_LICHEN, 0), "glow lichen");
            assertTrue(falls(falling, Block.SNOW_LAYER, 0), "thin snow");
        }
    }

    @Test void previousCellsStillFall() {
        assertTrue(falls(Block.SAND, Block.AIR, 0));
        assertTrue(falls(Block.SAND, Block.STILL_WATER, 0));
        assertTrue(falls(Block.GRAVEL, Block.FIRE, 0));
    }

    @Test void solidAndNonReplaceableBlocksStillSupport() {
        assertFalse(falls(Block.SAND, Block.STONE, 0));
        assertFalse(falls(Block.SAND, Block.RED_FLOWER, 0), "flowers support, as in vanilla");
        assertFalse(falls(Block.SAND, Block.TORCH, 0), "torches support, as in vanilla");
        assertFalse(falls(Block.SAND, Block.SNOW_LAYER, 7), "full snow layer supports");
        assertFalse(falls(Block.SNOW_LAYER, Block.SNOW_LAYER, 0), "snow never sinks into snow");
    }
}
