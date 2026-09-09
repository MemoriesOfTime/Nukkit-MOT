package cn.nukkit.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shulker box must be as fragile against explosions as vanilla Bedrock makes it.
 *
 * <p>Nukkit keeps five times the vanilla blast resistance, and {@link cn.nukkit.level.Explosion}
 * divides by five again while tracing a ray. A stored 30 therefore meant a blast resistance of 6
 * instead of the vanilla 2: TNT flattened the stone around the box and left the box itself intact.
 */
class BlockShulkerBoxBlastResistanceTest {

    private static final double VANILLA_BLAST_RESISTANCE = 2d;
    private static final double NUKKIT_SCALE = 5d;

    @Test
    void dyedAndUndyedBoxesKeepVanillaBlastResistance() {
        assertEquals(VANILLA_BLAST_RESISTANCE * NUKKIT_SCALE,
                new BlockShulkerBox().getResistance(),
                1e-12,
                "dyed shulker box blast resistance");
        assertEquals(VANILLA_BLAST_RESISTANCE * NUKKIT_SCALE,
                new BlockUndyedShulkerBox().getResistance(),
                1e-12,
                "undyed shulker box inherits the same value");
    }

    @Test
    void boxIsNotTougherThanAChest() {
        assertTrue(new BlockShulkerBox().getResistance() < new BlockChest().getResistance(),
                "vanilla shulker boxes are weaker than chests, not stronger");
    }

    @Test
    void primedTntRemovesTheBoxThroughTheExplosionFormula() {
        // The ray in Explosion#explodeA starts at 0.7..1.3 times the blast size and pays
        // (resistance / 5 + 0.3) * 0.3 for every step through a block.
        double weakestRay = 4d * 0.7d;
        double cost = (new BlockShulkerBox().getResistance() / NUKKIT_SCALE + 0.3d) * 0.3d;
        assertTrue(weakestRay - cost > 0d,
                "even the weakest TNT ray must break through a shulker box");
    }
}
