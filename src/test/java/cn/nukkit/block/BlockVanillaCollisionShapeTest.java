package cn.nukkit.block;

import cn.nukkit.block.properties.enums.DripstoneThickness;
import cn.nukkit.math.AxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Collision boxes must match the client (minecraft-data bedrock/1.26.30
 * blockCollisionShapes.json). A server-only full cube lifts, blocks or holds players
 * where the client sees air.
 */
class BlockVanillaCollisionShapeTest {

    private static final double EPS = 1.0E-6;

    private static void assertBox(AxisAlignedBB box, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        assertEquals(minX, box.getMinX(), EPS);
        assertEquals(minY, box.getMinY(), EPS);
        assertEquals(minZ, box.getMinZ(), EPS);
        assertEquals(maxX, box.getMaxX(), EPS);
        assertEquals(maxY, box.getMaxY(), EPS);
        assertEquals(maxZ, box.getMaxZ(), EPS);
    }

    private static BlockPointedDripstone dripstone(DripstoneThickness thickness, boolean hanging) {
        BlockPointedDripstone block = new BlockPointedDripstone();
        block.setThickness(thickness);
        block.setHanging(hanging);
        block.setComponents(10, 40, -3);
        return block;
    }

    @Test
    void snifferEggMatchesBedrockShape() {
        BlockSnifferEgg egg = new BlockSnifferEgg();
        egg.setComponents(10, 40, -3);
        assertBox(egg.getBoundingBox(), 10 + 1 / 16d, 40, -3 + 2 / 16d, 10 + 15 / 16d, 41, -3 + 14 / 16d);
    }

    @Test
    void standingTipIsElevenSixteenthsHighAndSixWide() {
        assertBox(dripstone(DripstoneThickness.TIP, false).getBoundingBox(),
                10 + 5 / 16d, 40, -3 + 5 / 16d, 10 + 11 / 16d, 40 + 11 / 16d, -3 + 11 / 16d);
    }

    @Test
    void hangingTipStartsFiveSixteenthsUp() {
        assertBox(dripstone(DripstoneThickness.TIP, true).getBoundingBox(),
                10 + 5 / 16d, 40 + 5 / 16d, -3 + 5 / 16d, 10 + 11 / 16d, 41, -3 + 11 / 16d);
    }

    @Test
    void widthFollowsThicknessInBothDirections() {
        double[][] expected = {
                // thickness ordinal -> half width in sixteenths
                {DripstoneThickness.MERGE.ordinal(), 3},
                {DripstoneThickness.FRUSTUM.ordinal(), 4},
                {DripstoneThickness.MIDDLE.ordinal(), 5},
                {DripstoneThickness.BASE.ordinal(), 6}};
        for (double[] row : expected) {
            DripstoneThickness thickness = DripstoneThickness.values()[(int) row[0]];
            double half = row[1] / 16d;
            for (boolean hanging : new boolean[]{false, true}) {
                assertBox(dripstone(thickness, hanging).getBoundingBox(),
                        10.5 - half, 40, -2.5 - half, 10.5 + half, 41, -2.5 + half);
            }
        }
    }
}
